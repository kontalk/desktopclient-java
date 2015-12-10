/*
 *  Kontalk Java client
 *  Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.client;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.IQTypeFilter;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smackx.caps.EntityCapsManager;
import org.jivesoftware.smackx.caps.cache.SimpleDirectoryPersistentCache;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.pubsub.PubSubManager;
import org.kontalk.Kontalk;
import org.kontalk.system.Config;
import org.kontalk.misc.KonException;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.misc.JID;
import org.kontalk.model.OutMessage;
import org.kontalk.system.Control;
import org.kontalk.system.RosterHandler;

/**
 * Network client for an XMPP Kontalk Server.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class Client implements StanzaListener, Runnable {
    private static final Logger LOGGER = Logger.getLogger(Client.class.getName());

    private static final String CAPS_CACHE_DIR = "caps_cache";
    private static final LinkedBlockingQueue<Task> TASK_QUEUE = new LinkedBlockingQueue<>();

    public enum PresenceCommand {REQUEST, GRANT, DENY};

    private static enum Command {CONNECT, DISCONNECT};

    private final Control mControl;

    private final KonMessageSender mMessageSender;

    private KonConnection mConn = null;
    private AvatarSendReceiver mAvatarSendReceiver = null;

    public Client(Control control) {
        mControl = control;
        //mLimited = limited;

        mMessageSender = new KonMessageSender(this, mControl);

        // enable Smack debugging (print raw XML packet)
        //SmackConfiguration.DEBUG = true;

        // setting caps cache
        File cacheDir = Kontalk.appDir().resolve(CAPS_CACHE_DIR).toFile();
        if (cacheDir.mkdir())
            LOGGER.info("created caps cache directory");

        if (!cacheDir.isDirectory()) {
            LOGGER.warning("invalid cache directory: "+cacheDir);
            return;
        }

        EntityCapsManager.setPersistentCache(
                new SimpleDirectoryPersistentCache(cacheDir));
    }

    public void connect(PersonalKey key) {
        this.disconnect();

        LOGGER.config("connecting...");
        mControl.setStatus(Control.Status.CONNECTING);

        Config config = Config.getInstance();
        //String network = config.getString(KonConf.SERV_NET);
        String host = config.getString(Config.SERV_HOST);
        int port = config.getInt(Config.SERV_PORT);
        EndpointServer server = new EndpointServer(host, port);

        boolean validateCertificate = config.getBoolean(Config.SERV_CERT_VALIDATION);

        // create connection
        mConn = new KonConnection(server,
                        key.getServerLoginKey(),
                        key.getBridgeCertificate(),
                        validateCertificate);

        // connection listener
        mConn.addConnectionListener(new KonConnectionListener(mControl));

        Roster roster = Roster.getInstanceFor(mConn);
        // subscriptions handled by roster handler
        roster.setSubscriptionMode(Roster.SubscriptionMode.manual);

        mAvatarSendReceiver = new AvatarSendReceiver(mConn, mControl.getAvatarHandler());

        // packet listeners
        RosterHandler rosterHandler = mControl.getRosterHandler();
        RosterListener rl = new KonRosterListener(roster, rosterHandler);
        roster.addRosterListener(rl);

        StanzaFilter messageFilter = new StanzaTypeFilter(Message.class);
        mConn.addAsyncStanzaListener(
                new KonMessageListener(this, mControl, mAvatarSendReceiver),
                messageFilter);

        StanzaFilter vCardFilter = new StanzaTypeFilter(VCard4.class);
        mConn.addAsyncStanzaListener(new VCardListener(mControl), vCardFilter);

        StanzaFilter blockingCommandFilter = new StanzaTypeFilter(BlockingCommand.class);
        mConn.addAsyncStanzaListener(new BlockListListener(mControl), blockingCommandFilter);

        StanzaFilter publicKeyFilter = new StanzaTypeFilter(PublicKeyPublish.class);
        mConn.addAsyncStanzaListener(new PublicKeyListener(mControl), publicKeyFilter);

        StanzaFilter presenceFilter = new StanzaTypeFilter(Presence.class);
        mConn.addAsyncStanzaListener(new PresenceListener(roster, rosterHandler), presenceFilter);

        // our service discovery: want avatar from other users
        ServiceDiscoveryManager.getInstanceFor(mConn).
                addFeature(AvatarSendReceiver.NOTIFY_FEATURE);

        // listen to all IQ errors
        mConn.addAsyncStanzaListener(this, IQTypeFilter.ERROR);

        // continue async
        List<?> args = new ArrayList<>(0);
        Client.TASK_QUEUE.offer(new Client.Task(Client.Command.CONNECT, args));
    }

    private void connectAsync() {
        // TODO unsure if everything is thread-safe
        synchronized (this) {
            // connect
            try {
                mConn.connect();
            } catch (XMPPException | SmackException | IOException ex) {
                LOGGER.log(Level.WARNING, "can't connect to "+mConn.getServer(), ex);
                mControl.setStatus(Control.Status.FAILED);
                mControl.handleException(new KonException(KonException.Error.CLIENT_CONNECT, ex));
                return;
            }

            // login
            try {
                mConn.login();
            } catch (XMPPException | SmackException | IOException ex) {
                LOGGER.log(Level.WARNING, "can't login on "+mConn.getServer(), ex);
                mConn.disconnect();
                mControl.setStatus(Control.Status.FAILED);
                mControl.handleException(new KonException(KonException.Error.CLIENT_LOGIN, ex));
                return;
            }
        }

        mConn.addStanzaAcknowledgedListener(new AcknowledgedListener(mControl));

        // (server) service discovery, XEP-0030
        // NOTE: smack automatically creates instances of SDM and CapsM and connects them
        //ServiceDiscoveryManager discoManager = ServiceDiscoveryManager.getInstanceFor(mConn);
//        try {
//            // blocking
//            // NOTE: null parameter does not work
//            DiscoverInfo i = discoManager.discoverInfo(mConn.getServiceName());
//            for (DiscoverInfo.Feature f: i.getFeatures()) {
//                System.out.println("server feature: "+f.getVar());
//            }
//        } catch (SmackException.NoResponseException |
//                XMPPException.XMPPErrorException |
//                SmackException.NotConnectedException ex) {
//            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
//        }

        // Caps, XEP-0115
        // NOTE: caps manager is automatically used by Smack
        //EntityCapsManager capsManager = EntityCapsManager.getInstanceFor(mConn);

        // PEP, XEP-0163
        // NOTE: Smack's implementation is not usable, use PubSub instead
//        PEPManager m = new PEPManager(mConn);
//        m.addPEPListener(new PEPListener() {
//            @Override
//            public void eventReceived(String from, PEPEvent event) {
//                LOGGER.info("from: "+from+" event: "+event);
//            }
//        });

        // PubSub, XEP-0060
        // NOTE: pubsub is currently unsupported by beta.kontalk.net
//        PubSubManager pubSubManager = new PubSubManager(mConn, mConn.getServiceName());
//        try {
//            DiscoverInfo i = pubSubManager.getSupportedFeatures();
//            // same as server service discovery features!?
//            for (DiscoverInfo.Feature f: i.getFeatures()) {
//                System.out.println("feature: "+f.getVar());
//            }
//        } catch (SmackException.NoResponseException |
//                XMPPException.XMPPErrorException |
//                SmackException.NotConnectedException ex) {
//            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
//        }
        // here be exceptions
//        try {
//            for (Affiliation a: pubSubManager.getAffiliations()) {
//                System.out.println("aff: "+a.toXML());
//            }
//            for (Subscription s: pubSubManager.getSubscriptions()) {
//                System.out.println("subs: "+s.toXML());
//            }
//        } catch (SmackException.NoResponseException |
//                XMPPException.XMPPErrorException |
//                SmackException.NotConnectedException ex) {
//            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
//        }

        this.sendBlocklistRequest();

        mControl.setStatus(Control.Status.CONNECTED);
    }

    public void disconnect() {
        synchronized (this) {
            if (mConn != null && mConn.isConnected()) {
                mConn.disconnect();
            }
        }
        mControl.setStatus(Control.Status.DISCONNECTED);
    }

    public boolean isConnected() {
        return mConn != null && mConn.isAuthenticated();
    }

    /**
     * The full JID of the user currently logged in.
     */
    public Optional<JID> getOwnJID() {
        String user = mConn.getUser();
        if (user == null)
            return Optional.empty();
        return Optional.of(JID.full(user));
    }

    public boolean sendMessage(OutMessage message, boolean sendChatState) {
        return mMessageSender.sendMessage(message, sendChatState);
    }

    // TODO unused
    public void sendVCardRequest(String jid) {
        VCard4 vcard = new VCard4();
        vcard.setType(IQ.Type.get);
        vcard.setTo(jid);
        this.sendPacket(vcard);
    }

    public void sendPublicKeyRequest(JID jid) {
        LOGGER.info("to "+jid);
        PublicKeyPublish publicKeyRequest = new PublicKeyPublish();
        publicKeyRequest.setTo(jid.string());
        this.sendPacket(publicKeyRequest);
    }

    public void sendBlocklistRequest() {
        this.sendPacket(BlockingCommand.blocklist());
    }

    public void sendBlockingCommand(JID jid, boolean blocking) {
        if (mConn == null || !this.isConnected()) {
            LOGGER.warning("not connected");
            return;
        }

        new BlockSendReceiver(mControl, mConn, blocking, jid).sendAndListen();
    }

    public void sendUserPresence(String statusText) {
        Presence presence = new Presence(Presence.Type.available);
        if (!statusText.isEmpty())
            presence.setStatus(statusText);

        // note: not setting priority, according to anti-dicrimination rules;)

        // for testing
        //presence.addExtension(new PresenceSignature(""));

        this.sendPacket(presence);
    }

    public void sendPresenceSubscription(JID jid, PresenceCommand command) {
        LOGGER.info("to: "+jid+ ", command: "+command);
        Presence.Type type = null;
        switch(command) {
            case REQUEST: type = Presence.Type.subscribe; break;
            case GRANT: type = Presence.Type.subscribed; break;
            case DENY: type = Presence.Type.unsubscribed; break;
        }
        Presence presence = new Presence(type);
        presence.setTo(jid.string());
        this.sendPacket(presence);
    }

    public void sendChatState(JID jid, String threadID, ChatState state) {
        Message message = new Message(jid.string(), Message.Type.chat);
        if (!threadID.isEmpty())
            message.setThread(threadID);
        message.addExtension(new ChatStateExtension(state));

        this.sendPacket(message);
    }

    synchronized boolean sendPackets(Stanza[] stanzas) {
        boolean sent = true;
        for (Stanza s: stanzas)
            sent &= this.sendPacket(s);
        return sent;
    }

    synchronized boolean sendPacket(Stanza p) {
        if (mConn == null) {
            LOGGER.warning("not connected");
            return false;
        }

        return mConn.send(p);
    }

    @Override
    public void processPacket(Stanza packet) {
        LOGGER.warning("IQ error: "+packet);
    }

    public boolean addToRoster(JID jid, String name) {
        if (!this.isConnected()) {
            LOGGER.info("not connected");
            return false;
        }

        try {
            // also sends presence subscription request
            Roster.getInstanceFor(mConn).createEntry(jid.string(), name,
                    null);
        } catch (SmackException.NotLoggedInException |
                SmackException.NoResponseException |
                XMPPException.XMPPErrorException |
                SmackException.NotConnectedException ex) {
            LOGGER.log(Level.WARNING, "can't add contact to roster", ex);
            return false;
        }
        return true;
    }

    public boolean removeFromRoster(JID jid) {
        if (!this.isConnected()) {
            LOGGER.info("not connected");
            return false;
        }
        Roster roster = Roster.getInstanceFor(mConn);
        RosterEntry entry = roster.getEntry(jid.string());
        if (entry == null) {
            LOGGER.warning("can't find roster entry for jid: "+jid);
            return true;
        }
        try {
            // blocking
            roster.removeEntry(entry);
        } catch (SmackException.NotLoggedInException |
                SmackException.NoResponseException |
                XMPPException.XMPPErrorException |
                SmackException.NotConnectedException ex) {
            LOGGER.log(Level.WARNING, "can't remove contact from roster", ex);
            return false;
        }
        return true;
    }

    public boolean updateRosterEntry(JID jid, String newName) {
        if (!this.isConnected()) {
            LOGGER.info("not connected");
            return false;
        }
        Roster roster = Roster.getInstanceFor(mConn);
        RosterEntry entry = roster.getEntry(jid.string());
        if (entry == null) {
            LOGGER.warning("can't find roster entry for jid: "+jid);
            return true;
        }
        try {
            entry.setName(newName);
        } catch (SmackException.NotConnectedException |
                SmackException.NoResponseException |
                XMPPException.XMPPErrorException ex) {
            LOGGER.log(Level.WARNING, "can't set name for entry", ex);
        }
        return true;
    }

    public void requestAvatar(JID jid, String id) {
        if (mAvatarSendReceiver == null) {
            LOGGER.warning("no avatar sender");
            return;
        }
        mAvatarSendReceiver.requestAndListen(jid, id);
    }

    public void publishAvatar(String id, byte[] data) {
        if (mConn == null)
            return;

        // TODO
        PubSubManager pubSubManager = new PubSubManager(mConn, mConn.getServiceName());
        new AvatarSender(pubSubManager).publish(id, data);
    }

    @Override
    public void run() {
        while (true) {
            Task t;
            try {
                // blocking
                t = TASK_QUEUE.take();
            } catch (InterruptedException ex) {
                LOGGER.log(Level.WARNING, "interrupted while waiting ", ex);
                return;
            }
            switch (t.command) {
                case CONNECT:
                    this.connectAsync();
                    break;
                case DISCONNECT:
                    this.disconnect();
                    break;
            }
        }
    }

    private static class Task {

        final Command command;
        final List<?> args;

        Task(Command c, List<?> a) {
            command = c;
            args = a;
        }
    }
}

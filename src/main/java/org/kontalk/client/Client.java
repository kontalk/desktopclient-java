/*
 *  Kontalk Java client
 *  Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.IQTypeFilter;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smackx.caps.EntityCapsManager;
import org.jivesoftware.smackx.caps.cache.SimpleDirectoryPersistentCache;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.iqlast.packet.LastActivity;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.misc.JID;
import org.kontalk.misc.KonException;
import org.kontalk.model.message.OutMessage;
import org.kontalk.persistence.Config;
import org.kontalk.system.AttachmentManager;
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

    // NOTE: disconnect is instantaneous, all resulting exceptions should be catched
    private enum Command {CONNECT, LAST_ACTIVITY};

    private final Control mControl;

    private final KonMessageSender mMessageSender;
    private final EnumMap<FeatureDiscovery.Feature, String> mFeatures;

    private KonConnection mConn = null;
    private AvatarSendReceiver mAvatarSendReceiver = null;
    private HTTPFileSlotRequester mSlotRequester = null;
    private FeatureDiscovery mFeatureDiscovery = null;

    private Client(Control control, Path appDir) {
        mControl = control;
        //mLimited = limited;

        mMessageSender = new KonMessageSender(this);

        // enable Smack debugging (print raw XML packets)
        //SmackConfiguration.DEBUG = true;

        mFeatures = new EnumMap<>(FeatureDiscovery.Feature.class);

        // setting caps cache
        File cacheDir = appDir.resolve(CAPS_CACHE_DIR).toFile();
        if (cacheDir.mkdir())
            LOGGER.info("created caps cache directory");

        if (!cacheDir.isDirectory()) {
            LOGGER.warning("invalid cache directory: "+cacheDir);
            return;
        }

        EntityCapsManager.setPersistentCache(
                new SimpleDirectoryPersistentCache(cacheDir));
    }

    public static Client create(Control control, Path appDir) {
        Client client = new Client(control, appDir);

        Thread clientThread = new Thread(client, "Client Connector");
        clientThread.setDaemon(true);
        clientThread.start();

        return client;
    }

    public void connect(PersonalKey key) {
        this.disconnect();

        LOGGER.config("connecting...");
        this.newStatus(Control.Status.CONNECTING);

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
        mConn.addConnectionListener(new KonConnectionListener(this, mControl));

        Roster roster = Roster.getInstanceFor(mConn);
        // subscriptions handled by roster handler
        roster.setSubscriptionMode(Roster.SubscriptionMode.manual);

        mAvatarSendReceiver = new AvatarSendReceiver(mConn, mControl.getAvatarHandler());

        // packet listeners
        RosterHandler rosterHandler = mControl.getRosterHandler();
        KonRosterListener rl = new KonRosterListener(roster, rosterHandler);
        roster.addRosterListener(rl);
        roster.addRosterLoadedListener(rl);

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

        StanzaFilter lastActivityFilter = new StanzaTypeFilter(LastActivity.class);
        mConn.addAsyncStanzaListener(new LastActivityListener(mControl), lastActivityFilter);

        if (config.getBoolean(Config.NET_REQUEST_AVATARS)) {
            // our service discovery: want avatar from other users
            ServiceDiscoveryManager.getInstanceFor(mConn).
                    addFeature(AvatarSendReceiver.NOTIFY_FEATURE);
        }

        // listen to all ACKs
        mConn.addStanzaAcknowledgedListener(new AcknowledgedListener(mControl));

        // listen to all IQ errors
        mConn.addAsyncStanzaListener(this, IQTypeFilter.ERROR);

        // continue async
        Client.TASK_QUEUE.offer(new Client.Task(Client.Command.CONNECT, new ArrayList<>(0)));
    }

    private void connectAsync() {
        // TODO unsure if everything is thread-safe
        synchronized (this) {
            // connect
            try {
                mConn.connect();
            } catch (XMPPException | SmackException | IOException ex) {
                LOGGER.log(Level.WARNING, "can't connect to "+mConn.getServer(), ex);
                this.newStatus(Control.Status.FAILED);
                mControl.onException(new KonException(KonException.Error.CLIENT_CONNECT, ex));
                return;
            }

            // login
            try {
                mConn.login();
            } catch (XMPPException | SmackException | IOException ex) {
                LOGGER.log(Level.WARNING, "can't login on "+mConn.getServer(), ex);
                mConn.disconnect();
                this.newStatus(Control.Status.FAILED);
                mControl.onException(new KonException(KonException.Error.CLIENT_LOGIN, ex));
                return;
            }
        }

        mFeatureDiscovery = new FeatureDiscovery(mConn);

        mFeatures.clear();
        mFeatures.putAll(mFeatureDiscovery.getServerFeatures());

        mSlotRequester = mFeatures.containsKey(FeatureDiscovery.Feature.HTTP_FILE_UPLOAD) ?
                new HTTPFileSlotRequester(mConn,
                        JID.bare(mFeatures.get(FeatureDiscovery.Feature.HTTP_FILE_UPLOAD))) :
                null;

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

        this.newStatus(Control.Status.CONNECTED);
    }

    public void disconnect() {
        if (mConn != null && mConn.isConnected()) {
            this.newStatus(Control.Status.DISCONNECTING);
            mConn.disconnect();
        }
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

    public EnumSet<FeatureDiscovery.Feature> getServerFeature() {
        EnumSet<FeatureDiscovery.Feature> e = EnumSet.noneOf(FeatureDiscovery.Feature.class);
        e.addAll(mFeatures.keySet());
        return e;
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

    public void sendLastActivityRequest(JID jid) {
        Client.TASK_QUEUE.offer(new Client.Task(Client.Command.LAST_ACTIVITY, Arrays.asList(jid)));
    }

    private void sendLastActivityRequestAsync(JID jid) {
        if (mFeatureDiscovery == null) {
            LOGGER.warning("no feature discovery");
            return;
        }

        // blocking
        if (!mFeatureDiscovery.getFeaturesFor(jid.domain())
                .containsKey(FeatureDiscovery.Feature.LAST_ACTIVITY))
            // not supported by server
            return;

        LastActivity request = new LastActivity(jid.string());
        this.sendPacket(request);
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
            LOGGER.info("can't find roster entry for jid: "+jid);
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
        if (mAvatarSendReceiver == null) {
            LOGGER.warning("no avatar sender");
            return;
        }
        if (mFeatures.containsKey(FeatureDiscovery.Feature.USER_AVATAR)) {
            mAvatarSendReceiver.publish(id, data);
        } else {
            LOGGER.warning("not supported by server");
        }
    }

    public boolean deleteAvatar() {
        if (mAvatarSendReceiver == null) {
            LOGGER.warning("no avatar sender");
            return false;
        }

        if (mFeatures.containsKey(FeatureDiscovery.Feature.USER_AVATAR)) {
            return mAvatarSendReceiver.delete();
        } else {
            LOGGER.warning("not supported by server");
            // if not supported there should be no avatar set
            return true;
        }
    }

    /** Request upload slot (XEP-0636). Blocking */
    public AttachmentManager.Slot getUploadSlot(String name, long length, String mime) {
        if (mSlotRequester == null) {
            LOGGER.warning("no slot requester");
            return new AttachmentManager.Slot();
        }

        return mSlotRequester.getSlot(name, length, mime);
    }

    /* package internal*/

    void newStatus(Control.Status status) {
        if (status != Control.Status.CONNECTED)
            mFeatures.clear();

        mControl.onStatusChange(status, this.getServerFeature());
    }

    void newException(KonException konException) {
        mControl.onException(konException);
    }

    String multiAddressHost() {
        return mFeatures.containsKey(FeatureDiscovery.Feature.MULTI_ADDRESSING)
                && mConn != null ? mConn.getHost() : "";
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
                case LAST_ACTIVITY:
                    this.sendLastActivityRequestAsync((JID) t.args.get(0));
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

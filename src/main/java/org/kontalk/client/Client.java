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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bouncycastle.openpgp.PGPException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.NotFilter;
import org.jivesoftware.smack.filter.OrFilter;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.filter.StanzaIdFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.packet.RosterPacket;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.system.Config;
import org.kontalk.misc.KonException;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.model.KonMessage.Status;
import org.kontalk.model.OutMessage;
import org.kontalk.model.Contact;
import org.kontalk.system.Control;
import org.kontalk.util.XMPPUtils;

/**
 * Network client for an XMPP Kontalk Server.
 *
 * Note: By default incoming presence subscription requests are automatically
 * granted by Smack (but Kontalk uses a custom subscription request!?)
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class Client implements StanzaListener, Runnable {
    private static final Logger LOGGER = Logger.getLogger(Client.class.getName());

    private static final LinkedBlockingQueue<Task> TASK_QUEUE = new LinkedBlockingQueue<>();

    private static enum Command {CONNECT, DISCONNECT};

    private final Control mControl;
    private KonConnection mConn = null;

    public Client(Control control) {
        mControl = control;
        //mLimited = limited;

        // enable debug window
        //SmackConfiguration.DEBUG_ENABLED = true;
    }

    public void connect(PersonalKey key) {
        this.disconnect();
        mControl.setStatus(Control.Status.CONNECTING);

        Config config = Config.getInstance();
        // tigase: use hostname as network
        //String network = config.getString(KonConf.SERV_NET);
        String network = config.getString(Config.SERV_HOST);
        String host = config.getString(Config.SERV_HOST);
        int port = config.getInt(Config.SERV_PORT);
        EndpointServer server = new EndpointServer(network, host, port);
        boolean validateCertificate = config.getBoolean(Config.SERV_CERT_VALIDATION);

        // create connection
        try {
            mConn = new KonConnection(server,
                    key.getBridgePrivateKey(),
                    key.getBridgeCertificate(),
                    validateCertificate);
        } catch (PGPException ex) {
            LOGGER.log(Level.WARNING, "can't create connection", ex);
            mControl.setStatus(Control.Status.FAILED);
            mControl.handleException(new KonException(KonException.Error.CLIENT_CONNECTION, ex));
            return;
        }

        // connection listener
        mConn.addConnectionListener(new KonConnectionListener(mControl));

        // packet listeners
        RosterListener rl = new KonRosterListener(Roster.getInstanceFor(mConn), mControl);
        Roster.getInstanceFor(mConn).addRosterListener(rl);

        StanzaFilter messageFilter = new StanzaTypeFilter(Message.class);
        mConn.addAsyncStanzaListener(new KonMessageListener(this, mControl), messageFilter);

        StanzaFilter vCardFilter = new StanzaTypeFilter(VCard4.class);
        mConn.addAsyncStanzaListener(new VCardListener(mControl), vCardFilter);

        StanzaFilter blockingCommandFilter = new StanzaTypeFilter(BlockingCommand.class);
        mConn.addAsyncStanzaListener(new BlockListListener(mControl), blockingCommandFilter);

        StanzaFilter publicKeyFilter = new StanzaTypeFilter(PublicKeyPublish.class);
        mConn.addAsyncStanzaListener(new PublicKeyListener(mControl), publicKeyFilter);

        StanzaFilter presenceFilter = new StanzaTypeFilter(Presence.class);
        mConn.addAsyncStanzaListener(new PresenceListener(this, Roster.getInstanceFor(mConn), mControl), presenceFilter);

         // fallback listener
        mConn.addAsyncStanzaListener(this,
                new NotFilter(
                        new OrFilter(
                                messageFilter,
                                vCardFilter,
                                blockingCommandFilter,
                                publicKeyFilter,
                                vCardFilter,
                                presenceFilter,
                                // handled by roster listener
                                new StanzaTypeFilter(RosterPacket.class)
                        )
                )
        );

        // continue async
        List<?> args = new ArrayList<>(0);
        Client.TASK_QUEUE.offer(new Client.Task(Client.Command.CONNECT, args));
    }

    private void connectAsync() {
        // TODO unsure if everything is thread-safe
        synchronized (this) {
            // connect
            LOGGER.info("connecting to "+mConn.getDestination()+" ...");
            try {
                mConn.connect();
            } catch (XMPPException | SmackException | IOException ex) {
                LOGGER.log(Level.WARNING, "can't connect", ex);
                mControl.setStatus(Control.Status.FAILED);
                mControl.handleException(new KonException(KonException.Error.CLIENT_CONNECT, ex));
                return;
            }

            // login
            try {
                mConn.login();
            } catch (XMPPException | SmackException | IOException ex) {
                LOGGER.log(Level.WARNING, "can't login", ex);
                mConn.disconnect();
                mControl.setStatus(Control.Status.FAILED);
                mControl.handleException(new KonException(KonException.Error.CLIENT_LOGIN, ex));
                return;
            }
        }

        mConn.addStanzaAcknowledgedListener(new AcknowledgedListener(mControl));

        this.sendInitialPresence();

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

    /**
     * The JID of the user currently logged in.
     * @return the full JID of the user logged in or an empty string if not
     * logged in
     */
    public String getOwnJID() {
        return !this.isConnected() ? "" : mConn.getUser();
    }

    public void sendMessage(OutMessage message) {
        // check for correct receipt status and reset it
        Status status = message.getReceiptStatus();
        assert status == Status.PENDING || status == Status.ERROR;
        message.setStatus(Status.PENDING);

        if (!this.isConnected()) {
            LOGGER.info("not sending message, not connected");
            return;
        }

        if (!XMPPUtils.isValid(message.getJID())) {
            LOGGER.warning("not sending message, invalid JID: "+message.getJID());
            return;
        }

        Message smackMessage = new Message(message.getJID(), Message.Type.chat);
        smackMessage.setStanzaId(message.getXMPPID());
        smackMessage.addExtension(new DeliveryReceiptRequest());
        Config conf = Config.getInstance();
        if (conf.getBoolean(Config.NET_SEND_CHAT_STATE))
            smackMessage.addExtension(new ChatStateExtension(ChatState.active));

        if (message.getCoderStatus().getEncryption() == Coder.Encryption.NOT &&
                message.getCoderStatus().getSigning() == Coder.Signing.NOT) {
            // TODO send more possible content
            smackMessage.setBody(message.getContent().getPlainText());
        } else {
            Optional<byte[]> encrypted = Coder.processOutMessage(message);
            // check also for security errors just to be sure
            if (!encrypted.isPresent() ||
                    !message.getCoderStatus().getErrors().isEmpty()) {
                LOGGER.warning("encryption failed, not sending message");
                message.setStatus(Status.ERROR);
                mControl.handleSecurityErrors(message);
                return;
            }
            smackMessage.addExtension(new E2EEncryption(encrypted.get()));
        }

        this.sendPacket(smackMessage);
    }

    public void sendVCardRequest(String jid) {
        VCard4 vcard = new VCard4();
        vcard.setType(IQ.Type.get);
        vcard.setTo(jid);
        this.sendPacket(vcard);
    }

    public void sendPublicKeyRequest(String jid) {
        PublicKeyPublish publicKeyRequest = new PublicKeyPublish();
        publicKeyRequest.setTo(jid);
        this.sendPacket(publicKeyRequest);
    }

    public void sendBlocklistRequest() {
        this.sendPacket(BlockingCommand.blocklist());
    }

    public void sendBlockingCommand(String jid, boolean blocking) {
        if (!this.isConnected()) {
            LOGGER.warning("not sending blocking command, not connected");
            return;
        }

        String command = blocking ? BlockingCommand.BLOCK : BlockingCommand.UNBLOCK;
        BlockingCommand blockingCommand = new BlockingCommand(command, jid);

        // add response listener
        StanzaListener blockResponseListener = new BlockResponseListener(mControl, mConn, blocking, jid);
        mConn.addAsyncStanzaListener(blockResponseListener, new StanzaIdFilter(blockingCommand));

        this.sendPacket(blockingCommand);
    }

    public void sendInitialPresence() {
        Presence presence = new Presence(Presence.Type.available);
        List<?> stats = Config.getInstance().getList(Config.NET_STATUS_LIST);
        if (!stats.isEmpty()) {
            String stat = (String) stats.get(0);
            if (!stat.isEmpty())
                presence.setStatus(stat);
        }
        // note: not setting priority, according to anti-dicrimination rules;)
        this.sendPacket(presence);
    }

    public void sendPresenceSubscriptionRequest(String jid) {
        Presence subscribeRequest = new Presence(Presence.Type.subscribe);
        subscribeRequest.setTo(jid);
        this.sendPacket(subscribeRequest);
    }

    public void sendChatState(String jid, Optional<String> optThreadID, ChatState state) {
        if (!Config.getInstance().getBoolean(Config.NET_SEND_CHAT_STATE))
            return;

        Message message = new Message(jid, Message.Type.chat);
        if (optThreadID.isPresent())
            message.setThread(optThreadID.get());
        message.addExtension(new ChatStateExtension(state));

        this.sendPacket(message);
    }

    synchronized void sendPacket(Stanza p) {
        try {
            mConn.sendStanza(p);
        } catch (SmackException.NotConnectedException ex) {
            LOGGER.info("can't send packet, not connected.");
        }
        LOGGER.info("sent packet: "+p.toXML());
    }

    @Override
    public void processPacket(Stanza packet) {
        LOGGER.info("got packet (unhandled): "+packet.toXML());
    }

    public boolean addToRoster(Contact contact) {
        if (!this.isConnected()) {
            LOGGER.info("can't add contact to roster, not connected");
            return false;
        }

        String rosterName = contact.getName();
        if (rosterName.isEmpty())
            rosterName = XmppStringUtils.parseLocalpart(rosterName);
        try {
            // also sends presence subscription request
            Roster.getInstanceFor(mConn).createEntry(contact.getJID(), rosterName,
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

    public boolean removeFromRoster(Contact contact) {
        if (!this.isConnected()) {
            LOGGER.info("can't remove contact from roster, not connected");
            return false;
        }
        Roster roster = Roster.getInstanceFor(mConn);
        RosterEntry entry = roster.getEntry(contact.getJID());
        if (entry == null) {
            LOGGER.warning("can't find roster entry for contact: "+contact);
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

    public boolean isConnected() {
        return mConn != null && mConn.isAuthenticated();
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

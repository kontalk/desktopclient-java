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
import org.kontalk.system.Config;
import org.kontalk.misc.KonException;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.model.Chat;
import org.kontalk.misc.JID;
import org.kontalk.model.KonMessage.Status;
import org.kontalk.model.OutMessage;
import org.kontalk.model.MessageContent;
import org.kontalk.model.MessageContent.Attachment;
import org.kontalk.model.MessageContent.Preview;
import org.kontalk.model.Transmission;
import org.kontalk.system.Control;
import org.kontalk.system.RosterHandler;
import org.kontalk.util.ClientUtils;
import org.kontalk.util.EncodingUtils;

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

        // enable Smack debugging (print raw XML packet)
        //SmackConfiguration.DEBUG = true;
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
        RosterHandler rosterSyncer = mControl.getRosterHandler();
        RosterListener rl = new KonRosterListener(Roster.getInstanceFor(mConn), rosterSyncer);
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
        mConn.addAsyncStanzaListener(new PresenceListener(Roster.getInstanceFor(mConn), rosterSyncer), presenceFilter);

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
     * The full JID of the user currently logged in.
     */
    public Optional<JID> getOwnJID() {
        String user = mConn.getUser();
        if (user == null)
            return Optional.empty();
        return Optional.of(JID.full(user));
    }

    public void sendMessage(OutMessage message, boolean sendChatState) {
        // check for correct receipt status and reset it
        Status status = message.getStatus();
        assert status == Status.PENDING || status == Status.ERROR;
        message.setStatus(Status.PENDING);

        if (!this.isConnected()) {
            LOGGER.info("not sending message(s), not connected");
            return;
        }

        MessageContent content = message.getContent();
        Optional<Attachment> optAtt = content.getAttachment();
        if (optAtt.isPresent() && !optAtt.get().hasURL()) {
            LOGGER.warning("attachment not uploaded");
            message.setStatus(Status.ERROR);
            return;
        }

        boolean encrypted =
                message.getCoderStatus().getEncryption() != Coder.Encryption.NOT ||
                message.getCoderStatus().getSigning() != Coder.Signing.NOT;

        Chat chat = message.getChat();

        Message protoMessage = encrypted ? new Message() : rawMessage(content, chat, false);

        protoMessage.setType(Message.Type.chat);
        protoMessage.setStanzaId(message.getXMPPID());
        String threadID = chat.getXMPPID();
        if (!threadID.isEmpty())
            protoMessage.setThread(threadID);

        // extensions

        // TODO with group chat? (for muc "NOT RECOMMENDED")
        if (!chat.isGroupChat())
            protoMessage.addExtension(new DeliveryReceiptRequest());

        if (sendChatState)
            protoMessage.addExtension(new ChatStateExtension(ChatState.active));

        if (encrypted) {
            Optional<byte[]> encryptedData = content.isComplex() ?
                        Coder.encryptStanza(message,
                                rawMessage(content, chat, true).toXML().toString()) :
                        Coder.encryptMessage(message);
            // check also for security errors just to be sure
            if (!encryptedData.isPresent() ||
                    !message.getCoderStatus().getErrors().isEmpty()) {
                LOGGER.warning("encryption failed");
                message.setStatus(Status.ERROR);
                mControl.handleSecurityErrors(message);
                return;
            }
            protoMessage.addExtension(new E2EEncryption(encryptedData.get()));
        }

        // transmission specific
        Transmission[] transmissions = message.getTransmissions();
        ArrayList<Message> sendMessages = new ArrayList<>(transmissions.length);
        for (Transmission transmission: message.getTransmissions()) {
            Message sendMessage = protoMessage.clone();
            JID to = transmission.getJID();
            if (!to.isValid()) {
                LOGGER.warning("invalid JID: "+to);
                return;
            }
            sendMessage.setTo(to.string());
            sendMessages.add(sendMessage);
        }

        this.sendPackets(sendMessages.toArray(new Message[0]));
    }

    private static Message rawMessage(MessageContent content, Chat chat, boolean encrypted) {
        Message smackMessage = new Message();

        // text
        String text = content.getPlainText();
        if (!text.isEmpty())
            smackMessage.setBody(content.getPlainText());

        // attachment
        Optional<Attachment> optAtt = content.getAttachment();
        if (optAtt.isPresent()) {
            Attachment att = optAtt.get();

            OutOfBandData oobData = new OutOfBandData(att.getURL().toString(),
                    att.getMimeType(), att.getLength(), encrypted);
            smackMessage.addExtension(oobData);

            Optional<Preview> optPreview = content.getPreview();
            if (optPreview.isPresent()) {
                Preview preview = optPreview.get();
                String data = EncodingUtils.bytesToBase64(preview.getData());
                BitsOfBinary bob = new BitsOfBinary(preview.getMimeType(), data);
                smackMessage.addExtension(bob);
            }
        }

        // group command
        Optional<Chat.GID> optGID = chat.getGID();
        if (optGID.isPresent()) {
            Chat.GID gid = optGID.get();
            Optional<MessageContent.GroupCommand> optGroupCommand = content.getGroupCommand();
            smackMessage.addExtension(optGroupCommand.isPresent() ?
                    ClientUtils.groupCommandToGroupExtension(chat, optGroupCommand.get()) :
                    new GroupExtension(gid.id, gid.ownerJID.string()));
        }

        return smackMessage;
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
        LOGGER.info("jid: "+jid+" blocking="+blocking);

        String command = blocking ? BlockingCommand.BLOCK : BlockingCommand.UNBLOCK;
        BlockingCommand blockingCommand = new BlockingCommand(command, jid.string());

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

        // for testing
        //presence.addExtension(new PresenceSignature(""));

        this.sendPacket(presence);
    }

    public void sendPresenceSubscriptionRequest(JID jid) {
        LOGGER.info("to "+jid);
        Presence subscribeRequest = new Presence(Presence.Type.subscribe);
        subscribeRequest.setTo(jid.string());
        this.sendPacket(subscribeRequest);
    }

    public void sendChatState(JID jid, String threadID, ChatState state) {
        Message message = new Message(jid.string(), Message.Type.chat);
        if (!threadID.isEmpty())
            message.setThread(threadID);
        message.addExtension(new ChatStateExtension(state));

        this.sendPacket(message);
    }

    private synchronized void sendPackets(Stanza[] stanzas) {
        for (Stanza s: stanzas)
            this.sendPacket(s);
    }

    synchronized void sendPacket(Stanza p) {
        try {
            mConn.sendStanza(p);
        } catch (SmackException.NotConnectedException ex) {
            LOGGER.info("can't send packet, not connected.");
        }
        LOGGER.config("packet: "+p);
    }

    @Override
    public void processPacket(Stanza packet) {
        LOGGER.config("unhandled: "+packet);
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

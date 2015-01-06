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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bouncycastle.openpgp.PGPException;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.NotFilter;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketIDFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.kontalk.KonConf;
import org.kontalk.KonException;
import org.kontalk.Kontalk;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.model.OutMessage;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class Client implements PacketListener, Runnable {
    private final static Logger LOGGER = Logger.getLogger(Client.class.getName());

    private final static LinkedBlockingQueue<Task> TASK_QUEUE = new LinkedBlockingQueue<>();

    private static enum Command {CONNECT, DISCONNECT};

    private final Kontalk mModel;
    protected KonConnection mConn;

    // Limited connection flag.
    //protected boolean mLimited;

    public Client(Kontalk model) {
        mModel = model;
        //mLimited = limited;
    }

    public void connect(PersonalKey key) {

        this.disconnect();
        mModel.statusChanged(Kontalk.Status.CONNECTING);

        KonConf config = KonConf.getInstance();
        // tigase: use hostname as network
        //String network = config.getString(KonConf.SERV_NET);
        String network = config.getString(KonConf.SERV_HOST);
        String host = config.getString(KonConf.SERV_HOST);
        int port = config.getInt(KonConf.SERV_PORT);
        EndpointServer server = new EndpointServer(network, host, port);

        // create connection
        try {
            mConn = new KonConnection(server,
                    key.getBridgePrivateKey(),
                    key.getBridgeCertificate());
        } catch (PGPException ex) {
            LOGGER.log(Level.WARNING, "can't create connection", ex);
            mModel.statusChanged(Kontalk.Status.FAILED);
            mModel.handleException(new KonException(KonException.Error.CLIENT_CONNECTION, ex));
            return;
        }

        // listeners
        RosterListener rl = new KonRosterListener(mConn.getRoster(), this);
        mConn.getRoster().addRosterListener(rl);
        PacketFilter messageFilter = new PacketTypeFilter(Message.class);
        mConn.addPacketListener(new KonMessageListener(this), messageFilter);
        PacketFilter vCardFilter = new PacketTypeFilter(VCard4.class);
        mConn.addPacketListener(new VCardListener(), vCardFilter);
        PacketFilter blockingCommandFilter = new PacketTypeFilter(BlockingCommand.class);
        mConn.addPacketListener(new BlockListListener(), blockingCommandFilter);
         // fallback
        mConn.addPacketListener(this,
                new AndFilter(
                        new NotFilter(messageFilter),
                        new NotFilter(vCardFilter),
                        new NotFilter(blockingCommandFilter)
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
                mModel.statusChanged(Kontalk.Status.FAILED);
                mModel.handleException(new KonException(KonException.Error.CLIENT_CONNECT, ex));
                return;
            }

            // login
            try {
                mConn.login();
            } catch (XMPPException | SmackException | IOException ex) {
                LOGGER.log(Level.WARNING, "can't login", ex);
                mModel.statusChanged(Kontalk.Status.FAILED);
                mModel.handleException(new KonException(KonException.Error.CLIENT_LOGIN, ex));
                return;
            }
        }

        LOGGER.info("connected!");

        this.sendPresence();

        this.sendBlocklistRequest();

        mModel.statusChanged(Kontalk.Status.CONNECTED);
    }

    public void disconnect() {
        synchronized (this) {
            if (mConn != null) {
                mConn.disconnect();
                mConn = null;
            }
        }
        mModel.statusChanged(Kontalk.Status.DISCONNECTED);
    }

    public void sendMessage(OutMessage message) {
        if (mConn == null || !mConn.isAuthenticated()) {
            LOGGER.info("not sending message, not connected");
            return;
        }

        Message smackMessage = new Message();
        smackMessage.setPacketID(message.getXMPPID());
        smackMessage.setType(Message.Type.chat);
        smackMessage.setTo(message.getJID());
        // TODO
        //smackMessage.addExtension(new ServerReceiptRequest());
        KonConf conf = KonConf.getInstance();
        if (conf.getBoolean(KonConf.NET_SEND_CHAT_STATE))
            smackMessage.addExtension(new ChatStateExtension(ChatState.active));

        if (message.getEncryption() == Coder.Encryption.NOT &&
                message.getSigning() == Coder.Signing.NOT) {
            // TODO send more possible content
            smackMessage.setBody(message.getContent().getPlainText());
        } else {
            byte[] encrypted = Coder.processOutMessage(message);
            // check also for security errors just to be sure
            if (encrypted == null || !message.getSecurityErrors().isEmpty()) {
                LOGGER.warning("encryption failed, not sending message");
                mModel.handleSecurityErrors(message);
                return;
            }
            smackMessage.addExtension(new E2EEncryption(encrypted));
        }

        this.sendPacket(smackMessage);
    }

    public void sendVCardRequest(String jid) {
        VCard4 vcard = new VCard4();
        vcard.setType(IQ.Type.get);
        vcard.setTo(jid);
        this.sendPacket(vcard);
    }

    public void sendBlocklistRequest() {
        this.sendPacket(BlockingCommand.blocklist());
    }

    public void sendBlockingCommand(String jid, boolean blocking) {
        if (mConn == null || !mConn.isAuthenticated()) {
            LOGGER.warning("not sending blocking command, not connected");
            return;
        }

        String command = blocking ? BlockingCommand.BLOCK : BlockingCommand.UNBLOCK;
        BlockingCommand blockingCommand = new BlockingCommand(command, jid);

        // add response listener
        PacketListener blockResponseListener = new BlockResponseListener(mConn, blocking, jid);
        mConn.addPacketListener(blockResponseListener, new PacketIDFilter(blockingCommand));

        this.sendPacket(blockingCommand);
    }

    public void sendPresence() {
        Presence presence = new Presence(Presence.Type.available);
        // TODO presence, priority ...
        //presence.setStatus();
        this.sendPacket(presence);
    }

    synchronized void sendPacket(Packet p) {
        try {
            mConn.sendPacket(p);
        } catch (SmackException.NotConnectedException ex) {
            LOGGER.info("can't send packet, not connected.");
        }
        LOGGER.info("sent packet: "+p.toXML());
    }

    @Override
    public void processPacket(Packet packet) {
        LOGGER.info("got packet: "+packet.toXML());
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

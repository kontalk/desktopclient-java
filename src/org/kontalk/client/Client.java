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
import org.kontalk.model.KonMessage;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class Client implements PacketListener, Runnable {
    private final static Logger LOGGER = Logger.getLogger(Client.class.getName());

    public final static String KONTALK_NETWORK = "kontalk.net";
    public final static LinkedBlockingQueue<Task> TASK_QUEUE = new LinkedBlockingQueue();

    public static enum Command {CONNECT, DISCONNECT};

    private final Kontalk mModel;
    private final KonConf mConfig;
    protected KonConnection mConn;

    // Limited connection flag.
    //protected boolean mLimited;

    public Client(Kontalk model) {
        mModel = model;
        mConfig = KonConf.getInstance();
        //mLimited = limited;
    }

    private void connect(PersonalKey key) {

        this.disconnect();
        mModel.statusChanged(Kontalk.Status.CONNECTING);

        String network = mConfig.getString("server.network");
        String host = mConfig.getString("server.host");
        int port = mConfig.getInt("server.port");
        EndpointServer Server = new EndpointServer(network, host, port);

        synchronized (this) {

            // create connection
            try {
                mConn = new KonConnection(Server,
                        key.getBridgePrivateKey(),
                        key.getBridgeCertificate());
            } catch (XMPPException | PGPException ex) {
                LOGGER.log(Level.WARNING, "can't create connection", ex);
                mModel.statusChanged(Kontalk.Status.FAILED);
                mModel.handleException(new KonException(KonException.Error.CLIENT_CONNECTION, ex));
                return;
            }

            // connect
            LOGGER.info("connecting...");
            try {
                mConn.connect();
            } catch (XMPPException | SmackException | IOException ex) {
                LOGGER.log(Level.WARNING, "can't connect", ex);
                mModel.statusChanged(Kontalk.Status.FAILED);
                mModel.handleException(new KonException(KonException.Error.CLIENT_CONNECT, ex));
                return;
            }
            System.out.println("connected");

            // listeners
            RosterListener rl = new KonRosterListener(mConn.getRoster(), this);
            mConn.getRoster().addRosterListener(rl);
            PacketFilter messageFilter = new PacketTypeFilter(Message.class);
            mConn.addPacketListener(new MessageListener(this), messageFilter);
            PacketFilter vCardFilter = new PacketTypeFilter(VCard4.class);
            mConn.addPacketListener(new VCardListener(), vCardFilter);
            PacketFilter blockingCommandFilter = new PacketTypeFilter(BlockingCommand.class);
            mConn.addPacketListener(new BlockingCommandListener(), blockingCommandFilter);
             // fallback
            mConn.addPacketListener(this,
                    new AndFilter(
                            new NotFilter(messageFilter),
                            new NotFilter(vCardFilter),
                            new NotFilter(blockingCommandFilter)
                    )
            );

            // login
            try {
                // the dummy values are not actually used
                // server does authentification based purely on the pgp key
                mConn.login("dummy", "dummy");
            } catch (XMPPException | SmackException | IOException ex) {
                LOGGER.log(Level.WARNING, "can't login", ex);
                mModel.statusChanged(Kontalk.Status.FAILED);
                mModel.handleException(new KonException(KonException.Error.CLIENT_LOGIN, ex));
                return;
            }
        }

        LOGGER.info("connected!");

        // TODO
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

    public void sendMessage(KonMessage message) {
        if (mConn == null || !mConn.isAuthenticated()) {
            return;
        }

        Message smackMessage = new Message();
        smackMessage.setPacketID(message.getXMPPID());
        smackMessage.setType(Message.Type.chat);
        smackMessage.setTo(message.getJID());
        smackMessage.addExtension(new ServerReceiptRequest());
        KonConf conf = KonConf.getInstance();
        if (conf.getBoolean(KonConf.NET_SEND_CHAT_STATE))
            smackMessage.addExtension(new ChatStateExtension(ChatState.active));

        if (message.getEncryption() == Coder.Encryption.NOT &&
                message.getSigning() == Coder.Signing.NOT) {
            smackMessage.setBody(message.getText());
        } else {
            byte[] encrypted = Coder.processOutMessage(message);
            // check also for security errors just to be sure
            if (encrypted == null || !message.getSecurityErrors().isEmpty()) {
                LOGGER.warning("encryption failed, not sending message");
                return;
            }
            smackMessage.addExtension(new E2EEncryption(encrypted));
        }

        this.sendPacket(smackMessage);
    }

    public void sendVCardRequest(String jid) {
        VCard4 vcard = new VCard4();
        vcard.setType(IQ.Type.GET);
        vcard.setTo(jid);
        this.sendPacket(vcard);
    }

    public void sendBlocklistRequest() {
        this.sendPacket(BlockingCommand.blocklist());
    }

    public void sendBlockingCommand(String jid, boolean blocking) {
        String command = blocking ? BlockingCommand.BLOCK : BlockingCommand.UNBLOCK;
        BlockingCommand blockingCommand = new BlockingCommand(command, jid);
        this.sendPacket(blockingCommand);
    }

    public void sendPresence() {
        Presence presence = new Presence(Presence.Type.available);
        // TODO
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
                    this.connect((PersonalKey) t.args.get(0));
                    break;
                case DISCONNECT:
                    this.disconnect();
                    break;
            }
        }
    }

    public static class Task {

        final Command command;
        final List args;

        public Task(Command c, List a) {
            command = c;
            args = a;
        }
    }
}

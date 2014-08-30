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

package org.kontalk;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.apache.commons.lang.SystemUtils;
import org.jivesoftware.smack.util.StringUtils;
import org.kontalk.client.Client;
import org.kontalk.crypto.PGP;
import org.kontalk.model.Account;
import org.kontalk.model.KonMessage;
import org.kontalk.model.KonThread;
import org.kontalk.model.MessageList;
import org.kontalk.model.ThreadList;
import org.kontalk.model.User;
import org.kontalk.model.UserList;
import org.kontalk.view.View;

/**
 * @author Alexander Bikadorov
 */
public final class Kontalk {
    private final static Logger LOGGER = Logger.getLogger(Kontalk.class.getName());

    public enum Status {
        DISCONNECTING, DISCONNECTED, CONNECTING, CONNECTED, SHUTTING_DOWN, FAILED
    }

    private ServerSocket mRun = null;
    private final KonConf mConfig;
    private final Client mClient;
    private final View mView;
    private final UserList mUserList;
    private final ThreadList mThreadList;
    private final MessageList mMessageList;
    private final String mConfigDir;

    static {
        // register provider
        PGP.registerProvider();
    }

    public Kontalk(String[] args) {

        // check if already running
        try {
            InetAddress addr = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
            mRun = new ServerSocket(9871, 10, addr);
        } catch(java.net.BindException ex) {
            LOGGER.severe("already running");
            System.exit(2);
        } catch(IOException ex) {
            LOGGER.log(Level.WARNING, "can't create socket", ex);
        }

        // use platform dependent configuration directory
        String homeDir = System.getProperty("user.home");
        if (SystemUtils.IS_OS_WINDOWS) {
            mConfigDir = homeDir + "/Kontalk";
        } else {
            mConfigDir = homeDir + "/.kontalk";
        }
        boolean created = new File(mConfigDir).mkdirs();
        if (created)
            LOGGER.info("created configuration directory");

        mConfig = KonConf.initialize(mConfigDir + "/kontalk.properties");

        parseArgs(args);

        mUserList = UserList.getInstance();
        mThreadList = ThreadList.getInstance();
        mMessageList = MessageList.getInstance();

        mClient = new Client(this);

        mView = new View(this);
    }

    public void start() {
        new Thread(mClient).start();

        Database.initialize(this, mConfigDir);

        // order matters!
        mUserList.load();
        mThreadList.load();
        mMessageList.load();

        mView.init();

        if (mConfig.getBoolean(KonConf.MAIN_CONNECT_STARTUP))
            this.connect();
    }

    public void shutDown() {
        LOGGER.info("Shutting down...");
        mView.statusChanged(Status.SHUTTING_DOWN);
        mUserList.save();
        mThreadList.save();
        mClient.disconnect();
        if (Database.getInstance() != null)
            Database.getInstance().close();
        mConfig.saveToFile();
        try {
            mRun.close();
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't close run socket", ex);
        }
        System.exit(0);
    }

    public void connect() {
        Account account = Account.getInstance();
        // TODO new account for each connection?
        try {
            account.reload();
        } catch (KonException ex) {
            // something wrong with the account, tell view
            this.handleException(ex);
            return;
        }
        List args = new ArrayList(1);
        args.add(account.getPersonalKey());
        Client.TASK_QUEUE.offer(new Client.Task(Client.Command.CONNECT, args));
    }

    public void disconnect() {
        mView.statusChanged(Status.DISCONNECTING);
        mClient.disconnect();
    }

    public void statusChanged(Status status) {
        mView.statusChanged(status);
        if (status == Status.CONNECTED) {
            // send all pending messages
            for (KonMessage m : mMessageList.getMessages()) {
                if (m.getReceiptStatus() == KonMessage.Status.PENDING) {
                    // TODO
                    //mClient.sendMessage(m);
                }
            }
            // send vcard/public key requests for kontalk users with missing key
            // TODO also do this when new user is created from roster
            for (User user : mUserList.getUser()) {
                String network = StringUtils.parseServer(user.getJID());
                if (user.getFingerprint() == null &&
                        network.equalsIgnoreCase(Client.KONTALK_NETWORK))
                mClient.sendVCardRequest(user.getJID());
            }

        }
    }

    public void sendText(KonThread thread, String text) {
        // TODO no group chat support yet
        Set<User> user = thread.getUser();
        for (User oneUser: user) {
            KonMessage newMessage = mMessageList.addTo(
                    thread,
                    oneUser,
                    text,
                    oneUser.getEncrypted());
            mClient.sendMessage(newMessage);
        }
    }

    public void handleException(KonException ex) {
        mView.handleException(ex);
    }

    public void setUserBlocking(User user, boolean blocking) {
        mClient.sendBlockingCommand(user.getJID(), blocking);
    }

    // parse optional arguments
    private void parseArgs(String[] args) {
        if (args.length == 0)
            return;
        if (args.length == 2 && Pattern.matches(".*:\\d*", args[1])) {
            String[] argsegs = args[1].split(Pattern.quote(":"));
            if (argsegs[0].length() != 0)
                mConfig.setProperty("server.host", argsegs[0]);
            if (argsegs[1].length() != 0)
                mConfig.setProperty("server.port", Integer.valueOf(argsegs[1]));
            //client.setUsername(args[0]);
        } else if (args.length == 1 && !Pattern.matches(".*:\\d*", args[0])) {
            //client.setUsername(args[0]);
        } else {
            String className = this.getClass().getEnclosingClass().getName();
            LOGGER.log(Level.WARNING, "Usage: java {0} [USERNAME [SERVER:PORT]]", className);
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        LOGGER.setLevel(Level.ALL);

        Kontalk model = new Kontalk(args);
        model.start();
        //model.connect();
    }

}

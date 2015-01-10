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
import java.nio.file.Paths;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.apache.commons.lang.SystemUtils;
import org.kontalk.client.Client;
import org.kontalk.crypto.PGP;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.model.Account;
import org.kontalk.model.KonMessage;
import org.kontalk.model.KonThread;
import org.kontalk.model.MessageList;
import org.kontalk.model.OutMessage;
import org.kontalk.model.ThreadList;
import org.kontalk.model.User;
import org.kontalk.model.UserList;
import org.kontalk.util.CryptoUtils;
import org.kontalk.view.View;

/**
 * @author Alexander Bikadorov
 */
public final class Kontalk {
    private final static Logger LOGGER = Logger.getLogger(Kontalk.class.getName());

    public final static String VERSION = "0.01a";
    private final static String CONFIG_DIR;

    public enum Status {
        DISCONNECTING, DISCONNECTED, CONNECTING, CONNECTED, SHUTTING_DOWN, FAILED
    }

    private ServerSocket mRun;

    private final Client mClient;
    private final View mView;

    private Status mCurrentStatus = Status.DISCONNECTED;

    static {
        // check java version
        String jVersion = System.getProperty("java.version");
        if (jVersion.startsWith("1.7")) {
            View.showWrongJavaVersionDialog();
            LOGGER.severe("java too old: "+jVersion);
            System.exit(-3);
        }

        // use platform dependent configuration directory
        String homeDir = System.getProperty("user.home");
        if (SystemUtils.IS_OS_WINDOWS) {
            CONFIG_DIR = Paths.get(homeDir,"Kontalk").toString();
        } else {
            CONFIG_DIR = Paths.get(homeDir, ".kontalk").toString();
        }

        // create app directory
        boolean created = new File(CONFIG_DIR).mkdirs();
        if (created)
            LOGGER.info("created configuration directory");

        // log to file
        String logPath = Paths.get(CONFIG_DIR, "debug.log").toString();
        Handler fileHandler = null;
        try {
            fileHandler = new FileHandler(logPath, 1024*1000, 1, true);
        } catch (IOException | SecurityException ex) {
            LOGGER.log(Level.WARNING, "can't log to file", ex);
        }
        if (fileHandler != null) {
            fileHandler.setFormatter(new SimpleFormatter());
            Logger.getLogger("").addHandler(fileHandler);
        }

        LOGGER.info("--START, version: "+VERSION+"--");

        // fix crypto restriction
        CryptoUtils.removeCryptographyRestrictions();

        // register provider
        PGP.registerProvider();
    }

    private Kontalk(String[] args) {
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

        this.parseArgs(args);

        mClient = new Client(this);

        mView = new View(this);
    }

    public void start() {
        MessageCenter.initialize(this);

        new Thread(mClient).start();

        try {
            Database.initialize(CONFIG_DIR);
        } catch (KonException ex) {
            LOGGER.log(Level.SEVERE, "can't initialize database", ex);
            this.shutDown();
            return;
        }

        // order matters!
        UserList.getInstance().load();
        ThreadList.getInstance().load();
        MessageList.getInstance().load();

        mView.init();

        // use password option to determine if account was imported
        KonConf config = KonConf.getInstance();
        if (config.getString(KonConf.ACC_PASS).isEmpty()) {
            mView.showImportWizard();
            return;
        }

        if (config.getBoolean(KonConf.MAIN_CONNECT_STARTUP))
            this.connect();
    }

    public void shutDown() {
        LOGGER.info("Shutting down...");
        mCurrentStatus = Status.SHUTTING_DOWN;
        mView.statusChanged();
        UserList.getInstance().save();
        ThreadList.getInstance().save();
        mClient.disconnect();
        if (Database.getInstance() != null)
            Database.getInstance().close();
        KonConf.getInstance().saveToFile();
        try {
            mRun.close();
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't close run socket", ex);
        }
        System.exit(0);
    }

    public void connect() {
        PersonalKey key;
        try {
            key = Account.getInstance().getPersonalKey();
        } catch (KonException ex) {
            // something wrong with the account, tell view
            this.handleException(ex);
            return;
        }
        mClient.connect(key);
    }

    public void disconnect() {
        mCurrentStatus = Status.DISCONNECTING;
        mView.statusChanged();
        mClient.disconnect();
    }

    public Status getCurrentStatus() {
        return mCurrentStatus;
    }

    public void statusChanged(Status status) {
        mCurrentStatus = status;
        mView.statusChanged();
        if (status == Status.CONNECTED) {
            // send all pending messages
            for (KonMessage m : MessageList.getInstance().getMessages()) {
                if (m.getReceiptStatus() == KonMessage.Status.PENDING) {
                    assert m instanceof OutMessage;
                    mClient.sendMessage((OutMessage) m);
                }
            }
            // send public key requests for Kontalk users with missing key
            for (User user : UserList.getInstance().getUser()) {
                // TODO only for domains that a part of the Kontalk network
                if (user.getFingerprint().isEmpty())
                    mClient.sendPublicKeyRequest(user.getJID());
            }

        }
    }

    public void sendText(KonThread thread, String text) {
        // TODO no group chat support yet
        Set<User> user = thread.getUser();
        for (User oneUser: user) {
            OutMessage newMessage = MessageCenter.getInstance().newOutMessage(
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

    public void handleSecurityErrors(KonMessage message) {
        mView.handleSecurityErrors(message);
    }

    public void setUserBlocking(User user, boolean blocking) {
        mClient.sendBlockingCommand(user.getJID(), blocking);
    }

    // parse optional arguments
    private void parseArgs(String[] args) {
        if (args.length != 0) {
            String className = this.getClass().getEnclosingClass().getName();
            LOGGER.log(Level.WARNING, "Usage: java {0} ", className);
        }
    }

    public static String getConfigDir() {
        return CONFIG_DIR;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        LOGGER.setLevel(Level.ALL);

        Kontalk model = new Kontalk(args);
        model.start();
    }

}

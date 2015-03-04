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

import org.kontalk.misc.KonException;
import org.kontalk.system.Database;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.apache.commons.lang.SystemUtils;
import org.kontalk.crypto.PGPUtils;
import org.kontalk.model.MessageList;
import org.kontalk.model.ThreadList;
import org.kontalk.model.UserList;
import org.kontalk.system.ControlCenter;
import org.kontalk.util.CryptoUtils;
import org.kontalk.view.View;

/**
 * @author Alexander Bikadorov
 */
public final class Kontalk {
    private final static Logger LOGGER = Logger.getLogger(Kontalk.class.getName());

    public final static String VERSION = "0.01a2";
    public final static String RES_PATH = "org/kontalk/res/";
    private final static String CONFIG_DIR;

    private static ServerSocket RUN_LOCK;

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
        PGPUtils.registerProvider();
    }

    private Kontalk(String[] args) {
        // check if already running
        try {
            InetAddress addr = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
            RUN_LOCK = new ServerSocket(9871, 10, addr);
        } catch(java.net.BindException ex) {
            LOGGER.severe("already running");
            System.exit(2);
        } catch(IOException ex) {
            LOGGER.log(Level.WARNING, "can't create socket", ex);
        }

        this.parseArgs(args);
    }

    public void start() {
        ControlCenter control = new ControlCenter();

        Optional<View> optView = View.create(control);
        if (!optView.isPresent()) {
            control.shutDown();
            return; // never reached
        }
        View view = optView.get();

        try {
            Database.initialize(CONFIG_DIR);
        } catch (KonException ex) {
            LOGGER.log(Level.SEVERE, "can't initialize database", ex);
            control.shutDown();
            return; // never reached
        }

        // order matters!
        UserList.getInstance().load();
        ThreadList.getInstance().load();
        MessageList.getInstance().load();

        view.init();

        control.launch();
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

    public static void exit() {
        try {
            RUN_LOCK.close();
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't close run socket", ex);
        }
        LOGGER.info("exit");
        System.exit(0);
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

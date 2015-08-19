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
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.apache.commons.lang.SystemUtils;
import org.kontalk.crypto.PGPUtils;
import org.kontalk.model.ChatList;
import org.kontalk.model.ContactList;
import org.kontalk.system.Config;
import org.kontalk.system.Control;
import org.kontalk.system.Control.ViewControl;
import org.kontalk.util.CryptoUtils;
import org.kontalk.util.Tr;
import org.kontalk.view.View;

/**
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class Kontalk {
    private static final Logger LOGGER = Logger.getLogger(Kontalk.class.getName());

    public static final String VERSION = "3.0.2";
    private static final Path APP_DIR;

    private static ServerSocket RUN_LOCK;

    static {
        // platform dependent configuration directory
        String homeDir = System.getProperty("user.home");
        APP_DIR = SystemUtils.IS_OS_WINDOWS ?
                Paths.get(homeDir, "Kontalk") :
                Paths.get(homeDir, ".kontalk");
    }

    private Kontalk() {}

    private void start() {
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

        // initialize translation
        Tr.init();

        // check java version
        String jVersion = System.getProperty("java.version");
        if (jVersion.startsWith("1.7")) {
            View.showWrongJavaVersionDialog();
            LOGGER.severe("java too old: "+jVersion);
            System.exit(-3);
        }

        // create app directory
        boolean created = APP_DIR.toFile().mkdirs();
        if (created)
            LOGGER.info("created configuration directory");

        // logging
        Logger logger = Logger.getLogger("");
        logger.setLevel(Level.CONFIG);
        for (Handler h : logger.getHandlers()) {
            if (h instanceof ConsoleHandler)
                h.setLevel(Level.CONFIG);
        }
        String logPath = APP_DIR.resolve("debug.log").toString();
        Handler fileHandler = null;
        try {
            fileHandler = new FileHandler(logPath, 1024*1000, 1, true);
        } catch (IOException | SecurityException ex) {
            LOGGER.log(Level.WARNING, "can't log to file", ex);
        }
        if (fileHandler != null) {
            fileHandler.setLevel(Level.INFO);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
        }

        LOGGER.info("--START, version: "+VERSION+"--");

        // fix crypto restriction
        CryptoUtils.removeCryptographyRestrictions();

        // register provider
        PGPUtils.registerProvider();


        Config.initialize(APP_DIR.resolve(Config.FILENAME));

        ViewControl control = Control.create();

        Optional<View> optView = View.create(control);
        if (!optView.isPresent()) {
            control.shutDown();
            return; // never reached
        }
        View view = optView.get();

        try {
            Database.initialize(APP_DIR.resolve(Database.FILENAME));
        } catch (KonException ex) {
            LOGGER.log(Level.SEVERE, "can't initialize database", ex);
            control.shutDown();
            return; // never reached
        }

        // order matters!
        ContactList.getInstance().load();
        ChatList.getInstance().load();

        view.init();

        control.launch();
    }

    public static Path getAppDir() {
        return APP_DIR;
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

        Kontalk app = new Kontalk();
        app.start();
    }
}

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
import org.kontalk.system.AccountLoader;
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

    public static final String VERSION = "3.0.3";
    private final Path mAppDir;

    private static ServerSocket RUN_LOCK = null;

    Kontalk() {
        // platform dependent configuration directory
        this(Paths.get(System.getProperty("user.home"),
                SystemUtils.IS_OS_WINDOWS ? "Kontalk" : ".kontalk"));
    }

    Kontalk(Path appDir) {
        mAppDir = appDir;
    }

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
        boolean created = mAppDir.toFile().mkdirs();
        if (created)
            LOGGER.info("created application directory");

        // logging
        Logger logger = Logger.getLogger("");
        logger.setLevel(Level.CONFIG);
        for (Handler h : logger.getHandlers()) {
            if (h instanceof ConsoleHandler)
                h.setLevel(Level.CONFIG);
        }
        String logPath = mAppDir.resolve("debug.log").toString();
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


        Config.initialize(mAppDir.resolve(Config.FILENAME));
        AccountLoader.initialize(mAppDir);

        ViewControl control = Control.create(mAppDir);

        Optional<View> optView = View.create(control);
        if (!optView.isPresent()) {
            control.shutDown();
            return; // never reached
        }
        View view = optView.get();

        try {
            Database.initialize(mAppDir.resolve(Database.FILENAME));
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

    public Path getAppDir() {
        return mAppDir;
    }

    public static void exit() {
        if (RUN_LOCK != null) {
            try {
                RUN_LOCK.close();
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "can't close run socket", ex);
            }
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

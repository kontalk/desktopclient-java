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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.SystemUtils;
import org.kontalk.crypto.PGPUtils;
import org.kontalk.model.ChatList;
import org.kontalk.model.ContactList;
import org.kontalk.system.Control;
import org.kontalk.system.Control.ViewControl;
import org.kontalk.util.CryptoUtils;
import org.kontalk.util.EncodingUtils;
import org.kontalk.util.Tr;
import org.kontalk.view.View;

/**
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class Kontalk {
    private static final Logger LOGGER = Logger.getLogger(Kontalk.class.getName());

    public static final String VERSION = "3.0.4";

    private static ServerSocket RUN_LOCK = null;
    private static Path APP_DIR = null;

    Kontalk() {
        // platform dependent configuration directory
        this(Paths.get(System.getProperty("user.home"),
                SystemUtils.IS_OS_WINDOWS ? "Kontalk" : ".kontalk"));
    }

    Kontalk(Path appDir) {
        APP_DIR = appDir.toAbsolutePath();
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
        boolean created = APP_DIR.toFile().mkdirs();
        if (created)
            LOGGER.info("created application directory: "+APP_DIR);

        if (!Files.isWritable(APP_DIR)) {
            LOGGER.severe("invalid app directory: "+APP_DIR);
            return;
        }

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

        ViewControl control = Control.create();

        View view = View.create(control).orElse(null);
        if (view == null) {
            control.shutDown();
            return; // never reached
        }

        try {
            // do now to test if successful
            Database.initialize();
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

    public static Path appDir() {
        if (APP_DIR == null)
            throw new IllegalStateException("app dir not initialized");

        return APP_DIR;
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

        // parse args, i18n?
        Options options = new Options();
        options.addOption("h", "help", false, "show this help message");
        options.addOption(Option.builder("d")
                //.argName("app_dir")
                .hasArg()
                .desc("set custom configuration directory")
                .longOpt("appdir")
                .build()
        );

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            showHelp(options);
            return;
        }
        if (cmd.hasOption("h")) {
            showHelp(options);
            return;
        }

        String appDir = cmd.getOptionValue("d", "");

        Kontalk app = appDir.isEmpty() ?
                new Kontalk() :
                new Kontalk(Paths.get(appDir));
        app.start();
    }

    private static void showHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        String eol = EncodingUtils.EOL;
        formatter.printHelp("java -jar [kontalk_jar]",
                eol + "Kontalk Java Desktop Client" + eol,
                options,
                "",
                true);
    }
}

/*
 *  Kontalk Java client
 *  Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>
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

package org.kontalk.persistence;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.kontalk.util.Tr;

/**
 * Global configuration options.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class Config extends PropertiesConfiguration {
    private static final Logger LOGGER = Logger.getLogger(Config.class.getName());

    private static Config INSTANCE = null;

    private static final String FILENAME = "kontalk.properties";

    // all configuration property keys
    // disable network property for now -> same as server host
    //public static final String SERV_NET = "server.network";
    public static final String SERV_HOST = "server.host";
    public static final String SERV_PORT = "server.port";
    public static final String SERV_CERT_VALIDATION = "server.cert_validation";
    public static final String ACC_PASS = "account.passphrase";
    public static final String ACC_JID = "account.jid";
    public static final String VIEW_FRAME_WIDTH = "view.frame.width";
    public static final String VIEW_FRAME_HEIGHT = "view.frame.height";
    public static final String VIEW_CHAT_SPLITTER_POS = "view.splitter_pos";
    public static final String VIEW_SELECTED_CHAT = "view.thread";
    public static final String VIEW_CHAT_BG = "view.thread_bg";
    public static final String VIEW_USER_CONTACT = "view.user_in_contactlist";
    public static final String VIEW_HIDE_BLOCKED = "view.hide_blocked_contacts";
    public static final String NET_SEND_CHAT_STATE = "net.chatstate";
    public static final String NET_SEND_ROSTER_NAME = "net.roster_name";
    public static final String NET_STATUS_LIST = "net.status_list";
    public static final String NET_AUTO_SUBSCRIPTION = "net.auto_subscription";
    public static final String NET_REQUEST_AVATARS = "net.request_avatars";
    public static final String NET_MAX_IMG_SIZE = "net.max_img_size";
    public static final String MAIN_CONNECT_STARTUP = "main.connect_startup";
    public static final String MAIN_TRAY = "main.tray";
    public static final String MAIN_TRAY_CLOSE = "main.tray_close";
    public static final String MAIN_ENTER_SENDS = "main.enter_sends";

    // default server address
    //public static final String DEFAULT_SERV_NET = "kontalk.net";
    public static final String DEFAULT_SERV_HOST = "beta.kontalk.net";
    public static final int DEFAULT_SERV_PORT = 5999;

    private final String mDefaultXMPPStatus =
            Tr.tr("Hey, I'm using Kontalk on my PC!");

    private Config(Path configFile) {
        super();

        // separate list elements by tab character
        this.setListDelimiter((char) 9);

        try {
            this.load(configFile.toString());
        } catch (ConfigurationException ex) {
            LOGGER.info("configuration file not found; using default values");
        }

        this.setFileName(configFile.toString());

        // init config / set default values for new properties
        Map<String, Object> map = new HashMap<>();
        //map.put(SERV_NET, DEFAULT_SERV_NET);
        map.put(SERV_HOST, DEFAULT_SERV_HOST);
        map.put(SERV_PORT, DEFAULT_SERV_PORT);
        map.put(SERV_CERT_VALIDATION, true);
        map.put(ACC_PASS, "");
        map.put(ACC_JID, "");
        map.put(VIEW_FRAME_WIDTH, 600);
        map.put(VIEW_FRAME_HEIGHT, 650);
        map.put(VIEW_CHAT_SPLITTER_POS, -1);
        map.put(VIEW_SELECTED_CHAT, -1);
        map.put(VIEW_CHAT_BG, "");
        map.put(VIEW_USER_CONTACT, false);
        map.put(VIEW_HIDE_BLOCKED, false);
        map.put(NET_SEND_CHAT_STATE, true);
        map.put(NET_SEND_ROSTER_NAME, false);
        map.put(NET_STATUS_LIST, new String[]{mDefaultXMPPStatus});
        map.put(NET_AUTO_SUBSCRIPTION, false);
        map.put(NET_REQUEST_AVATARS, true);
        map.put(NET_MAX_IMG_SIZE, -1);
        map.put(MAIN_CONNECT_STARTUP, true);
        map.put(MAIN_TRAY, true);
        map.put(MAIN_TRAY_CLOSE, false);
        map.put(MAIN_ENTER_SENDS, true);

        map.entrySet().stream()
                .filter(e -> !this.containsKey(e.getKey()))
                .forEach(e -> this.setProperty(e.getKey(), e.getValue()));
    }

    public void saveToFile() {
        try {
            this.save();
        } catch (ConfigurationException ex) {
            LOGGER.log(Level.WARNING, "can't save configuration", ex);
        }
    }

    public static void initialize(Path appDir) {
        if (INSTANCE != null) {
            LOGGER.warning("already initialized");
            return;
        }

        INSTANCE = new Config(appDir.resolve(Config.FILENAME));
    }

    public static Config getInstance() {
        if (INSTANCE == null)
            throw new IllegalStateException("not initialized");

        return INSTANCE;
    }
}

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

package org.kontalk.util;

import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.kontalk.Kontalk;

/**
 * Translation for strings used in view.
 * Use the Python script for updating the string properties file!
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public class Tr {
    private static final Logger LOGGER = Logger.getLogger(Tr.class.getName());

    private static final String DEFAULT_LANG = "en";
    private static final String I18N_DIR = "i18n/";
    private static final String STRING_FILE = "strings";
    private static final String PROP_EXT = ".properties";

    private static final String WIKI_BASE = "https://github.com/kontalk/desktopclient-java/wiki";
    private static final String WIKI_HOME = "Home";
    private static final List<String> WIKI_LANGS = Arrays.asList("de");


    /** Map default (English) strings to translated strings. **/
    private static Map<String, String> TR_MAP = null;

    /**
     * Translate string used in user interface.
     * Spaces at beginning or end of string not supported!
     * @param s string thats wants to be translated (in English)
     * @return translation of input string (depending of platform language)
     */
    public static String tr(String s) {
        if (TR_MAP == null || !TR_MAP.containsKey(s))
            return s;
        return TR_MAP.get(s);
    }

    public static void init() {
        // get language
        String lang = Locale.getDefault().getLanguage();
        if (lang.equals(DEFAULT_LANG)) {
            return;
        }

        LOGGER.info("Setting language: "+lang);

        // load string keys file
        String path = Kontalk.RES_PATH + I18N_DIR + STRING_FILE + PROP_EXT;
        PropertiesConfiguration stringKeys;
        try {
            stringKeys = new PropertiesConfiguration(ClassLoader.getSystemResource(path));
        } catch (ConfigurationException ex) {
            LOGGER.log(Level.WARNING, "can't load string key file", ex);
            return;
        }

        // load translation file
        path = Kontalk.RES_PATH + I18N_DIR + STRING_FILE + "_" + lang + PROP_EXT;
        URL url = ClassLoader.getSystemResource(path);
        if (url == null) {
            LOGGER.info("can't find translation file: "+path);
            return;
        }
        PropertiesConfiguration tr = new PropertiesConfiguration();
        tr.setEncoding("UTF-8");
        try {
            tr.load(url);
        } catch (ConfigurationException ex) {
            LOGGER.log(Level.WARNING, "can't load translation file", ex);
            return;
        }

        TR_MAP = new HashMap<>();
        Iterator<String> it = tr.getKeys();
        while (it.hasNext()) {
            String k = it.next();
            if (!stringKeys.containsKey(k)) {
                LOGGER.warning("key in translation but not in key file: "+k);
                continue;
            }
            TR_MAP.put(stringKeys.getString(k), tr.getString(k));
        }
    }

    public static String getLocalizedWikiLink() {
        String lang = Locale.getDefault().getLanguage();
        if (WIKI_LANGS.contains(lang)) {
            // damn URI decoding
            return WIKI_BASE + "/%5B" + lang + "%5D-" + WIKI_HOME;
        }
        return WIKI_BASE;
    }
}

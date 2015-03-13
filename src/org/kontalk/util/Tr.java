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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.kontalk.Kontalk;

/**
 * Translation for strings used in view.
 * TODO strings key file can't be updated dynamically
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class Tr {
    private final static Logger LOGGER = Logger.getLogger(Tr.class.getName());

    private final static String DEFAULT_LANG = "en";
    private final static String I18N_DIR = "i18n/";
    private final static String STRING_FILE = "strings";
    private final static String PROP_EXT = ".properties";

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

    public static void init(){
        // get language
        String lang = Locale.getDefault().getLanguage();
        // TODO for testing
        lang = "de";
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
        PropertiesConfiguration tr;
        try {
            tr = new PropertiesConfiguration(ClassLoader.getSystemResource(path));
        } catch (ConfigurationException ex) {
            LOGGER.info("can't load translation file");
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
}

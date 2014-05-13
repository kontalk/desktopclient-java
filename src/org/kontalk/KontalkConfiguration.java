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

import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class KontalkConfiguration extends PropertiesConfiguration{
    private final static Logger LOGGER = Logger.getLogger(KontalkConfiguration.class.getName());

    private final static String CONFIGFILENAME = "kontalk.properties";
    
    private static KontalkConfiguration INSTANCE = null;
    
    public final static String SERV_NET = "server.network";
    public final static String SERV_HOST = "server.host";
    public final static String SERV_PORT = "server.port";
    public final static String ACC_PUB_KEY = "account.public_key";
    public final static String ACC_PRIV_KEY = "account.private_key";
    public final static String ACC_BRIDGE_CERT = "account.bridge_cert";
    public final static String ACC_PASS = "account.passphrase";
    
    public final static String DEFAULT_SERV_NET = "kontalk.net";
    public final static String DEFAULT_SERV_HOST = "prime.kontalk.net";
    public final static int DEFAULT_SERV_PORT = 5222;
    
    private KontalkConfiguration(String filename) throws ConfigurationException {
        super(filename);
    }

    private KontalkConfiguration() {
        super();
        initConfig();
    }

    private void initConfig() {
        setFileName(CONFIGFILENAME);
        setProperty(SERV_NET, DEFAULT_SERV_NET);
        setProperty(SERV_HOST, DEFAULT_SERV_HOST);
        setProperty(SERV_PORT, DEFAULT_SERV_PORT);
        setProperty(ACC_PUB_KEY, "kontalk-public.pgp");
        setProperty(ACC_PRIV_KEY, "kontalk-private.pgp");
        setProperty(ACC_BRIDGE_CERT, "kontalk-login.crt");
        //mConfig.setProperty("account.bridge_key", "kontalk-login.key");
        setProperty(ACC_PASS, "");
    }

    public static KontalkConfiguration getConfiguration() {
        if ( INSTANCE != null)
            return INSTANCE;
        
        try {
            INSTANCE = new KontalkConfiguration(CONFIGFILENAME);
        } catch (ConfigurationException ex) {
            LOGGER.info("Configuration not found. Using default values");
            INSTANCE = new KontalkConfiguration();
        }
        return INSTANCE;
    }

    public void saveToFile() {
        try {
            this.save();
        } catch (ConfigurationException ex) {
            LOGGER.log(Level.WARNING, "Can't save configuration", ex);
        }
    }
    
}

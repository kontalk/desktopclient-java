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

package org.kontalk.client;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 * Client for OpenPGP HTTP Keyserver Protocol.
 *
 * See https://tools.ietf.org/html/draft-shaw-openpgp-hkp-00
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class HKPClient {
    private static final Logger LOGGER = Logger.getLogger(HKPClient.class.getName());

    //private static final short DEFAULT_PORT = 11371;
    private static final short DEFAULT_SSL_PORT = 443;

    private static final int MAX_CONTENT_LENGTH = 9001;

    public String search(String server, String keyID) {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpRequestBase get = new HttpGet(
                "https://"+server+"/pks/lookup?op=get&options=mr&exact=on&search=0x"+keyID);

        // execute request
        CloseableHttpResponse response;
        try {
            response = client.execute(get);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't execute request, server: "+server, ex);
            return "";
        }
        try {
            int code = response.getStatusLine().getStatusCode();
            if (code != HttpStatus.SC_OK) {
                if (code == HttpStatus.SC_NOT_FOUND) {
                    LOGGER.config("key not found, server: "+server+"; keyID="+keyID);
                } else {
                    LOGGER.warning("unexpected response, server: "+server+"; code=" + code);
                }
                return "";
            }

            HttpEntity entity = response.getEntity();
            if (entity == null) {
                LOGGER.warning("no download response entity");
                return "";
            }

            if (entity.getContentLength() > MAX_CONTENT_LENGTH) {
                LOGGER.warning("content too big");
                return "";
            }

            String contentStr;
            try {
                contentStr = IOUtils.toString(entity.getContent(), "UTF-8");
            } catch (IOException | IllegalStateException ex) {
                LOGGER.log(Level.WARNING, " can't read content", ex);
                return "";
            }

    //        for (Header h: response.getAllHeaders()) {
    //            System.out.println("header: "+h);
    //        }

            return contentStr;
        } finally {
            try {
                response.close();
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "can't close response", ex);
            }
        }
    }
}

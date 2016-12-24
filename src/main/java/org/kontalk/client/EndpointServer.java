/*
 * Kontalk Java client
 * Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.client;

/**
 * Defines a server address and features.
 * @author Daniele Ricci
 * @version 1.0
 */
public final class EndpointServer {

    private final String mHost;
    private final int mPort;
    private final String mNetwork;

    public EndpointServer(String host, int port) {
        // tigase: use hostname as network
        mNetwork = host;
        mHost = host;
        mPort = port;
    }

    // TODO unused
    public EndpointServer(String network, String host, int port) {
        mNetwork = network;
        mHost = host;
        mPort = port;
    }

    @Override
    public String toString() {
        return mNetwork + "|" + mHost + ":" + mPort;
    }

    public String getHost() {
        return mHost;
    }

    public int getPort() {
        return mPort;
    }

    public String getNetwork() {
        return mNetwork;
    }

}

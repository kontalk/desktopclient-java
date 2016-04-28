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

package org.kontalk.client;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.address.packet.MultipleAddresses;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jivesoftware.smackx.pubsub.packet.PubSub;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class FeatureDiscovery {
    private static final Logger LOGGER = Logger.getLogger(FeatureDiscovery.class.getName());

    private static final Map<String, Feature> FEATURE_MAP;

    public enum Feature {
        USER_AVATAR,
        MULTI_ADDRESSING,
        /** New XEP-0363 upload service. */
        HTTP_FILE_UPLOAD
    }

    static {
        FEATURE_MAP = new HashMap<>();
        FEATURE_MAP.put(PubSub.NAMESPACE, Feature.USER_AVATAR);
        FEATURE_MAP.put(MultipleAddresses.NAMESPACE, Feature.MULTI_ADDRESSING);
        FEATURE_MAP.put(HTTPFileUpload.NAMESPACE, Feature.HTTP_FILE_UPLOAD);
    }

    /** (server) service discovery, XEP-0030. */
    static EnumMap<Feature, String> discover(XMPPConnection conn) {
        // NOTE: smack automatically creates instances of SDM and CapsM and connects them
        ServiceDiscoveryManager discoManager = ServiceDiscoveryManager.getInstanceFor(conn);

        // 1. get features from server
        EnumMap<Feature, String> features = discover(discoManager, conn.getServiceName());

        DiscoverItems items = null;
        try {
            items = discoManager.discoverItems(conn.getServiceName());
        } catch (SmackException.NoResponseException |
                XMPPException.XMPPErrorException |
                SmackException.NotConnectedException ex) {
            LOGGER.log(Level.WARNING, "can't get service discovery items", ex);
            return features;
        }

        // 2. get features from server items
        for (DiscoverItems.Item item: items.getItems()) {
            features.putAll(discover(discoManager, item.getEntityID()));
        }

        LOGGER.info("supported server features: "+features);
        return features;
    }

    private static EnumMap<Feature, String> discover(ServiceDiscoveryManager dm, String entity) {
        EnumMap<Feature, String> features = new EnumMap<>(FeatureDiscovery.Feature.class);
        DiscoverInfo info;
        try {
            // blocking
            // NOTE: null parameter does not work
            info = dm.discoverInfo(entity);
        } catch (SmackException.NoResponseException |
                XMPPException.XMPPErrorException |
                SmackException.NotConnectedException ex) {
            LOGGER.log(Level.WARNING, "can't get service discovery info", ex);
            return features;
        }

        List<DiscoverInfo.Identity> identities = info.getIdentities();
        LOGGER.config("entity: " + entity + " identities: " +
                identities.stream()
                        .map(i -> i.toXML())
                        .collect(Collectors.toList()));

        for (DiscoverInfo.Feature feature: info.getFeatures()) {
            String var = feature.getVar();
            if (FEATURE_MAP.containsKey(var)) {
                features.put(FEATURE_MAP.get(var), entity);
            }
        }

        return features;
    }
}

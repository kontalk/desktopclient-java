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
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.address.packet.MultipleAddresses;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jivesoftware.smackx.iqlast.packet.LastActivity;
import org.jivesoftware.smackx.pubsub.packet.PubSub;
import org.kontalk.misc.JID;

/**
 *  Feature Service discovery (XEP-0030).
 *
 *  A cache is used for discovering each entity at most once. Assumption is that entity features
 *  do not change during a connection session.
 *
 *  NOTE: Caps (XEP-0115) and caps cache is unfortunately not supported with server entities.
 *  The "ver=..." identifier is send with presence stanzas and server obviously don't send them.
 *  XEP-0115 mentions that the server connecting to can send a caps stanza as <stream:features>
 *  but this doesn't seem to be happen with Tigase.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class FeatureDiscovery {
    private static final Logger LOGGER = Logger.getLogger(FeatureDiscovery.class.getName());

    public enum Feature {
        USER_AVATAR,
        MULTI_ADDRESSING,
        /** New XEP-0363 upload service. */
        HTTP_FILE_UPLOAD,
        /** XEP-0012. */
        LAST_ACTIVITY
    }

    private static final Map<String, Feature> FEATURE_MAP;

    static {
        FEATURE_MAP = new HashMap<>();
        FEATURE_MAP.put(PubSub.NAMESPACE, Feature.USER_AVATAR);
        FEATURE_MAP.put(MultipleAddresses.NAMESPACE, Feature.MULTI_ADDRESSING);
        FEATURE_MAP.put(HTTPFileUpload.NAMESPACE, Feature.HTTP_FILE_UPLOAD);
        FEATURE_MAP.put(LastActivity.NAMESPACE, Feature.LAST_ACTIVITY);
    }

    private final KonConnection mConn;
    // NOTE: ignoring resource
    private final Map<JID, EnumMap<Feature, JID>> mCache = new HashMap<>();

    FeatureDiscovery(KonConnection conn) {
        mConn = conn;
    }

    /** Discover all known features of connected server and its items.  */
    EnumMap<Feature, JID> getServerFeatures() {
        // TODO use ServiceDiscoveryManager.serverSupportsFeature()
        return getFeatures(JID.fromSmack(mConn.getServiceName()), true);
    }

    /** Discover all known features of an entity.  */
    EnumMap<Feature, JID> getFeaturesFor(JID entity) {
        return getFeatures(entity, false);
    }

    private EnumMap<Feature, JID> getFeatures(JID entity, boolean withItems) {
        if (!mCache.containsKey(entity))
            mCache.put(entity, this.discover(entity, withItems));

        return mCache.get(entity);
    }

    private EnumMap<Feature, JID> discover(JID entity, boolean withItems) {
        // NOTE: smack automatically creates instances of SDM and CapsM and connects them
        ServiceDiscoveryManager discoManager = ServiceDiscoveryManager.getInstanceFor(mConn);

        // 1. get features from server
        EnumMap<Feature, JID> features = discover(discoManager, entity);
        if (features == null)
            return new EnumMap<>(FeatureDiscovery.Feature.class);

        if (!withItems)
            return features;

        // 2. get server items
        DiscoverItems items;
        try {
            items = discoManager.discoverItems(entity.toBareSmack());
        } catch (SmackException.NoResponseException |
                XMPPException.XMPPErrorException |
                SmackException.NotConnectedException |
                InterruptedException ex) {
            LOGGER.log(Level.WARNING, "can't get service discovery items", ex);
            return features;
        }

        // 3. get features from server items
        for (DiscoverItems.Item item: items.getItems()) {
            EnumMap<Feature, JID> itemFeatures = discover(discoManager, JID.fromSmack(item.getEntityID()));
            if (itemFeatures != null)
                features.putAll(itemFeatures);
        }

        LOGGER.info("supported server features: "+features);
        return features;
    }

    private static EnumMap<Feature, JID> discover(ServiceDiscoveryManager dm, JID entity) {
        DiscoverInfo info;
        try {
            // blocking
            // NOTE: null parameter does not work
            info = dm.discoverInfo(entity.toSmack());
        } catch (SmackException.NoResponseException |
                XMPPException.XMPPErrorException |
                SmackException.NotConnectedException |
                InterruptedException ex) {
            // not supported by all servers/server not reachable, we only know after trying
            //LOGGER.log(Level.WARNING, "can't get service discovery info", ex);
            LOGGER.warning("can't get info for " + entity + " " + ex.getMessage());
            return null;
        }

        EnumMap<Feature, JID> features = new EnumMap<>(FeatureDiscovery.Feature.class);
        for (DiscoverInfo.Feature feature: info.getFeatures()) {
            String var = feature.getVar();
            if (FEATURE_MAP.containsKey(var)) {
                features.put(FEATURE_MAP.get(var), entity);
            }
        }

        List<DiscoverInfo.Identity> identities = info.getIdentities();
        LOGGER.config("entity: " + entity
                + " identities: " + identities.stream()
                .map(DiscoverInfo.Identity::toXML).collect(Collectors.toList())
                + " features: " + info.getFeatures().stream()
                .map(DiscoverInfo.Feature::getVar).collect(Collectors.toList()));

        return features;
    }
}

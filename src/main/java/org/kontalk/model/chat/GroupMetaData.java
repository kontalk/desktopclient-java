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

package org.kontalk.model.chat;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.kontalk.misc.JID;

/**
 * Immutable meta data fields for a specific group chat protocol implementation.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public abstract class GroupMetaData {
    private static final Logger LOGGER = Logger.getLogger(GroupMetaData.class.getName());

    abstract String toJSON();

    /** Data fields specific to a Kontalk group chat (custom protocol). */
    public static class KonGroupData extends GroupMetaData {
        private static final String JSON_OWNER_JID = "jid";
        private static final String JSON_ID = "id";

        public final JID owner;
        public final String id;

        public KonGroupData(JID ownerJID, String id) {
            this.owner = ownerJID;
            this.id = id;
        }

        // using legacy lib, raw types extend Object
        @SuppressWarnings(value = "unchecked")
        @Override
        public String toJSON() {
            JSONObject json = new JSONObject();
            json.put(JSON_OWNER_JID, owner.string());
            json.put(JSON_ID, id);
            return json.toJSONString();
        }

        private static GroupMetaData fromJSON(Map<?, ?> map) {
            JID jid = JID.bare((String) map.get(JSON_OWNER_JID));
            String id = (String) map.get(JSON_ID);
            return new KonGroupData(jid, id);
        }

        @Override
        public final boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof KonGroupData)) {
                return false;
            }
            KonGroupData oGID = (KonGroupData) o;
            return owner.equals(oGID.owner) && id.equals(oGID.id);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 37 * hash + Objects.hashCode(this.owner);
            hash = 37 * hash + Objects.hashCode(this.id);
            return hash;
        }

        @Override
        public String toString() {
            return "KGD:{id="+id+",owner="+owner+"}";
        }
    }

    /** Data fields specific to a MUC (XEP-0045) chat. Not used/deprecated! */
    public static class MUCData extends GroupMetaData {
        private static final String JSON_ROOM = "room";
        private static final String JSON_PW = "pw";

        public final JID room;
        public final String password;

        public MUCData(JID room) {
            this(room, "");
        }

        public MUCData(JID room, String password) {
            this.room = room;
            this.password = password;
        }

        // using legacy lib, raw types extend Object
        @SuppressWarnings(value = "unchecked")
        @Override
        public String toJSON() {
            JSONObject json = new JSONObject();
            json.put(JSON_ROOM, room.string());
            json.put(JSON_PW, password);
            return json.toJSONString();
        }

        private static GroupMetaData fromJSON(Map<?, ?> map) {
            JID room = JID.bare((String) map.get(JSON_ROOM));
            String pw = (String) map.get(JSON_PW);
            return new MUCData(room, pw);
        }

        @Override
        public final boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof MUCData)) {
                return false;
            }
            MUCData oData = (MUCData) o;
            return room.equals(oData.room);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 79 * hash + Objects.hashCode(this.room);
            return hash;
        }

        @Override
        public String toString() {
            return "MUCD:{room="+room+"}";
        }
    }

    static GroupMetaData fromJSONOrNull(String json) {
        Object obj = JSONValue.parse(json);
        try {
            Map<?, ?> map = (Map) obj;
            return map.containsKey(MUCData.JSON_ROOM) ?
                    MUCData.fromJSON(map) :
                    KonGroupData.fromJSON(map);
        } catch (NullPointerException | ClassCastException ex) {
            LOGGER.log(Level.WARNING, "can't parse JSON", ex);
            return null;
        }
    }
}

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

import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.packet.Message;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class ClientUtils {
    private static final Logger LOGGER = Logger.getLogger(ClientUtils.class.getName());

    /**
     * Message attributes to identify the chat for a message.
     */
    public static class MessageIDs {
        public final String jid;
        public final String xmppID;
        public final String xmppThreadID;
        //public final Optional<GroupID> groupID;

        private MessageIDs(String jid, String xmppID, String threadID) {
            this.jid = jid;
            this.xmppID = xmppID;
            this.xmppThreadID = threadID;
        }

        public static MessageIDs from(Message m) {
            return from(m, "");
        }

        public static MessageIDs from(Message m, String receiptID) {
            return new MessageIDs(
                    StringUtils.defaultString(m.getFrom()),
                    !receiptID.isEmpty() ? receiptID :
                            StringUtils.defaultString(m.getStanzaId()),
                    StringUtils.defaultString(m.getThread()));
        }

        @Override
        public String toString() {
            return "IDs:jid="+jid+",xmpp="+xmppID+",thread="+xmppThreadID;
        }
    }
}

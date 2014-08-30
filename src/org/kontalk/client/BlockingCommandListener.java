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

import java.util.logging.Logger;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.StringUtils;
import org.kontalk.model.User;
import org.kontalk.model.UserList;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class BlockingCommandListener implements PacketListener {
    private final static Logger LOGGER = Logger.getLogger(BlockingCommand.class.getName());

    public BlockingCommandListener() {
        ProviderManager.addIQProvider(BlockingCommand.BLOCKLIST, BlockingCommand.NAMESPACE, new BlockingCommand.Provider());
    }

    @Override
    public void processPacket(Packet packet) {
        BlockingCommand p = (BlockingCommand) packet;
        LOGGER.info("got blocking command: "+p.toXML());

        // TODO
        System.out.println("blocked items: "+p.getItems());
        if (p.getItems() != null) {
            for (String jid : p.getItems()) {
                if (StringUtils.isFullJID(jid)) {
                    LOGGER.info("ignoring blocking of JID with ressource");
                    return;
                }
                if (!UserList.getInstance().containsUserWithJID(jid)) {
                    LOGGER.info("ignoring blocking of JID not in user list");
                    return;
                }
                User user = UserList.getInstance().getUserByJID(jid);
                LOGGER.info("blocked user: "+user.getID());
                user.setBlocked(true);
            }
            //UserList.getInstance().changed();
        }
    }

}

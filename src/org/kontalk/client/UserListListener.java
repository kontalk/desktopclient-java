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

import java.util.Collection;
import java.util.logging.Logger;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.packet.Presence;
import org.kontalk.model.UserList;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class UserListListener implements RosterListener {
    private final static Logger LOGGER = Logger.getLogger(UserListListener.class.getName());
    
    private Roster mRoster;
    
    UserListListener(Roster roster) {
        mRoster = roster;
    }
    
    @Override
    public void entriesAdded(Collection<String> addresses) {
        syncFromRoster();
    }

    @Override
    public void entriesUpdated(Collection<String> addresses) {
        syncFromRoster();
    }

    @Override
    public void entriesDeleted(Collection<String> addresses) {
        syncFromRoster();
    }

    @Override
    public void presenceChanged(Presence presence) {
        syncFromRoster();
    }

    private void syncFromRoster() {
         if (mRoster == null)
            return;
        LOGGER.info("updating from roster");
      
        // TODO
        UserList userList = UserList.getInstance();
        for (RosterEntry entry: mRoster.getEntries()) {
            userList.addUser(entry.getUser(), entry.getName());
        }
    }
   
}

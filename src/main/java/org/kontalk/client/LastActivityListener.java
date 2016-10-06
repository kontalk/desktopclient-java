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

import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.iqlast.packet.LastActivity;
import org.kontalk.misc.JID;
import org.kontalk.system.Control;

/**
 * Listener for Last Activity Response packets (XEP-0012).
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public class LastActivityListener implements StanzaListener {
    private static final Logger LOGGER = Logger.getLogger(LastActivityListener.class.getName());

    private final Control mControl;

    public LastActivityListener(Control control) {
        mControl = control;
    }

    @Override
    public void processPacket(Stanza packet) throws SmackException.NotConnectedException {
        LOGGER.config("last activity: " + packet);

        LastActivity lastActivity = (LastActivity) packet;

        long lastActivityTime = lastActivity.getIdleTime();
        if (lastActivityTime < 0) {
            // error message or parsing error, not important here (logged by Client class)
            return;
        }

        mControl.onLastActivity(JID.full(lastActivity.getFrom()),
                lastActivity.getIdleTime(),
                StringUtils.defaultString(lastActivity.getStatusMessage()));
    }
}

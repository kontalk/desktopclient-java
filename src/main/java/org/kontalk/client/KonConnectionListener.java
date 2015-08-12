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

import java.util.logging.Level;
import java.util.logging.Logger;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.misc.KonException;
import org.kontalk.system.Config;
import org.kontalk.system.Control;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class KonConnectionListener implements ConnectionListener {
    private static final Logger LOGGER = Logger.getLogger(KonConnectionListener.class.getName());

    private final Control mControl;

    KonConnectionListener(Control control) {
        mControl = control;
    }

    @Override
    public void connected(XMPPConnection connection) {
        LOGGER.info("to "+connection.getHost());
    }

    @Override
    public void authenticated(XMPPConnection connection, boolean resumed) {
        LOGGER.info("as "+connection.getUser());
        String jid = XmppStringUtils.parseBareJid(connection.getUser());
        Config.getInstance().setProperty(Config.ACC_JID, jid);
    }

    @Override
    public void connectionClosed() {
        LOGGER.info("connection closed");
    }

    @Override
    public void connectionClosedOnError(Exception ex) {
        LOGGER.log(Level.WARNING, "connection closed on error", ex);
        mControl.setStatus(Control.Status.ERROR);
        mControl.handleException(new KonException(KonException.Error.CLIENT_ERROR, ex));
    }

    @Override
    public void reconnectingIn(int seconds) {
        LOGGER.info("reconnecting in " + seconds + " secs");
    }

    @Override
    public void reconnectionSuccessful() {
        LOGGER.info("reconnected");
    }

    @Override
    public void reconnectionFailed(Exception e) {
        LOGGER.info("reconnection failed");
    }
}

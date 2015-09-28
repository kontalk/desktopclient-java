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

package org.kontalk.misc;

import org.kontalk.crypto.PGPUtils.PGPCoderKey;
import org.kontalk.model.InMessage;
import org.kontalk.model.KonMessage;
import org.kontalk.model.Contact;
import org.kontalk.system.RosterHandler;

/**
 * Events passed from controller to view.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public class ViewEvent {

    private ViewEvent() {}

    /** Application status changed. */
    public static class StatusChanged extends ViewEvent {
    }

    /** Key is password protected (ask for password). */
    public static class PasswordSet extends ViewEvent {
    }

    /** The personal account is missing (show import wizard). */
    public static class MissingAccount extends ViewEvent {
        public final boolean connect;

        public MissingAccount(boolean connect) {
            this.connect = connect;
        }
    }

    /** An exception was thrown somewhere. */
    public static class Exception extends ViewEvent {
        public final KonException exception;

        public Exception(KonException exception) {
            this.exception = exception;
        }
    }

    /** There was a security error while de-/encrypting a message. */
    public static class SecurityError extends ViewEvent {
        public final KonMessage message;

        public SecurityError(KonMessage message) {
            this.message = message;
        }
    }

    /** Got a new message. */
    public static class NewMessage extends ViewEvent {
        public final InMessage message;

        public NewMessage(InMessage message) {
            this.message = message;
        }
    }

    /** Got a new public key (ask whattodo). */
    public static class NewKey extends ViewEvent {
        public final Contact contact;
        public final PGPCoderKey key;

        public NewKey(Contact contact, PGPCoderKey key) {
            this.contact = contact;
            this.key = key;
        }
    }

    /** Contact was deleted in roster (ask whattodo). */
    public static class ContactDeleted extends ViewEvent {
        public final Contact contact;

        public ContactDeleted(Contact contact) {
            this.contact = contact;
        }
    }

    /** A presence error. */
    public static class PresenceError extends ViewEvent {
        public final Contact contact;
        public final RosterHandler.Error error;

        public PresenceError(Contact contact, RosterHandler.Error error) {
            this.contact = contact;
            this.error = error;
        }
    }
}

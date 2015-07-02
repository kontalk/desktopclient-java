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

import org.kontalk.model.KonMessage;

/**
 * Events passed from controller to view.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public abstract class ViewEvent {

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
}

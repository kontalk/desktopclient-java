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

package org.kontalk;

/**
 * Used to pass exceptions from origin to view.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class KonException extends Exception {

    public enum Error {
        ACCOUNT_KEY, ACCOUNT_FILE, CLIENT_CONNECTION, CLIENT_CONNECT, CLIENT_LOGIN
    }

    private final Error mError;
    private final Exception mException;

    public KonException(Error error, java.lang.Exception ex) {
        super();
        mException = ex;
        mError = error;
    }

    public Exception getOriginalException() {
        return mException;
    }

    public Error getError() {
        return mError;
    }
}

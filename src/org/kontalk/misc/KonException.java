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

/**
 * Application internal exceptions.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class KonException extends Exception {

    public enum Error {
        DB,
        IMPORT_ARCHIVE,
        IMPORT_READ_FILE,
        IMPORT_KEY,
        IMPORT_CHANGE_PASSWORD,
        IMPORT_WRITE_FILE,
        RELOAD_READ_FILE,
        RELOAD_KEY,
        CLIENT_CONNECTION,
        CLIENT_CONNECT,
        CLIENT_LOGIN,
        CLIENT_ERROR
    }

    private final Error mError;
    private final Class<?> mExceptionClass;

    public KonException(Error error, java.lang.Exception ex) {
        super();
        mError = error;
        mExceptionClass = ex.getClass();
    }

    public Class<?> getExceptionClass() {
        return mExceptionClass;
    }

    public Error getError() {
        return mError;
    }
}

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
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class KonException extends Exception {

    public enum Error {
        DB,
        IMPORT_ARCHIVE,
        IMPORT_READ_FILE,
        IMPORT_KEY,
        CHANGE_PASS,
        CHANGE_PASS_COPY,
        WRITE_FILE,
        READ_FILE,
        LOAD_KEY,
        LOAD_KEY_DECRYPT,
        CLIENT_CONNECT,
        CLIENT_LOGIN,
        CLIENT_ERROR,
        DOWNLOAD_CREATE,
        DOWNLOAD_RESPONSE,
        DOWNLOAD_EXECUTE,
        DOWNLOAD_WRITE,
        UPLOAD_CREATE,
        UPLOAD_RESPONSE,
        UPLOAD_EXECUTE,
    }

    private final Error mError;

    public KonException(Error error, Exception ex) {
        super(ex);
        mError = error;
    }

    public KonException(Error error) {
        super();
        mError = error;
    }

    public Class<?> getCauseClass() {
        Throwable cause = this.getCause();
        return cause == null ? this.getClass() : cause.getClass();
    }

    public Error getError() {
        return mError;
    }
}

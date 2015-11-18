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

import java.util.Optional;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class Callback<V> {
    public final V value;
    public final Optional<Exception> exception;

    public Callback() {
        this.value = null;
        this.exception = Optional.empty();
    }

    public Callback(V response) {
        this.value = response;
        this.exception = Optional.empty();
    }

    public Callback(Exception ex) {
        this.value = null;
        this.exception = Optional.of(ex);
    }

    public interface Handler<V> {
        void handle(Callback<V> callback);
    }
}

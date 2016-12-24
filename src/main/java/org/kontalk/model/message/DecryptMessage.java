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

package org.kontalk.model.message;

import java.util.EnumSet;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.Coder.Signing;
import org.kontalk.model.Contact;

/**
 * Interface for decryptable messages.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public interface DecryptMessage {

    Contact getContact();

    boolean isEncrypted();

    MessageContent getContent();

    void setDecryptedContent(MessageContent content);

    void setSigning(Signing signing);

    void setSecurityErrors(EnumSet<Coder.Error> errors);
}

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

package org.kontalk.model;

import java.util.EnumSet;
import java.util.logging.Logger;
import org.kontalk.crypto.Coder;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class CoderStatus {
    private final static Logger LOGGER = Logger.getLogger(CoderStatus.class.getName());

    private Coder.Encryption mEncryption;
    private Coder.Signing mSigning;
    private final EnumSet<Coder.Error> mErrors;

    public CoderStatus(Coder.Encryption encryption,
            Coder.Signing signing,
            EnumSet<Coder.Error> errors) {
        this.mEncryption = encryption;
        this.mSigning = signing;
        this.mErrors = errors;
    }

    void setDecrypted() {
        assert mEncryption == Coder.Encryption.ENCRYPTED;
        mEncryption = Coder.Encryption.DECRYPTED;
    }

    public Coder.Encryption getEncryption() {
        return mEncryption;
    }

    public Coder.Signing getSigning() {
        return mSigning;
    }

    public void setSigning(Coder.Signing signing) {
        if (signing == mSigning)
            return;

        // check for locical errors in coder
        if (signing == Coder.Signing.NOT)
            assert mSigning == Coder.Signing.UNKNOWN;
        if (signing == Coder.Signing.SIGNED)
            assert mSigning == Coder.Signing.UNKNOWN;
        if (signing == Coder.Signing.VERIFIED)
            assert mSigning == Coder.Signing.SIGNED ||
                    mSigning == Coder.Signing.UNKNOWN;

        mSigning = signing;
    }

    public EnumSet<Coder.Error> getErrors() {
        return mErrors;
    }

    public void setSecurityErrors(EnumSet<Coder.Error> errors) {
        mErrors.clear();
        mErrors.addAll(errors);
    }
}

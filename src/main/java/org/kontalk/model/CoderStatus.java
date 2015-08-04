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
import org.kontalk.crypto.Coder;

/**
 * The encoding/decoding status of a an item (text, attachment, ...) in a
 * message.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public class CoderStatus {

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

    public Coder.Encryption getEncryption() {
        return mEncryption;
    }

    public boolean isEncrypted() {
        return mEncryption == Coder.Encryption.ENCRYPTED;
    }

    /**
     * Return whether the data is (or was) encrypted.
     * @return true if message is (or was) encrypted, else false
     */
    public boolean isSecure() {
        return mEncryption == Coder.Encryption.ENCRYPTED ||
                mEncryption == Coder.Encryption.DECRYPTED;
    }

    void setDecrypted() {
        assert mEncryption == Coder.Encryption.ENCRYPTED;
        mEncryption = Coder.Encryption.DECRYPTED;
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
        // better return a copy
        return mErrors.clone();
    }

    public boolean hasSecurityError(Coder.Error error) {
        return mErrors.contains(error);
    }

    public void setSecurityErrors(EnumSet<Coder.Error> errors) {
        mErrors.clear();
        mErrors.addAll(errors);
    }

    @Override
    public String toString() {
        return "CSTAT:encr="+mEncryption+",sign="+mSigning+",err="+mErrors;
    }

    static CoderStatus createInsecure() {
        return new CoderStatus(Coder.Encryption.NOT, Coder.Signing.NOT,
                EnumSet.noneOf(Coder.Error.class));
    }

    static CoderStatus createEncrypted() {
        return new CoderStatus(Coder.Encryption.ENCRYPTED, Coder.Signing.UNKNOWN,
                EnumSet.noneOf(Coder.Error.class));
    }
}

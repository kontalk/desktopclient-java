/*
 * Kontalk Java client
 * Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.kontalk.util;

import java.util.Arrays;
import java.util.List;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import org.apache.commons.codec.digest.DigestUtils;
import org.kontalk.misc.JID;

/**
 * XMPP related functions.
 *
 * @author Alexander Bikadorov
 */
public final class XMPPUtils {

    // TODO do not hardcode, maybe download
    private static final List<String> KONTALK_DOMAINS = Arrays.asList("beta.kontalk.net");

    private XMPPUtils() {
        throw new AssertionError();
    }

    public static String phoneNumberToKontalkLocal(String number) {
        PhoneNumberUtil pnUtil = PhoneNumberUtil.getInstance();
        PhoneNumber n;
        try {
            n = pnUtil.parse(number, null);
        } catch (NumberParseException ex) {
            return "";
        }

        if (!pnUtil.isValidNumber(n))
            return "";

        return DigestUtils.sha1Hex(
                PhoneNumberUtil.getInstance().format(n,
                PhoneNumberUtil.PhoneNumberFormat.E164));
    }

    public static boolean isKontalkJID(JID jid) {
        return KONTALK_DOMAINS.contains(jid.domain());
    }
}

/*
 * Kontalk Java client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.codec.digest.DigestUtils;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.model.Contact;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * XMPP related functions.
 *
 * @author Daniele Ricci
 */
public final class XMPPUtils {

    public static final String XML_XMPP_TYPE = "application/xmpp+xml";

    // TODO do not hardcode, maybe download
    private static final List<String> KONTALK_SERVER = Arrays.asList("beta.kontalk.org");

    private XMPPUtils() {
        throw new AssertionError();
    }

    private static XmlPullParserFactory _xmlFactory;

    private static XmlPullParser getPullParser(String data) throws XmlPullParserException {
        if (_xmlFactory == null) {
            _xmlFactory = XmlPullParserFactory.newInstance();
            _xmlFactory.setNamespaceAware(true);
        }

        XmlPullParser parser = _xmlFactory.newPullParser();
        parser.setInput(new StringReader(data));

        return parser;
    }

    /**
     * Parses a &lt;xmpp&gt;-wrapped message stanza.
     */
    public static Message parseMessageStanza(String data)
            throws XmlPullParserException, IOException, SmackException {

        XmlPullParser parser = getPullParser(data);
        boolean done = false, in_xmpp = false;
        Message msg = null;

        while (!done) {
            int eventType = parser.next();

            if (eventType == XmlPullParser.START_TAG) {
                if ("xmpp".equals(parser.getName())) {
                    in_xmpp = true;
                } else if ("message".equals(parser.getName()) && in_xmpp) {
                    msg = PacketParserUtils.parseMessage(parser);
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if ("xmpp".equals(parser.getName())) {
                    done = true;
                }
            }
        }

        return msg;
    }

    public static boolean isHash(String jid) {
        return XmppStringUtils.parseLocalpart(jid).matches("[0-9a-f]{40}");
    }

    public static boolean isValid(String jid) {
        return !XmppStringUtils.parseLocalpart(jid).isEmpty() &&
                !XmppStringUtils.parseDomain(jid).isEmpty();
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

    public static boolean isKontalkContact(Contact contact){
        return KONTALK_SERVER.contains(XmppStringUtils.parseDomain(contact.getJID()));
    }
}

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

package org.kontalk.misc;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Localpart;
import org.jxmpp.jid.util.JidUtil;
import org.jxmpp.stringprep.XmppStringprepException;
import org.jxmpp.stringprep.simple.SimpleXmppStringprep;
import org.jxmpp.util.XmppStringUtils;

/**
 * A Jabber ID (the address of an XMPP entity). Immutable.
 *
 * Escaping of local part (XEP-0106) supported.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class JID {
    private static final Logger LOGGER = Logger.getLogger(JID.class.getName());

    static {
        // good to know. For working JID validation
        SimpleXmppStringprep.setup();
    }

    private final String mLocal; // escaped!
    private final String mDomain;
    private final String mResource;
    private final boolean mValid;

    private JID(String local, String domain, String resource) {
        mLocal = local;
        mDomain = domain;
        mResource = resource;

        mValid = !mLocal.isEmpty() && !mDomain.isEmpty()
                // NOTE: domain check could be stronger - compliant with RFC 6122, but
                // server does not accept most special characters
                // NOTE: resource not checked
                && JidUtil.isTypicalValidEntityBareJid(
                        XmppStringUtils.completeJidFrom(mLocal, mDomain));
    }

    /** Return unescaped local part. */
    public String local() {
        return XmppStringUtils.unescapeLocalpart(mLocal);
    }

    public String domain() {
        return mDomain;
    }

    /** Return JID as escaped string. */
    public String string() {
        return XmppStringUtils.completeJidFrom(mLocal, mDomain, mResource);
    }

    public String asUnescapedString() {
        return XmppStringUtils.completeJidFrom(this.local(), mDomain, mResource);
    }

    public boolean isValid() {
        return mValid;
    }

    public boolean isHash() {
        return mLocal.matches("[0-9a-f]{40}");
    }

    public boolean isFull() {
        return !mResource.isEmpty();
    }

    public JID toBare() {
        return new JID(mLocal, mDomain, "");
    }

    /** To invalid(!) domain JID. */
    public JID toDomain() {
        return new JID("", mDomain, "");
    }

    public BareJid toBareSmack() {
        try {
            return JidCreate.bareFrom(this.string());
        } catch (XmppStringprepException ex) {
            LOGGER.log(Level.WARNING, "could not convert to smack", ex);
            throw new RuntimeException("You didn't check isValid(), idiot!");
        }
    }

    public Jid toSmack() {
        try {
            return JidCreate.from(this.string());
        } catch (XmppStringprepException ex) {
            LOGGER.log(Level.WARNING, "could not convert to smack", ex);
            throw new RuntimeException("Not even a simple Jid?");
        }
    }

    /**
     * Comparing only bare JIDs.
     * Case-insensitive.
     */
    @Override
    public final boolean equals(Object o) {
        if (o == this)
            return true;

       if (!(o instanceof JID))
           return false;

       JID oJID = (JID) o;
       return mLocal.equalsIgnoreCase(oJID.mLocal) &&
               mDomain.equalsIgnoreCase(oJID.mDomain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mLocal, mDomain);
    }

    /** Use this only for debugging and otherwise string() instead! */
    @Override
    public String toString() {
        return "'"+this.string()+"'";
    }

    public static JID full(String jid) {
        jid = StringUtils.defaultString(jid);
        return escape(XmppStringUtils.parseLocalpart(jid),
                XmppStringUtils.parseDomain(jid),
                XmppStringUtils.parseResource(jid));
    }

    public static JID bare(String jid) {
        jid = StringUtils.defaultString(jid);
        return escape(XmppStringUtils.parseLocalpart(jid), XmppStringUtils.parseDomain(jid), "");
    }

    public static JID fromSmack(Jid jid) {
        Localpart localpart = jid.getLocalpartOrNull();
        return new JID(localpart != null ? localpart.toString() : "",
                jid.getDomain().toString(),
                jid.getResourceOrEmpty().toString());
    }

    public static JID bare(String local, String domain) {
        return escape(local, domain, "");
    }

    private static JID escape(String local, String domain, String resource) {
        return new JID(XmppStringUtils.escapeLocalpart(local), domain, resource);
    }

    public static JID deleted(int id) {
        return new JID("", Integer.toString(id), "");
    }
}

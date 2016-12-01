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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kontalk.misc.JID;
import org.kontalk.model.Contact;
import org.kontalk.model.Model;
import org.kontalk.persistence.Database;

/**
 * A transmission of one message.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final public class Transmission {
    private static final Logger LOGGER = Logger.getLogger(Transmission.class.getName());

    public static final String TABLE = "transmissions";
    public static final String COL_MESSAGE_ID = "message_id";
    private static final String COL_CONTACT_ID = "user_id";
    private static final String COL_JID = "jid";
    private static final String COL_REC_DATE = "received_date";
    public static final String SCHEMA = "( " +
            Database.SQL_ID +
            COL_MESSAGE_ID + " INTEGER NOT NULL, " +
            // from or to contact
            COL_CONTACT_ID + " INTEGER NOT NULL, " +
            // full jid with resource
            // TODO do we really need this anywhere?
            COL_JID + " TEXT NOT NULL, " +
            // received date, if received yet
            COL_REC_DATE + " INTEGER, " +
            "FOREIGN KEY ("+COL_MESSAGE_ID+") REFERENCES "+KonMessage.TABLE+" (_id) " +
            "FOREIGN KEY ("+COL_CONTACT_ID+") REFERENCES "+Contact.TABLE+" (_id) " +
            ")";

    private final int mID;

    private final Contact mContact;
    private final JID mJID;
    private Date mReceivedDate;

    Transmission(Contact contact, JID jid, int messageID) {
        mContact = contact;
        mJID = jid;
        mReceivedDate = null;

        mID = this.insert(messageID);
    }

    private Transmission(Database db, int id, Contact contact, JID jid, Date receivedDate) {
        mID = id;
        mContact = contact;
        mJID = jid;
        mReceivedDate = receivedDate;
    }

    public Contact getContact() {
        return mContact;
    }

    public JID getJID() {
        return mJID;
    }

    public Optional<Date> getReceivedDate() {
        return Optional.ofNullable(mReceivedDate);
    }

    public boolean isReceived() {
        return mReceivedDate != null;
    }

    void setReceived(Date date) {
        mReceivedDate = date;
        this.save();
    }

    private int insert(int messageID) {
        List<Object> values = Arrays.asList(
                messageID,
                mContact.getID(),
                mJID,
                mReceivedDate);

        int id = Model.database().execInsert(TABLE, values);
        if (id <= 0) {
            LOGGER.log(Level.WARNING, "could not insert");
            return -2;
        }
        return id;
    }

    private void save() {
        Map<String, Object> set = new HashMap<>();
        set.put(COL_REC_DATE, mReceivedDate);
        Model.database().execUpdate(TABLE, set, mID);
    }

    boolean delete() {
        if (mID < 0) {
            LOGGER.warning("not in database: "+this);
            return true;
        }
        return Model.database().execDelete(TABLE, mID);
    }

    @Override
    public String toString() {
        return "T:id="+mID+",contact="+mContact+",jid="+mJID+",recdate="+mReceivedDate;
    }

    static Set<Transmission> load(int messageID, Map<Integer, Contact> contactMap) {
        Database db = Model.database();
        HashSet<Transmission> ts = new HashSet<>();
        try (ResultSet transmissionRS = db.execSelectWhereInsecure(TABLE,
                COL_MESSAGE_ID + " == " + messageID)) {
            while (transmissionRS.next()) {
                ts.add(load(db, transmissionRS, contactMap));
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load transmission(s) from db", ex);
            return Collections.<Transmission>emptySet();
        }
        if (ts.isEmpty())
            LOGGER.warning("no transmission(s) found, messageID: "+messageID);
        return ts;
    }

    private static Transmission load(Database db, ResultSet resultSet,
            Map<Integer, Contact> contactMap)
            throws SQLException {
        int id = resultSet.getInt("_id");

        int contactID = resultSet.getInt(COL_CONTACT_ID);
        Contact contact = contactMap.get(contactID);
        if (contact == null) {
            LOGGER.warning("can't find contact in db, id: "+contactID);
            return null;
        }
        JID jid = JID.full(resultSet.getString(COL_JID));
        long rDate = resultSet.getLong(COL_REC_DATE);
        Date receivedDate = rDate == 0 ? null : new Date(rDate);

        return new Transmission(db, id, contact, jid, receivedDate);
    }

    @Override
    public final boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof Transmission))
            return false;

        Transmission oTransmission = (Transmission) o;

        return mContact.equals(oTransmission.mContact)
                && mJID.equals(oTransmission.mJID);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + Objects.hashCode(this.mContact);
        hash = 71 * hash + Objects.hashCode(this.mJID);
        return hash;
    }
}

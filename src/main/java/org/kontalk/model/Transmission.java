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

import org.kontalk.misc.JID;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kontalk.system.Database;

/**
 * A transmission of one message.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final public class Transmission {
    private static final Logger LOGGER = Logger.getLogger(Transmission.class.getName());

    public static final String TABLE = "transmissions";
    static final String COL_MESSAGE_ID = "message_id";
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
    protected Optional<Date> mReceivedDate;

    Transmission(Contact contact, JID jid, int messageID) {
        mContact = contact;
        mJID = jid;
        mReceivedDate = Optional.empty();

        mID = this.insert(messageID);
    }

    private Transmission(int id, Contact contact, JID jid, Date receivedDate) {
        mID = id;
        mContact = contact;
        mJID = jid;
        mReceivedDate = Optional.ofNullable(receivedDate);
    }

    public Contact getContact() {
        return mContact;
    }

    public JID getJID() {
        return mJID;
    }

    public Optional<Date> getReceivedDate() {
        return mReceivedDate;
    }

    public boolean isReceived() {
        return mReceivedDate.isPresent();
    }

    void setReceived(Date date) {
        mReceivedDate = Optional.of(date);
        this.save();
    }

    private int insert(int messageID) {
        Database db = Database.getInstance();

        List<Object> values = new LinkedList<>();
        values.add(messageID);
        values.add(mContact.getID());
        values.add(mJID);
        values.add(mReceivedDate);

        int id = db.execInsert(TABLE, values);
        if (id <= 0) {
            LOGGER.log(Level.WARNING, "could not insert");
            return -2;
        }
        return id;
    }

    private void save() {
        Database db = Database.getInstance();
        Map<String, Object> set = new HashMap<>();
        set.put(COL_REC_DATE, mReceivedDate);
        db.execUpdate(TABLE, set, mID);
    }

    @Override
    public String toString() {
        return "T:id="+mID+",contact="+mContact+",jid="+mJID+",recdate="+mReceivedDate;
    }

    static Transmission[] load(int messageID) {
        Database db = Database.getInstance();
        ArrayList<Transmission> ts = new ArrayList<>();
        try (ResultSet transmissionRS = db.execSelectWhereInsecure(TABLE,
                COL_MESSAGE_ID + " == " + messageID)) {
            while (transmissionRS.next()) {
                ts.add(load(transmissionRS));
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load transmission(s) from db", ex);
            return new Transmission[0];
        }
        if (ts.isEmpty())
            LOGGER.warning("no transmission(s) found, messageID: "+messageID);
        return ts.toArray(new Transmission[0]);
    }

    private static Transmission load(ResultSet resultSet) throws SQLException {
        int id = resultSet.getInt("_id");

        int contactID = resultSet.getInt(COL_CONTACT_ID);
        Optional<Contact> optContact = ContactList.getInstance().get(contactID);
        if (!optContact.isPresent()) {
            LOGGER.warning("can't find contact in db, id: "+contactID);
            return null;
        }
        JID jid = JID.full(resultSet.getString(COL_JID));
        long rDate = resultSet.getLong(COL_REC_DATE);
        Date receivedDate = rDate == 0 ? null : new Date(rDate);

        return new Transmission(id, optContact.get(), jid, receivedDate);
    }
}

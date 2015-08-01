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

package org.kontalk.view;

import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Observer;
import java.util.Optional;
import java.util.Set;
import javax.swing.ListSelectionModel;
import org.apache.commons.lang.StringEscapeUtils;
import org.kontalk.model.ThreadList;
import org.kontalk.model.Contact;
import org.kontalk.model.ContactList;
import org.kontalk.system.Control;
import org.kontalk.util.Tr;
import org.kontalk.view.ContactListView.ContactItem;

/**
 * Display all contact (aka contacts) in a brief list.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class ContactListView extends Table<ContactItem, Contact> implements Observer {

    private final ContactList mContactList;
    private final ContactPopupMenu mPopupMenu;

    ContactListView(final View view, ContactList contactList) {
        super(view, true);

        mContactList = contactList;

        this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        //this.setDragEnabled(true);

        // right click popup menu
        mPopupMenu = new ContactPopupMenu();

        // actions triggered by mouse events
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Optional<Contact> optContact = ContactListView.this.getSelectedValue();
                if (!optContact.isPresent())
                    return;

                Contact selectedContact = optContact.get();
                if (e.getClickCount() == 2) {
                    mView.showThread(selectedContact);
                } else {
                    mView.showContactDetails(selectedContact);
                }
            }
            @Override
            public void mousePressed(MouseEvent e) {
                check(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                check(e);
            }
            private void check(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = ContactListView.this.rowAtPoint(e.getPoint());
                    ContactListView.this.setSelectedItem(row);
                    ContactListView.this.showPopupMenu(e);
                }
            }
        });

        this.updateOnEDT(null);
    }

    @Override
    protected void updateOnEDT(Object arg) {
        Set<ContactItem> newItems = new HashSet<>();
        Set<Contact> contacts = mContactList.getAll();
        for (Contact contact: contacts)
            if (!this.containsValue(contact))
                newItems.add(new ContactItem(contact));
        this.sync(contacts, newItems);
    }

    private void showPopupMenu(MouseEvent e) {
        // note: only work when right click does also selection
        mPopupMenu.show(this.getSelectedItem(), this, e.getX(), e.getY());
    }

    /** One item in the contact list representing a contact. */
    final class ContactItem extends Table<ContactItem, Contact>.TableItem {

        private final WebLabel mNameLabel;
        private final WebLabel mStatusLabel;
        private Color mBackround;

        ContactItem(Contact contact) {
            super(contact);

            //this.setPaintFocus(true);
            this.setLayout(new BorderLayout(View.GAP_DEFAULT, View.GAP_SMALL));
            this.setMargin(View.MARGIN_SMALL);

            mNameLabel = new WebLabel("foo");
            mNameLabel.setFontSize(14);
            this.add(mNameLabel, BorderLayout.CENTER);

            mStatusLabel = new WebLabel("foo");
            mStatusLabel.setForeground(Color.GRAY);
            mStatusLabel.setFontSize(11);
            this.add(mStatusLabel, BorderLayout.SOUTH);

            this.updateOnEDT(null);
        }

        @Override
        public String getTooltipText() {
            String html = "<html><body>";
                    //"<h3>Header</h3>" +

            if (mValue.getOnline() == Contact.Online.YES)
                html += Tr.tr("Online")+"<br>";

            if (!mValue.getStatus().isEmpty()) {
                String status = StringEscapeUtils.escapeHtml(mValue.getStatus());
                html += Tr.tr("Status")+": " + status + "<br>";
            }

            if (mValue.getOnline() != Contact.Online.YES) {
                html += Utils.lastSeen(mValue, false) + "<br>";
            }

            if (mValue.isBlocked()) {
                html += Tr.tr("Contact is blocked!") + "<br>";
            }

            return html+"</body></html>" ;
        }

        @Override
        protected void render(int tableWidth, boolean isSelected) {
            if (isSelected)
                this.setBackground(View.BLUE);
            else
                this.setBackground(mBackround);
        }

        @Override
        protected boolean contains(String search) {
            return mValue.getName().toLowerCase().contains(search) ||
                    mValue.getJID().toLowerCase().contains(search);
        }

        @Override
        protected void updateOnEDT(Object arg) {
            // name
            String name = Utils.name(mValue);
            if (!name.equals(mNameLabel.getText())) {
                mNameLabel.setText(name);
                ContactListView.this.updateSorting();
            }

            // status
            mStatusLabel.setText(Utils.mainStatus(mValue));

            // online status
            Contact.Subscription subStatus = mValue.getSubScription();
            mBackround = mValue.getOnline() == Contact.Online.YES ? View.LIGHT_BLUE:
                    subStatus == Contact.Subscription.UNSUBSCRIBED ||
                    subStatus == Contact.Subscription.PENDING ||
                    mValue.isBlocked() ? View.LIGHT_GREY :
                    Color.WHITE;
            this.setBackground(mBackround);

            ContactListView.this.repaint();
        }

        @Override
        public int compareTo(TableItem o) {
            return mValue.getName().compareToIgnoreCase(o.mValue.getName());
        }
    }

    private class ContactPopupMenu extends WebPopupMenu {

        ContactItem mItem;
        WebMenuItem mNewMenuItem;
        WebMenuItem mBlockMenuItem;
        WebMenuItem mUnblockMenuItem;
        WebMenuItem mDeleteMenuItem;

        ContactPopupMenu() {
            mNewMenuItem = new WebMenuItem(Tr.tr("New Chat"));
            mNewMenuItem.setToolTipText(Tr.tr("Creates a new chat for this contact"));
            mNewMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    Set<Contact> contact = new HashSet<>();
                    contact.add(mItem.mValue);
                    ContactListView.this.mView.callCreateNewThread(contact);
                }
            });
            this.add(mNewMenuItem);

            mBlockMenuItem = new WebMenuItem(Tr.tr("Block Contact"));
            mBlockMenuItem.setToolTipText(Tr.tr("Block all messages from this contact"));
            mBlockMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    mView.getControl().sendContactBlocking(mItem.mValue, true);
                }
            });
            this.add(mBlockMenuItem);

            mUnblockMenuItem = new WebMenuItem(Tr.tr("Unblock Contact"));
            mUnblockMenuItem.setToolTipText(Tr.tr("Unblock this contact"));
            mUnblockMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    mView.getControl().sendContactBlocking(mItem.mValue, false);
                }
            });
            this.add(mUnblockMenuItem);

            mDeleteMenuItem = new WebMenuItem(Tr.tr("Delete Contact"));
            mDeleteMenuItem.setToolTipText(Tr.tr("Delete this contact"));
            mDeleteMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    String text = Tr.tr("Permanently delete this contact?") + "\n" +
                            Tr.tr("Chats and messages will not be deleted.");
                    if (!Utils.confirmDeletion(ContactListView.this, text))
                        return;
                    mView.getControl().deleteContact(mItem.mValue);
                }
            });
            this.add(mDeleteMenuItem);
        }

        void show(ContactItem item, Component invoker, int x, int y) {
            mItem = item;

            // dont allow creation of more than one thread for a contact
            mNewMenuItem.setVisible(!ThreadList.getInstance().contains(item.mValue));

            if (mItem.mValue.isBlocked()) {
                mBlockMenuItem.setVisible(false);
                mUnblockMenuItem.setVisible(true);
            } else {
                mBlockMenuItem.setVisible(true);
                mUnblockMenuItem.setVisible(false);
            }

            Control.Status status = ContactListView.this.mView.getCurrentStatus();
            boolean connected = status == Control.Status.CONNECTED;
            mBlockMenuItem.setEnabled(connected);
            mUnblockMenuItem.setEnabled(connected);
            mDeleteMenuItem.setEnabled(connected);

            this.show(invoker, x, y);
        }
    }
}

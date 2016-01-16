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

import com.alee.extended.image.DisplayType;
import com.alee.extended.image.WebImage;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.GroupingType;
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
import java.util.Set;
import javax.swing.Box;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.apache.commons.lang.StringEscapeUtils;
import org.kontalk.model.ChatList;
import org.kontalk.model.Contact;
import org.kontalk.model.ContactList;
import org.kontalk.system.Control;
import org.kontalk.util.Tr;
import org.kontalk.view.ContactListView.ContactItem;

/**
 * Display all contact (aka contacts) in a brief list.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class ContactListView extends ListView<ContactItem, Contact> implements Observer {

    private final ContactList mContactList;
    private final ContactPopupMenu mPopupMenu;

    ContactListView(final View view, ContactList contactList) {
        super(view, true);

        mContactList = contactList;

        this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        //this.setDragEnabled(true);

        // right click popup menu
        mPopupMenu = new ContactPopupMenu();

        // actions triggered by selection
        this.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                Contact contact = ContactListView.this.getSelectedValue().orElse(null);
                if (contact == null)
                    return;

                mView.showContactDetails(contact);
            }
        });

        // actions triggered by mouse events
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Contact contact = ContactListView.this.getSelectedValue().orElse(null);
                    if (contact != null)
                        mView.showChat(contact);
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
    final class ContactItem extends ListView<ContactItem, Contact>.TableItem {

        private final WebImage mAvatar;
        private final WebLabel mNameLabel;
        private final WebLabel mStatusLabel;
        private Color mBackground;

        ContactItem(Contact contact) {
            super(contact);

            //this.setPaintFocus(true);
            this.setLayout(new BorderLayout(View.GAP_DEFAULT, 0));
            this.setMargin(View.MARGIN_SMALL);

            mAvatar = new WebImage().setDisplayType(DisplayType.fitComponent);
            mAvatar.setPreferredSize(View.AVATAR_LIST_DIM);
            this.add(mAvatar, BorderLayout.WEST);

            mNameLabel = new WebLabel();
            mNameLabel.setFontSize(View.FONT_SIZE_BIG);

            mStatusLabel = new WebLabel();
            mStatusLabel.setForeground(Color.GRAY);
            mStatusLabel.setFontSize(View.FONT_SIZE_TINY);
            this.add(
                    new GroupPanel(View.GAP_SMALL, false,
                            mNameLabel,
                            new GroupPanel(GroupingType.fillFirst,
                                    Box.createGlue(), mStatusLabel)
                    ), BorderLayout.CENTER);

            this.updateOnEDT(null);
        }

        @Override
        public String getTooltipText() {
            String html = "<html><body>";
                    //"<h3>Header</h3>" +

            if (mValue.getOnline() == Contact.Online.YES)
                html += Tr.tr("Online") + "<br>";
            if (!mValue.getStatus().isEmpty()) {
                String status = StringEscapeUtils.escapeHtml(mValue.getStatus());
                html += Tr.tr("Status") + ": " + status + "<br>";
            }
            if (mValue.getOnline() != Contact.Online.YES) {
                html += Utils.lastSeen(mValue, false, true) + "<br>";
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
                this.setBackground(mBackground);
        }

        @Override
        protected boolean contains(String search) {
            return mValue.getName().toLowerCase().contains(search) ||
                    mValue.getJID().string().toLowerCase().contains(search);
        }

        @Override
        protected void updateOnEDT(Object arg) {
            // avatar
            mAvatar.setImage(AvatarLoader.load(mValue));

            // name
            String name = Utils.displayName(mValue);
            if (!name.equals(mNameLabel.getText())) {
                mNameLabel.setText(name);
                ContactListView.this.updateSorting();
            }

            // status
            mStatusLabel.setText(Utils.mainStatus(mValue, false));

            // online status
            Contact.Subscription subStatus = mValue.getSubScription();
            mBackground = mValue.getOnline() == Contact.Online.YES ? View.LIGHT_BLUE:
                    subStatus == Contact.Subscription.UNSUBSCRIBED ||
                    subStatus == Contact.Subscription.PENDING ||
                    mValue.isBlocked() ? View.LIGHT_GREY :
                    Color.WHITE;
            this.setBackground(mBackground);

            ContactListView.this.repaint();
        }

        @Override
        public int compareTo(TableItem o) {
            return Utils.compareContacts(mValue, o.mValue);
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
                    mView.getControl().getOrCreateSingleChat(mItem.mValue);
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
                            View.REMOVE_CONTACT_NOTE;
                    if (!Utils.confirmDeletion(ContactListView.this, text))
                        return;
                    mView.getControl().deleteContact(mItem.mValue);
                    mView.showNothing();
                }
            });
            this.add(mDeleteMenuItem);
        }

        void show(ContactItem item, Component invoker, int x, int y) {
            mItem = item;

            // dont allow creation of more than one chat for a contact
            mNewMenuItem.setVisible(!ChatList.getInstance().contains(item.mValue));

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

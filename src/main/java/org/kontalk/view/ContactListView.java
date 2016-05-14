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

package org.kontalk.view;

import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.GroupingType;
import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Observer;
import java.util.Optional;
import javax.swing.Box;
import org.apache.commons.lang.StringEscapeUtils;
import org.kontalk.model.Contact;
import org.kontalk.model.Model;
import org.kontalk.model.chat.Chat;
import org.kontalk.persistence.Config;
import org.kontalk.system.Control;
import org.kontalk.util.Tr;
import org.kontalk.view.ContactListView.ContactItem;

/**
 * Display all contacts in a brief list.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class ContactListView extends ListView<ContactItem, Contact> implements Observer {

    private final Model mModel;

    ContactListView(final View view, Model model) {
        super(view, false);

        mModel = model;

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
        });

        this.updateOnEDT(null);
    }

    @Override
    protected void updateOnEDT(Object arg) {
        boolean hideBlocked = Config.getInstance()
                .getBoolean(Config.VIEW_HIDE_BLOCKED);
        this.sync(Utils.allContacts(mModel.contacts(), !hideBlocked));
    }

    @Override
    protected ContactItem newItem(Contact value) {
        return new ContactItem(value);
    }

    @Override
    protected void selectionChanged(Optional<Contact> optContact) {
        mView.onContactSelectionChanged(optContact);
    }

    @Override
    protected WebPopupMenu rightClickMenu(ContactItem item) {
        WebPopupMenu menu = new WebPopupMenu();

        WebMenuItem newItem = new WebMenuItem(Tr.tr("New Chat"));
        newItem.setToolTipText(Tr.tr("Creates a new chat for this contact"));
        newItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                Chat chat = mView.getControl().getOrCreateSingleChat(
                        ContactListView.this.getSelectedItem().mValue);
                mView.showChat(chat);
            }
        });
        menu.add(newItem);

        WebMenuItem blockItem = new WebMenuItem(Tr.tr("Block Contact"));
        blockItem.setToolTipText(Tr.tr("Block all messages from this contact"));
        blockItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                mView.getControl().sendContactBlocking(
                        ContactListView.this.getSelectedItem().mValue, true);
            }
        });
        menu.add(blockItem);

        WebMenuItem unblockItem = new WebMenuItem(Tr.tr("Unblock Contact"));
        unblockItem.setToolTipText(Tr.tr("Unblock this contact"));
        unblockItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                mView.getControl().sendContactBlocking(
                        ContactListView.this.getSelectedItem().mValue, false);
            }
        });
        menu.add(unblockItem);

        WebMenuItem deleteItem = new WebMenuItem(Tr.tr("Delete Contact"));
        deleteItem.setToolTipText(Tr.tr("Delete this contact"));
        deleteItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                String text = Tr.tr("Permanently delete this contact?") + "\n" +
                        mView.tr_remove_contact;
                if (!Utils.confirmDeletion(ContactListView.this, text))
                    return;
                mView.getControl().deleteContact(
                        ContactListView.this.getSelectedItem().mValue);
            }
        });
        menu.add(deleteItem);

        // dont allow creation of more than one chat for a contact
        newItem.setVisible(!mModel.chats().contains(item.mValue));

        if (item.mValue.isBlocked()) {
            blockItem.setVisible(false);
            unblockItem.setVisible(true);
        } else {
            blockItem.setVisible(true);
            unblockItem.setVisible(false);
        }

        Control.Status status = mView.currentStatus();
        boolean connected = status == Control.Status.CONNECTED;
        blockItem.setEnabled(connected);
        unblockItem.setEnabled(connected);
        deleteItem.setEnabled(connected);

        return menu;
    }

    @Override
    protected void onRenameEvent() {
        Contact contact = this.getSelectedValue().orElse(null);
        if (contact == null)
            return;

        mView.requestRenameFocus(contact);
    }

    /** One item in the contact list representing a contact. */
    final class ContactItem extends ListView<ContactItem, Contact>.TableItem {

        private final ComponentUtils.AvatarImage mAvatar;
        private final WebLabel mNameLabel;
        private final WebLabel mStatusLabel;
        private Color mBackground;

        ContactItem(Contact contact) {
            super(contact);

            //this.setPaintFocus(true);
            this.setLayout(new BorderLayout(View.GAP_DEFAULT, 0));
            this.setMargin(View.MARGIN_SMALL);

            mAvatar = new ComponentUtils.AvatarImage(View.AVATAR_LIST_SIZE);
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
            Contact.ViewChange c = arg != null ? (Contact.ViewChange) arg : null;

            // avatar
            if (c == null || c == Contact.ViewChange.NAME || c == Contact.ViewChange.AVATAR) {
                mAvatar.setAvatarImage(mValue);
            }

            if (c == null || c == Contact.ViewChange.NAME) {
                // name
                String name = Utils.displayName(mValue);
                if (!name.equals(mNameLabel.getText())) {
                    mNameLabel.setText(name);
                    ContactListView.this.updateSorting();
                }
            }

            // status
            if (c == null || c == Contact.ViewChange.SUBSCRIPTION ||
                    c == Contact.ViewChange.ONLINE ||
                    c == Contact.ViewChange.BLOCKED) {
                mStatusLabel.setText(Utils.mainStatus(mValue, false));
            }

            // online status
            if (c == null || c == Contact.ViewChange.ONLINE ||
                    c == Contact.ViewChange.SUBSCRIPTION ||
                    c == Contact.ViewChange.BLOCKED) {
                Contact.Subscription subStatus = mValue.getSubScription();
                mBackground = mValue.getOnline() ==
                        Contact.Online.YES ? View.LIGHT_BLUE:
                        subStatus == Contact.Subscription.UNSUBSCRIBED ||
                        subStatus == Contact.Subscription.PENDING ||
                        mValue.isBlocked() ? View.LIGHT_GREY :
                        Color.WHITE;
                this.setBackground(mBackground);
            }

            ContactListView.this.repaint();

            if (c == Contact.ViewChange.BLOCKED &&
                    Config.getInstance().getBoolean(Config.VIEW_HIDE_BLOCKED)) {
                // "blocked" status changed, hide/show this item
                ContactListView.this.updateOnEDT(null);
            }
        }

        @Override
        public int compareTo(TableItem o) {
            return Utils.compareContacts(mValue, o.mValue);
        }
    }
}

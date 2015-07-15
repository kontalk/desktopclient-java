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
import org.kontalk.model.User;
import org.kontalk.model.UserList;
import org.kontalk.system.Control;
import org.kontalk.util.Tr;
import org.kontalk.util.XMPPUtils;
import static org.kontalk.view.TableView.TOOLTIP_DATE_FORMAT;
import org.kontalk.view.UserListView.UserItem;

/**
 * Display all user (aka contacts) in a brief list.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class UserListView extends TableView<UserItem, User> implements Observer {

    private final UserList mUserList;
    private final UserPopupMenu mPopupMenu;

    UserListView(final View view, UserList userList) {
        super(view);

        mUserList = userList;

        this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        //this.setDragEnabled(true);

        // right click popup menu
        mPopupMenu = new UserPopupMenu();

        // actions triggered by mouse events
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Optional<User> optUser = UserListView.this.getSelectedValue();
                if (!optUser.isPresent())
                    return;

                User selectedUser = optUser.get();
                if (e.getClickCount() == 2) {
                    mView.showThread(selectedUser);
                } else {
                    mView.showUserDetails(selectedUser);
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
                    int row = UserListView.this.rowAtPoint(e.getPoint());
                    UserListView.this.setSelectedItem(row);
                    UserListView.this.showPopupMenu(e);
                }
            }
        });

        this.updateOnEDT(null);
    }

    @Override
    protected void updateOnEDT(Object arg) {
        Set<UserItem> newItems = new HashSet<>();
        Set<User> user = mUserList.getAll();
        for (User oneUser: user)
            if (!this.containsValue(oneUser))
                newItems.add(new UserItem(oneUser));
        this.sync(user, newItems);
    }

    private void showPopupMenu(MouseEvent e) {
        // note: only work when right click does also selection
        mPopupMenu.show(this.getSelectedItem(), this, e.getX(), e.getY());
    }

    /** One item in the contact list representing a user. */
    final class UserItem extends TableView<UserItem, User>.TableItem {

        private final WebLabel mNameLabel;
        private final WebLabel mJIDLabel;
        private Color mBackround;

        UserItem(User user) {
            super(user);

            //this.setPaintFocus(true);
            this.setMargin(5);
            this.setLayout(new BorderLayout(10, 5));

            mNameLabel = new WebLabel("foo");
            mNameLabel.setFontSize(14);
            this.add(mNameLabel, BorderLayout.CENTER);

            mJIDLabel = new WebLabel("foo");
            mJIDLabel.setForeground(Color.GRAY);
            mJIDLabel.setFontSize(11);
            this.add(mJIDLabel, BorderLayout.SOUTH);

            this.updateOnEDT(null);
        }

        @Override
        public String getTooltipText() {
            String html = "<html><body>";
                    //"<h3>Header</h3>" +

            if (mValue.getOnline() == User.Online.YES)
                html += Tr.tr("Online")+"<br>";

            if (!mValue.getStatus().isEmpty()) {
                String status = StringEscapeUtils.escapeHtml(mValue.getStatus());
                html += Tr.tr("Status")+": " + status + "<br>";
            }

            if (mValue.getOnline() != User.Online.YES) {
                String lastSeen = !mValue.getLastSeen().isPresent() ?
                        Tr.tr("never") :
                        TOOLTIP_DATE_FORMAT.format(mValue.getLastSeen().get());
                html += Tr.tr("Last seen")+": " + lastSeen + "<br>";
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
            // may have changed (of user): JID, name, online
            String jid = mValue.getJID();
            if (XMPPUtils.isHash(jid));
                jid = Utils.shortenUserName(jid, 9);
            mJIDLabel.setText(jid);
            String name = !mValue.getName().isEmpty() ?
                    mValue.getName() :
                    Tr.tr("<unknown>");
            mNameLabel.setText(name);
            mBackround = mValue.getOnline() == User.Online.YES ?
                    View.LIGHT_BLUE :
                    Color.WHITE;
            this.setBackground(mBackround);
        }
    }

    private class UserPopupMenu extends WebPopupMenu {

        UserItem mItem;
        WebMenuItem mNewMenuItem;
        WebMenuItem mBlockMenuItem;
        WebMenuItem mUnblockMenuItem;

        UserPopupMenu() {
            mNewMenuItem = new WebMenuItem(Tr.tr("New Chat"));
            mNewMenuItem.setToolTipText(Tr.tr("Creates a new chat for this contact"));
            mNewMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    Set<User> user = new HashSet<>();
                    user.add(mItem.mValue);
                    UserListView.this.mView.callCreateNewThread(user);
                }
            });
            this.add(mNewMenuItem);

            mBlockMenuItem = new WebMenuItem(Tr.tr("Block Contact"));
            mBlockMenuItem.setToolTipText(Tr.tr("Block all messages from this contact"));
            mBlockMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    UserListView.this.mView.callSetUserBlocking(mItem.mValue, true);
                }
            });
            this.add(mBlockMenuItem);

            mUnblockMenuItem = new WebMenuItem(Tr.tr("Unblock Contact"));
            mUnblockMenuItem.setToolTipText(Tr.tr("Unblock this contact"));
            mUnblockMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    UserListView.this.mView.callSetUserBlocking(mItem.mValue, false);
                }
            });
            this.add(mUnblockMenuItem);

            WebMenuItem deleteMenuItem = new WebMenuItem(Tr.tr("Delete Contact"));
            deleteMenuItem.setToolTipText(Tr.tr("Delete this contact"));
            deleteMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    // TODO delete threads/messages too? android client may add it
                    // to roster again? useful at all? only self-created contacts?
                }
            });
            // see above
            //this.add(deleteMenuItem);
        }

        void show(UserItem item, Component invoker, int x, int y) {
            mItem = item;

            // dont allow creation of more than one thread for a user
            mNewMenuItem.setVisible(!ThreadList.getInstance().contains(item.mValue));

            if (mItem.mValue.isBlocked()) {
                mBlockMenuItem.setVisible(false);
                mUnblockMenuItem.setVisible(true);
            } else {
                mBlockMenuItem.setVisible(true);
                mUnblockMenuItem.setVisible(false);
            }

            Control.Status status = UserListView.this.mView.getCurrentStatus();
            mBlockMenuItem.setEnabled(status == Control.Status.CONNECTED);
            mUnblockMenuItem.setEnabled(status == Control.Status.CONNECTED);

            this.show(invoker, x, y);
        }
    }
}

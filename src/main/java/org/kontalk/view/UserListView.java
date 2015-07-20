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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import javax.swing.ListSelectionModel;
import org.apache.commons.lang.StringEscapeUtils;
import org.kontalk.model.ThreadList;
import org.kontalk.model.User;
import org.kontalk.model.UserList;
import org.kontalk.system.Control;
import org.kontalk.util.Tr;
import org.kontalk.view.UserListView.UserItem;

/**
 * Display all user (aka contacts) in a brief list.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class UserListView extends Table<UserItem, User> implements Observer {

    private final UserList mUserList;
    private final UserPopupMenu mPopupMenu;
    private final Timer mTimer = new Timer();

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

        // update periodically items to be up-to-date with 'last seen' text
        TimerTask statusTask = new TimerTask() {
                    @Override
                    public void run() {
                        UserListView.this.updateAllItems();
                    }
                };
        long timerInterval = TimeUnit.SECONDS.toMillis(60);
        mTimer.schedule(statusTask, timerInterval, timerInterval);

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
    final class UserItem extends Table<UserItem, User>.TableItem {

        private final WebLabel mNameLabel;
        private final WebLabel mStatusLabel;
        private Color mBackround;

        UserItem(User user) {
            super(user);

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

            if (mValue.getOnline() == User.Online.YES)
                html += Tr.tr("Online")+"<br>";

            if (!mValue.getStatus().isEmpty()) {
                String status = StringEscapeUtils.escapeHtml(mValue.getStatus());
                html += Tr.tr("Status")+": " + status + "<br>";
            }

            if (mValue.getOnline() != User.Online.YES) {
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
                UserListView.this.updateSorting();
            }

            // status
            mStatusLabel.setText(Utils.mainStatus(mValue));

            // online status
            User.Subscription subStatus = mValue.getSubScription();
            mBackround = mValue.getOnline() == User.Online.YES ? View.LIGHT_BLUE:
                    subStatus == User.Subscription.UNSUBSCRIBED ||
                    subStatus == User.Subscription.PENDING ||
                    mValue.isBlocked() ? View.LIGHT_GREY :
                    Color.WHITE;
            this.setBackground(mBackround);


            UserListView.this.repaint();
        }

        @Override
        public int compareTo(TableItem o) {
            return mValue.getName().compareToIgnoreCase(o.mValue.getName());
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
                    mView.getControl().sendUserBlocking(mItem.mValue, true);
                }
            });
            this.add(mBlockMenuItem);

            mUnblockMenuItem = new WebMenuItem(Tr.tr("Unblock Contact"));
            mUnblockMenuItem.setToolTipText(Tr.tr("Unblock this contact"));
            mUnblockMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    mView.getControl().sendUserBlocking(mItem.mValue, false);
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

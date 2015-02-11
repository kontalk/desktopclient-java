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

import com.alee.extended.panel.GroupPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.checkbox.WebCheckBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.text.WebTextField;
import com.alee.managers.tooltip.WebCustomTooltip;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import javax.swing.ListSelectionModel;
import org.kontalk.model.User;
import org.kontalk.model.UserList;
import org.kontalk.system.ControlCenter;

/**
 * Display all user (aka contacts) in a brief list.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
final class UserListView extends ListView implements Observer {

    private final View mView;
    private final UserList mUserList;
    private final UserPopupMenu mPopupMenu;

    private WebCustomTooltip mTip = null;

    UserListView(final View view, UserList userList) {
        super();

        mView = view;

        mUserList = userList;

        this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // right click popup menu
        mPopupMenu = new UserPopupMenu();

        // actions triggered by mouse events
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (e.getClickCount() == 2) {
                    mView.selectThreadByUser(getSelectedUser());
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
                    UserListView.this.setSelectedIndex(locationToIndex(e.getPoint()));
                    UserListView.this.showPopupMenu(e);
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (mTip != null)
                    mTip.closeTooltip();
            }
        });

        mUserList.addObserver(this);
    }

    @Override
    public void update(Observable o, Object arg) {
        mListModel.clear();
        for (User oneUser: mUserList.getUser()) {
            mListModel.addElement(new UserItemView(oneUser));
        }
    }

    User getSelectedUser() {
        if (getSelectedIndex() == -1)
            return null;
        ListItem p = mListModel.get(getSelectedIndex());
        return ((UserItemView) p).getUser();
    }

    private void showPopupMenu(MouseEvent e) {
        // note: only work when right click does also selection
        ListItem item = mListModel.get(getSelectedIndex());
        mPopupMenu.show((UserItemView) item, this, e.getX(), e.getY());
    }

    /**
     * One item in the contact list representing a user.
     */
    private class UserItemView extends ListItem {

        private final User mUser;
        private final WebLabel mNameLabel;
        private final WebLabel mJIDLabel;
        private final Color mBackround;

        UserItemView(User user) {
            mUser = user;

            //this.setPaintFocus(true);
            this.setMargin(5);
            this.setLayout(new BorderLayout(10, 5));

            this.add(new WebLabel(Integer.toString(mUser.getID())), BorderLayout.WEST);

            mNameLabel = new WebLabel();
            mNameLabel.setFontSize(14);
            this.add(mNameLabel, BorderLayout.CENTER);

            mJIDLabel = new WebLabel();
            mJIDLabel.setForeground(Color.GRAY);
            mJIDLabel.setFontSize(11);
            this.add(mJIDLabel, BorderLayout.SOUTH);

            // if too long, draw three dots at the end
            mJIDLabel.setText("dummy text");
            Dimension size = mJIDLabel.getPreferredSize();
            mJIDLabel.setMinimumSize(size);
            mJIDLabel.setPreferredSize(size);
            mNameLabel.setText("dummy text");
            size = mNameLabel.getPreferredSize();
            mNameLabel.setMinimumSize(size);
            mNameLabel.setPreferredSize(size);

            String name = !mUser.getName().isEmpty() ? mUser.getName() : "<unknown>";
            mNameLabel.setText(name);
            mJIDLabel.setText(mUser.getJID());

            mBackround = mUser.getAvailable() == User.Available.YES ? View.LIGHT_BLUE : Color.WHITE;
            this.setBackground(mBackround);
        }

        User getUser() {
            return mUser;
        }

        @Override
        public String getTooltipText() {
            String isAvailable;
            if (mUser.getAvailable() == User.Available.YES)
                isAvailable = "Yes";
            else if (mUser.getAvailable() == User.Available.NO)
                isAvailable = "No";
            else
                isAvailable = "?";

            String status = mUser.getStatus().isEmpty() ? "?" : mUser.getStatus();

            String lastSeen = !mUser.getLastSeen().isPresent() ? "?" :
                    TOOLTIP_DATE_FORMAT.format(mUser.getLastSeen().get());

            String isBlocked = mUser.isBlocked() ? "YES" : "No";

            String html = "<html><body>" +
                    //"<h3>Header</h3>" +
                    "<br>" +
                    "Available: " + isAvailable + "<br>" +
                    "Status: " + status + "<br>" +
                    "Blocked: " + isBlocked + "<br>" +
                    "Last seen: " + lastSeen + "<br>" +
                    "";

            return html;
        }

        @Override
        void repaint(boolean isSelected) {
            if (isSelected)
                this.setBackground(View.BLUE);
            else
                this.setBackground(mBackround);
        }

        @Override
        protected boolean contains(String search) {
            return mUser.getName().toLowerCase().contains(search) ||
                    mUser.getJID().toLowerCase().contains(search);
        }
    }

    private class UserPopupMenu extends WebPopupMenu {

        UserItemView mSelectedUserView;
        WebMenuItem mBlockMenuItem;
        WebMenuItem mUnblockMenuItem;

        UserPopupMenu() {
            WebMenuItem newMenuItem = new WebMenuItem("New Thread");
            newMenuItem.setToolTipText("Creates a new thread for this contact");
            newMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    Set<User> user = new HashSet<>();
                    user.add(mSelectedUserView.getUser());
                    UserListView.this.mView.callCreateNewThread(user);
                }
            });
            this.add(newMenuItem);

            WebMenuItem editMenuItem = new WebMenuItem("Edit Contact");
            editMenuItem.setToolTipText("Edit this contact");
            editMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    WebDialog editUserDialog = new EditUserDialog(mSelectedUserView);
                    editUserDialog.setVisible(true);
                }
            });
            this.add(editMenuItem);

            mBlockMenuItem = new WebMenuItem("Block Contact");
            mBlockMenuItem.setToolTipText("Block all messages from this contact");
            mBlockMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    UserListView.this.mView.callSetUserBlocking(mSelectedUserView.getUser(), true);
                }
            });
            this.add(mBlockMenuItem);

            mUnblockMenuItem = new WebMenuItem("Unblock Contact");
            mUnblockMenuItem.setToolTipText("Unblock this contact");
            mUnblockMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    UserListView.this.mView.callSetUserBlocking(mSelectedUserView.getUser(), false);
                }
            });
            this.add(mUnblockMenuItem);

            WebMenuItem deleteMenuItem = new WebMenuItem("Delete Contact");
            deleteMenuItem.setToolTipText("Delete this contact");
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

        void show(UserItemView selectedUserView, Component invoker, int x, int y) {
            mSelectedUserView = selectedUserView;

            if (mSelectedUserView.getUser().isBlocked()) {
                mBlockMenuItem.setVisible(false);
                mUnblockMenuItem.setVisible(true);
            } else {
                mBlockMenuItem.setVisible(true);
                mUnblockMenuItem.setVisible(false);
            }

            ControlCenter.Status status = UserListView.this.mView.getCurrentStatus();
            mBlockMenuItem.setEnabled(status == ControlCenter.Status.CONNECTED);
            mUnblockMenuItem.setEnabled(status == ControlCenter.Status.CONNECTED);

            this.show(invoker, x, y);
        }

    }

    private class EditUserDialog extends WebDialog {

        private final UserItemView mUserView;
        private final WebTextField mNameField;
        private final WebTextField mJIDField;
        private final WebCheckBox mEncryptionBox;

        EditUserDialog(UserItemView userView) {

            mUserView = userView;

            this.setTitle("Edit Contact");
            //this.setSize(400, 280);
            this.setResizable(false);
            this.setModal(true);

            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            // editable fields
            WebPanel namePanel = new WebPanel();
            namePanel.setLayout(new BorderLayout(10, 5));
            namePanel.add(new WebLabel("Display Name:"), BorderLayout.WEST);
            mNameField = new WebTextField(mUserView.getUser().getName());
            mNameField.setInputPrompt(mUserView.getUser().getName());
            mNameField.setHideInputPromptOnFocus(false);
            namePanel.add(mNameField, BorderLayout.CENTER);
            groupPanel.add(namePanel);
            groupPanel.add(new WebSeparator(true, true));

            String hasKey = "<html>Encryption Key: ";
            if (mUserView.getUser().hasKey()) {
                hasKey += "Available</html>";
            } else {
                hasKey += "<font color='red'>Not Available</font></html>";
            }
            groupPanel.add(new WebLabel(hasKey));

            mEncryptionBox = new WebCheckBox("Encryption");
            mEncryptionBox.setAnimated(false);
            mEncryptionBox.setSelected(mUserView.getUser().getEncrypted());
            groupPanel.add(mEncryptionBox);
            groupPanel.add(new WebSeparator(true, true));

            groupPanel.add(new WebLabel("JID:"));
            mJIDField = new WebTextField(mUserView.getUser().getJID(), 38);
            mJIDField.setInputPrompt(mUserView.getUser().getJID());
            mJIDField.setHideInputPromptOnFocus(false);
            groupPanel.add(mJIDField);
            groupPanel.add(new WebSeparator(true, true));

            this.add(groupPanel, BorderLayout.CENTER);

            // buttons
            WebButton cancelButton = new WebButton("Cancel");
            cancelButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    EditUserDialog.this.dispose();
                }
            });
            final WebButton saveButton = new WebButton("Save");
            saveButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (!EditUserDialog.this.isConfirmed())
                        return;

                    EditUserDialog.this.saveUser();
                    EditUserDialog.this.dispose();
                }
            });

            GroupPanel buttonPanel = new GroupPanel(2, cancelButton, saveButton);
            buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
            this.add(buttonPanel, BorderLayout.SOUTH);

            this.pack();
        }

        private boolean isConfirmed() {
            if (!mJIDField.getText().equals(mUserView.getUser().getJID())) {
                String warningText = "Changing the JID is only useful in very "
                        + " rare cases. Are you sure?";
                int selectedOption = WebOptionPane.showConfirmDialog(this,
                        warningText,
                        "Please Confirm",
                        WebOptionPane.OK_CANCEL_OPTION,
                        WebOptionPane.WARNING_MESSAGE);
                if (selectedOption != WebOptionPane.OK_OPTION) {
                    return false;
                }
            }
            return true;
        }

        private void saveUser() {
            if (!mNameField.getText().isEmpty()) {
                mUserView.getUser().setName(mNameField.getText());
            }
            mUserView.getUser().setEncrypted(mEncryptionBox.isSelected());
            if (!mJIDField.getText().isEmpty()) {
                mUserView.getUser().setJID(mJIDField.getText());
            }
        }
    }
}

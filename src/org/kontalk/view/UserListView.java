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
import com.alee.extended.panel.GroupingType;
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
import com.alee.managers.tooltip.TooltipManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import org.kontalk.model.User;
import org.kontalk.model.UserList;
import org.kontalk.system.Control;
import org.kontalk.util.Tr;
import org.kontalk.view.UserListView.UserItem;

/**
 * Display all user (aka contacts) in a brief list.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
final class UserListView extends TableView<UserItem, User> implements Observer {

    private final View mView;
    private final UserList mUserList;
    private final UserPopupMenu mPopupMenu;

    UserListView(final View view, UserList userList) {
        super();

        mView = view;

        mUserList = userList;

        this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        //this.setDragEnabled(true);

        // right click popup menu
        mPopupMenu = new UserPopupMenu();

        // actions triggered by mouse events
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    mView.selectThreadByUser(UserListView.this.getSelectedValue());
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
        for (User oneUser: mUserList.getAll())
            if (!this.containsValue(oneUser))
                this.addItem(new UserItem(oneUser));
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
            // if too long, draw three dots at the end
            Dimension size = mNameLabel.getPreferredSize();
            mNameLabel.setMinimumSize(size);
            mNameLabel.setPreferredSize(size);
            this.add(mNameLabel, BorderLayout.CENTER);

            mJIDLabel = new WebLabel("foo");
            mJIDLabel.setForeground(Color.GRAY);
            mJIDLabel.setFontSize(11);
            size = mJIDLabel.getPreferredSize();
            mJIDLabel.setMinimumSize(size);
            mJIDLabel.setPreferredSize(size);
            this.add(mJIDLabel, BorderLayout.SOUTH);

            this.updateOnEDT(null);
        }

        @Override
        public String getTooltipText() {
            String no = Tr.tr("No");
            String dunno = Tr.tr("?");

            String isOnline;
            if (mValue.getOnline() == User.Online.YES)
                isOnline = Tr.tr("Yes");
            else if (mValue.getOnline() == User.Online.NO)
                isOnline = no;
            else
                isOnline = dunno;

            String status = mValue.getStatus().isEmpty() ? dunno : mValue.getStatus();

            String lastSeen = !mValue.getLastSeen().isPresent() ? dunno :
                    TOOLTIP_DATE_FORMAT.format(mValue.getLastSeen().get());

            String isBlocked = mValue.isBlocked() ? Tr.tr("YES") : no;

            String html = "<html><body>" +
                    //"<h3>Header</h3>" +
                    "<br>" +
                    Tr.tr("Available")+": " + isOnline + "<br>" +
                    Tr.tr("Status")+": " + status + "<br>" +
                    Tr.tr("Blocked")+": " + isBlocked + "<br>" +
                    Tr.tr("Last seen")+": " + lastSeen + "<br>" +
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
            return mValue.getName().toLowerCase().contains(search) ||
                    mValue.getJID().toLowerCase().contains(search);
        }

        @Override
        protected void updateOnEDT(Object arg) {
            // may have changed (of user): JID, name, online
            mJIDLabel.setText(mValue.getJID());
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
        WebMenuItem mBlockMenuItem;
        WebMenuItem mUnblockMenuItem;

        UserPopupMenu() {
            WebMenuItem newMenuItem = new WebMenuItem(Tr.tr("New Thread"));
            newMenuItem.setToolTipText(Tr.tr("Creates a new thread for this contact"));
            newMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    Set<User> user = new HashSet<>();
                    user.add(mItem.mValue);
                    UserListView.this.mView.callCreateNewThread(user);
                }
            });
            this.add(newMenuItem);

            WebMenuItem editMenuItem = new WebMenuItem(Tr.tr("Edit Contact"));
            editMenuItem.setToolTipText(Tr.tr("Edit this contact"));
            editMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    EditUserDialog editUserDialog = new EditUserDialog(mItem);
                    mItem.mValue.addObserver(editUserDialog);
                    editUserDialog.setVisible(true);
                }
            });
            this.add(editMenuItem);

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

    private class EditUserDialog extends WebDialog implements Observer {

        private final UserItem mItem;
        private final WebTextField mNameField;
        private final WebLabel mKeyLabel;
        private final WebLabel mFPLabel;
        private final WebTextField mFPField;
        private final WebCheckBox mEncryptionBox;
        private String mJID;

        EditUserDialog(UserItem item) {
            mItem = item;

            this.setTitle(Tr.tr("Edit Contact"));
            this.setMinimumSize(new Dimension(400, -1));
            this.setResizable(false);
            this.setModal(true);

            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            // editable fields
            WebPanel namePanel = new WebPanel();
            namePanel.setLayout(new BorderLayout(10, 5));
            namePanel.add(new WebLabel(Tr.tr("Display Name:")), BorderLayout.WEST);
            mNameField = new WebTextField();
            mNameField.setHideInputPromptOnFocus(false);
            namePanel.add(mNameField, BorderLayout.CENTER);
            groupPanel.add(namePanel);
            groupPanel.add(new WebSeparator(true, true));

            mKeyLabel = new WebLabel();
            WebButton updButton = new WebButton(View.getIcon("ic_ui_refresh.png"));
            String updText = Tr.tr("Update key");
            TooltipManager.addTooltip(updButton, updText);
            updButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    UserListView.this.mView.callRequestKey(EditUserDialog.this.mItem.mValue);
                }
            });
            groupPanel.add(new GroupPanel(6, mKeyLabel, updButton));

            mFPLabel = new WebLabel(Tr.tr("Fingerprint:")+" ");
            mFPField = View.createTextField("");
            String fpText = Tr.tr("The unique ID of this contact's key");
            TooltipManager.addTooltip(mFPField, fpText);
            groupPanel.add(new GroupPanel(mFPLabel, mFPField));

            this.updateOnEDT();

            mEncryptionBox = new WebCheckBox(Tr.tr("Use Encryption"));
            mEncryptionBox.setAnimated(false);
            mEncryptionBox.setSelected(mItem.mValue.getEncrypted());
            String encText = Tr.tr("Encrypt and sign all messages send to this contact");
            TooltipManager.addTooltip(mEncryptionBox, encText);
            groupPanel.add(new GroupPanel(mEncryptionBox, new WebSeparator()));
            groupPanel.add(new WebSeparator(true, true));

            final int l = 50;
            mJID = mItem.mValue.getJID();
            final WebTextField jidField = new WebTextField(View.shortenJID(mJID, l));
            jidField.setDrawBorder(false);
            jidField.setMinimumHeight(20);
            jidField.setInputPrompt(mJID);
            jidField.setHideInputPromptOnFocus(false);
            jidField.addFocusListener(new FocusListener() {
                @Override
                public void focusGained(FocusEvent e) {
                    jidField.setText(mJID);
                    jidField.setDrawBorder(true);
                }
                @Override
                public void focusLost(FocusEvent e) {
                    mJID = jidField.getText();
                    jidField.setText(View.shortenJID(mJID, l));
                    jidField.setDrawBorder(false);
                }
            });
            String jidText = Tr.tr("The unique address of this contact");
            TooltipManager.addTooltip(jidField, jidText);
            groupPanel.add(new GroupPanel(GroupingType.fillLast,
                    new WebLabel("JID: "),
                    jidField));
            groupPanel.add(new WebSeparator(true, true));

            this.add(groupPanel, BorderLayout.CENTER);

            // buttons
            WebButton cancelButton = new WebButton(Tr.tr("Cancel"));
            cancelButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    EditUserDialog.this.close();
                }
            });
            final WebButton saveButton = new WebButton("Save");
            saveButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (!EditUserDialog.this.isConfirmed())
                        return;
                    EditUserDialog.this.save();
                    EditUserDialog.this.close();
                }
            });
            this.getRootPane().setDefaultButton(saveButton);

            GroupPanel buttonPanel = new GroupPanel(2, cancelButton, saveButton);
            buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
            this.add(buttonPanel, BorderLayout.SOUTH);

            this.pack();
        }

        @Override
        public void update(Observable o, final Object arg) {
            if (SwingUtilities.isEventDispatchThread()) {
                this.updateOnEDT();
                return;
            }
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    EditUserDialog.this.updateOnEDT();
                }
            });
        }

        private void updateOnEDT() {
            // may have changed: user name and/or key
            mNameField.setText(mItem.mValue.getName());
            mNameField.setInputPrompt(mItem.mValue.getName());
            String hasKey = "<html>"+Tr.tr("Encryption Key")+": ";
            if (mItem.mValue.hasKey()) {
                hasKey += Tr.tr("Available")+"</html>";
                TooltipManager.removeTooltips(mKeyLabel);
                mFPField.setText(mItem.mValue.getFingerprint());
                mFPLabel.setVisible(true);
                mFPField.setVisible(true);
            } else {
                hasKey += "<font color='red'>"+Tr.tr("Not Available")+"</font></html>";
                String keyText = Tr.tr("The key for this user could not yet be received");
                TooltipManager.addTooltip(mKeyLabel, keyText);
                mFPLabel.setVisible(false);
                mFPField.setVisible(false);
            }
            mKeyLabel.setText(hasKey);
        }

        private boolean isConfirmed() {
            if (!mJID.equals(mItem.mValue.getJID())) {
                String warningText =
                        Tr.tr("Changing the JID is only useful in very rare cases. Are you sure?");
                int selectedOption = WebOptionPane.showConfirmDialog(this,
                        warningText,
                        Tr.tr("Please Confirm"),
                        WebOptionPane.OK_CANCEL_OPTION,
                        WebOptionPane.WARNING_MESSAGE);
                if (selectedOption != WebOptionPane.OK_OPTION) {
                    return false;
                }
            }
            return true;
        }

        private void save() {
            String newName = mNameField.getText();
            if (!newName.equals(mItem.mValue.getName())) {
                mItem.mValue.setName(mNameField.getText());
            }
            mItem.mValue.setEncrypted(mEncryptionBox.isSelected());
            if (!mJID.isEmpty() && !mJID.equals(mItem.mValue.getJID())) {
                mItem.mValue.setJID(mJID);
            }
        }

        private void close() {
            this.dispose();
            this.mItem.mValue.deleteObserver(this);
        }
    }
}

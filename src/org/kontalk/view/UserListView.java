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
import com.alee.laf.label.WebLabel;
import com.alee.laf.list.WebList;
import com.alee.laf.list.WebListCellRenderer;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.text.WebTextField;
import com.alee.managers.tooltip.TooltipManager;
import com.alee.managers.tooltip.TooltipWay;
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
import java.text.SimpleDateFormat;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.kontalk.model.User;
import org.kontalk.model.UserList;

/**
 * Display all known user (aka contacts) in a list.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class UserListView extends WebList {
    private final static Logger LOGGER = Logger.getLogger(UserListView.class.getName());

    private final static SimpleDateFormat TOOLTIP_DATE_FORMAT =
            new SimpleDateFormat("EEE, MMM d yyyy, HH:mm");

    private final DefaultListModel<UserView> mListModel = new DefaultListModel();
    private final WebPopupMenu mPopupMenu;

    private WebCustomTooltip mTip = null;

    UserListView(final View modelView) {

        this.setModel(mListModel);
        this.setCellRenderer(new UserListRenderer());

        this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // right click popup menu
        mPopupMenu = new WebPopupMenu();
        // note: actions only work when right click does also selection
        WebMenuItem newMenuItem = new WebMenuItem("New Contact");
        newMenuItem.setToolTipText("Creates a new thread for this contact");
        newMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                // TODO
            }
        });
        mPopupMenu.add(newMenuItem);

        WebMenuItem editMenuItem = new WebMenuItem("Edit Contact");
        editMenuItem.setToolTipText("Edit this contact");
        editMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                JDialog editUserDialog = new EditUserDialog(mListModel.get(getSelectedIndex()));
                editUserDialog.setVisible(true);
            }
        });
        mPopupMenu.add(editMenuItem);

        WebMenuItem deleteMenuItem = new WebMenuItem("Delete Contact");
        deleteMenuItem.setToolTipText("Delete this contact");
        deleteMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                // TODO delete threads/messages too? android client may add it
                // to roster again? useful at all? only self created contacts?
            }
        });
        mPopupMenu.add(deleteMenuItem);

        // actions triggered by selection
        this.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting())
                    return;
                // TODO ?
                //modelView.selectedUserChanged(getSelectedUserID());
            }
        });

        // actions triggered by mouse events
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (e.getClickCount() == 2) {
                    modelView.selectThreadByUser(getSelectedUser());
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
                    setSelectedIndex(locationToIndex(e.getPoint()));
                    showPopupMenu(e);
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (mTip != null)
                    mTip.closeTooltip();
            }
        });
    }

    void modelChanged(UserList user) {
        mListModel.clear();
        for (User oneUser: user.getUser())
            mListModel.addElement(new UserView(oneUser));
    }

    User getSelectedUser() {
        if (getSelectedIndex() == -1)
            return null;
        return mListModel.get(getSelectedIndex()).getUser();
    }

    private void showPopupMenu(MouseEvent e){
           mPopupMenu.show(this, e.getX(), e.getY());
       }

    void showTooltip(UserView userView) {
        if (mTip != null)
            mTip.closeTooltip();

        WebCustomTooltip tip = TooltipManager.showOneTimeTooltip(this,
                this.getMousePosition(),
                userView.getTooltipText(),
                TooltipWay.down);
        mTip = tip;
    }

    /**
     * One item in the contact list representing a user.
     */
    private class UserView extends WebPanel{

        private final User mUser;
        private final WebLabel mNameLabel;
        private final WebLabel mJIDLabel;

        UserView(User user) {
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

            updateView();
        }

        User getUser() {
            return mUser;
        }

        void paintSelected(boolean isSelected){
            if (isSelected)
                this.setBackground(View.BLUE);
            else
                this.setBackground(Color.WHITE);
        }

        private void updateView() {
           // if too long, draw three dots at the end
           mJIDLabel.setText("dummy text");
           Dimension size = mJIDLabel.getPreferredSize();
           mJIDLabel.setMinimumSize(size);
           mJIDLabel.setPreferredSize(size);
           mNameLabel.setText("dummy text");
           size = mNameLabel.getPreferredSize();
           mNameLabel.setMinimumSize(size);
           mNameLabel.setPreferredSize(size);

           String name = mUser.getName() != null ? mUser.getName() : "<unknown>";
           mNameLabel.setText(name);
           mJIDLabel.setText(mUser.getJID());
        }

        // catch the event, when a tooltip should be shown for this item and
        // create a own one
        @Override
           public String getToolTipText(MouseEvent event) {
               showTooltip(this);
               return null;
           }

        public String getTooltipText() {
            String isAvailable;
            if (mUser.getAvailable() == User.Available.YES)
                isAvailable = "Yes";
            else if (mUser.getAvailable() == User.Available.NO)
                isAvailable = "No";
            else
                isAvailable = "?";

            String status = mUser.getStatus() == null ? "?" : mUser.getStatus();

            String lastSeen = mUser.getLastSeen() == null ? "?" :
                    TOOLTIP_DATE_FORMAT.format(mUser.getLastSeen());

            String html = "<html><body>" +
                    //"<h3>Header</h3>" +
                    "Available: " + isAvailable + "<br>" +
                    "Status: " + status + "<br>" +
                    "Last seen: " + lastSeen + "<br>" +
                    "";

            return html;
        }
    }

    private class UserListRenderer extends WebListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList list,
                                                Object value,
                                                int index,
                                                boolean isSelected,
                                                boolean hasFocus) {
            if (value instanceof UserView) {
                UserView userView = (UserView) value;
                userView.paintSelected(isSelected);
                TooltipManager.showTooltips(userView);
                return userView;
            } else {
                return new WebPanel(new WebLabel("ERRROR"));
            }
        }
    }

    private class EditUserDialog extends WebDialog {

        private final UserView mUserView;
        private final WebTextField mNameField;
        private final WebTextField mJIDField;

        public EditUserDialog(UserView userView) {

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
                    dispose();
                }
            });
            final WebButton saveButton = new WebButton("Save");
            saveButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    saveUser();
                    dispose();
                }
            });

            GroupPanel buttonPanel = new GroupPanel(2, cancelButton, saveButton);
            buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
            this.add(buttonPanel, BorderLayout.SOUTH);

            this.pack();
        }

        private void saveUser() {
            if (!mNameField.getText().isEmpty()) {
                mUserView.getUser().setName(mNameField.getText());
            }
            if (!mJIDField.getText().isEmpty()) {
                mUserView.getUser().setJID(mJIDField.getText());
            }
            mUserView.updateView();
        }
    }
}

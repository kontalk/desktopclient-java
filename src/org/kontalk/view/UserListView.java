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
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.text.WebTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.logging.Logger;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.jivesoftware.smack.util.StringUtils;
import org.kontalk.model.User;
import org.kontalk.model.UserList;

/**
 * Display all known user (aka contacts) in a list.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class UserListView extends WebList {
    private final static Logger LOGGER = Logger.getLogger(UserListView.class.getName());
    
    private final DefaultListModel<UserView> mListModel = new DefaultListModel();
    private final WebPopupMenu mPopupMenu;
    
    UserListView(final View modelView) {
        
        this.setModel(mListModel);
        this.setCellRenderer(new UserListRenderer());
        
        this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        //this.setPreferredWidth(150);
        
        // right click popup menu
        mPopupMenu = new WebPopupMenu();
        // note: actions only work when right click does also selection
        WebMenuItem newMenuItem = new WebMenuItem("New Thread");
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
                    modelView.selectThread(getSelectedUser());
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
        });
    }

    void modelChanged(UserList user) {
        mListModel.clear();
        for (User oneUser: user.values())
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
    
    /**
     * One item in the contact list representing a user.
     */
    private class UserView extends WebPanel{
        
        private final User mUser;
        WebLabel nameLabel;
        WebLabel jidLabel;
     
        UserView(User user) {
            mUser = user;
            
            //this.setPaintFocus(true);
            this.setMargin(5);
            this.setLayout(new BorderLayout(10, 5));
            
            this.add(new WebLabel(Integer.toString(mUser.getID())), BorderLayout.WEST);
            
            nameLabel = new WebLabel();
            nameLabel.setFontSize(14);
            this.add(nameLabel, BorderLayout.CENTER);
            
            jidLabel = new WebLabel();
            jidLabel.setForeground(Color.GRAY);
            jidLabel.setFontSize(11);
            this.add(jidLabel, BorderLayout.SOUTH);
            
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
           String mName = mUser.getName() != null ? mUser.getName() : "<unknown>";
           String mJID = mUser.getJID().length() < 25 ? mUser.getJID() : 
                   mUser.getJID().substring(0, 24) + "...";
           nameLabel.setText(mName);
           jidLabel.setText(mJID);
        }
    }
    
    private class UserListRenderer extends DefaultListCellRenderer {
        
        @Override
        public Component getListCellRendererComponent(JList list,
                                                Object value,
                                                int index,
                                                boolean isSelected,
                                                boolean hasFocus) {
            if (value instanceof UserView) {
                UserView userView = (UserView) value;
                userView.paintSelected(isSelected);
                return userView;
            } else {
                return new WebPanel(new WebLabel("ERRROR"));
            }
        }
    }
    
    private class EditUserDialog extends WebDialog {

        private final UserView mUserView;
        private final WebTextField nameField;
        private final WebTextField jidField;
        
        public EditUserDialog(UserView userView) {
            
            mUserView = userView;
            
            this.setTitle("Edit Contact");
            this.setSize(400, 280);
            this.setResizable(false);
            this.setModal(true);
            
            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            groupPanel.add(new WebLabel("Last seen:  TODO"));
            groupPanel.add(new WebLabel("Status:  TODO"));
            groupPanel.add(new WebSeparator(true, true));
            
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
            
            // editable fields
            groupPanel.add(new WebLabel("Name:"));
            nameField = new WebTextField(mUserView.getUser().getName(), 16);
            nameField.setInputPrompt(mUserView.getUser().getName());
            nameField.setHideInputPromptOnFocus(false);
            groupPanel.add(nameField);
            groupPanel.add(new WebSeparator(true, true));
            
            groupPanel.add(new WebLabel("JID:"));
            jidField = new WebTextField(mUserView.getUser().getJID(), 24);
            jidField.setInputPrompt(mUserView.getUser().getJID());
            jidField.setHideInputPromptOnFocus(false);
            groupPanel.add(jidField);
            groupPanel.add(new WebSeparator(true, true));
            
            this.add(groupPanel, BorderLayout.CENTER);
            
            GroupPanel buttonPanel = new GroupPanel(2, cancelButton, saveButton);
            buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
            this.add(buttonPanel, BorderLayout.SOUTH);
        }
        
        private void saveUser() {
            if (!nameField.getText().isEmpty()) {
                mUserView.getUser().setName(nameField.getText());
            }
            if (!jidField.getText().isEmpty()) {
                mUserView.getUser().setJID(jidField.getText());
            }
            mUserView.updateView();
        }
    }
}

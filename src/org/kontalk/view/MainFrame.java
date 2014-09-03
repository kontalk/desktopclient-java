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

import com.alee.extended.label.WebLinkLabel;
import com.alee.extended.label.WebVerticalLabel;
import com.alee.extended.panel.GroupPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.checkbox.WebCheckBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.list.WebList;
import com.alee.laf.menu.WebMenu;
import com.alee.laf.menu.WebMenuBar;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.rootpane.WebFrame;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.splitpane.WebSplitPane;
import com.alee.laf.tabbedpane.WebTabbedPane;
import com.alee.laf.text.WebTextField;
import com.alee.managers.hotkey.Hotkey;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.SystemTray;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.Icon;
import static javax.swing.JSplitPane.VERTICAL_SPLIT;
import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.kontalk.KonConf;
import org.kontalk.Kontalk;
import org.kontalk.model.User;
import org.kontalk.model.UserList;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class MainFrame extends WebFrame {
    private final static Logger LOGGER = Logger.getLogger(ThreadView.class.getName());

    public static enum Tab {THREADS, USER};

    private final KonConf mConf = KonConf.getInstance();
    private final WebMenuItem mConnectMenuItem;
    private final WebMenuItem mDisconnectMenuItem;
    private final WebTabbedPane mTabbedPane;

    public MainFrame(final View viewModel,
            ListView userList,
            ListView threadList,
            Component threadView,
            Component sendTextField,
            Component sendButton,
            Component statusBar) {

        // general view + behaviour
        this.setTitle("Kontalk Java Client");
        this.setSize(mConf.getInt(KonConf.VIEW_FRAME_WIDTH),
                mConf.getInt(KonConf.VIEW_FRAME_HEIGHT));

        this.setIconImage(View.getImage("kontalk.png"));

        // closing behaviour
        this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (mConf.getBoolean(KonConf.MAIN_TRAY_CLOSE) &&
                        SystemTray.getSystemTray().getTrayIcons().length > 0)
                    MainFrame.this.toggleState();
                else
                    viewModel.shutDown();
            }
        });

        this.addWindowStateListener(new WindowStateListener() {
            @Override
            public void windowStateChanged(WindowEvent e) {
                // TODO tray behaviour?
            }
        });

        // menu
        WebMenuBar menubar = new WebMenuBar();
        this.setJMenuBar(menubar);

        WebMenu konNetMenu = new WebMenu("KonNet");
        konNetMenu.setMnemonic(KeyEvent.VK_K);

        mConnectMenuItem = new WebMenuItem("Connect");
        mConnectMenuItem.setAccelerator(Hotkey.ALT_C);
        mConnectMenuItem.setToolTipText("Connect to Server");
        mConnectMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                viewModel.connect();
            }
        });
        konNetMenu.add(mConnectMenuItem);

        mDisconnectMenuItem = new WebMenuItem("Disconnect");
        mDisconnectMenuItem.setAccelerator(Hotkey.ALT_D);
        mDisconnectMenuItem.setToolTipText("Disconnect from Server");
        mDisconnectMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                viewModel.disconnect();
            }
        });
        konNetMenu.add(mDisconnectMenuItem);
        konNetMenu.addSeparator();

        WebMenuItem statusMenuItem = new WebMenuItem("Set status");
        statusMenuItem.setAccelerator(Hotkey.ALT_S);
        statusMenuItem.setToolTipText("Set status text send to other user");
        statusMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                WebDialog statusDialog = new StatusDialog();
                statusDialog.setVisible(true);
            }
        });
        konNetMenu.add(statusMenuItem);
        konNetMenu.addSeparator();

        WebMenuItem exitMenuItem = new WebMenuItem("Exit");
        exitMenuItem.setAccelerator(Hotkey.ALT_E);
        exitMenuItem.setToolTipText("Exit application");
        exitMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                viewModel.shutDown();
            }
        });
        konNetMenu.add(exitMenuItem);

        menubar.add(konNetMenu);

        WebMenu optionsMenu = new WebMenu("Options");
        optionsMenu.setMnemonic(KeyEvent.VK_O);

        WebMenuItem conConfMenuItem = new WebMenuItem("Preferences");
        conConfMenuItem.setAccelerator(Hotkey.ALT_P);
        conConfMenuItem.setToolTipText("Set application preferences");
        conConfMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                viewModel.showConfig();
            }
        });
        optionsMenu.add(conConfMenuItem);

        menubar.add(optionsMenu);

        WebMenu helpMenu = new WebMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);

        WebMenuItem aboutMenuItem = new WebMenuItem("About");
        aboutMenuItem.setToolTipText("About Kontalk");
        aboutMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                MainFrame.this.showAboutDialog();
            }
        });
        helpMenu.add(aboutMenuItem);

        menubar.add(helpMenu);

        // Layout...
        this.setLayout(new BorderLayout(5, 5));

        // ...left...
        mTabbedPane = new WebTabbedPane(WebTabbedPane.LEFT);
        WebButton newThreadButton = new WebButton("New");
        newThreadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // TODO
            }
        });
        WebPanel threadListPanel = this.createListPane(threadList, newThreadButton);
        mTabbedPane.addTab("", threadListPanel);
        mTabbedPane.setTabComponentAt(Tab.THREADS.ordinal(),
                new WebVerticalLabel("Threads"));

        WebButton newUserButton = new WebButton("Add");
        newUserButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                WebDialog addUserDialog = new AddUserDialog();
                addUserDialog.setVisible(true);
            }
        });
        WebPanel userListPanel = this.createListPane(userList, newUserButton);
        mTabbedPane.addTab("", userListPanel);
        mTabbedPane.setTabComponentAt(Tab.USER.ordinal(),
                new WebVerticalLabel("Contacts"));
        mTabbedPane.setPreferredSize(new Dimension(250, -1));
        this.add(mTabbedPane, BorderLayout.WEST);

        // ...right...
        WebPanel bottomPanel = new WebPanel();
        WebScrollPane textFieldScrollPane = new ScrollPane(sendTextField);
        bottomPanel.add(textFieldScrollPane, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        bottomPanel.setMinimumSize(new Dimension(0, 32));
        WebSplitPane splitPane = new WebSplitPane(VERTICAL_SPLIT, threadView, bottomPanel);
        splitPane.setResizeWeight(1.0);
        this.add(splitPane, BorderLayout.CENTER);

        // ...bottom
        this.add(statusBar, BorderLayout.SOUTH);
    }

    private WebPanel createListPane(final ListView list, Component newButton) {
        Icon clearIcon = View.getIcon("ic_ui_clear.png");
        WebPanel listPanel = new WebPanel();
        WebPanel searchPanel = new WebPanel();
        final WebTextField searchField = new WebTextField();
        searchField.setInputPrompt("Search...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                this.filterList();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                this.filterList();
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                this.filterList();
            }
            private void filterList() {
                list.filter(searchField.getText());
            }
        });
        // TODO
        //searchField.getDocument().addDocumentListener(listener);
        WebButton clearSearchButton = new WebButton(clearIcon);
        clearSearchButton.setUndecorated(true);
        clearSearchButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchField.clear();
            }
        });
        searchField.setTrailingComponent(clearSearchButton);
        searchPanel.add(searchField, BorderLayout.CENTER);
        // TODO
        //searchPanel.add(newButton, BorderLayout.EAST);
        listPanel.add(searchPanel, BorderLayout.NORTH);
        WebScrollPane scrollPane = new ScrollPane(list);
        listPanel.add(scrollPane, BorderLayout.CENTER);
        return listPanel;
    }

    void selectTab(Tab tab) {
        mTabbedPane.setSelectedIndex(tab.ordinal());
    }

    void save() {
        mConf.setProperty(KonConf.VIEW_FRAME_WIDTH, this.getWidth());
        mConf.setProperty(KonConf.VIEW_FRAME_HEIGHT, this.getHeight());
    }

    void toggleState() {
        if (this.getState() == Frame.NORMAL) {
            this.setState(Frame.ICONIFIED);
            this.setVisible(false);
        } else {
            this.setState(Frame.NORMAL);
            this.setVisible(true);
        }
    }

    public final void statusChanged(Kontalk.Status status) {
        switch (status) {
            case CONNECTING:
                mConnectMenuItem.setEnabled(false);
                break;
            case CONNECTED:
                mConnectMenuItem.setEnabled(false);
                mDisconnectMenuItem.setEnabled(true);
                break;
            case DISCONNECTING:
                mDisconnectMenuItem.setEnabled(false);
                break;
            case DISCONNECTED:
                mConnectMenuItem.setEnabled(true);
                mDisconnectMenuItem.setEnabled(false);
                break;
            case FAILED:
                mConnectMenuItem.setEnabled(true);
                mDisconnectMenuItem.setEnabled(false);
            break;
        }
    }

    private void showAboutDialog() {
        WebPanel aboutPanel = new WebPanel();
        aboutPanel.add(new WebLabel("Kontalk Java Client v0.1"));
        WebLinkLabel linkLabel = new WebLinkLabel();
        linkLabel.setLink("http://www.kontalk.org");
        linkLabel.setText("Visit kontalk.org");
        aboutPanel.add(linkLabel, BorderLayout.SOUTH);
        Icon icon = View.getIcon("kontalk.png");
        WebOptionPane.showMessageDialog(this,
                aboutPanel,
                "About",
                WebOptionPane.INFORMATION_MESSAGE,
                icon);
    }

    private class StatusDialog extends WebDialog {

        private final WebTextField mStatusField;
        private final WebList mStatusList;

        StatusDialog() {
            this.setTitle("Status");
            this.setResizable(false);
            this.setModal(true);

            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            String[] strings = mConf.getStringArray(KonConf.NET_STATUS_LIST);
            List<String> stats = new ArrayList(Arrays.asList(strings));
            String currentStatus = "";
            if (!stats.isEmpty())
                currentStatus = stats.remove(0);

            stats.remove("");

            groupPanel.add(new WebLabel("Your current status:"));
            mStatusField = new WebTextField(currentStatus, 30);
            groupPanel.add(mStatusField);
            groupPanel.add(new WebSeparator(true, true));

            groupPanel.add(new WebLabel("Previously used:"));
            mStatusList = new WebList(stats);
            mStatusList.setMultiplySelectionAllowed(false);
            mStatusList.addListSelectionListener(new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent e) {
                    if (e.getValueIsAdjusting())
                        return;
                    mStatusField.setText(mStatusList.getSelectedValue().toString());
                }
            });
            WebScrollPane listScrollPane = new ScrollPane(mStatusList);
            listScrollPane.setPreferredHeight(100);
            listScrollPane.setPreferredWidth(0);
            groupPanel.add(listScrollPane);
            this.add(groupPanel, BorderLayout.CENTER);

            // buttons
            WebButton cancelButton = new WebButton("Cancel");
            cancelButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    StatusDialog.this.dispose();
                }
            });
            final WebButton saveButton = new WebButton("Save");
            saveButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    StatusDialog.this.saveStatus();
                    StatusDialog.this.dispose();
                }
            });

            GroupPanel buttonPanel = new GroupPanel(2, cancelButton, saveButton);
            buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
            this.add(buttonPanel, BorderLayout.SOUTH);

            this.pack();
        }

        private void saveStatus() {
            String newStatus = mStatusField.getText();

            String[] strings = mConf.getStringArray(KonConf.NET_STATUS_LIST);
            List<String> stats = new ArrayList(Arrays.asList(strings));

            stats.remove(newStatus);

            stats.add(0, newStatus);

            if (stats.size() > 20)
                stats = stats.subList(0, 20);

            mConf.setProperty(KonConf.NET_STATUS_LIST, stats.toArray());
        }
    }

    private class AddUserDialog extends WebDialog {

        private final WebTextField mNameField;
        private final WebTextField mJIDField;
        private final WebCheckBox mEncryptionBox;

        AddUserDialog() {
            this.setTitle("Add New Contact");
            //this.setSize(400, 280);
            this.setResizable(false);
            this.setModal(true);

            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            // editable fields
            WebPanel namePanel = new WebPanel();
            namePanel.setLayout(new BorderLayout(10, 5));
            namePanel.add(new WebLabel("Display Name:"), BorderLayout.WEST);
            mNameField = new WebTextField();
            namePanel.add(mNameField, BorderLayout.CENTER);
            groupPanel.add(namePanel);
            groupPanel.add(new WebSeparator(true, true));

            mEncryptionBox = new WebCheckBox("Encryption");
            mEncryptionBox.setAnimated(false);
            mEncryptionBox.setSelected(true);
            groupPanel.add(mEncryptionBox);
            groupPanel.add(new WebSeparator(true, true));

            groupPanel.add(new WebLabel("JID:"));
            mJIDField = new WebTextField(38);
            groupPanel.add(mJIDField);
            groupPanel.add(new WebSeparator(true, true));

            this.add(groupPanel, BorderLayout.CENTER);

            // buttons
            WebButton cancelButton = new WebButton("Cancel");
            cancelButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    AddUserDialog.this.dispose();
                }
            });
            final WebButton saveButton = new WebButton("Save");
            saveButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    AddUserDialog.this.saveUser();
                    AddUserDialog.this.dispose();
                }
            });

            GroupPanel buttonPanel = new GroupPanel(2, cancelButton, saveButton);
            buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
            this.add(buttonPanel, BorderLayout.SOUTH);

            this.pack();
        }

        private void saveUser() {
            User newUser = UserList.getInstance().addUser(mJIDField.getText(), mNameField.getText());
            newUser.setEncrypted(mEncryptionBox.isSelected());
        }
    }
}

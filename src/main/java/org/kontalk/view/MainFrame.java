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
import com.alee.extended.painter.BorderPainter;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.GroupingType;
import com.alee.extended.panel.WebOverlay;
import com.alee.laf.button.WebButton;
import com.alee.laf.button.WebToggleButton;
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
import com.alee.laf.tabbedpane.WebTabbedPane;
import com.alee.laf.text.WebTextArea;
import com.alee.laf.text.WebTextField;
import com.alee.managers.hotkey.Hotkey;
import com.alee.managers.popup.PopupAdapter;
import com.alee.managers.popup.WebPopup;
import com.alee.managers.tooltip.TooltipManager;
import com.alee.utils.WebUtils;
import com.alee.utils.swing.DocumentChangeListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.SystemTray;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.Icon;
import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.kontalk.system.Config;
import org.kontalk.Kontalk;
import org.kontalk.system.Control;
import org.kontalk.util.Tr;
import org.kontalk.util.XMPPUtils;

/**
 * The application window.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class MainFrame extends WebFrame {

    private static final String WIKI_URL =
            "https://github.com/kontalk/desktopclient-java/wiki";

    static enum Tab {THREADS, USER};

    private final View mView;
    private final Config mConf = Config.getInstance();
    private final WebMenuItem mConnectMenuItem;
    private final WebMenuItem mDisconnectMenuItem;
    private final WebTabbedPane mTabbedPane;
    private WebPopup mAddUserPopup = new WebPopup();

    MainFrame(final View view,
            Table<?, ?> userList,
            Table<?, ?> threadList,
            Component content,
            Component searchPanel,
            Component statusBar) {
        mView = view;

        // general view + behaviour
        this.setTitle("Kontalk Java Client");
        this.setSize(mConf.getInt(Config.VIEW_FRAME_WIDTH),
                mConf.getInt(Config.VIEW_FRAME_HEIGHT));

        this.setIconImage(Utils.getImage("kontalk.png"));

        // closing behaviour
        this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (mConf.getBoolean(Config.MAIN_TRAY_CLOSE) &&
                        SystemTray.getSystemTray().getTrayIcons().length > 0)
                    MainFrame.this.toggleState();
                else
                    mView.callShutDown();
            }
        });

        // menu
        WebMenuBar menubar = new WebMenuBar();
        this.setJMenuBar(menubar);

        WebMenu konNetMenu = new WebMenu("KonNet");
        konNetMenu.setMnemonic(KeyEvent.VK_K);

        mConnectMenuItem = new WebMenuItem(Tr.tr("Connect"));
        mConnectMenuItem.setAccelerator(Hotkey.ALT_C);
        mConnectMenuItem.setToolTipText(Tr.tr("Connect to Server"));
        mConnectMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                mView.getControl().connect();
            }
        });
        konNetMenu.add(mConnectMenuItem);

        mDisconnectMenuItem = new WebMenuItem(Tr.tr("Disconnect"));
        mDisconnectMenuItem.setAccelerator(Hotkey.ALT_D);
        mDisconnectMenuItem.setToolTipText(Tr.tr("Disconnect from Server"));
        mDisconnectMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                mView.getControl().disconnect();
            }
        });
        konNetMenu.add(mDisconnectMenuItem);
        konNetMenu.addSeparator();

        WebMenuItem statusMenuItem = new WebMenuItem(Tr.tr("Set status"));
        statusMenuItem.setAccelerator(Hotkey.ALT_S);
        statusMenuItem.setToolTipText(Tr.tr("Set status text send to other user"));
        statusMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                WebDialog statusDialog = new StatusDialog();
                statusDialog.setVisible(true);
            }
        });
        konNetMenu.add(statusMenuItem);
        konNetMenu.addSeparator();

        WebMenuItem exitMenuItem = new WebMenuItem(Tr.tr("Exit"));
        exitMenuItem.setAccelerator(Hotkey.ALT_E);
        exitMenuItem.setToolTipText(Tr.tr("Exit application"));
        exitMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                mView.callShutDown();
            }
        });
        konNetMenu.add(exitMenuItem);

        menubar.add(konNetMenu);

        WebMenu optionsMenu = new WebMenu(Tr.tr("Options"));
        optionsMenu.setMnemonic(KeyEvent.VK_O);

        WebMenuItem conConfMenuItem = new WebMenuItem(Tr.tr("Preferences"));
        conConfMenuItem.setAccelerator(Hotkey.ALT_P);
        conConfMenuItem.setToolTipText(Tr.tr("Set application preferences"));
        conConfMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                mView.showConfig();
            }
        });
        optionsMenu.add(conConfMenuItem);

        menubar.add(optionsMenu);

        WebMenu helpMenu = new WebMenu(Tr.tr("Help"));
        helpMenu.setMnemonic(KeyEvent.VK_H);

        WebMenuItem wikiItem = new WebMenuItem(Tr.tr("Online wiki"));
        wikiItem.setToolTipText(Tr.tr("Visit the wiki"));
        wikiItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                WebUtils.browseSiteSafely(WIKI_URL);
            }
        });
        helpMenu.add(wikiItem);
        WebMenuItem aboutMenuItem = new WebMenuItem(Tr.tr("About"));
        aboutMenuItem.setToolTipText(Tr.tr("About Kontalk"));
        aboutMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                MainFrame.this.showAboutDialog();
            }
        });
        helpMenu.add(aboutMenuItem);

        menubar.add(helpMenu);

        // Layout...
        this.setLayout(new BorderLayout(View.GAP_SMALL, View.GAP_SMALL));

        // ...left...
        WebPanel sidePanel = new WebPanel(false);
        sidePanel.add(searchPanel, BorderLayout.NORTH);
        mTabbedPane = new WebTabbedPane(WebTabbedPane.LEFT);
        //String threadOverlayText =
        //        Tr.t/r("No chats to display. You can create a new chat from your contacts");
        WebScrollPane threadPane = createTablePane(threadList, "threadOverlayText");
        mTabbedPane.addTab("", threadPane);
        mTabbedPane.setTabComponentAt(Tab.THREADS.ordinal(),
                new WebVerticalLabel(Tr.tr("Chats")));

        //String userOverlayText = T/r.tr("No contacts to display. You have no friends ;(");
        WebScrollPane userPane = createTablePane(userList, "userOverlayText");
        final WebToggleButton addUserButton = new WebToggleButton(
                Utils.getIcon("ic_ui_add.png"));
        TooltipManager.addTooltip(addUserButton, Tr.tr("Add a new Contact"));
        addUserButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!MainFrame.this.mAddUserPopup.isShowing())
                    MainFrame.this.showAddUserPopup(addUserButton);
            }
        });
        mTabbedPane.addTab("", new GroupPanel(GroupingType.fillFirst, false,
                userPane, addUserButton));

        mTabbedPane.setTabComponentAt(Tab.USER.ordinal(),
                new WebVerticalLabel(Tr.tr("Contacts")));
        mTabbedPane.setPreferredSize(new Dimension(250, -1));
        mTabbedPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                mView.tabPaneChanged(Tab.values()[mTabbedPane.getSelectedIndex()]);
            }
        });

        sidePanel.add(mTabbedPane, BorderLayout.CENTER);
        this.add(sidePanel, BorderLayout.WEST);

        // ...right...
        this.add(content, BorderLayout.CENTER);

        // ...bottom
        this.add(statusBar, BorderLayout.SOUTH);
    }

    public Tab getCurrentTab() {
        return Tab.values()[mTabbedPane.getSelectedIndex()];
    }

    void selectTab(Tab tab) {
        mTabbedPane.setSelectedIndex(tab.ordinal());
    }

    void save() {
        mConf.setProperty(Config.VIEW_FRAME_WIDTH, this.getWidth());
        mConf.setProperty(Config.VIEW_FRAME_HEIGHT, this.getHeight());
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

    final void statusChanged(Control.Status status) {
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
                // fallthrough
            case FAILED:
                // fallthrough
            case ERROR:
                mConnectMenuItem.setEnabled(true);
                mDisconnectMenuItem.setEnabled(false);
            break;
        }
    }

    private void showAboutDialog() {
        WebPanel aboutPanel = new WebPanel(new GridLayout(0, 1, View.GAP_SMALL, View.GAP_SMALL));
        aboutPanel.add(new WebLabel("Kontalk Java Client v" + Kontalk.VERSION));
        WebLinkLabel linkLabel = new WebLinkLabel();
        linkLabel.setLink("http://www.kontalk.org");
        linkLabel.setText(Tr.tr("Visit kontalk.org"));
        aboutPanel.add(linkLabel);
        WebLabel soundLabel = new WebLabel(Tr.tr("Notification sound by")+" FxProSound");
        aboutPanel.add(soundLabel);
        Icon icon = Utils.getIcon("kontalk.png");
        WebOptionPane.showMessageDialog(this,
                aboutPanel,
                Tr.tr("About"),
                WebOptionPane.INFORMATION_MESSAGE,
                icon);
    }

    private void showAddUserPopup(final WebToggleButton invoker) {
        mAddUserPopup = new WebPopup();
        mAddUserPopup.setCloseOnFocusLoss(true);
        mAddUserPopup.addPopupListener(new PopupAdapter() {
            @Override
            public void popupWillBeClosed() {
                invoker.doClick();
            }
        });
        mAddUserPopup.add(new AddUserPanel(this));
        //mPopup.packPopup();
        mAddUserPopup.showAsPopupMenu(invoker);
    }

    private class StatusDialog extends WebDialog {

        private final WebTextField mStatusField;
        private final WebList mStatusList;

        StatusDialog() {
            this.setTitle(Tr.tr("Status"));
            this.setResizable(false);
            this.setModal(true);

            GroupPanel groupPanel = new GroupPanel(View.GAP_DEFAULT, false);
            groupPanel.setMargin(View.MARGIN_BIG);

            String[] strings = mConf.getStringArray(Config.NET_STATUS_LIST);
            List<String> stats = new ArrayList<>(Arrays.<String>asList(strings));
            String currentStatus = "";
            if (!stats.isEmpty())
                currentStatus = stats.remove(0);

            stats.remove("");

            groupPanel.add(new WebLabel(Tr.tr("Your current status:")));
            mStatusField = new WebTextField(currentStatus, 30);
            groupPanel.add(mStatusField);
            groupPanel.add(new WebSeparator(true, true));

            groupPanel.add(new WebLabel(Tr.tr("Previously used:")));
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
            groupPanel.add(listScrollPane);
            this.add(groupPanel, BorderLayout.CENTER);

            // buttons
            WebButton cancelButton = new WebButton(Tr.tr("Cancel"));
            cancelButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    StatusDialog.this.dispose();
                }
            });
            final WebButton saveButton = new WebButton(Tr.tr("Save"));
            saveButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    StatusDialog.this.saveStatus();
                    StatusDialog.this.dispose();
                }
            });
            this.getRootPane().setDefaultButton(saveButton);

            GroupPanel buttonPanel = new GroupPanel(2, cancelButton, saveButton);
            buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
            this.add(buttonPanel, BorderLayout.SOUTH);

            this.pack();
        }

        private void saveStatus() {
            String newStatus = mStatusField.getText();

            String[] strings = mConf.getStringArray(Config.NET_STATUS_LIST);
            List<String> stats = new ArrayList<>(Arrays.asList(strings));

            stats.remove(newStatus);

            stats.add(0, newStatus);

            if (stats.size() > 20)
                stats = stats.subList(0, 20);

            mConf.setProperty(Config.NET_STATUS_LIST, stats.toArray());
            mView.getControl().sendStatusText();
        }
    }

    private class AddUserPanel extends WebPanel {

        private final WebTextField mNameField;
        private final WebTextField mJIDField;
        private final WebCheckBox mEncryptionBox;

        AddUserPanel(final Component focusGainer) {

            GroupPanel groupPanel = new GroupPanel(View.GAP_BIG, false);
            groupPanel.setMargin(View.MARGIN_BIG);

            groupPanel.add(new WebLabel(Tr.tr("Add Contact")).setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            final WebButton mSaveButton = new WebButton(Tr.tr("Create"));

            // editable fields
            mNameField = new WebTextField(20);
            mNameField.getDocument().addDocumentListener(new DocumentChangeListener() {
                @Override
                public void documentChanged(DocumentEvent e) {
                    mSaveButton.setEnabled(XMPPUtils.isValid(mJIDField.getText()));
                }
            });
            groupPanel.add(new GroupPanel(View.GAP_DEFAULT,
                    new WebLabel(Tr.tr("Name:")), mNameField));

            mJIDField = new WebTextField();
            mJIDField.setInputPrompt("username@jabber-server.com");
            mJIDField.getDocument().addDocumentListener(new DocumentChangeListener() {
                @Override
                public void documentChanged(DocumentEvent e) {
                    mSaveButton.setEnabled(XMPPUtils.isValid(mJIDField.getText()));
                }
            });
            groupPanel.add(new GroupPanel(GroupingType.fillLast, View.GAP_DEFAULT,
                    new WebLabel("Jabber ID:"), mJIDField));
            groupPanel.add(new WebSeparator(true, true));

            mEncryptionBox = new WebCheckBox(Tr.tr("Encryption"));
            mEncryptionBox.setAnimated(false);
            mEncryptionBox.setSelected(true);
            groupPanel.add(mEncryptionBox);
            groupPanel.add(new WebSeparator(true, true));

            this.add(groupPanel, BorderLayout.CENTER);

            mSaveButton.setEnabled(false);
            mSaveButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    AddUserPanel.this.saveUser();
                    focusGainer.requestFocus();
                }
            });

            GroupPanel buttonPanel = new GroupPanel(mSaveButton);
            buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
            this.add(buttonPanel, BorderLayout.SOUTH);
        }

        private void saveUser() {
            mView.getControl().createNewUser(mJIDField.getText(),
                    mNameField.getText(),
                    mEncryptionBox.isSelected());
        }
    }

    private static WebScrollPane createTablePane(final Table<?, ?> table,
            String overlayText) {

        WebScrollPane scrollPane = new ScrollPane(table);
        // overlay for empty list
        WebOverlay listOverlayPanel = new WebOverlay(scrollPane);
        listOverlayPanel.setOverlayMargin(20);
        final WebTextArea overlayArea = new WebTextArea();
        overlayArea.setText(overlayText);
        overlayArea.setLineWrap(true);
        overlayArea.setWrapStyleWord(true);
        overlayArea.setMargin(View.MARGIN_DEFAULT);
        overlayArea.setFontSize(15);
        overlayArea.setEditable(false);
        BorderPainter<WebTextArea> borderPainter = new BorderPainter<>(Color.LIGHT_GRAY);
        borderPainter.setRound(15);
        overlayArea.setPainter(borderPainter);
        // TODO
//        table.addListDataListener(new ListDataListener() {
//            @Override
//            public void intervalAdded(ListDataEvent e) {
//                this.setOverlay();
//            }
//            @Override
//            public void intervalRemoved(ListDataEvent e) {
//                this.setOverlay();
//            }
//            @Override
//            public void contentsChanged(ListDataEvent e) {
//            }
//            private void setOverlay() {
//                overlayArea.setVisible(table.getModelSize() == 0);
//            }
//        });
        //listOverlayPanel.addOverlay(new GroupPanel(false, overlayArea));
        //listPanel.add(listOverlayPanel, BorderLayout.CENTER);
        return scrollPane;
    }
}

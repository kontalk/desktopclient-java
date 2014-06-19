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
import com.alee.laf.text.WebTextField;
import com.alee.managers.hotkey.Hotkey;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Toolkit;
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
import javax.swing.ImageIcon;
import javax.swing.ScrollPaneConstants;
import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.kontalk.KonConf;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class MainFrame extends WebFrame {
    private final static Logger LOGGER = Logger.getLogger(ThreadView.class.getName());

    public static enum Tab {THREADS, USER};

    private final KonConf mConf = KonConf.getInstance();
    private final WebTabbedPane mTabbedPane;

    public MainFrame(final View viewModel,
            Component userList,
            Component threadList,
            Component threadView,
            Component sendTextField,
            Component sendButton,
            Component statusBar) {

        // general view + behaviour
        this.setTitle("Kontalk Java Client");
        this.setSize(mConf.getInt(KonConf.VIEW_FRAME_WIDTH),
                mConf.getInt(KonConf.VIEW_FRAME_HEIGHT));

        if (View.ICON_IMAGE_URL != null) {
            this.setIconImage(Toolkit.getDefaultToolkit().createImage(View.ICON_IMAGE_URL));
        } else {
            LOGGER.warning("can't find icon image resource");
        }
        //this.setResizable(false);

        // closing behaviour
        this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e){
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

        WebMenuItem connectMenuItem = new WebMenuItem("Connect");
        connectMenuItem.setAccelerator(Hotkey.ALT_C);
        connectMenuItem.setToolTipText("Connect to Server");
        connectMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                viewModel.connect();
            }
        });
        konNetMenu.add(connectMenuItem);

        WebMenuItem disconnectMenuItem = new WebMenuItem("Disconnect");
        disconnectMenuItem.setAccelerator(Hotkey.ALT_D);
        disconnectMenuItem.setToolTipText("Disconnect from Server");
        disconnectMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                viewModel.disconnect();
            }
        });
        konNetMenu.add(disconnectMenuItem);
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

        WebScrollPane threadScrollPane = new WebScrollPane(threadList);
        threadScrollPane.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        mTabbedPane.addTab("", threadScrollPane);
        WebScrollPane userScrollPane = new WebScrollPane(userList);
        userScrollPane.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        mTabbedPane.addTab("", userScrollPane);
        mTabbedPane.setTabComponentAt(Tab.THREADS.ordinal(),
                new WebVerticalLabel("Threads"));
        mTabbedPane.setTabComponentAt(Tab.USER.ordinal(),
                new WebVerticalLabel("Contacts"));
        mTabbedPane.setPreferredSize(new Dimension(250, -1));
        this.add(mTabbedPane, BorderLayout.WEST);

        // ...right...
        WebPanel rightPanel = new WebPanel();
        rightPanel.add(threadView);
        WebPanel bottomPanel = new WebPanel();
        bottomPanel.add(sendTextField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        rightPanel.add(bottomPanel, BorderLayout.SOUTH);
        this.add(rightPanel, BorderLayout.CENTER);

        // ...bottom
        this.add(statusBar, BorderLayout.SOUTH);
    }

    void selectTab(Tab tab){
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

    private void showAboutDialog() {
        WebPanel aboutPanel = new WebPanel();
        aboutPanel.add(new WebLabel("Kontalk Java Client v0.1"));
        WebLinkLabel linkLabel = new WebLinkLabel();
        linkLabel.setLink("http://www.kontalk.org");
        linkLabel.setText("Visit kontalk.org");
        aboutPanel.add(linkLabel, BorderLayout.SOUTH);
        ImageIcon icon;
        if (View.ICON_IMAGE_URL != null) {
            icon = new ImageIcon(View.ICON_IMAGE_URL);
        } else {
            icon = new ImageIcon();
        }
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
            WebScrollPane listScrollPane = new WebScrollPane(mStatusList);
            listScrollPane.setHorizontalScrollBarPolicy(
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
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
}

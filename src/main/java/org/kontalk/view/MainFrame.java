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
import com.alee.laf.button.WebToggleButton;
import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebMenu;
import com.alee.laf.menu.WebMenuBar;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.rootpane.WebFrame;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.tabbedpane.WebTabbedPane;
import com.alee.laf.text.WebTextArea;
import com.alee.managers.hotkey.Hotkey;
import com.alee.utils.WebUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.SystemTray;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.Icon;
import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.kontalk.system.Config;
import org.kontalk.Kontalk;
import org.kontalk.system.Control;
import org.kontalk.util.Tr;

/**
 * The application window.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class MainFrame extends WebFrame {

    static enum Tab {CHATS, CONTACT};

    private final View mView;
    private final WebMenuItem mConnectMenuItem;
    private final WebMenuItem mDisconnectMenuItem;
    private final WebTabbedPane mTabbedPane;
    private final WebToggleButton mAddGroupButton;
    private final WebToggleButton mAddContactButton;

    MainFrame(final View view,
            ListView<?, ?> contactList,
            ListView<?, ?> chatList,
            Component content,
            WebPanel searchPanel,
            Component statusBar) {
        mView = view;

        final Config conf = Config.getInstance();

        // general view + behaviour
        this.setTitle("Kontalk Java Client");
        this.setSize(conf.getInt(Config.VIEW_FRAME_WIDTH),
                conf.getInt(Config.VIEW_FRAME_HEIGHT));

        this.setIconImage(Utils.getImage("kontalk.png"));

        // closing behaviour
        this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (conf.getBoolean(Config.MAIN_TRAY_CLOSE) &&
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
                WebDialog statusDialog = new ProfileDialog(mView);
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
                WebUtils.browseSiteSafely(Tr.getLocalizedWikiLink());
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
        mTabbedPane = new WebTabbedPane(WebTabbedPane.LEFT);
        //String chatOverlayText =
        //        Tr.t/r("No chats to display. You can create a new chat from your contacts");
        WebScrollPane chatPane = createTablePane(chatList, "chatOverlayText");
        mAddGroupButton = new ComponentUtils.ToggleButton(
                // TODO different button
                Utils.getIcon("ic_ui_add.png"),
                Tr.tr("Create a new group chat"),
                new ComponentUtils.AddGroupChatPanel(mView, this));
        WebPanel chatPanel = new GroupPanel(GroupingType.fillFirst, false,
                chatPane, mAddGroupButton);
        chatPanel.setPaintSides(false, false, false, false);
        mTabbedPane.addTab("", chatPanel);
        mTabbedPane.setTabComponentAt(Tab.CHATS.ordinal(),
                new WebVerticalLabel(Tr.tr("Chats")));

        //String contactOverlayText = T/r.tr("No contacts to display. You have no friends ;(");
        WebScrollPane contactPane = createTablePane(contactList, "contactOverlayText");
        mAddContactButton = new ComponentUtils.ToggleButton(
                Utils.getIcon("ic_ui_add.png"),
                Tr.tr("Add a new contact"),
                new ComponentUtils.AddContactPanel(mView, this));
        WebPanel contactPanel = new GroupPanel(GroupingType.fillFirst, false,
                contactPane, mAddContactButton);
        contactPanel.setPaintSides(false, false, false, false);
        mTabbedPane.addTab("", contactPanel);
        mTabbedPane.setTabComponentAt(Tab.CONTACT.ordinal(),
                new WebVerticalLabel(Tr.tr("Contacts")));
        // setSize() does not work, whatever
        mTabbedPane.setPreferredSize(new Dimension(View.LISTS_WIDTH, -1));
        mTabbedPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                mView.tabPaneChanged(Tab.values()[mTabbedPane.getSelectedIndex()]);
            }
        });
        this.add(new GroupPanel(GroupingType.fillLast, false,
                searchPanel, mTabbedPane),
                BorderLayout.WEST);

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
        Config conf = Config.getInstance();
        conf.setProperty(Config.VIEW_FRAME_WIDTH, this.getWidth());
        conf.setProperty(Config.VIEW_FRAME_HEIGHT, this.getHeight());
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

    final void onStatusChanged(Control.Status status) {
        switch (status) {
            case CONNECTING:
                mConnectMenuItem.setEnabled(false);
                break;
            case CONNECTED:
                mConnectMenuItem.setEnabled(false);
                mDisconnectMenuItem.setEnabled(true);
                mAddContactButton.setEnabled(true);
                break;
            case DISCONNECTING:
                mDisconnectMenuItem.setEnabled(false);
                mAddContactButton.setEnabled(false);
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

    private static WebScrollPane createTablePane(final ListView<?, ?> list,
            String overlayText) {

        WebScrollPane scrollPane = new ComponentUtils.ScrollPane(list);
        scrollPane.setDrawBorder(false);
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

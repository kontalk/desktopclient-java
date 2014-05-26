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
import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebMenu;
import com.alee.laf.menu.WebMenuBar;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebFrame;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.tabbedpane.WebTabbedPane;
import com.alee.managers.hotkey.Hotkey;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.ScrollPaneConstants;
import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class MainFrame extends WebFrame {
    private final static Logger LOGGER = Logger.getLogger(ThreadView.class.getName());

    private final static URL ICON_IMAGE_URL = ClassLoader.getSystemResource(
            "org/kontalk/res/kontalk.png");

    public static enum Tab {THREADS, USER};

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
        this.setSize(600, 650);

        if (ICON_IMAGE_URL != null) {
            this.setIconImage(Toolkit.getDefaultToolkit().createImage(ICON_IMAGE_URL));
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

        // menu
        WebMenuBar menubar = new WebMenuBar();
        this.setJMenuBar(menubar);

        WebMenu fileMenu = new WebMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        WebMenuItem connectMenuItem = new WebMenuItem("Connect");
        connectMenuItem.setAccelerator(Hotkey.ALT_C);
        connectMenuItem.setToolTipText("Connect to Server");
        connectMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                viewModel.connect();
            }
        });
        fileMenu.add(connectMenuItem);

        WebMenuItem disconnectMenuItem = new WebMenuItem("Disconnect");
        disconnectMenuItem.setAccelerator(Hotkey.ALT_D);
        disconnectMenuItem.setToolTipText("Disconnect from Server");
        disconnectMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                viewModel.disconnect();
            }
        });
        fileMenu.add(disconnectMenuItem);
        fileMenu.addSeparator();

        WebMenuItem exitMenuItem = new WebMenuItem("Exit");
        exitMenuItem.setAccelerator(Hotkey.ALT_E);
        exitMenuItem.setToolTipText("Exit application");
        exitMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                viewModel.shutDown();
            }
        });
        fileMenu.add(exitMenuItem);

        menubar.add(fileMenu);

        WebMenu optionsMenu = new WebMenu("Options");
        optionsMenu.setMnemonic(KeyEvent.VK_O);

        WebMenuItem conConfMenuItem = new WebMenuItem("Configuration");
        conConfMenuItem.setAccelerator(Hotkey.ALT_N);
        conConfMenuItem.setToolTipText("Set account configuration");
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
                showAboutDialog();
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
//        WebScrollPane threadViewScrollPane = new WebScrollPane(threadView);
//        threadViewScrollPane.setHorizontalScrollBarPolicy(
//                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
//        threadViewScrollPane.getVerticalScrollBar().setUnitIncrement(25);
//        rightPanel.add(threadViewScrollPane, BorderLayout.CENTER);
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

    private void showAboutDialog() {
        WebPanel aboutPanel = new WebPanel();
        aboutPanel.add(new WebLabel("Kontalk Java Client v0.1"));
        WebLinkLabel linkLabel = new WebLinkLabel();
        linkLabel.setLink("http://www.kontalk.org");
        linkLabel.setText("Visit kontalk.org");
        aboutPanel.add(linkLabel, BorderLayout.SOUTH);
        ImageIcon icon;
        if (ICON_IMAGE_URL != null) {
            icon = new ImageIcon(ICON_IMAGE_URL);
        } else {
            icon = new ImageIcon();
        }
        WebOptionPane.showMessageDialog(this,
                aboutPanel,
                "About",
                WebOptionPane.INFORMATION_MESSAGE,
                icon);
    }
}

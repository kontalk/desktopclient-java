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

import com.alee.extended.label.WebVerticalLabel;
import com.alee.laf.menu.WebMenu;
import com.alee.laf.menu.WebMenuBar;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.splitpane.WebSplitPane;
import com.alee.laf.tabbedpane.WebTabbedPane;
import com.alee.managers.hotkey.Hotkey;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import static javax.swing.JSplitPane.HORIZONTAL_SPLIT;
import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class MainFrame extends JFrame {

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
        this.setSize(500, 600);
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
                WebOptionPane.showMessageDialog(null, 
                        "Kontalk Java Client v0.1", "About", 
                        WebOptionPane.INFORMATION_MESSAGE);
            }
        });
        helpMenu.add(aboutMenuItem);
        
        menubar.add(helpMenu);
        
        // Layout...
        this.setLayout(new BorderLayout(5, 5));

        // ...left...
        mTabbedPane = new WebTabbedPane(WebTabbedPane.LEFT);
        mTabbedPane.addTab("", new WebScrollPane(threadList));
        mTabbedPane.addTab("", new WebScrollPane(userList));
        mTabbedPane.setTabComponentAt(Tab.THREADS.ordinal(), 
                new WebVerticalLabel("Threads"));
        mTabbedPane.setTabComponentAt(Tab.USER.ordinal(), 
                new WebVerticalLabel("Contacts"));
        this.add(mTabbedPane, BorderLayout.WEST);

        // ...right...
        WebPanel rightPanel = new WebPanel();
        rightPanel.add(new WebScrollPane(threadView), BorderLayout.CENTER);
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
    
}    

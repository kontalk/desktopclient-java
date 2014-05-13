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

import com.alee.extended.statusbar.WebStatusBar;
import com.alee.extended.statusbar.WebStatusLabel;
import com.alee.laf.button.WebButton;
import com.alee.managers.hotkey.Hotkey;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Logger;
import javax.swing.JDialog;
import javax.swing.JTextField;
import org.kontalk.KontalkException;
import org.kontalk.MyKontalk;
import org.kontalk.model.UserList;
import org.kontalk.model.KontalkThread;
import org.kontalk.model.ThreadList;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class View {
    private final static Logger LOGGER = Logger.getLogger(View.class.getName());
    
    final static Color BLUE = new Color(131, 173, 239);
    
    private final MyKontalk mModel;
    private final UserListView mUserListView;
    private final ThreadListView mThreadListView;
    private final ChatView mChatView;
    private final JTextField mSendTextField;
    private final WebButton mSendButton;
    private final WebStatusLabel mStatusBarLabel;
    private final MainFrame mMainFrame;
    
    public View(MyKontalk model) {
        mModel = model;
        
        mUserListView = new UserListView(this);
        mThreadListView = new ThreadListView(this);
        
        mChatView = new ChatView();
        
        // text field
        mSendTextField = new JTextField();
        //this.textField.setColumns(25);        
        mSendTextField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendText();
            }
        });
        
        // send button
        mSendButton = new WebButton("Send");
        mSendButton.addHotkey(Hotkey.CTRL_S);
        mSendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendText();
            }
        });
        
        // status bar
        WebStatusBar statusBar = new WebStatusBar();
        mStatusBarLabel = new WebStatusLabel(" ");
        statusBar.add(mStatusBarLabel);
        
        // main frame
        mMainFrame = new MainFrame(this, mUserListView, mThreadListView, 
                mChatView, mSendTextField, mSendButton, statusBar);
        mMainFrame.setVisible(true);
        
        // TODO: always disconnected?
        this.statusChanged(MyKontalk.Status.DISCONNECTED);
    }

    public void statusChanged(MyKontalk.Status status) {
        switch (status) {
            case CONNECTING:
                mStatusBarLabel.setText("Connecting...");
                break;
            case CONNECTED:
                mChatView.setBackground(Color.white);
                mSendTextField.setEditable(true);
                mSendButton.setEnabled(true);
                mStatusBarLabel.setText("Connected");
                break;
            case DISCONNECTING:
                mStatusBarLabel.setText("Disconnecting...");
                break;
            case DISCONNECTED:
                mChatView.setBackground(Color.lightGray);
                mSendTextField.setEditable(false);
                mSendButton.setEnabled(false);
                mStatusBarLabel.setText("Not connected");    
                break;
            case SHUTTING_DOWN:
                mStatusBarLabel.setText("Shutting down...");
                break;
            }
    }
    
    public void threadListChanged(ThreadList threads) {
        mThreadListView.modelChanged(threads);
    }
    
    public void threadChanged(KontalkThread thread) {
        if (mChatView.getCurrentThreadID() == thread.getID()) {
            mChatView.showThread(thread);
        }
    }
    
    public void userListChanged(UserList user) {
        mUserListView.modelChanged(user);
    }

    public void showConfig() {
        this.showConfig("Default text here");
    }
    
    public void connectionProblem(KontalkException ex) {
        this.showConfig("Help Message here");
    }
    
    private void showConfig(String helpText) {
        JDialog configFrame = new ConfigurationFrame(this, helpText);
        configFrame.setVisible(true);
    }
    
    void shutDown() {
        mModel.shutDown();
    } 

    void connect() {
        mModel.connect();
    }

    void disconnect() {
        mModel.disconnect();
    }

    void selectedUserChanged(int userID) {
        if (userID == -1) {
            mChatView.showThread(null);
        } else {
            KontalkThread thread = mModel.getThreadByUserID(userID);
            mThreadListView.selectThread(thread.getID());
            mMainFrame.selectTab(MainFrame.Tab.THREADS);
            mChatView.showThread(thread);
        }
    }
    
    void selectedThreadChanged(int threadID) {
        if (threadID == -1)
            return;
        
        KontalkThread thread = mModel.getThreadByID(threadID);
        if (thread == null)
            return;

        mChatView.showThread(thread);
    }

    private void sendText() {
       int threadID = mThreadListView.getSelectedThreadID();
       if (threadID <= 0) {
           // TODO
           // nothing selected
           return;
       }
       mModel.sendText(threadID, mSendTextField.getText());
       mSendTextField.setText("");
    }
    
}

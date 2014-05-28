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
import javax.swing.ToolTipManager;
import org.kontalk.KontalkException;
import org.kontalk.MyKontalk;
import org.kontalk.model.KontalkThread;
import org.kontalk.model.ThreadList;
import org.kontalk.model.User;
import org.kontalk.model.UserList;

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
    private final ThreadView mThreadView;
    private final JTextField mSendTextField;
    private final WebButton mSendButton;
    private final WebStatusLabel mStatusBarLabel;
    private final MainFrame mMainFrame;

    public View(MyKontalk model) {
        mModel = model;

        ToolTipManager.sharedInstance().setInitialDelay(200);

        mUserListView = new UserListView(this, UserList.getInstance());
        mThreadListView = new ThreadListView(this, ThreadList.getInstance());
        // notify threadlist of changes in user list
        UserList.getInstance().addListener(mThreadListView);

        mThreadView = new ThreadView();

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
                mThreadView, mSendTextField, mSendButton, statusBar);
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
                mThreadView.setColor(Color.white);
                mStatusBarLabel.setText("Connected");
                break;
            case DISCONNECTING:
                mStatusBarLabel.setText("Disconnecting...");
                break;
            case DISCONNECTED:
                mThreadView.setColor(Color.lightGray);
                mStatusBarLabel.setText("Not connected");
                break;
            case SHUTTING_DOWN:
                mStatusBarLabel.setText("Shutting down...");
                mMainFrame.save();
                break;
            }
    }

    public void threadChanged(KontalkThread thread) {
        if (mThreadView.getCurrentThreadID() == thread.getID()) {
            mThreadView.showThread(thread);
        }
    }

    public void showConfig() {
        this.showConfig("Default text here");
    }

    public void connectionProblem(KontalkException ex) {
        this.showConfig("Help Message here");
    }

    private void showConfig(String helpText) {
        JDialog configFrame = new ConfigurationDialog(mMainFrame, this, helpText);
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

    void selectThreadByUser(User user) {
        if (user == null) {
            mThreadView.showThread(null);
        } else {
            KontalkThread thread = ThreadList.getInstance().getThreadByUser(user);
            mThreadListView.selectThread(thread.getID());
            mMainFrame.selectTab(MainFrame.Tab.THREADS);
            mThreadView.showThread(thread);
        }
    }

    void selectedThreadChanged(KontalkThread thread) {
        if (thread == null)
            return;
        mThreadView.showThread(thread);
    }

    private void sendText() {
       KontalkThread thread = mThreadListView.getSelectedThread();
       if (thread == null) {
           // TODO
           // nothing selected
           return;
       }
       mModel.sendText(thread, mSendTextField.getText());
       mSendTextField.setText("");
    }

}

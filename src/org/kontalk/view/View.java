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
import com.alee.laf.WebLookAndFeel;
import com.alee.laf.button.WebButton;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.text.WebTextArea;
import com.alee.managers.hotkey.Hotkey;
import com.alee.managers.hotkey.HotkeyData;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.tooltip.TooltipManager;
import com.alee.managers.tooltip.TooltipWay;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLHandshakeException;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.ToolTipManager;
import org.bouncycastle.openpgp.PGPException;
import org.jivesoftware.smack.SmackException.ConnectionException;
import org.jivesoftware.smack.sasl.SASLErrorException;
import org.kontalk.KonConf;
import org.kontalk.KonException;
import org.kontalk.Kontalk;
import org.kontalk.crypto.Coder;
import org.kontalk.model.KonMessage;
import org.kontalk.model.KonThread;
import org.kontalk.model.MessageList;
import org.kontalk.model.ThreadList;
import org.kontalk.model.User;
import org.kontalk.model.UserList;

/**
 * Initialize and control the user interface.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class View {
    private final static Logger LOGGER = Logger.getLogger(View.class.getName());

    public final static String RES_PATH = "org/kontalk/res/";
    final static Color BLUE = new Color(130, 170, 240);
    final static Color LIGHT_BLUE = new Color(220, 220, 250);

    private final Kontalk mModel;
    private final UserListView mUserListView;
    private final ThreadListView mThreadListView;
    private final ThreadView mThreadView;
    private final WebTextArea mSendTextArea;
    private final WebButton mSendButton;
    private final WebStatusLabel mStatusBarLabel;
    private final MainFrame mMainFrame;
    private TrayIcon mTrayIcon;

    public View(Kontalk model) {
        mModel = model;

        WebLookAndFeel.install();

        ToolTipManager.sharedInstance().setInitialDelay(200);

        mUserListView = new UserListView(this, UserList.getInstance());
        mThreadListView = new ThreadListView(this, ThreadList.getInstance());
        // notify threadlist of changes in user list
        UserList.getInstance().addObserver(mThreadListView);

        mThreadView = new ThreadView();

        // text field
        mSendTextArea = new WebTextArea();
        mSendTextArea.setMargin(5);
        mSendTextArea.setLineWrap(true);
        mSendTextArea.setWrapStyleWord(true);

        // send button
        mSendButton = new WebButton("Send");
        // for showing the hotkey tooltip
        TooltipManager.addTooltip(mSendButton, "Send Message");
        mSendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                View.this.sendText();
            }
        });

        // status bar
        WebStatusBar statusBar = new WebStatusBar();
        mStatusBarLabel = new WebStatusLabel(" ");
        statusBar.add(mStatusBarLabel);

        // main frame
        mMainFrame = new MainFrame(this, mUserListView, mThreadListView,
                mThreadView, mSendTextArea, mSendButton, statusBar);
        mMainFrame.setVisible(true);

        // tray
        this.setTray();

        // hotkeys
        this.setHotkeys();

        // notifier
        MessageList.getInstance().addObserver(new Notifier());

        this.statusChanged();
    }

    final void setTray() {
        if (!KonConf.getInstance().getBoolean(KonConf.MAIN_TRAY)) {
            this.removeTray();
            return;
        }

        if (!SystemTray.isSupported()) {
            LOGGER.info("tray icon not supported");
            return;
        }

        if (mTrayIcon != null)
            // already set
            return;

        // load image
        Image image = getImage("kontalk.png");
        //image = image.getScaledInstance(22, 22, Image.SCALE_SMOOTH);

        // TODO popup menu
        final WebPopupMenu popup = new WebPopupMenu("Kontalk");
        WebMenuItem quitItem = new WebMenuItem("Quit");
        quitItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                View.this.callShutDown();
            }
        });
        popup.add(quitItem);

        // create an action listener to listen for default action executed on the tray icon
        MouseListener listener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                check(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1)
                    mMainFrame.toggleState();
                else
                    check(e);
            }
            private void check(MouseEvent e) {
                if (!e.isPopupTrigger())
                    return;

                // TODO ugly
                popup.setLocation(e.getX() - 20, e.getY() - 40);
                popup.setInvoker(popup);
                popup.setVisible(true);
            }
        };

        mTrayIcon = new TrayIcon(image, "Kontalk" /*, popup*/);
        mTrayIcon.setImageAutoSize(true);
        mTrayIcon.addMouseListener(listener);

        SystemTray tray = SystemTray.getSystemTray();
        try {
            tray.add(mTrayIcon);
        } catch (AWTException ex) {
            LOGGER.log(Level.WARNING, "can't add tray icon", ex);
        }
    }

    void setHotkeys() {
        final boolean enterSends = KonConf.getInstance().getBoolean(KonConf.MAIN_ENTER_SENDS);

        for (KeyListener l : mSendTextArea.getKeyListeners())
            mSendTextArea.removeKeyListener(l);
        mSendTextArea.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (enterSends && e.getKeyCode() == KeyEvent.VK_ENTER &&
                        e.getModifiersEx() == KeyEvent.CTRL_DOWN_MASK) {
                    e.consume();
                    mSendTextArea.append(System.getProperty("line.separator"));
                }
                if (enterSends && e.getKeyCode() == KeyEvent.VK_ENTER &&
                        e.getModifiers() == 0) {
                    // only ignore
                    e.consume();
                }
            }
            @Override
            public void keyReleased(KeyEvent e) {
            }
            @Override
            public void keyTyped(KeyEvent e) {
            }
        });

        mSendButton.removeHotkeys();
        HotkeyData sendHotkey = enterSends ? Hotkey.ENTER : Hotkey.CTRL_ENTER;
        mSendButton.addHotkey(sendHotkey, TooltipWay.up);
    }

    Kontalk.Status getCurrentStatus() {
        return mModel.getCurrentStatus();
    }

    public final void statusChanged() {
        Kontalk.Status status = mModel.getCurrentStatus();
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
                //if (mTrayIcon != null)
                //    trayIcon.setImage(updatedImage);
                break;
            case SHUTTING_DOWN:
                mMainFrame.save();
                mThreadListView.save();
                this.removeTray();
                mMainFrame.setVisible(false);
                mMainFrame.dispose();
                break;
            case FAILED:
                mStatusBarLabel.setText("Connecting failed");
                break;
            }

        mMainFrame.statusChanged(status);
    }

    /**
     * Setup view on startup after model was initialized.
     */
    public void init() {
        mThreadListView.selectLastThread();
    }

    public void handleException(KonException ex) {
        String errorText = getErrorText(ex);
        WebOptionPane.showMessageDialog(mMainFrame, errorText, "Error", WebOptionPane.ERROR_MESSAGE);
    }

    public void handleSecurityErrors(KonMessage message) {
        String errorText = "<html>";

        boolean isOut = message.getDir() == KonMessage.Direction.OUT;
        errorText += isOut ? "Decryption error:" : "Encryption error:";

        for (Coder.Error error : message.getSecurityErrors()) {
            errorText += "<br>";
            switch (error) {
                case UNKNOWN_ERROR:
                    errorText += "Unknown error";
                    break;
                case KEY_UNAVAILABLE:
                    errorText += "Key for receiver not found.";
                    break;
                default:
                    errorText += "Unusual coder error: " + error.toString();
            }
        }

        errorText += "</html>";

        NotificationManager.showNotification(mThreadView, errorText);
    }

    public void showImportWizard() {
        JDialog importFrame = new ImportDialog();
        importFrame.setVisible(true);
    }

    void showConfig() {
        JDialog configFrame = new ConfigurationDialog(mMainFrame, this);
        configFrame.setVisible(true);
    }

    void callShutDown() {
        mModel.shutDown();
    }

    void connect() {
        mModel.connect();
    }

    void disconnect() {
        mModel.disconnect();
    }

    void selectThreadByUser(User user) {
        if (user == null)
            return;

        KonThread thread = ThreadList.getInstance().getThreadByUser(user);
        this.showThread(thread);
    }

    void newThread(Set<User> user) {
        KonThread thread = ThreadList.getInstance().createNewThread(user);
        this.showThread(thread);
    }

    private void showThread(KonThread thread) {
        mThreadListView.selectThread(thread.getID());
        mMainFrame.selectTab(MainFrame.Tab.THREADS);
    }

    void selectedThreadChanged(KonThread thread) {
        if (thread == null)
            return;

        thread.setRead();
        mThreadView.showThread(thread);
    }

    private void sendText() {
       KonThread thread = mThreadListView.getSelectedThread();
       if (thread == null) {
           // TODO
           // nothing selected
           return;
       }
       mModel.sendText(thread, mSendTextArea.getText());
       mSendTextArea.setText("");
    }

    void setUserBlocking(User user, boolean blocking) {
        mModel.setUserBlocking(user, blocking);
    }

    private void removeTray() {
        if (mTrayIcon != null) {
            SystemTray tray = SystemTray.getSystemTray();
            tray.remove(mTrayIcon);
            mTrayIcon = null;
        }
    }

    static Icon getIcon(String fileName) {
        return new ImageIcon(getImage(fileName));
    }

    static Image getImage(String fileName) {
        URL imageUrl = ClassLoader.getSystemResource(RES_PATH + fileName);
        if (imageUrl == null) {
            LOGGER.warning("can't find icon image resource");
            return new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        }
        return Toolkit.getDefaultToolkit().createImage(imageUrl);
    }

    static String getErrorText(KonException ex) {
        String eol = System.getProperty("line.separator");
        String errorText = "Uknown error!?";
        switch(ex.getError()) {
            case IMPORT_ARCHIVE:
                errorText = "Can't open key archive.";
                break;
            case IMPORT_READ_FILE:
                errorText = "Can't load keyfile(s) from archive.";
                break;
            case IMPORT_KEY:
                errorText = "Can't create personal key from key files. ";
                if (ex.getExceptionClass().equals(IOException.class)) {
                    errorText += eol + "Is the public key file valid?";
                }
                if (ex.getExceptionClass().equals(CertificateException.class)) {
                    // bridge
                    errorText += eol + "Are all key files valid?";
                }
                if (ex.getExceptionClass().equals(PGPException.class)) {
                    errorText += eol + "Is the passphrase correct?";
                }
                break;
            case IMPORT_CHANGE_PASSWORD:
                errorText = "Can't change password. Internal error!?";
                break;
            case IMPORT_WRITE_FILE:
                errorText = "Can't write key files to configuration directory.";
                break;
            case RELOAD_READ_FILE:
                errorText = "Can't read key files from configuration directory.";
                errorText += " Please reimport your key.";
                break;
            case RELOAD_KEY:
                errorText = "Can't load key files from configuration directory.";
                errorText += " Please reimport your key.";
                break;
            case CLIENT_CONNECTION:
                errorText = "Can't create connection";
                break;
            case CLIENT_CONNECT:
                errorText = "Can't connect to server.";
                if (ex.getExceptionClass().equals(ConnectionException.class)) {
                    errorText += eol + "Is the server address correct?";
                }
                if (ex.getExceptionClass().equals(SSLHandshakeException.class)) {
                    errorText += eol + "The server rejects the key.";
                }
                break;
            case CLIENT_LOGIN:
                errorText = "Can't login to server.";
                if (ex.getExceptionClass().equals(SASLErrorException.class)) {
                    errorText += eol + "The server rejects the account. Is the "
                            + "specified server correct and the account valid?";
                }
                break;
        }
        return errorText;
    }
}

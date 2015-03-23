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
import com.alee.laf.rootpane.WebDialog;
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
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLHandshakeException;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import org.bouncycastle.openpgp.PGPException;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.ConnectionException;
import org.jivesoftware.smack.sasl.SASLErrorException;
import org.kontalk.Kontalk;
import org.kontalk.system.KonConf;
import org.kontalk.misc.KonException;
import org.kontalk.crypto.Coder;
import org.kontalk.misc.ViewEvent;
import org.kontalk.model.InMessage;
import org.kontalk.model.KonMessage;
import org.kontalk.model.KonThread;
import org.kontalk.model.MessageList;
import org.kontalk.model.ThreadList;
import org.kontalk.model.User;
import org.kontalk.model.UserList;
import org.kontalk.system.ControlCenter;
import org.kontalk.util.Tr;

/**
 * Initialize and control the user interface.
 * TODO leaking 'this' in constructor everywhere
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class View implements Observer {
    private final static Logger LOGGER = Logger.getLogger(View.class.getName());

    final static Color BLUE = new Color(130, 170, 240);
    final static Color LIGHT_BLUE = new Color(220, 220, 250);

    private final ControlCenter mControl;
    private final UserListView mUserListView;
    private final ThreadListView mThreadListView;
    private final ThreadView mThreadView;
    private final WebTextArea mSendTextArea;
    private final WebButton mSendButton;
    private final WebStatusLabel mStatusBarLabel;
    private final MainFrame mMainFrame;
    private TrayIcon mTrayIcon;

    private View(ControlCenter control) {
        mControl = control;

        WebLookAndFeel.install();

        ToolTipManager.sharedInstance().setInitialDelay(200);

        mUserListView = new UserListView(this, UserList.getInstance());
        mThreadListView = new ThreadListView(this, ThreadList.getInstance());
        // notify thread list of changes in user list (name changes)
        UserList.getInstance().addObserver(mThreadListView);

        mThreadView = new ThreadView(this);

        // text field
        mSendTextArea = new WebTextArea();
        mSendTextArea.setMargin(5);
        mSendTextArea.setLineWrap(true);
        mSendTextArea.setWrapStyleWord(true);

        // send button
        mSendButton = new WebButton(Tr.tr("Send"));
        // for showing the hotkey tooltip
        TooltipManager.addTooltip(mSendButton, Tr.tr("Send Message"));
        mSendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                View.this.callSendText();
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
        MessageList.getInstance().addObserver(new Notifier(this));

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

        // popup menu outside of frame, officially not supported
        final WebPopupMenu popup = new WebPopupMenu("Kontalk");
        WebMenuItem quitItem = new WebMenuItem(Tr.tr("Quit"));
        quitItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                View.this.callShutDown();
            }
        });
        popup.add(quitItem);

        // workaround: menu does not disappear when focus is lost
        final WebDialog hiddenDialog = new WebDialog();
        hiddenDialog.setUndecorated(true);

        // create an action listener to listen for default action executed on the tray icon
        MouseListener listener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // menu must be shown on mouse release
                //check(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1)
                    mMainFrame.toggleState();
                else
                    check(e);
            }
            private void check(MouseEvent e) {
//                if (!e.isPopupTrigger())
//                    return;

                hiddenDialog.setVisible(true);

                // TODO ugly code
                popup.setLocation(e.getX() - 20, e.getY() - 40);
                popup.setInvoker(hiddenDialog);
                popup.setCornerWidth(0);
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

    /**
     * Setup view on startup after model was initialized.
     */
    public void init() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                View.this.mThreadListView.selectLastThread();

                if (ThreadList.getInstance().getThreads().isEmpty())
                    mMainFrame.selectTab(MainFrame.Tab.USER);
            }
        });
    }

    ControlCenter.Status getCurrentStatus() {
        return mControl.getCurrentStatus();
    }

    void showConfig() {
        JDialog configFrame = new ConfigurationDialog(mMainFrame, this);
        configFrame.setVisible(true);
    }

    /* control to view */

    @Override
    public void update(Observable o, final Object arg) {
        if (SwingUtilities.isEventDispatchThread()) {
            this.updateOnEDT(arg);
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                View.this.updateOnEDT(arg);
            }
        });
    }

    private void updateOnEDT(Object arg) {
       if (arg instanceof ViewEvent.StatusChanged) {
           this.statusChanged();
       } else if (arg instanceof ViewEvent.MissingAccount) {
           ViewEvent.MissingAccount missAccount = (ViewEvent.MissingAccount) arg;
           this.showImportWizard(missAccount.connect);
       } else if (arg instanceof ViewEvent.Exception) {
           ViewEvent.Exception exception = (ViewEvent.Exception) arg;
           this.handleException(exception.exception);
       } else if (arg instanceof ViewEvent.SecurityError) {
           ViewEvent.SecurityError error = (ViewEvent.SecurityError) arg;
           this.handleSecurityErrors(error.message);
       } else {
           LOGGER.warning("unexpected argument");
       }
    }

    private void statusChanged() {
        ControlCenter.Status status = mControl.getCurrentStatus();
        switch (status) {
            case CONNECTING:
                mStatusBarLabel.setText(Tr.tr("Connecting..."));
                break;
            case CONNECTED:
                mThreadView.setColor(Color.white);
                mStatusBarLabel.setText(Tr.tr("Connected"));
                break;
            case DISCONNECTING:
                mStatusBarLabel.setText(Tr.tr("Disconnecting..."));
                break;
            case DISCONNECTED:
                mThreadView.setColor(Color.lightGray);
                mStatusBarLabel.setText(Tr.tr("Not connected"));
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
                mStatusBarLabel.setText(Tr.tr("Connecting failed"));
                break;
            case ERROR:
                mThreadView.setColor(Color.lightGray);
                mStatusBarLabel.setText(Tr.tr("Connection error"));
                break;
            }

        mMainFrame.statusChanged(status);
    }

    void showImportWizard(boolean connect) {
        JDialog importFrame = new ImportDialog(this, connect);
        importFrame.setVisible(true);
    }

    private void handleException(KonException ex) {
        String errorText = getErrorText(ex);
        WebOptionPane.showMessageDialog(mMainFrame,
                errorText,
                Tr.tr("Error"),
                WebOptionPane.ERROR_MESSAGE);
    }

    private void handleSecurityErrors(KonMessage message) {
        String errorText = "<html>";

        boolean isOut = message.getDir() == KonMessage.Direction.OUT;
        errorText += isOut ? Tr.tr("Encryption error") : Tr.tr("Decryption error");
        errorText += ":";

        for (Coder.Error error : message.getCoderStatus().getErrors()) {
            errorText += "<br>";
            switch (error) {
                case UNKNOWN_ERROR:
                    errorText += Tr.tr("Unknown error");
                    break;
                case KEY_UNAVAILABLE:
                    errorText += Tr.tr("Key for receiver not found.");
                    break;
                default:
                    errorText += Tr.tr("Unusual coder error")+": " + error.toString();
            }
        }

        errorText += "</html>";

        NotificationManager.showNotification(mThreadView, errorText);
    }

    private void removeTray() {
        if (mTrayIcon != null) {
            SystemTray tray = SystemTray.getSystemTray();
            tray.remove(mTrayIcon);
            mTrayIcon = null;
        }
    }

    /* view to control */

    void callShutDown() {
        mControl.shutDown();
    }

    void callConnect() {
        mControl.connect();
    }

    void callDisconnect() {
        mControl.disconnect();
    }

    void callCreateNewThread(Set<User> user) {
        KonThread thread = mControl.createNewThread(user);
        this.showThread(thread);
    }

    private void callSendText() {
       KonThread thread = mThreadListView.getSelectedListValue();
       if (thread == null) {
           // nothing selected
           return;
       }
       mControl.sendText(thread, mSendTextArea.getText());
       mSendTextArea.setText("");
    }

    void callSetUserBlocking(User user, boolean blocking) {
        mControl.sendUserBlocking(user, blocking);
    }

    void callDecrypt(InMessage message) {
        mControl.decryptAndDownload(message);
    }

    /* view internal */

    void selectThreadByUser(User user) {
        if (user == null)
            return;

        KonThread thread = ThreadList.getInstance().getThreadByUser(user);
        this.showThread(thread);
    }

    private void showThread(KonThread thread) {
        mThreadListView.selectItem(thread);
        mMainFrame.selectTab(MainFrame.Tab.THREADS);
    }

    void selectedThreadChanged(KonThread thread) {
        if (thread == null)
            return;

        mThreadView.showThread(thread);
    }

    Optional<KonThread> getCurrentShownThread() {
        return mThreadView.getCurrentThread();
    }

    boolean mainFrameIsFocused() {
        return mMainFrame.isFocused();
    }

    void reloadThreadBG() {
        mThreadView.loadDefaultBG();
    }

    void updateThreadViewSettings(KonThread thread, Color color) {
        mThreadView.updateViewSettings(thread, color);
    }

    static Icon getIcon(String fileName) {
        return new ImageIcon(getImage(fileName));
    }

    static Image getImage(String fileName) {
        URL imageUrl = ClassLoader.getSystemResource(Kontalk.RES_PATH + fileName);
        if (imageUrl == null) {
            LOGGER.warning("can't find icon image resource");
            return new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        }
        return Toolkit.getDefaultToolkit().createImage(imageUrl);
    }

    static String getErrorText(KonException ex) {
        String eol = System.getProperty("line.separator");
        String errorText = Tr.tr("Unknown error!?");
        switch(ex.getError()) {
            case IMPORT_ARCHIVE:
                errorText = Tr.tr("Can't open key archive.");
                break;
            case IMPORT_READ_FILE:
                errorText = Tr.tr("Can't load keyfile(s) from archive.");
                break;
            case IMPORT_KEY:
                errorText = Tr.tr("Can't create personal key from key files.")+" ";
                if (ex.getExceptionClass().equals(IOException.class)) {
                    errorText += eol + Tr.tr("Is the public key file valid?");
                }
                if (ex.getExceptionClass().equals(CertificateException.class)) {
                    // bridge
                    errorText += eol + Tr.tr("Are all key files valid?");
                }
                if (ex.getExceptionClass().equals(PGPException.class)) {
                    errorText += eol + Tr.tr("Is the passphrase correct?");
                }
                break;
            case IMPORT_CHANGE_PASSWORD:
                errorText = Tr.tr("Can't change password. Internal error(!?)");
                break;
            case IMPORT_WRITE_FILE:
                errorText = Tr.tr("Can't write key files to configuration directory.");
                break;
            case RELOAD_READ_FILE:
            case RELOAD_KEY:
                switch (ex.getError()) {
                    case RELOAD_READ_FILE:
                        errorText = Tr.tr("Can't read key files from configuration directory.");
                        break;
                    case RELOAD_KEY:
                        errorText = Tr.tr("Can't load key files from configuration directory.");
                        break;
                }
                errorText += " "+Tr.tr("Please reimport your key.");
                break;
            case CLIENT_CONNECTION:
                errorText = Tr.tr("Can't create connection");
                break;
            case CLIENT_CONNECT:
                errorText = Tr.tr("Can't connect to server.");
                if (ex.getExceptionClass().equals(ConnectionException.class)) {
                    errorText += eol + Tr.tr("Is the server address correct?");
                }
                if (ex.getExceptionClass().equals(SSLHandshakeException.class)) {
                    errorText += eol + Tr.tr("The server rejects the key.");
                }
                if (ex.getExceptionClass().equals(SmackException.NoResponseException.class)) {
                    errorText += eol + Tr.tr("The server does not respond.");
                }
                break;
            case CLIENT_LOGIN:
                errorText = Tr.tr("Can't login to server.");
                if (ex.getExceptionClass().equals(SASLErrorException.class)) {
                    errorText += eol +
                            Tr.tr("The server rejects the account. Is the specified server correct and the account valid?");
                }
                break;
            case CLIENT_ERROR:
                errorText = Tr.tr("Connection to server closed on error.");
                // TODO more details
                break;
        }
        return errorText;
    }

    public static Optional<View> create(final ControlCenter control) {
        Optional<View> optView = invokeAndWait(new Callable<View>() {
            @Override
            public View call() throws Exception {
                return new View(control);
            }
        });
        if(!optView.isPresent()) {
            LOGGER.log(Level.SEVERE, "can't start view");
            return optView;
        }
        control.addObserver(optView.get());
        return optView;
    }

    static <T> Optional<T> invokeAndWait(Callable<T> callable) {
        try {
            FutureTask<T> task = new FutureTask<>(callable);
            SwingUtilities.invokeLater(task);
            // blocking
            return Optional.of(task.get());
        } catch (ExecutionException | InterruptedException ex) {
            LOGGER.log(Level.WARNING, "can't execute task", ex);
        }
        return Optional.empty();
    }

    public static void showWrongJavaVersionDialog() {
        String jVersion = System.getProperty("java.version");
        if (jVersion.length() >= 3)
            jVersion = jVersion.substring(2, 3);
        String errorText = Tr.tr("The installed Java version is too old")+": " + jVersion;
        errorText += System.getProperty("line.separator");
        errorText += Tr.tr("Please install Java 8.");
        WebOptionPane.showMessageDialog(null,
                errorText,
                Tr.tr("Unsupported Java Version"),
                WebOptionPane.ERROR_MESSAGE);
    }
}

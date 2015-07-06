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

import com.alee.extended.filechooser.WebFileChooserField;
import com.alee.extended.filefilter.ImageFilesFilter;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.statusbar.WebStatusBar;
import com.alee.extended.statusbar.WebStatusLabel;
import com.alee.laf.WebLookAndFeel;
import com.alee.laf.button.WebButton;
import com.alee.laf.checkbox.WebCheckBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.text.WebPasswordField;
import com.alee.laf.text.WebTextArea;
import com.alee.laf.text.WebTextField;
import com.alee.managers.hotkey.Hotkey;
import com.alee.managers.hotkey.HotkeyData;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.tooltip.TooltipManager;
import com.alee.managers.tooltip.TooltipWay;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
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
import javax.swing.event.DocumentEvent;

import com.alee.utils.swing.DocumentChangeListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.EventQueue;
import java.util.Arrays;
import javax.swing.event.DocumentListener;
import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.ConnectionException;
import org.jivesoftware.smack.sasl.SASLErrorException;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.Kontalk;
import org.kontalk.system.Config;
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
import org.kontalk.system.Control;
import org.kontalk.util.Tr;

/**
 * Initialize and control the user interface.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class View implements Observer {
    private final static Logger LOGGER = Logger.getLogger(View.class.getName());

    final static Color BLUE = new Color(130, 170, 240);
    final static Color LIGHT_BLUE = new Color(220, 220, 250);
    final static Color GREEN = new Color(83, 196, 46);

    private final Control mControl;
    private final UserListView mUserListView;
    private final ThreadListView mThreadListView;
    private final ThreadView mThreadView;
    private final WebTextArea mSendTextArea;
    private final WebButton mSendButton;
    private final WebStatusLabel mStatusBarLabel;
    private final MainFrame mMainFrame;
    private TrayIcon mTrayIcon;

    private View(Control control) {
        mControl = control;

        WebLookAndFeel.install();

        ToolTipManager.sharedInstance().setInitialDelay(200);

        mUserListView = new UserListView(this, UserList.getInstance());
        UserList.getInstance().addObserver(mUserListView);
        mThreadListView = new ThreadListView(this, ThreadList.getInstance());
        ThreadList.getInstance().addObserver(mThreadListView);

        mThreadView = new ThreadView(this);
        ThreadList.getInstance().addObserver(mThreadView);
        // text field
        mSendTextArea = new WebTextArea();
        mSendTextArea.setMargin(5);
        mSendTextArea.setLineWrap(true);
        mSendTextArea.setWrapStyleWord(true);
        mSendTextArea.getDocument().addDocumentListener(new DocumentChangeListener() {
            @Override
            public void documentChanged(DocumentEvent e) {
                View.this.handleKeyTypeEvent(e.getDocument().getLength() == 0);
            }
        });
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                mSendTextArea.requestFocusInWindow();
            }
        });

        // send button
        mSendButton = new WebButton(Tr.tr("Send"));
        // for showing the hotkey tooltip
        TooltipManager.addTooltip(mSendButton, Tr.tr("Send Message"));
        mSendButton.setEnabled(false);
        mSendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Component focusOwner = mMainFrame.getFocusOwner();
                if (focusOwner != mSendTextArea && focusOwner != mSendButton)
                    return;

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
        if (!Config.getInstance().getBoolean(Config.MAIN_TRAY)) {
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
        final boolean enterSends = Config.getInstance().getBoolean(Config.MAIN_ENTER_SENDS);

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

                if (ThreadList.getInstance().getAll().isEmpty())
                    mMainFrame.selectTab(MainFrame.Tab.USER);
            }
        });
    }

    Control.Status getCurrentStatus() {
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
       } else if (arg instanceof ViewEvent.PasswordSet) {
           this.showPasswordDialog(false);
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
        Control.Status status = mControl.getCurrentStatus();
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

    void showPasswordDialog(boolean wasWrong) {
        WebPanel passPanel = new WebPanel();
        WebLabel passLabel = new WebLabel(Tr.tr("Please enter your key password:"));
        passPanel.add(passLabel, BorderLayout.NORTH);
        final WebPasswordField passField = new WebPasswordField();
        passPanel.add(passField, BorderLayout.CENTER);
        if (wasWrong) {
            WebLabel wrongLabel = new WebLabel(Tr.tr("Wrong password"));
            wrongLabel.setForeground(Color.RED);
            passPanel.add(wrongLabel, BorderLayout.SOUTH);
        }
        WebOptionPane passPane = new WebOptionPane(passPanel,
                WebOptionPane.QUESTION_MESSAGE,
                WebOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = passPane.createDialog(mMainFrame, Tr.tr("Enter password"));
        dialog.setModal(true);
        dialog.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                passField.requestFocusInWindow();
            }
        });
        // blocking
        dialog.setVisible(true);

        Object value = passPane.getValue();
        if (value != null && value.equals(WebOptionPane.OK_OPTION))
            mControl.connect(passField.getPassword());
    }

    void showImportWizard(boolean connect) {
        WebDialog importFrame = new ImportDialog(this, connect);
        importFrame.setVisible(true);
    }

    private void handleException(KonException ex) {
        if (ex.getError() == KonException.Error.LOAD_KEY_DECRYPT) {
            this.showPasswordDialog(true);
            return;
        }
        String errorText = getErrorText(ex);
        Icon icon = NotificationIcon.error.getIcon();
        NotificationManager.showNotification(mThreadView, errorText, icon);
    }

    // TODO more information for message exs
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
                case INVALID_PRIVATE_KEY:
                    errorText += Tr.tr("This message was encrypted with an old or invalid key");
                    break;
                default:
                    errorText += Tr.tr("Unusual coder error")+": " + error.toString();
            }
        }

        errorText += "</html>";

        // TODO too intrusive for user, but use the explanation above for message view
        //NotificationManager.showNotification(mThreadView, errorText);
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

    void callCreateNewUser(String jid, String name, boolean encrypted) {
        mControl.createNewUser(jid, name, encrypted);
    }

    private void callSendText() {
       Optional<KonThread> optThread = mThreadView.getCurrentThread();
       if (!optThread.isPresent())
           // now current thread
           return;

       mControl.sendText(optThread.get(), mSendTextArea.getText());
       mSendTextArea.setText("");
    }

    void callSetUserBlocking(User user, boolean blocking) {
        mControl.sendUserBlocking(user, blocking);
    }

    void callDecrypt(InMessage message) {
        mControl.decryptAndDownload(message);
    }

    void callRequestKey(User user) {
        mControl.sendKeyRequest(user);
    }

    void callSendStatusText() {
        mControl.sendStatusText();
    }

    /* view internal */

    void selectThreadByUser(User user) {
        if (user == null)
            return;

        KonThread thread = ThreadList.getInstance().get(user);
        this.showThread(thread);
    }

    private void showThread(KonThread thread) {
        mThreadListView.setSelectedItem(thread);
        mMainFrame.selectTab(MainFrame.Tab.THREADS);
    }

    void showThread(boolean show) {
        mThreadView.showThread(show ?mThreadListView.getSelectedValue() : null);
    }

    void selectedThreadChanged(KonThread thread) {
        if (thread == null)
            return;

        mThreadView.showThread(thread);
    }

    private void handleKeyTypeEvent(boolean empty) {
        this.checkSendButtonStatus();

        Optional<KonThread> optThread = mThreadView.getCurrentThread();
        if (!optThread.isPresent())
            return;

        // workaround: clearing the text area is not a key event
        if (!empty)
            mControl.handleOwnChatStateEvent(optThread.get(), ChatState.composing);
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

    void checkSendButtonStatus() {
        mSendButton.setEnabled(mThreadView.getCurrentThread().isPresent() &&
                !mSendTextArea.getText().trim().isEmpty());
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

    static WebFileChooserField createImageChooser(boolean enabled, String path) {
        WebFileChooserField chooser = new WebFileChooserField();
        chooser.setEnabled(enabled);
        chooser.getChooseButton().setEnabled(enabled);
        if (!path.isEmpty())
            chooser.setSelectedFile(new File(path));
        chooser.setMultiSelectionEnabled(false);
        chooser.setShowRemoveButton(true);
        chooser.getWebFileChooser().setFileFilter(new ImageFilesFilter());
        File file = new File(path);
        if (file.exists()) {
            chooser.setSelectedFile(file);
        }
        if (file.getParentFile() != null && file.getParentFile().exists())
            chooser.getWebFileChooser().setCurrentDirectory(file.getParentFile());
        return chooser;
    }

    static WebTextField createTextField(final String text) {
        final WebTextField field = new WebTextField(text, false);
        field.setEditable(false);
        field.setBackground(null);
        field.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                check(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                check(e);
            }
            private void check(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    WebPopupMenu popupMenu = new WebPopupMenu();
                    popupMenu.add(View.createCopyMenuItem(field.getText(), ""));
                    popupMenu.show(field, e.getX(), e.getY());
                }
            }
        });
        return field;
    }

    static WebMenuItem createCopyMenuItem(final String copyText, String toolTipText) {
        WebMenuItem item = new WebMenuItem(Tr.tr("Copy"));
        if (!toolTipText.isEmpty())
            item.setToolTipText(toolTipText);
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
                clip.setContents(new StringSelection(copyText), null);
            }
        });
        return item;
    }

    static String getErrorText(KonException ex) {
        String eol = " " + System.getProperty("line.separator");
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
                break;
            case CHANGE_PASS:
                errorText = Tr.tr("Can't change password. Internal error(!?)");
                break;
            case WRITE_FILE:
                errorText = Tr.tr("Can't write key files to configuration directory.");
                break;
            case READ_FILE:
            case LOAD_KEY:
                switch (ex.getError()) {
                    case READ_FILE:
                        errorText = Tr.tr("Can't read key files from configuration directory.");
                        break;
                    case LOAD_KEY:
                        errorText = Tr.tr("Can't load key files from configuration directory.");
                        break;
                }
                errorText += " "+Tr.tr("Please reimport your key.");
                break;
            case LOAD_KEY_DECRYPT:
                errorText = Tr.tr("Can't decrypt key. Is the passphrase correct?");
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

    static String shortenJID(String jid, int maxLength) {
        if (jid.length() > maxLength) {
            String local = XmppStringUtils.parseLocalpart(jid);
            local = StringUtils.abbreviate(local, (int)(maxLength * 0.4));
            String domain = XmppStringUtils.parseDomain(jid);
            domain = StringUtils.abbreviate(domain, (int)(maxLength * 0.6));
            jid = XmppStringUtils.completeJidFrom(local, domain);
        }
        return jid;
    }

    static String shortenUserName(String jid, int maxLength) {
        String local = XmppStringUtils.parseLocalpart(jid);
        local = StringUtils.abbreviate(local, maxLength);
        String domain = XmppStringUtils.parseDomain(jid);
        return XmppStringUtils.completeJidFrom(local, domain);
    }

    public static Optional<View> create(final Control control) {
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

    static abstract class PassPanel extends WebPanel {

        private final boolean mPassSet;
        private final WebCheckBox mSetPass;
        private final WebPasswordField mOldPassField;
        private final WebLabel mWrongPassLabel;
        private final WebPasswordField mNewPassField;
        private final WebPasswordField mConfirmPassField;

        PassPanel(boolean passSet) {
            mPassSet = passSet;

            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            DocumentListener docListener = new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    PassPanel.this.checkDoneButton();
                }
                @Override
                public void removeUpdate(DocumentEvent e) {
                    PassPanel.this.checkDoneButton();
                }
                @Override
                public void changedUpdate(DocumentEvent e) {
                    PassPanel.this.checkDoneButton();
                }
            };

            mOldPassField = new WebPasswordField(30);
            mWrongPassLabel = new WebLabel(Tr.tr("Wrong password"));
            if (mPassSet) {
                groupPanel.add(new WebLabel(Tr.tr("Current password:")));
                mOldPassField.getDocument().addDocumentListener(docListener);
                groupPanel.add(mOldPassField);
                mWrongPassLabel.setBoldFont();
                mWrongPassLabel.setForeground(Color.RED);
                mWrongPassLabel.setVisible(false);
                groupPanel.add(mWrongPassLabel);
                groupPanel.add(new WebSeparator());
            }

            mSetPass = new WebCheckBox(Tr.tr("Set key password"));
            String setPassText = Tr.tr("If not set, key is saved unprotected!");
            TooltipManager.addTooltip(mSetPass, setPassText);
            groupPanel.add(new GroupPanel(mSetPass, new WebSeparator()));
            mSetPass.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    boolean selected = e.getStateChange() == ItemEvent.SELECTED;
                    mNewPassField.setEnabled(selected);
                    mConfirmPassField.setEnabled(selected);
                    PassPanel.this.checkDoneButton();
                }
            });
            mNewPassField = new WebPasswordField(30);
            mNewPassField.setInputPrompt(Tr.tr("Enter new password"));
            mNewPassField.setEnabled(false);
            mNewPassField.setHideInputPromptOnFocus(false);
            mNewPassField.getDocument().addDocumentListener(docListener);
            groupPanel.add(mNewPassField);
            mConfirmPassField = new WebPasswordField(30);
            mConfirmPassField.setInputPrompt(Tr.tr("Confirm password"));
            mConfirmPassField.setEnabled(false);
            mConfirmPassField.setHideInputPromptOnFocus(false);
            mConfirmPassField.getDocument().addDocumentListener(docListener);
            groupPanel.add(mConfirmPassField);

            this.checkDoneButton();

            this.add(groupPanel);
        }

        private void checkDoneButton() {
            if (mPassSet && mOldPassField.getPassword().length < 1) {
                this.onInvalidInput();
                return;
            }
            if (!mSetPass.isSelected()) {
                this.onValidInput();
                return;
            }
            char[] newPass = mNewPassField.getPassword();
            if (newPass.length > 0 &&
                    Arrays.equals(newPass, mConfirmPassField.getPassword())) {
                this.onValidInput();
            } else {
                this.onInvalidInput();
            }
        }

        char[] getOldPassword() {
            return mOldPassField.getPassword();
        }

        Optional<char[]> getNewPassword() {
            if (!mSetPass.isSelected())
                return Optional.of(new char[0]);

            char[] newPass = mNewPassField.getPassword();
            // better check again
            if (!Arrays.equals(newPass, mConfirmPassField.getPassword()))
                Optional.empty();

            return Optional.of(newPass);
        }

        void showWrongPassword() {
            mWrongPassLabel.setVisible(true);
        }

        abstract void onValidInput();

        abstract void onInvalidInput();
    }
}

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

import com.alee.extended.panel.GroupPanel;
import com.alee.extended.statusbar.WebStatusBar;
import com.alee.extended.statusbar.WebStatusLabel;
import com.alee.laf.WebLookAndFeel;
import com.alee.laf.button.WebButton;
import com.alee.laf.label.WebLabel;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.text.WebPasswordField;
import com.alee.laf.text.WebTextArea;
import com.alee.managers.hotkey.Hotkey;
import com.alee.managers.hotkey.HotkeyData;
import com.alee.managers.language.data.TooltipWay;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationListener;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.NotificationOption;
import com.alee.managers.notification.WebNotificationPopup;
import com.alee.managers.tooltip.TooltipManager;
import java.awt.Color;
import java.awt.event.*;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;

import com.alee.utils.swing.DocumentChangeListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.EventQueue;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.kontalk.system.Config;
import org.kontalk.misc.KonException;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PGPUtils;
import org.kontalk.misc.ViewEvent;
import org.kontalk.model.KonMessage;
import org.kontalk.model.KonThread;
import org.kontalk.model.ThreadList;
import org.kontalk.model.Contact;
import org.kontalk.model.ContactList;
import org.kontalk.system.Control;
import org.kontalk.system.Control.ViewControl;
import org.kontalk.util.Tr;

/**
 * Initialize and control the user interface.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class View implements Observer {
    private static final Logger LOGGER = Logger.getLogger(View.class.getName());

    static final int GAP_DEFAULT = 10;
    static final int GAP_BIG = 15;
    static final int GAP_SMALL = 5;
    static final int MARGIN_DEFAULT = 10;
    static final int MARGIN_BIG = 15;
    static final int MARGIN_SMALL = 5;

    static final Color BLUE = new Color(130, 170, 240);
    static final Color LIGHT_BLUE = new Color(220, 220, 250);
    static final Color LIGHT_GREY = new Color(240, 240, 240);
    static final Color GREEN = new Color(83, 196, 46);

    private final ViewControl mControl;
    private final TrayManager mTrayManager;

    private final Notifier mNotifier;

    private final SearchPanel mSearchPanel;
    private final UserListView mUserListView;
    private final ThreadListView mThreadListView;
    private final Content mContent;
    private final ThreadView mThreadView;
    private final WebTextArea mSendTextArea;
    private final WebButton mSendButton;
    private final WebStatusLabel mStatusBarLabel;
    private final MainFrame mMainFrame;

    private View(ViewControl control) {
        mControl = control;

        WebLookAndFeel.install();

        ToolTipManager.sharedInstance().setInitialDelay(200);

        mUserListView = new UserListView(this, ContactList.getInstance());
        ContactList.getInstance().addObserver(mUserListView);
        mThreadListView = new ThreadListView(this, ThreadList.getInstance());
        ThreadList.getInstance().addObserver(mThreadListView);

        // text area
        mSendTextArea = new WebTextArea();
        mSendTextArea.setMargin(View.MARGIN_SMALL);
        mSendTextArea.setLineWrap(true);
        mSendTextArea.setWrapStyleWord(true);
        mSendTextArea.setFontSize(13);
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
        mSendButton.setMargin(MARGIN_SMALL);
        mSendButton.setFontStyle(true, false);
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

        // thread view
        mThreadView = new ThreadView(this, mSendTextArea, mSendButton);
        ThreadList.getInstance().addObserver(mThreadView);

        // content area
        mContent = new Content(this, mThreadView);

        // search panel
        mSearchPanel = new SearchPanel(
                new Table[]{mUserListView, mThreadListView},
                mThreadView);

        // status bar
        WebStatusBar statusBar = new WebStatusBar();
        mStatusBarLabel = new WebStatusLabel(" ");
        statusBar.add(mStatusBarLabel);

        // main frame
        mMainFrame = new MainFrame(this, mUserListView, mThreadListView,
                mContent, mSearchPanel, statusBar);
        mMainFrame.setVisible(true);

        // tray
        mTrayManager = new TrayManager(this, mMainFrame);
        ThreadList.getInstance().addObserver(mTrayManager);

        // hotkeys
        this.setHotkeys();

        // notifier
        mNotifier = new Notifier(this);

        this.statusChanged();
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
           this.showException(exception.exception);
       } else if (arg instanceof ViewEvent.SecurityError) {
           ViewEvent.SecurityError error = (ViewEvent.SecurityError) arg;
           this.showSecurityErrors(error.message);
       } else if (arg instanceof ViewEvent.NewMessage) {
           ViewEvent.NewMessage newMessage = (ViewEvent.NewMessage) arg;
           mNotifier.onNewMessage(newMessage.message);
       } else if (arg instanceof ViewEvent.NewKey) {
           ViewEvent.NewKey newKey = (ViewEvent.NewKey) arg;
           this.confirmNewKey(newKey.user, newKey.key);
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
                mThreadView.setColor(Color.WHITE);
                mStatusBarLabel.setText(Tr.tr("Connected"));
                NotificationManager.hideAllNotifications();
                break;
            case DISCONNECTING:
                mStatusBarLabel.setText(Tr.tr("Disconnecting..."));
                break;
            case DISCONNECTED:
                mThreadView.setColor(Color.LIGHT_GRAY);
                mStatusBarLabel.setText(Tr.tr("Not connected"));
                //if (mTrayIcon != null)
                //    trayIcon.setImage(updatedImage);
                break;
            case SHUTTING_DOWN:
                mMainFrame.save();
                mThreadListView.save();
                mTrayManager.removeTray();
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

        mMainFrame.onStatusChanged(status);
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

    private void showException(KonException ex) {
        if (ex.getError() == KonException.Error.LOAD_KEY_DECRYPT) {
            this.showPasswordDialog(true);
            return;
        }
        String errorText = Utils.getErrorText(ex);
        Icon icon = NotificationIcon.error.getIcon();
        NotificationManager.showNotification(mContent, errorText, icon);
    }

    // TODO more information for message exs
    private void showSecurityErrors(KonMessage message) {
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

    private void confirmNewKey(final Contact user, final PGPUtils.PGPCoderKey key) {
        WebPanel panel = new GroupPanel(GAP_DEFAULT, false);
        panel.setOpaque(false);

        panel.add(new WebLabel(Tr.tr("Received new key for Contact")).setBoldFont());
        panel.add(new WebSeparator(true, true));

        panel.add(new WebLabel(Tr.tr("Contact:")));
        String userText = Utils.name(user) + " " + Utils.jid(user.getJID(), 30, true);
        panel.add(new WebLabel(userText).setBoldFont());

        panel.add(new WebLabel(Tr.tr("Key fingerprint:")));
        WebTextArea fpArea = Utils.createFingerprintArea();
        fpArea.setText(Utils.fingerprint(key.fingerprint));
        panel.add(fpArea);

        String expl = Tr.tr("When declining the key further communication to and from this contact will be blocked.");
        WebTextArea explArea = new WebTextArea(expl, 3, 30);
        explArea.setEditable(false);
        explArea.setLineWrap(true);
        explArea.setWrapStyleWord(true);
        panel.add(explArea);

        WebNotificationPopup popup = NotificationManager.showNotification(panel,
                NotificationOption.accept, NotificationOption.decline,
                NotificationOption.cancel);
        popup.setClickToClose(false);
        popup.addNotificationListener(new NotificationListener() {
            @Override
            public void optionSelected(NotificationOption option) {
                switch (option) {
                    case accept :
                        mControl.acceptKey(user, key);
                        break;
                    case decline :
                        mControl.declineKey(user);
                }
            }
            @Override
            public void accepted() {}
            @Override
            public void closed() {}
        });
    }

    /* view to control */

    ViewControl getControl() {
        return mControl;
    }

    void callShutDown() {
        // trigger save if user details are shown
        mContent.showNothing();
        mControl.shutDown();
    }

    void callCreateNewThread(Set<Contact> user) {
        KonThread thread = mControl.createNewThread(user);
        this.selectThread(thread);
    }

    private void callSendText() {
       Optional<KonThread> optThread = mContent.getCurrentThread();
       if (!optThread.isPresent())
           // now current thread
           return;

       mControl.sendText(optThread.get(), mSendTextArea.getText());
       mSendTextArea.setText("");
    }

    /* view internal */

    void showThread(Contact user) {
        KonThread thread = ThreadList.getInstance().get(user);
        this.selectThread(thread);
    }

    private void selectThread(KonThread thread) {
        mMainFrame.selectTab(MainFrame.Tab.THREADS);
        mThreadListView.setSelectedItem(thread);
    }

    void showUserDetails(Contact user) {
        mContent.showUser(user);
    }

    void showThread(KonThread thread) {
        if (mMainFrame.getCurrentTab() != MainFrame.Tab.THREADS)
            return;
        mContent.showThread(thread);
    }

    void clearSearch() {
        mSearchPanel.clear();
    }

    void tabPaneChanged(MainFrame.Tab tab) {
        if (tab == MainFrame.Tab.THREADS) {
            Optional<KonThread> optThread = mThreadListView.getSelectedValue();
            if (optThread.isPresent()) {
                mContent.showThread(optThread.get());
                return;
            }
        } else {
            Optional<Contact> optUser = mUserListView.getSelectedValue();
            if (optUser.isPresent()) {
                mContent.showUser(optUser.get());
                return;
            }
        }
        mContent.showNothing();
    }

    private void handleKeyTypeEvent(boolean empty) {
        mSendButton.setEnabled(!mSendTextArea.getText().trim().isEmpty());

        Optional<KonThread> optThread = mContent.getCurrentThread();
        if (!optThread.isPresent())
            return;

        // workaround: clearing the text area is not a key event
        if (!empty)
            mControl.handleOwnChatStateEvent(optThread.get(), ChatState.composing);
    }

    Optional<KonThread> getCurrentShownThread() {
        return mContent.getCurrentThread();
    }

    boolean mainFrameIsFocused() {
        return mMainFrame.isFocused();
    }

    void reloadThreadBG() {
        mThreadView.loadDefaultBG();
    }

    void updateTray() {
        mTrayManager.setTray();
    }

    public static Optional<View> create(final ViewControl control) {
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

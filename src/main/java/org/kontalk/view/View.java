/*
 *  Kontalk Java client
 *  Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>
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

import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.EnumSet;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.alee.extended.statusbar.WebStatusBar;
import com.alee.extended.statusbar.WebStatusLabel;
import com.alee.laf.WebLookAndFeel;
import com.alee.laf.label.WebLabel;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.text.WebPasswordField;
import org.kontalk.client.FeatureDiscovery;
import org.kontalk.misc.JID;
import org.kontalk.misc.ViewEvent;
import org.kontalk.model.Contact;
import org.kontalk.model.Model;
import org.kontalk.model.chat.Chat;
import org.kontalk.persistence.Config;
import org.kontalk.system.Control;
import org.kontalk.system.Control.ViewControl;
import org.kontalk.util.EncodingUtils;
import org.kontalk.util.Tr;

/**
 * Initialize and control the user interface.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class View implements Observer {
    private static final Logger LOGGER = Logger.getLogger(View.class.getName());

    static final String KONTALK_SITE = "https://www.kontalk.org";
    static final String KONTALK_RELEASES = "https://github.com/kontalk/desktopclient-java/releases";

    static final int LISTS_WIDTH = 300;

    static final int GAP_DEFAULT = 10;
    static final int GAP_BIG = 15;
    static final int GAP_SMALL = 5;
    static final int MARGIN_DEFAULT = 10;
    static final int MARGIN_BIG = 15;
    static final int MARGIN_SMALL = 5;
    static final int MARGIN_TINY = 2;

    static final int ROUND = 5;

    static final int FONT_SIZE_TINY = 11;
    static final int FONT_SIZE_SMALL = 12;
    static final int FONT_SIZE_NORMAL = 13;
    static final int FONT_SIZE_BIG = 14;
    static final int FONT_SIZE_HUGE = 16;

    static final int MAX_SUBJ_LENGTH = 30;
    static final int MAX_NAME_LENGTH = 60;
    static final int MAX_NAME_IN_LIST_LENGTH = 18;
    static final int MAX_NAME_IN_GROUP_LENGTH = 25;
    static final int MAX_NAME_IN_FROM_LABEL = 40;
    static final int MAX_NAME_IN_NOTIER = 20;
    static final int MAX_JID_LENGTH = 100;
    static final int MAX_JID_IN_NOTIFIER = 30;
    static final int MAX_USER_ID_LENGTH = 30;
    static final int MAX_XMPP_ID_LENGTH = 30;

    static final int PRETTY_JID_LENGTH = 28;

    static final Color BLUE = new Color(130, 170, 240);
    static final Color LIGHT_BLUE = new Color(220, 230, 250);
    static final Color LIGHT_GREY = new Color(240, 240, 240);
    //static final Color GREEN = new Color(83, 196, 46);
    static final Color LIGHT_GREEN = new Color(220, 250, 220);
    static final Color DARK_GREEN = new Color(0, 100, 0);
    static final Color DARK_RED = new Color(196, 46, 46);

    static final int CHAT_BG_ALPHA = 30;

    static final int AVATAR_LIST_SIZE = 30;
    static final int AVATAR_CHAT_SIZE = 40;
    static final int AVATAR_DETAIL_SIZE = 60;
    static final int AVATAR_PROFILE_SIZE = 150;

    private final ViewControl mControl;
    private final Model mModel;

    private final TrayManager mTrayManager;
    private final Notifier mNotifier;

    private final ContactListView mContactListView;
    private final ChatListView mChatListView;
    private final Content mContent;
    private final ChatView mChatView;
    private final WebStatusLabel mStatusBarLabel;
    private final MainFrame mMainFrame;

    final String tr_remove_contact = Tr.tr("Chats and messages will not be deleted.");

    private Control.Status mCurrentStatus;
    private EnumSet<FeatureDiscovery.Feature> mServerFeatures;

    private View(ViewControl control, Model model) {
        mControl = control;
        mModel = model;

        WebLookAndFeel.install();
        ToolTipManager.sharedInstance().setInitialDelay(200);

        // chat view
        mChatView = new ChatView(this);
        // content area
        mContent = new Content(this, mChatView);

        mContactListView = new ContactListView(this, mModel);
        mChatListView = new ChatListView(this, mModel.chats());

        // search panel
        SearchPanel searchPanel = new SearchPanel(
                new ListView[]{mContactListView, mChatListView},
                mChatView);
        // status bar
        WebStatusBar statusBar = new WebStatusBar();
        mStatusBarLabel = new WebStatusLabel(" ");
        statusBar.add(mStatusBarLabel);
        // main frame
        mMainFrame = new MainFrame(this, mModel, mContactListView, mChatListView,
                mContent, searchPanel, statusBar);
        mMainFrame.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                mChatView.getCurrentChat().ifPresent(Chat::setRead);
            }
        });

        // tray
        mTrayManager = new TrayManager(this, mModel, mMainFrame);
        // notifier
        mNotifier = new Notifier(this, mMainFrame);

        // register observer
        mModel.contacts().addObserver(mContactListView);
        mModel.chats().addObserver(mChatListView);
        mModel.chats().addObserver(mChatView);
        mModel.chats().addObserver(mTrayManager);

        this.setHotkeys();

        this.statusChanged(Control.Status.DISCONNECTED, EnumSet.noneOf(FeatureDiscovery.Feature.class));

        mMainFrame.setVisible(true);
    }

    public static Optional<View> create(ViewControl control, Model model) {
        View view;
        try {
            view = invokeAndWait(new Callable<View>() {
                @Override
                public View call() throws Exception {
                    return new View(control, model);
                }
            });
        } catch (ExecutionException | InterruptedException ex) {
            LOGGER.log(Level.WARNING, "can't start view", ex);
            return Optional.empty();
        }
        control.addObserver(view);
        return Optional.of(view);
    }

    void setHotkeys() {
        boolean enterSends = Config.getInstance().getBoolean(Config.MAIN_ENTER_SENDS);
        mChatView.setHotkeys(enterSends);
    }

    /**
     * Setup view on startup after model was initialized.
     */
    public void init() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                View.this.mChatListView.selectLastChat();

                if (mModel.chats().isEmpty())
                    mMainFrame.selectTab(MainFrame.Tab.CONTACT);
            }
        });
    }

    Control.Status currentStatus() {
        return mCurrentStatus;
    }

    EnumSet<FeatureDiscovery.Feature> serverFeatures() {
        return mServerFeatures;
    }

    void showConfig() {
        JDialog configFrame = new ConfigurationDialog(mMainFrame, this, mModel);
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
        if (arg instanceof ViewEvent.StatusChange) {
            ViewEvent.StatusChange statChange = (ViewEvent.StatusChange) arg;
            this.statusChanged(statChange.status, statChange.features);
        } else if (arg instanceof ViewEvent.PasswordSet) {
            this.showPasswordDialog(false);
        } else if (arg instanceof ViewEvent.MissingAccount) {
            ViewEvent.MissingAccount missAccount = (ViewEvent.MissingAccount) arg;
            this.showImportWizard(missAccount.connect);
        } else if (arg instanceof ViewEvent.Exception) {
            ViewEvent.Exception exception = (ViewEvent.Exception) arg;
            mNotifier.showException(exception.exception);
        } else if (arg instanceof ViewEvent.SecurityError) {
            ViewEvent.SecurityError error = (ViewEvent.SecurityError) arg;
            mNotifier.showSecurityErrors(error.message);
        } else if (arg instanceof ViewEvent.NewMessage) {
            ViewEvent.NewMessage newMessage = (ViewEvent.NewMessage) arg;
            mNotifier.onNewMessage(newMessage.message);
        } else if (arg instanceof ViewEvent.NewKey) {
            ViewEvent.NewKey newKey = (ViewEvent.NewKey) arg;
            if (!newKey.contact.hasKey())
                // TODO webkey, disabling for now
                return;
            mNotifier.confirmNewKey(newKey.contact, newKey.key);
        } else if (arg instanceof ViewEvent.ContactDeleted) {
            ViewEvent.ContactDeleted contactDeleted = (ViewEvent.ContactDeleted) arg;
            mNotifier.confirmContactDeletion(contactDeleted.contact);
        } else if (arg instanceof ViewEvent.PresenceError) {
            ViewEvent.PresenceError presenceError = (ViewEvent.PresenceError) arg;
            mNotifier.showPresenceError(presenceError.contact, presenceError.error);
        } else if (arg instanceof ViewEvent.SubscriptionRequest) {
            mNotifier.confirmSubscription((ViewEvent.SubscriptionRequest) arg);
        } else if (arg instanceof ViewEvent.RetryTimerMessage) {
            mStatusBarLabel.setText(
                    String.format(Tr.tr("Connection failure. Retry in %1$d seconds."),
                    ((ViewEvent.RetryTimerMessage) arg).countdown));
        } else {
            LOGGER.warning("unexpected argument: "+arg);
        }
    }

    private void statusChanged(Control.Status status, EnumSet<FeatureDiscovery.Feature> features) {
        mCurrentStatus = status;
        mServerFeatures = features;

        mChatView.onStatusChange(status, features);
        mMainFrame.onStatusChanged(status);

        switch (status) {
            case CONNECTING:
                mStatusBarLabel.setText(Tr.tr("Connecting…"));
                mNotifier.hideNotifications();
                break;
            case CONNECTED:
                mStatusBarLabel.setText(Tr.tr("Connected"));
                break;
            case DISCONNECTING:
                mStatusBarLabel.setText(Tr.tr("Disconnecting…"));
                break;
            case DISCONNECTED:
                mStatusBarLabel.setText(Tr.tr("Not connected"));
                //if (mTrayIcon != null)
                //    trayIcon.setImage(updatedImage);
                break;
            case SHUTTING_DOWN:
                mMainFrame.save();
                mChatListView.save();
                mTrayManager.removeTray();
                mMainFrame.setVisible(false);
                mMainFrame.dispose();
                break;
            case FAILED:
                mStatusBarLabel.setText(Tr.tr("Connecting failed"));
                break;
            case ERROR:
                mStatusBarLabel.setText(Tr.tr("Connection error"));
                break;
        }
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
        LOGGER.info("asking for password…");
        dialog.setVisible(true);

        Object value = passPane.getValue();
        if (value != null && value.equals(WebOptionPane.OK_OPTION))
            mControl.connect(passField.getPassword());
    }

    void showImportWizard(boolean connect) {
        WebDialog importFrame = new ImportDialog(this, connect);
        importFrame.setVisible(true);
    }

    /* view to control */

    ViewControl getControl() {
        return mControl;
    }

    void callShutDown() {
        // trigger save if contact details are shown
        mContent.showNothing();
        mControl.shutDown();
    }

    /* view internal */

    void showChat(Contact contact) {
        this.showChat(mControl.getOrCreateSingleChat(contact));
    }

    void showChat(Chat chat) {
        // show by selecting it
        mMainFrame.selectTab(MainFrame.Tab.CHATS);
        mChatListView.setSelectedItem(chat);
    }

    void onChatSelectionChanged(Optional<Chat> optChat) {
        if (mMainFrame.getCurrentTab() != MainFrame.Tab.CHATS)
            return;

        if (optChat.isPresent())
            mContent.showChat(optChat.get());
        else
            mContent.showNothing();
    }

    void onContactSelectionChanged(Optional<Contact> optContact) {
        Contact contact = optContact.orElse(null);
        if (contact == null || contact.isDeleted()) {
            mContent.showNothing();
            return;
        }

        mContent.showContact(contact);
    }

    void requestRenameFocus(Contact contact) {
        if (contact.isDeleted())
            return;

        this.showContactDetails(contact);
        mContent.requestRenameFocus();
    }

    void showContactDetails(Contact contact) {
        // show by selecting in contact list
        mMainFrame.selectTab(MainFrame.Tab.CONTACT);
        mContactListView.setSelectedItem(contact);
    }

    void tabPaneChanged(MainFrame.Tab tab) {
        if (tab == MainFrame.Tab.CHATS) {
            Chat chat = mChatListView.getSelectedValue().orElse(null);
            if (chat != null) {
                mContent.showChat(chat);
                return;
            }
        } else {
            Contact contact = mContactListView.getSelectedValue().orElse(null);
            if (contact != null) {
                mContent.showContact(contact);
                return;
            }
        }
        mContent.showNothing();
    }

    boolean chatIsVisible(Chat chat) {
        return mChatView.getCurrentChat().orElse(null) == chat && mMainFrame.isFocused();
    }

    void reloadChatBG() {
        mChatView.loadDefaultBG();
    }

    void updateContactList() {
        mContactListView.updateOnEDT(null);
    }

    void updateTray() {
        mTrayManager.setTray();
    }

    void updateMessageLists() {
        mChatView.updateMessageLists();
    }

    // TODO is this good?
    String names(List<JID> jids) {
        return Utils.displayNames(jids, mModel.contacts(), View.PRETTY_JID_LENGTH);
    }

    /* static */

    private static <T> T invokeAndWait(Callable<T> callable)
            throws InterruptedException, ExecutionException {
        FutureTask<T> task = new FutureTask<>(callable);
        SwingUtilities.invokeLater(task);
        // blocking
        return task.get();
    }

    public static void showWrongJavaVersionDialog() {
        String jVersion = System.getProperty("java.version");
        if (jVersion.length() >= 3)
            jVersion = jVersion.substring(2, 3);
        String errorText = Tr.tr("The installed Java version is too old")+": " + jVersion;
        errorText += EncodingUtils.EOL;
        errorText += Tr.tr("Please install Java 8.");
        WebOptionPane.showMessageDialog(null,
                errorText,
                Tr.tr("Unsupported Java Version"),
                WebOptionPane.ERROR_MESSAGE);
    }
}

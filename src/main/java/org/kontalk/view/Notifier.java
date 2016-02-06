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
import com.alee.global.StyleConstants;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.text.WebTextArea;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationListener;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.NotificationOption;
import com.alee.managers.notification.WebNotificationPopup;
import com.alee.managers.popup.PopupStyle;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import javax.swing.Icon;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PGPUtils;
import org.kontalk.misc.KonException;
import org.kontalk.misc.ViewEvent;
import org.kontalk.model.Contact;
import org.kontalk.model.message.InMessage;
import org.kontalk.model.message.KonMessage;
import org.kontalk.system.RosterHandler;
import org.kontalk.util.MediaUtils;
import org.kontalk.util.Tr;
import static org.kontalk.view.View.GAP_DEFAULT;

/**
 * Inform user about events.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class Notifier {

    private static final Icon NOTIFICATION_ICON = Utils.getIcon("ic_msg_pending.png");

    private final View mView;

    Notifier(View view) {
        mView = view;
    }

    void onNewMessage(InMessage newMessage) {
        if (newMessage.getChat() == mView.getCurrentShownChat().orElse(null) &&
                mView.mainFrameIsFocused())
            return;
        MediaUtils.playSound(MediaUtils.Sound.NOTIFICATION);
    }

    void showException(KonException ex) {
        if (ex.getError() == KonException.Error.LOAD_KEY_DECRYPT) {
            mView.showPasswordDialog(true);
            return;
        }
        Icon icon = NotificationIcon.error.getIcon();
        NotificationManager.showNotification(textArea(Utils.getErrorText(ex)), icon);
    }

    // TODO more information for message exs
    void showSecurityErrors(KonMessage message) {
        String errorText = "<html>";

        boolean isOut = !message.isInMessage();
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
        //NotificationManager.showNotification(mChatView, errorText);
    }

    void showPresenceError(Contact contact, RosterHandler.Error error) {
        WebPanel panel = panel(Tr.tr("Contact error"), contact);

        panel.add(new WebLabel(Tr.tr("Error:")).setBoldFont());
        String errorText = Tr.tr(error.toString());
        switch (error) {
            case SERVER_NOT_FOUND:
                errorText = Tr.tr("Server not found");
                break;
        }

        panel.add(textArea(errorText));

        NotificationManager.showNotification(panel, NotificationOption.cancel);
    }

    void confirmNewKey(final Contact contact, final PGPUtils.PGPCoderKey key) {
        WebPanel panel = panel(Tr.tr("Received new key for contact"), contact);

        panel.add(new WebLabel(Tr.tr("Key fingerprint:")));
        WebTextArea fpArea = Utils.createFingerprintArea();
        fpArea.setText(Utils.fingerprint(key.fingerprint));
        panel.add(fpArea);

        String expl = Tr.tr("When declining the key further communication to and from this contact will be blocked.");
        panel.add(textArea(expl));

        WebNotificationPopup popup = NotificationManager.showNotification(panel,
                NotificationOption.accept, NotificationOption.decline,
                NotificationOption.cancel);
        popup.setClickToClose(false);
        popup.addNotificationListener(new NotificationListener() {
            @Override
            public void optionSelected(NotificationOption option) {
                switch (option) {
                    case accept :
                        mView.getControl().acceptKey(contact, key);
                        break;
                    case decline :
                        mView.getControl().declineKey(contact);
                }
            }
            @Override
            public void accepted() {}
            @Override
            public void closed() {}
        });
    }

    void confirmContactDeletion(final Contact contact) {
        WebPanel panel = panel(Tr.tr("Contact was deleted on server"), contact);

        String expl = Tr.tr("Remove this contact from your contact list?") + "\n" +
                View.REMOVE_CONTACT_NOTE;
        panel.add(textArea(expl));

        WebNotificationPopup popup = NotificationManager.showNotification(panel,
                NotificationOption.yes, NotificationOption.no,
                NotificationOption.cancel);
        popup.setClickToClose(false);
        popup.addNotificationListener(new NotificationListener() {
            @Override
            public void optionSelected(NotificationOption option) {
                switch (option) {
                    case yes :
                        mView.getControl().deleteContact(contact);
                }
            }
            @Override
            public void accepted() {}
            @Override
            public void closed() {}
        });
    }

    void confirmSubscription(ViewEvent.SubscriptionRequest event){
        final Contact contact = event.contact;

        WebPanel panel = panel(Tr.tr("Authorization request"), contact);

        String expl = Tr.tr("When accepting, this contact will be able to see your online status.");
        panel.add(textArea(expl));

        WebNotificationPopup popup = NotificationManager.showNotification(panel,
                NotificationOption.accept, NotificationOption.decline,
                NotificationOption.cancel);
        popup.setClickToClose(false);
        popup.addNotificationListener(new NotificationListener() {
            @Override
            public void optionSelected(NotificationOption option) {
                switch (option) {
                    case accept :
                        mView.getControl().sendSubscriptionResponse(contact, true);
                        break;
                    case decline :
                        mView.getControl().sendSubscriptionResponse(contact, false);
                }
            }
            @Override
            public void accepted() {}
            @Override
            public void closed() {}
        });
    }

    // TODO not used
    private void showNotification() {
        final WebDialog dialog = new WebDialog();
        dialog.setUndecorated(true);
        dialog.setBackground(Color.BLACK);
        dialog.setBackground(StyleConstants.transparent);

        WebNotificationPopup popup = new WebNotificationPopup(PopupStyle.dark);
        popup.setIcon(Utils.getIcon("kontalk_small.png"));
        popup.setMargin(View.MARGIN_DEFAULT);
        popup.setDisplayTime(6000);
        popup.addNotificationListener(new NotificationListener() {
            @Override
            public void optionSelected(NotificationOption option) {
            }
            @Override
            public void accepted() {
            }
            @Override
            public void closed() {
                dialog.dispose();
            }
        });

        // content
        WebPanel panel = new WebPanel();
        panel.setMargin(View.MARGIN_DEFAULT);
        panel.setOpaque(false);
        WebLabel title = new WebLabel("A new Message!");
        title.setFontSize(View.FONT_SIZE_BIG);
        title.setForeground(Color.WHITE);
        panel.add(title, BorderLayout.NORTH);
        String text = "this is some message, and some longer text was added";
        WebLabel message = new WebLabel(text);
        message.setForeground(Color.WHITE);
        panel.add(message, BorderLayout.CENTER);
        popup.setContent(panel);

        //popup.packPopup();
        dialog.setSize(popup.getPreferredSize());

        // set position on screen
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        GraphicsConfiguration gc = gd.getDefaultConfiguration();
        Rectangle screenBounds = gc.getBounds();
        // get height of the task bar
        // doesn't work on all environments
        //Insets toolHeight = toolkit.getScreenInsets(popup.getGraphicsConfiguration());
        int toolHeight  = 40;
        dialog.setLocation(screenBounds.width - dialog.getWidth() - 10,
                screenBounds.height - toolHeight - dialog.getHeight());

        dialog.setVisible(true);
        NotificationManager.showNotification(dialog, popup);
    }

    private static WebPanel panel(String title, Contact contact) {
        WebPanel panel = new GroupPanel(GAP_DEFAULT, false);
        panel.setOpaque(false);

        panel.add(new WebLabel(title).setBoldFont());
        panel.add(new WebSeparator(true, true));

        panel.add(new WebLabel(Tr.tr("Contact:")).setBoldFont());
        panel.add(new WebLabel(contactText(contact)));

        return panel;
    }

    private static String contactText(Contact contact){
        return Utils.name(contact, 20) + " < " + Utils.jid(contact.getJID(), 30)+" >";
    }

    private static WebTextArea textArea(String text) {
        WebTextArea textArea = new WebTextArea(0, 30);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setText(text);
        return textArea;
    }
}

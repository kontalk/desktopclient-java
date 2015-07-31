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

import com.alee.global.StyleConstants;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebDialog;
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
import org.kontalk.model.InMessage;
import org.kontalk.util.MediaUtils;

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

    public void onNewMessage(InMessage newMessage) {
        if (newMessage.getThread() == mView.getCurrentShownThread().orElse(null) &&
                mView.mainFrameIsFocused())
            return;
        MediaUtils.playSound(MediaUtils.Sound.NOTIFICATION);
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
        title.setFontSize(14);
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
}

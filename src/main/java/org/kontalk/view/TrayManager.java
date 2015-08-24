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

import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.rootpane.WebDialog;
import java.awt.AWTException;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.kontalk.model.ChatList;
import org.kontalk.system.Config;
import org.kontalk.util.Tr;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class TrayManager implements Observer {
    private static final Logger LOGGER = Logger.getLogger(TrayManager.class.getName());

    static final Image NORMAL_TRAY = Utils.getImage("kontalk.png");
    static final Image NOTIFICATION_TRAY = Utils.getImage("kontalk_notification.png");

    private final View mView;
    private final MainFrame mMainFrame;
    private TrayIcon mTrayIcon = null;

    TrayManager(View view, MainFrame mainFrame) {
        mView = view;
        mMainFrame = mainFrame;
        this.setTray();
    }

    void setTray() {
        if (!Config.getInstance().getBoolean(Config.MAIN_TRAY)) {
            this.removeTray();
            return;
        }

        if (!SystemTray.isSupported()) {
            LOGGER.info("tray icon not supported");
            return;
        }

        if (mTrayIcon == null)
            mTrayIcon = createTrayIcon(mView, mMainFrame);

        SystemTray tray = SystemTray.getSystemTray();
        if (tray.getTrayIcons().length > 0)
            return;

        try {
            tray.add(mTrayIcon);
        } catch (AWTException ex) {
            LOGGER.log(Level.WARNING, "can't add tray icon", ex);
        }
    }

    void removeTray() {
        if (mTrayIcon != null) {
            SystemTray tray = SystemTray.getSystemTray();
            tray.remove(mTrayIcon);
        }
    }

    @Override
    public void update(Observable o, final Object arg) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                TrayManager.this.updateOnEDT(arg);
            }
        });
    }

    private void updateOnEDT(Object arg) {
        if (arg != null && !(arg instanceof Boolean))
            return;

        if (mTrayIcon == null)
            return;

        mTrayIcon.setImage(getTrayImage());
    }

    private static Image getTrayImage() {
        return ChatList.getInstance().isUnread() ?
                NOTIFICATION_TRAY :
                NORMAL_TRAY ;
    }

    private static TrayIcon createTrayIcon(final View view, final MainFrame mainFrame) {
        // popup menu outside of frame, officially not supported
        final WebPopupMenu popup = new WebPopupMenu();
        WebMenuItem quitItem = new WebMenuItem(Tr.tr("Quit"));
        quitItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                view.callShutDown();
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
                    mainFrame.toggleState();
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

        TrayIcon trayIcon = new TrayIcon(getTrayImage(), "Kontalk" /*, popup*/);
        trayIcon.setImageAutoSize(true);
        trayIcon.addMouseListener(listener);
        return trayIcon;
    }
}

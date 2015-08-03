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

import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagLayout;
import java.util.Optional;
import org.kontalk.model.Chat;
import org.kontalk.model.Contact;

/**
 * Content view area: show a thread or contact details
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public class Content extends WebPanel {

    private final View mView;
    private final ThreadView mThreadView;
    private Component mCurrent;

    Content(View view, ThreadView threadView) {
        mView = view;
        mThreadView = threadView;
        this.show(mThreadView);
    }

    Optional<Chat> getCurrentThread() {
        if (!(mCurrent instanceof ThreadView))
            return Optional.empty();
        return mThreadView.getCurrentThread();
    }

    void showThread(Chat thread) {
        mThreadView.showThread(thread);
        if (mCurrent != mThreadView)
            this.show(mThreadView);
    }

    void showContact(Contact contact) {
        this.show(new ContactDetails(mView, contact));
    }

    void showNothing() {
        WebPanel nothing = new WebPanel();
        WebPanel topPanel = new WebPanel(new GridBagLayout());
        topPanel.setMargin(40);
        topPanel.add(new WebLabel(Utils.getIcon("kontalk-big.png")));
        nothing.add(topPanel, BorderLayout.NORTH);
        this.show(nothing);
    }

    private void show(Component comp) {
        if (mCurrent instanceof ContactDetails) {
            ((ContactDetails) mCurrent).onClose();
        }
        // Swing...
        this.removeAll();
        this.add(comp, BorderLayout.CENTER);
        this.revalidate();
        this.repaint();

        mCurrent = comp;
    }
}

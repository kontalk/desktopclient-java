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

import com.alee.laf.panel.WebPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.Optional;
import org.kontalk.model.KonThread;
import org.kontalk.model.User;

/**
 * Content view area: show a thread or user details
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

    Optional<KonThread> getCurrentThread() {
        if (!(mCurrent instanceof ThreadView))
            return Optional.empty();
        return mThreadView.getCurrentThread();
    }

    void showThread(KonThread thread) {
        mThreadView.showThread(thread);
        if (mCurrent != mThreadView)
            this.show(mThreadView);
    }

    void showUser(User user) {
        this.show(new UserDetails(mView, user));
    }

    void showNothing() {
        this.show(new WebPanel());
    }

    private void show(Component comp) {
        if (mCurrent instanceof UserDetails) {
            ((UserDetails) mCurrent).onClose();
        }
        // Swing...
        this.removeAll();
        this.add(comp, BorderLayout.CENTER);
        this.revalidate();
        this.repaint();

        mCurrent = comp;
    }
}

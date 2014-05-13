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

import com.alee.laf.text.WebEditorPane;
import java.util.logging.Logger;
import org.kontalk.model.KontalkMessage;
import org.kontalk.model.KontalkThread;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class ChatView extends WebEditorPane {
    private final static Logger LOGGER = Logger.getLogger(ChatView.class.getName());

    private KontalkThread mCurrentThread = null;
    
    ChatView() {
        this.setEditable(false);
        this.setAutoscrolls(true);
    }
    
    void showThread(KontalkThread thread) {
        mCurrentThread = thread;
        if (thread == null) {
            this.setText("");
            return;
        }
        String uglyText = "";
        for (KontalkMessage message: thread) {
            String from;
            if (message.getDir().equals(KontalkMessage.Direction.OUT)) {
                from = "me          ";
            } else {
                from = message.getJID().substring(0, 8);
            }
            uglyText += from + " : "+message.getText()+"\n";
        }
        this.setText(uglyText);
    }
    
    int getCurrentThreadID() {
        if (mCurrentThread == null)
            return -1;
        else
            return mCurrentThread.getID();
    }

}

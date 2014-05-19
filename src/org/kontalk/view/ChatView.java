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
import com.alee.laf.list.WebList;
import com.alee.laf.list.WebListModel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.text.WebTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.text.SimpleDateFormat;
import java.util.logging.Logger;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import org.kontalk.model.KontalkMessage;
import org.kontalk.model.KontalkThread;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class ChatView extends WebList {
    private final static Logger LOGGER = Logger.getLogger(ChatView.class.getName());

    private final WebListModel<MessageView> mListModel = new WebListModel();

    private KontalkThread mCurrentThread = null;

    ChatView() {
        //this.setEditable(false);
        //this.setAutoscrolls(true);

        this.setModel(mListModel);
        this.setCellRenderer(new MessageListRenderer());

    }

    void showThread(KontalkThread thread) {

        mCurrentThread = thread;

        if (thread == null) {
            return;
        }

        mListModel.clear();

        for (KontalkMessage message: thread) {
            mListModel.addElement(new MessageView(message));
        }
        if (!mListModel.isEmpty())
            // TODO doesn't work, swing sucks
            this.ensureIndexIsVisible(mListModel.getSize() -1 );
    }

    int getCurrentThreadID() {
        if (mCurrentThread == null)
            return -1;
        else
            return mCurrentThread.getID();
    }

    private class MessageView extends WebPanel {

        MessageView(KontalkMessage message) {

            this.setOpaque(false);
            this.setMargin(3);
            //this.setBorder(new EmptyBorder(10, 10, 10, 10));

            WebPanel messagePanel = new WebPanel(true);
            //messagePanel.setLayout(new BorderLayout(10, 10));

            if (message.getDir().equals(KontalkMessage.Direction.IN)) {
                WebLabel fromLabel = new WebLabel(message.getJID().substring(0, 8));
                fromLabel.setFontSize(12);
                fromLabel.setForeground(Color.BLUE);
                fromLabel.setItalicFont();
                messagePanel.add(fromLabel, BorderLayout.NORTH);
                //messagePanel.setAlignmentX(Component.RIGHT_ALIGNMENT);
                //layout.setAlignment(FlowLayout.LEADING);
            } else {
                //layout.setAlignment(FlowLayout.TRAILING);
            }
            WebTextArea textArea = new WebTextArea(message.getText());
            textArea.setOpaque(false);
            textArea.setFontSize(13);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            // TODO this disables word wrap, but there is no easy solution to
            // set the textarea to one-line when possible and multi-line when
            // horizontal space is not enough
            textArea.setColumns(20);
            //textArea.setColumns(message.getText().length());
            //messagePanel.setPreferredWidth(199);
            messagePanel.add(textArea, BorderLayout.CENTER);
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, HH:mm");
            WebLabel dateLabel = new WebLabel(dateFormat.format(message.getDate()));
            dateLabel.setForeground(Color.GRAY);
            dateLabel.setFontSize(11);
            messagePanel.add(dateLabel, BorderLayout.SOUTH);

            if (message.getDir().equals(KontalkMessage.Direction.IN)) {
                this.add(messagePanel, BorderLayout.WEST);
            } else {
                this.add(messagePanel, BorderLayout.EAST);
            }
        }

    }

    private class MessageListRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList list,
                                                Object value,
                                                int index,
                                                boolean isSelected,
                                                boolean hasFocus) {
            if (value instanceof MessageView) {
                MessageView messageView = (MessageView) value;
                //messageView.paintSelected(isSelected);
                mListModel.update(messageView);
                return messageView;
            } else {
                return new WebPanel(new WebLabel("ERRROR"));
            }
        }
    }

}

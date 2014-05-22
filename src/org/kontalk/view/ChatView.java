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
import com.alee.laf.list.WebListCellRenderer;
import com.alee.laf.list.WebListModel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.text.WebTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.text.SimpleDateFormat;
import java.util.logging.Logger;
import javax.swing.DefaultListSelectionModel;
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
        // great swing option to disable selection
        this.setSelectionModel(new DefaultListSelectionModel() {
            @Override
            public void setSelectionInterval(int index0, int index1) {
            }
            @Override
            public void addSelectionInterval(int index0, int index1) {
            }
        });
    }

    void showThread(KontalkThread thread) {

        mCurrentThread = thread;

        if (thread == null) {
            return;
        }

        mListModel.clear();

        for (KontalkMessage message: thread) {
            MessageView newMessageView = new MessageView(message);
            mListModel.addElement(newMessageView);
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

        private final WebTextArea mTextArea;
        private final int mPreferredTextAreaWidth;

        MessageView(KontalkMessage message) {

            this.setOpaque(false);
            this.setMargin(3);
            //this.setBorder(new EmptyBorder(10, 10, 10, 10));

            WebPanel messagePanel = new WebPanel(true);
            messagePanel.setMargin(4);

            // from label
            if (message.getDir().equals(KontalkMessage.Direction.IN)) {
                WebLabel fromLabel = new WebLabel(message.getJID().substring(0, 8));
                fromLabel.setFontSize(12);
                fromLabel.setForeground(Color.BLUE);
                fromLabel.setItalicFont();
                messagePanel.add(fromLabel, BorderLayout.NORTH);
            }

            // text
            mTextArea = new WebTextArea(message.getText());
            mTextArea.setOpaque(false);
            mTextArea.setFontSize(13);
            // save the width that is requied to show the text in one line
            mPreferredTextAreaWidth = mTextArea.getPreferredSize().width;
            mTextArea.setLineWrap(true);
            mTextArea.setWrapStyleWord(true);
            messagePanel.add(mTextArea, BorderLayout.CENTER);

            // date label
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

        void resize(int listWidth) {
            int maxWidth = (int)(listWidth * 0.8);
            int width = Math.min(mPreferredTextAreaWidth, maxWidth);
            mTextArea.setSize(width, Short.MAX_VALUE);
        }

    }

    private class MessageListRenderer extends WebListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList list,
                                                Object value,
                                                int index,
                                                boolean isSelected,
                                                boolean hasFocus) {
            if (value instanceof MessageView) {
                MessageView messageView = (MessageView) value;
                messageView.resize(list.getWidth());
                return messageView;
            } else {
                return new WebPanel(new WebLabel("ERRROR"));
            }
        }
    }

}

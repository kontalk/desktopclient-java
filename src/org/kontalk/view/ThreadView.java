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
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.text.WebTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.swing.DefaultListSelectionModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JList;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.kontalk.model.KonMessage;
import org.kontalk.model.KonThread;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class ThreadView extends WebScrollPane {
    private final static Logger LOGGER = Logger.getLogger(ThreadView.class.getName());

    private static Icon PENDING_ICON;
    private static Icon SENT_ICON;
    private static Icon DELIVERED_ICON;

    private final Map<Integer, MessageViewList> mThreadCache = new HashMap();
    private int mCurrentThreadID = -1;

    ThreadView() {
        super(null);

        this.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        this.getVerticalScrollBar().setUnitIncrement(25);
    }

    int getCurrentThreadID() {
        return mCurrentThreadID;
    }

    void showThread(KonThread thread) {
        if (thread == null)
            return;

        if (!mThreadCache.containsKey(thread.getID())) {
            mThreadCache.put(thread.getID(), new MessageViewList(thread));
        }
        MessageViewList list = mThreadCache.get(thread.getID());
        this.setViewportView(list);
        list.update();
        // have to do it twice somehow
        list.update();

        mCurrentThreadID = thread.getID();
    }

    void setColor(Color color) {
        this.getViewport().setBackground(color);
    }

    /**
     * View all messages of on thread in a left/right MIM style list.
     */
    private class MessageViewList extends WebList implements ChangeListener {

        private final WebListModel<MessageView> mListModel = new WebListModel();

        private final KonThread mThread;

        MessageViewList(KonThread thread) {
            mThread = thread;
            mThread.addListener(this);

            //this.setEditable(false);
            //this.setAutoscrolls(true);
            this.setOpaque(false);

            this.setModel(mListModel);
            this.setCellRenderer(new MessageListRenderer());

            // beautiful swing option to disable selection
            this.setSelectionModel(new DefaultListSelectionModel() {
                @Override
                public void setSelectionInterval(int index0, int index1) {
                }
                @Override
                public void addSelectionInterval(int index0, int index1) {
                }
            });

            // load icons
            String iconPath = "org/kontalk/res/";
            PENDING_ICON = new ImageIcon(ClassLoader.getSystemResource(iconPath + "ic_msg_pending.png"));
            SENT_ICON = new ImageIcon(ClassLoader.getSystemResource(iconPath + "ic_msg_sent.png"));
            DELIVERED_ICON = new ImageIcon(ClassLoader.getSystemResource(iconPath + "ic_msg_delivered.png"));
        }

        private void update() {
            // TODO performance
            mListModel.clear();
            // insert messages
            for (KonMessage message: mThread.getMessages()) {
                MessageView newMessageView = new MessageView(message);
                mListModel.addElement(newMessageView);
            }
            if (!mListModel.isEmpty())
                this.ensureIndexIsVisible(mListModel.getSize() -1 );
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            update();
        }

    }

    /**
     * View for one message.
     */
    private class MessageView extends WebPanel implements ChangeListener {

        private final KonMessage mMessage;
        private final WebTextArea mTextArea;
        private final int mPreferredTextAreaWidth;
        private final WebLabel mStatusIconLabel;

        MessageView(KonMessage message) {
            mMessage = message;
            mMessage.addListener(this);

            this.setOpaque(false);
            this.setMargin(2);
            //this.setBorder(new EmptyBorder(10, 10, 10, 10));

            WebPanel messagePanel = new WebPanel(true);
            messagePanel.setMargin(1);

            // from label
            if (mMessage.getDir().equals(KonMessage.Direction.IN)) {
                WebLabel fromLabel = new WebLabel(mMessage.getJID().substring(0, 8));
                fromLabel.setFontSize(12);
                fromLabel.setForeground(Color.BLUE);
                fromLabel.setItalicFont();
                messagePanel.add(fromLabel, BorderLayout.NORTH);
            }

            // text
            mTextArea = new WebTextArea(mMessage.getText());
            mTextArea.setOpaque(false);
            mTextArea.setFontSize(13);
            // save the width that is requied to show the text in one line
            mPreferredTextAreaWidth = mTextArea.getPreferredSize().width;
            mTextArea.setLineWrap(true);
            mTextArea.setWrapStyleWord(true);
            messagePanel.add(mTextArea, BorderLayout.CENTER);

            WebPanel statusPanel = new WebPanel();
            statusPanel.setOpaque(false);
            statusPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
            // status icon
            mStatusIconLabel = new WebLabel();
            update();
            statusPanel.add(mStatusIconLabel);
            // date label
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, HH:mm");
            WebLabel dateLabel = new WebLabel(dateFormat.format(mMessage.getDate()));
            dateLabel.setForeground(Color.GRAY);
            dateLabel.setFontSize(11);
            statusPanel.add(dateLabel);
            messagePanel.add(statusPanel, BorderLayout.SOUTH);

            if (mMessage.getDir().equals(KonMessage.Direction.IN)) {
                this.add(messagePanel, BorderLayout.WEST);
            } else {
                this.add(messagePanel, BorderLayout.EAST);
            }
        }

        public int getMessageID() {
            return mMessage.getID();
        }

        private void resize(int listWidth) {
            int maxWidth = (int)(listWidth * 0.8);
            int width = Math.min(mPreferredTextAreaWidth, maxWidth);
            mTextArea.setSize(width, Short.MAX_VALUE);
        }

        private void update() {
            switch (mMessage.getStatus()) {
                case PENDING :
                    mStatusIconLabel.setIcon(PENDING_ICON);
                    break;
                case SENT :
                    mStatusIconLabel.setIcon(SENT_ICON);
                    break;
                case RECEIVED:
                    mStatusIconLabel.setIcon(DELIVERED_ICON);
                    break;
            }
            // TODO damn icon is not updated
            this.revalidate();
            this.invalidate();
            this.repaint();
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            update();
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

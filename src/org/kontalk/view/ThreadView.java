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
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.text.WebTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.DefaultListSelectionModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.kontalk.crypto.Coder;
import org.kontalk.model.KonMessage;
import org.kontalk.model.KonThread;
import org.kontalk.view.ListView.ListItem;

/**
 * Pane that shows the currently selected thread.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class ThreadView extends WebScrollPane {

    private static Icon PENDING_ICON;
    private static Icon SENT_ICON;
    private static Icon DELIVERED_ICON;
    private static Icon CRYPT_ICON;
    private static Icon UNENCRYPT_ICON;

    private final Map<Integer, MessageViewList> mThreadCache = new HashMap();
    private int mCurrentThreadID = -1;

    ThreadView() {
        super(null);

        this.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        this.getVerticalScrollBar().setUnitIncrement(25);

        // load icons
        String iconPath = "org/kontalk/res/";
        PENDING_ICON = new ImageIcon(ClassLoader.getSystemResource(iconPath + "ic_msg_pending.png"));
        SENT_ICON = new ImageIcon(ClassLoader.getSystemResource(iconPath + "ic_msg_sent.png"));
        DELIVERED_ICON = new ImageIcon(ClassLoader.getSystemResource(iconPath + "ic_msg_delivered.png"));
        CRYPT_ICON = new ImageIcon(ClassLoader.getSystemResource(iconPath + "ic_msg_crypt.png"));
        UNENCRYPT_ICON = new ImageIcon(ClassLoader.getSystemResource(iconPath + "ic_msg_unencrypt.png"));
    }

    int getCurrentThreadID() {
        return mCurrentThreadID;
    }

    void showThread(KonThread thread) {

        boolean isNew = false;
        if (!mThreadCache.containsKey(thread.getID())) {
            mThreadCache.put(thread.getID(), new MessageViewList(thread));
            isNew = true;
        }
        MessageViewList list = mThreadCache.get(thread.getID());
        this.setViewportView(list);

        if (list.getModelSize() > 0 && isNew) {
            // scroll down
            list.ensureIndexIsVisible(list.getModelSize() -1);
            //JScrollBar vertical = this.getVerticalScrollBar();
            //vertical.setValue(vertical.getMaximum());
        }

        mCurrentThreadID = thread.getID();
    }

    void setColor(Color color) {
        this.getViewport().setBackground(color);
    }

    /**
     * View all messages of on thread in a left/right MIM style list.
     */
    private class MessageViewList extends ListView implements ChangeListener {

        private final KonThread mThread;

        MessageViewList(KonThread thread) {
            super();

            mThread = thread;
            mThread.addListener(this);

            //this.setEditable(false);
            //this.setAutoscrolls(true);
            this.setOpaque(false);

            //this.setModel(mListModel);
            //this.setCellRenderer(new MessageListRenderer());

            this.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    // cell height may have changed, the list doesn't detect
                    // this, so we have to invalidate the cell size cache
                    MessageViewList.this.setFixedCellHeight(1);
                    MessageViewList.this.setFixedCellHeight(-1);
                }
            });

            // beautiful swing option to disable selection
            this.setSelectionModel(new DefaultListSelectionModel() {
                @Override
                public void setSelectionInterval(int index0, int index1) {
                }
                @Override
                public void addSelectionInterval(int index0, int index1) {
                }
            });

            // insert messages
            for (KonMessage message: mThread.getMessages()) {
                MessageView newMessageView = new MessageView(message);
                mListModel.addElement(newMessageView);
            }
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            // check for new messages to add
            if (mListModel.size() < mThread.getMessages().size()) {
                Set<KonMessage> oldMessages = new HashSet();
                for (ListItem m : mListModel.getElements())
                    oldMessages.add(((MessageView) m).mMessage);

                for (KonMessage message: mThread.getMessages()) {
                    if (!oldMessages.contains(message)) {
                        MessageView newMessageView = new MessageView(message);
                        // always inserted at the end, timestamp of message is
                        // ignored. Let's call it a feature.
                        mListModel.addElement(newMessageView);
                        // TODO doesn't work here somehow
                        this.ensureIndexIsVisible(mListModel.size() -1);
                    }
                }
            }

            if (ThreadView.this.mCurrentThreadID == mThread.getID()) {
                // we are seeing this thread right now
                // avoid loop
                if (!mThread.isRead())
                    mThread.setRead();
            }
        }

        /**
         * View for one message. The content is added to a panel inside this panel.
         */
        private class MessageView extends ListItem implements ChangeListener {

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
                    String from;
                    if (mMessage.getUser().getName() != null) {
                        from = mMessage.getUser().getName();
                    } else {
                        from = mMessage.getJID();
                        if (from.length() > 40)
                            from = from.substring(0, 8);
                    }
                    WebLabel fromLabel = new WebLabel(" "+from);
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
                // icons
                mStatusIconLabel = new WebLabel();
                update();
                statusPanel.add(mStatusIconLabel);
                WebLabel encryptIconLabel = new WebLabel();
                if (message.isEncrypted()) {
                    encryptIconLabel.setIcon(CRYPT_ICON);
                } else {
                    encryptIconLabel.setIcon(UNENCRYPT_ICON);
                }
                statusPanel.add(encryptIconLabel);
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

            @Override
            void resize(int listWidth) {
                // on the very first call the list width is zero
                //if (listWidth == 0)
                //    listWidth = 500;

                int maxWidth = (int)(listWidth * 0.8);
                int width = Math.min(mPreferredTextAreaWidth, maxWidth);
                // height is reset later
                mTextArea.setSize(width, mTextArea.getPreferredSize().height);
            }

            private void update() {
                switch (mMessage.getReceiptStatus()) {
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
            }

            @Override
            public void stateChanged(ChangeEvent e) {
                this.update();
                // need to repaint parent to see changes
                ThreadView.this.repaint();
            }

            @Override
            public String getTooltipText() {
                String encryption = "unknown";
                switch (mMessage.getEncryption()) {
                    case NOT: encryption = "not encrypted"; break;
                    case ENCRYPTED: encryption = "encrypted"; break;
                    case DECRYPTED: encryption = "decrypted"; break;
                }
                String verification = "unknown";
                switch (mMessage.getSigning()) {
                    case NOT: verification = "not signed"; break;
                    case SIGNED: verification = "signed"; break;
                    case VERIFIED: verification = "verified"; break;
                }
                String problems = "unknown";
                if (mMessage.getSecurityErrors().isEmpty()) {
                    problems = "none";
                } else {
                  for (Coder.Error error: mMessage.getSecurityErrors()) {
                      problems += error.toString() + " <br> ";
                  }
                }

                String html = "<html><body>" +
                        //"<h3>Header</h3>" +
                        "<br>" +
                        "Security: " + encryption + " / " + verification + "<br>" +
                        "Problems: " + problems;

                return html;
            }
        }
    }
}

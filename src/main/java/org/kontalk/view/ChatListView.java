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
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.Timer;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.kontalk.system.Config;
import org.kontalk.model.KonMessage;
import org.kontalk.model.Chat;
import org.kontalk.model.Chat.KonChatState;
import org.kontalk.model.ChatList;
import org.kontalk.model.Contact;
import org.kontalk.util.Tr;
import org.kontalk.view.ChatListView.ChatItem;

/**
 * Show a brief list of all chats.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class ChatListView extends Table<ChatItem, Chat> {

    private final ChatList mChatList;
    private final WebPopupMenu mPopupMenu;

    ChatListView(final View view, ChatList chatList) {
        super(view, true);
        mChatList = chatList;

        this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // right click popup menu
        mPopupMenu = new WebPopupMenu();

        WebMenuItem deleteMenuItem = new WebMenuItem(Tr.tr("Delete Chat"));
        deleteMenuItem.setToolTipText(Tr.tr("Delete this chat"));
        deleteMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                ChatListView.this.deleteSelectedChat();
            }
        });
        mPopupMenu.add(deleteMenuItem);

        // actions triggered by selection
        this.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            Chat lastChat = null;

            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting())
                    return;
                Optional<Chat> optChat = ChatListView.this.getSelectedValue();
                if (!optChat.isPresent())
                    return;

                // if event is caused by filtering, dont do anything
                if (lastChat == optChat.get())
                    return;

                mView.clearSearch();
                mView.showChat(optChat.get());
                lastChat = optChat.get();
            }
        });

        // actions triggered by mouse events
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                check(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                check(e);
            }
            private void check(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = ChatListView.this.rowAtPoint(e.getPoint());
                    ChatListView.this.setSelectedItem(row);
                    ChatListView.this.showPopupMenu(e);
                }
            }
        });

        this.updateOnEDT(null);
    }

    @Override
    protected void updateOnEDT(Object arg) {
        Set<ChatItem> newItems = new HashSet<>();
        Set<Chat> chats = mChatList.getAll();
        for (Chat chat: chats)
            if (!this.containsValue(chat))
                newItems.add(new ChatItem(chat));
        this.sync(chats, newItems);
    }

    void selectLastChat() {
        int i = Config.getInstance().getInt(Config.VIEW_SELECTED_CHAT);
        if (i < 0) i = 0;
        this.setSelectedItem(i);
    }

    void save() {
        Config.getInstance().setProperty(Config.VIEW_SELECTED_CHAT,
                this.getSelectedRow());
    }

    private void showPopupMenu(MouseEvent e) {
           mPopupMenu.show(this, e.getX(), e.getY());
    }

    private void deleteSelectedChat() {
        ChatItem t = this.getSelectedItem();
        if (t.mValue.getMessages().getAll().size() != 0) {
            String text = Tr.tr("Permanently delete all messages in this chat?");
            if (!Utils.confirmDeletion(this, text))
                return;
        }
        ChatItem chatItem = this.getSelectedItem();
        mView.getControl().deleteChat(chatItem.mValue);
    }

    protected final class ChatItem extends Table<ChatItem, Chat>.TableItem {

        private final WebLabel mTitleLabel;
        private final WebLabel mStatusLabel;
        private final WebLabel mChatStateLabel;
        private Color mBackground;

        ChatItem(Chat chat) {
            super(chat);

            this.setLayout(new BorderLayout(View.GAP_DEFAULT, View.GAP_SMALL));
            this.setMargin(View.MARGIN_SMALL);

            mTitleLabel = new WebLabel();
            mTitleLabel.setFontSize(14);
            if (chat.isGroupChat())
                mTitleLabel.setForeground(View.DARK_GREEN);
            this.add(mTitleLabel, BorderLayout.NORTH);

            mStatusLabel = new WebLabel();
            mStatusLabel.setForeground(Color.GRAY);
            mStatusLabel.setFontSize(11);
            this.add(mStatusLabel, BorderLayout.EAST);

            mChatStateLabel = new WebLabel();
            mChatStateLabel.setForeground(View.GREEN);
            mChatStateLabel.setFontSize(13);
            mChatStateLabel.setBoldFont();
            //mChatStateLabel.setMargin(0, 5, 0, 5);
            this.add(mChatStateLabel, BorderLayout.WEST);

            this.updateView(null);

            this.setBackground(mBackground);
        }

        @Override
        protected void render(int tableWidth, boolean isSelected) {
            if (isSelected)
                this.setBackground(View.BLUE);
            else
                this.setBackground(mBackground);
        }

        @Override
        protected String getTooltipText() {
            return "<html><body>" +
                    Tr.tr("Last activity")+": " + lastActivity(mValue, false) + "<br>"
                    + "";
        }

        @Override
        protected void updateOnEDT(Object arg) {
            this.updateView(arg);
            // needed for background repaint
            ChatListView.this.repaint();
        }

        private void updateView(Object arg) {
            if (arg == null || arg instanceof Contact || arg instanceof String) {
                mTitleLabel.setText(Utils.chatTitle(mValue));
            }

            if (arg == null || arg instanceof KonMessage) {
                this.updateBG();

                mStatusLabel.setText(lastActivity(mValue, true));
                ChatListView.this.updateSorting();
            } else if (arg instanceof Boolean) {
                this.updateBG();
            } else if (arg instanceof Timer) {
                mStatusLabel.setText(lastActivity(mValue, true));
            }

            if (arg instanceof Chat.KonChatState) {
                KonChatState state = (KonChatState) arg;
                String stateText = null;
                switch(state.getState()) {
                    case composing: stateText = Tr.tr("is writing..."); break;
                    //case paused: activity = T/r.tr("stopped typing"); break;
                    //case inactive: stateText = T/r.tr("is inactive"); break;
                }
                if (stateText == null) {
                    // 'inactive' is default
                    mChatStateLabel.setText("");
                    return;
                }

                if (mValue.isGroupChat())
                    stateText = state.getContact().getName() + " " + stateText;

                mChatStateLabel.setText(stateText + " ");
            }
        }

        private void updateBG() {
            mBackground = !mValue.isRead() ? View.LIGHT_BLUE : Color.WHITE;
        }

        @Override
        protected boolean contains(String search) {
            // always show entry for current chat
            Optional<Chat> optChat = mView.getCurrentShownChat();
            if (optChat.isPresent() && optChat.get() == mValue)
                return true;

            for (Contact contact: mValue.getContacts()) {
                if (contact.getName().toLowerCase().contains(search) ||
                        contact.getJID().string().toLowerCase().contains(search))
                    return true;
            }
            return mValue.getSubject().toLowerCase().contains(search);
        }

        @Override
        public int compareTo(TableItem o) {
            SortedSet<KonMessage> messages = this.mValue.getMessages().getAll();
            SortedSet<KonMessage> oMessages = o.mValue.getMessages().getAll();
            if (!messages.isEmpty() && !oMessages.isEmpty())
                return -messages.last().getDate().compareTo(oMessages.last().getDate());

            return -Integer.compare(this.mValue.getID(), o.mValue.getID());
        }
    }

    private static String lastActivity(Chat chat, boolean pretty) {
        SortedSet<KonMessage> messageSet = chat.getMessages().getAll();
        String lastActivity = messageSet.isEmpty() ? Tr.tr("no messages yet") :
                pretty ? Utils.PRETTY_TIME.format(messageSet.last().getDate()) :
                Utils.MID_DATE_FORMAT.format(messageSet.last().getDate());
        return  lastActivity;
    }
}

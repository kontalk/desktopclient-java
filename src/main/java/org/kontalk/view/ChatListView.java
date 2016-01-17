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

import com.alee.extended.image.DisplayType;
import com.alee.extended.image.WebImage;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.GroupingType;
import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import javax.swing.Box;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.kontalk.system.Config;
import org.kontalk.model.KonMessage;
import org.kontalk.model.Chat;
import org.kontalk.model.Chat.KonChatState;
import org.kontalk.model.ChatList;
import org.kontalk.model.Contact;
import org.kontalk.model.MessageContent.GroupCommand;
import org.kontalk.util.Tr;
import org.kontalk.view.ChatListView.ChatItem;

/**
 * Show a brief list of all chats.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class ChatListView extends ListView<ChatItem, Chat> {

    private final ChatList mChatList;

    ChatListView(final View view, ChatList chatList) {
        super(view, true);
        mChatList = chatList;

        this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // actions triggered by selection
        this.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            Chat lastChat = null;

            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting())
                    return;

                Chat chat = ChatListView.this.getSelectedValue().orElse(null);
                if (chat == null) {
                    // note: this happens also on righ-click for some reason
                    return;
                }

                // if event is caused by filtering, dont do anything
                if (lastChat == chat)
                    return;

                mView.clearSearch();
                mView.showChat(chat);
                lastChat = chat;
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

    private void deleteChat(ChatItem item) {
        if (!item.mValue.getMessages().isEmpty()) {
            String text = Tr.tr("Permanently delete all messages in this chat?");
            if (item.mValue.isGroupChat() && item.mValue.isValid())
                text += "\n\n"+Tr.tr("You will automatically leave this group.");
            if (!Utils.confirmDeletion(this, text))
                return;
        }
        ChatItem chatItem = this.getSelectedItem();
        mView.getControl().deleteChat(chatItem.mValue);
    }

    @Override
    protected WebPopupMenu rightClickMenu(ChatItem item) {
        WebPopupMenu menu = new WebPopupMenu();

        WebMenuItem deleteItem = new WebMenuItem(Tr.tr("Delete Chat"));
        deleteItem.setToolTipText(Tr.tr("Delete this chat"));
        deleteItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                ChatListView.this.deleteChat(ChatListView.this.getSelectedItem());
            }
        });
        menu.add(deleteItem);

        return menu;
    }

    protected final class ChatItem extends ListView<ChatItem, Chat>.TableItem {

        private final WebImage mAvatar;
        private final WebLabel mTitleLabel;
        private final WebLabel mStatusLabel;
        private final WebLabel mChatStateLabel;
        private Color mBackground;

        ChatItem(Chat chat) {
            super(chat);

            this.setLayout(new BorderLayout(View.GAP_DEFAULT, 0));
            this.setMargin(View.MARGIN_DEFAULT);

            mAvatar = new WebImage().setDisplayType(DisplayType.fitComponent);
            mAvatar.setPreferredSize(View.AVATAR_LIST_DIM);
            this.add(mAvatar, BorderLayout.WEST);

            mTitleLabel = new WebLabel();
            mTitleLabel.setFontSize(View.FONT_SIZE_BIG);
            mTitleLabel.setDrawShade(true);
            if (mValue.isGroupChat())
                    mTitleLabel.setForeground(View.DARK_GREEN);

            mStatusLabel = new WebLabel();
            mStatusLabel.setForeground(Color.GRAY);
            mStatusLabel.setFontSize(View.FONT_SIZE_TINY);
            this.add(mStatusLabel, BorderLayout.EAST);

            mChatStateLabel = new WebLabel();
            mChatStateLabel.setForeground(View.GREEN);
            mChatStateLabel.setFontSize(View.FONT_SIZE_NORMAL);
            mChatStateLabel.setBoldFont();
            //mChatStateLabel.setMargin(0, 5, 0, 5);

            this.add(
                    new GroupPanel(View.GAP_SMALL, false,
                            mTitleLabel,
                            new GroupPanel(GroupingType.fillFirst,
                                    Box.createGlue(), mStatusLabel, mChatStateLabel)
                    ), BorderLayout.CENTER);

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
            if (arg == null || arg instanceof Contact ||
                    arg instanceof String || arg instanceof GroupCommand) {
                mTitleLabel.setText(Utils.chatTitle(mValue));
            }

            // avatar may change when subject or contact name changes
            if (arg == null || arg instanceof Contact || arg instanceof String) {
                mAvatar.setImage(AvatarLoader.load(mValue));
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

            String stateText = "";
            if (arg instanceof Chat.KonChatState) {
                KonChatState state = (KonChatState) arg;
                switch(state.getState()) {
                    case composing: stateText = Tr.tr("is writing…"); break;
                    //case paused: activity = T/r.tr("stopped typing"); break;
                    //case inactive: stateText = T/r.tr("is inactive"); break;
                }
                if (!stateText.isEmpty() && mValue.isGroupChat())
                    stateText = state.getContact().getName() + ": " + stateText;
            }
            if (stateText.isEmpty()) {
                mChatStateLabel.setText("");
                mStatusLabel.setVisible(true);
            } else {
                mChatStateLabel.setText(stateText);
                mStatusLabel.setVisible(false);
            }

        }

        private void updateBG() {
            mBackground = !mValue.isRead() ? View.LIGHT_BLUE : Color.WHITE;
        }

        @Override
        protected boolean contains(String search) {
            // always show entry for current chat
            Chat chat = mView.getCurrentShownChat().orElse(null);
            if (chat != null && chat == mValue)
                return true;

            for (Contact contact: mValue.getAllContacts()) {
                if (contact.getName().toLowerCase().contains(search) ||
                        contact.getJID().string().toLowerCase().contains(search))
                    return true;
            }
            return mValue.getSubject().toLowerCase().contains(search);
        }

        @Override
        public int compareTo(TableItem o) {
            KonMessage m = this.mValue.getMessages().getLast().orElse(null);
            KonMessage oM = o.mValue.getMessages().getLast().orElse(null);
            if (m != null && oM != null)
                return -m.getDate().compareTo(oM.getDate());

            return -Integer.compare(this.mValue.getID(), o.mValue.getID());
        }
    }

    private static String lastActivity(Chat chat, boolean pretty) {
        KonMessage m = chat.getMessages().getLast().orElse(null);
        String lastActivity = m == null ? Tr.tr("no messages yet") :
                pretty ? Utils.PRETTY_TIME.format(m.getDate()) :
                Utils.MID_DATE_FORMAT.format(m.getDate());
        return lastActivity;
    }
}

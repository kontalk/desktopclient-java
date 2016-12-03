/*
 *  Kontalk Java client
 *  Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>
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

import javax.swing.Box;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Optional;

import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.GroupingType;
import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import org.kontalk.model.Contact;
import org.kontalk.model.chat.Chat;
import org.kontalk.model.chat.ChatList;
import org.kontalk.model.chat.GroupChat;
import org.kontalk.model.chat.Member;
import org.kontalk.model.chat.SingleChat;
import org.kontalk.model.message.KonMessage;
import org.kontalk.persistence.Config;
import org.kontalk.util.Tr;

/**
 * Show a brief list of all chats.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class ChatListView extends ListView<Chat> {

    private final ChatList mChatList;

    ChatListView(final View view, ChatList chatList) {
        super(view,
                new FlyweightChatItem(),
                new FlyweightChatItem(),
                ListSelectionModel.SINGLE_SELECTION,
                false,
                true);

        mChatList = chatList;

        this.updateOnEDT(null);
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

    @Override
    public int compare(Chat c1, Chat c2) {
        KonMessage m = c1.getMessages().getLast().orElse(null);
        KonMessage oM = c2.getMessages().getLast().orElse(null);
        return m != null && oM != null ?
                - m.getDate().compareTo(oM.getDate()) :
                - Integer.compare(c1.getID(), c2.getID());
    }

    @Override
    protected void updateOnEDT(Object arg) {
        if (arg == null || arg == ChatList.ViewChange.MODIFIED)
            this.sync(mChatList.getAll());
    }

    @Override
    protected void selectionChanged(Optional<Chat> value) {
        mView.onChatSelectionChanged(value);
    }

    @Override
    protected WebPopupMenu rightClickMenu(List<Chat> selectedValues) {
        WebPopupMenu menu = new WebPopupMenu();
        if (selectedValues.isEmpty())
            return menu;

        Chat chat = selectedValues.get(0);
        if (chat instanceof SingleChat) {
            final Contact contact = ((SingleChat) chat).getMember().getContact();
            if (!contact.isDeleted()) {
                WebMenuItem editItem = new WebMenuItem(Tr.tr("Edit Contact"));
                editItem.setToolTipText(Tr.tr("Edit contact settings"));
                editItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        mView.showContactDetails(contact);
                    }
                });
                menu.add(editItem);
            }
        }

        WebMenuItem deleteItem = new WebMenuItem(Tr.tr("Delete Chat"));
        deleteItem.setToolTipText(Tr.tr("Delete this chat"));
        deleteItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                ChatListView.this.deleteChat(chat);
            }
        });
        menu.add(deleteItem);

        return menu;
    }

    private void deleteChat(Chat chat) {
        if (!chat.getMessages().isEmpty()) {
            String text = Tr.tr("Permanently delete all messages in this chat?");
            if (chat.isGroupChat() && chat.isValid())
                text += "\n\n"+Tr.tr("You will automatically leave this group.");
            if (!Utils.confirmDeletion(this, text))
                return;
        }

        mView.getControl().deleteChat(chat);
    }

    @Override
    protected String getTooltipText(Chat value) {
        return "<html><body>" +
                lastActivity(value, true, false) + "<br>"
                + "</body></html>";
    }

    @Override
    protected void onRenameEvent() {
        Chat chat = this.getSelectedValue().orElse(null);
        if (chat instanceof SingleChat) {
            mView.requestRenameFocus(((SingleChat) chat).getMember().getContact());
            return;
        }

        if (chat instanceof GroupChat) {
            // TODO
        }
    }

    private static final class FlyweightChatItem extends FlyweightItem<Chat> {

        private final ComponentUtils.AvatarImage mAvatar;
        private final WebLabel mTitleLabel;
        private final WebLabel mStatusLabel;
        private final WebLabel mChatStateLabel;

        FlyweightChatItem() {
            this.setLayout(new BorderLayout(View.GAP_DEFAULT, 0));
            this.setMargin(View.MARGIN_DEFAULT);

            mAvatar = new ComponentUtils.AvatarImage(View.AVATAR_LIST_SIZE);
            this.add(mAvatar, BorderLayout.WEST);

            mTitleLabel = new WebLabel();
            mTitleLabel.setFontSize(View.FONT_SIZE_BIG);
            mTitleLabel.setDrawShade(true);

            mStatusLabel = new WebLabel();
            mStatusLabel.setForeground(Color.GRAY);
            mStatusLabel.setFontSize(View.FONT_SIZE_TINY);
            this.add(mStatusLabel, BorderLayout.EAST);

            mChatStateLabel = new WebLabel();
            mChatStateLabel.setForeground(View.DARK_RED);
            mChatStateLabel.setFontSize(View.FONT_SIZE_NORMAL);
            mChatStateLabel.setBoldFont();
            //mChatStateLabel.setMargin(0, 5, 0, 5);

            this.add(
                    new GroupPanel(View.GAP_SMALL, false,
                            mTitleLabel,
                            new GroupPanel(GroupingType.fillFirst,
                                    Box.createGlue(), mStatusLabel, mChatStateLabel)
                    ), BorderLayout.CENTER);
        }

        @Override
        protected void render(Chat value, int listWidth, boolean isSelected, boolean isLast) {
            // background
            this.setBackground(isSelected ? View.BLUE :
                    !value.isRead() ? View.LIGHT_BLUE :
                    Color.WHITE);

            // avatar
            mAvatar.setAvatarImage(value);

            // title
            mTitleLabel.setText(Utils.chatTitle(value));
            if (value.isGroupChat())
                mTitleLabel.setForeground(View.DARK_GREEN);

            // status
            mStatusLabel.setText(lastActivity(value, isSelected, true));

            // state
            String stateText = "";
            List<Member> members = value.getAllMembers();
            if (!members.isEmpty()) {
                Member member = members.get(0);
                switch (member.getState()) {
                    case composing:
                        stateText = Tr.tr("is writing…");
                        break;
                    //case paused: activity = T/r.tr("stopped typing"); break;
                    //case inactive: stateText = T/r.tr("is inactive"); break;
                }
            }
            // not used: chatstates for group chats
//                if (!stateText.isEmpty() && mValue.isGroupChat())
//                    stateText = member.getContact().getName() + ": " + stateText;
            mChatStateLabel.setText(stateText);
            mStatusLabel.setVisible(stateText.isEmpty());
        }

        protected boolean contains(String search) {
            // TODO always show entry for current chat
            //Chat chat = mView.getCurrentShownChat().orElse(null);
            //if (chat != null && chat == mValue)
                return true;
        }
    }

    private static String lastActivity(Chat chat, boolean withLabel, boolean pretty) {
        KonMessage m = chat.getMessages().getLast().orElse(null);
        return m == null ? Tr.tr("No messages yet") :
                (withLabel ? Tr.tr("Last message:") + " " : "") +
                (pretty ? Utils.PRETTY_TIME.format(m.getDate()) :
                         Utils.MID_DATE_FORMAT.format(m.getDate()));
    }
}

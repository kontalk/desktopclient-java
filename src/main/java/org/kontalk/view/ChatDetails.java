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

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

import com.alee.extended.colorchooser.GradientData;
import com.alee.extended.filechooser.WebFileChooserField;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.GroupingType;
import com.alee.laf.button.WebButton;
import com.alee.laf.label.WebLabel;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.radiobutton.WebRadioButton;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.slider.WebSlider;
import com.alee.laf.text.WebTextArea;
import com.alee.managers.tooltip.TooltipManager;
import com.alee.utils.swing.UnselectableButtonGroup;
import org.apache.commons.lang.StringUtils;
import org.kontalk.model.chat.Chat;
import org.kontalk.model.chat.GroupChat;
import org.kontalk.model.chat.Member;
import org.kontalk.util.Tr;
import org.kontalk.view.ComponentUtils.MemberList;

/**
 * Show and edit thread/chat settings.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class ChatDetails extends ComponentUtils.PopupPanel {

    private static final Color DEFAULT_BG = Color.WHITE;

    private final View mView;
    private final Chat mChat;
    private final ComponentUtils.EditableTextField mSubjectField;
    private final WebRadioButton mColorOpt;
    private final WebButton mColor;
    private final WebRadioButton mImgOpt;
    private final WebFileChooserField mImgChooser;
    //WebCheckBoxList mParticipantsList;

    ChatDetails(View view, Chat chat) {
        mView = view;
        mChat = chat;

        GroupPanel groupPanel = new GroupPanel(View.GAP_BIG, false);
        groupPanel.setMargin(View.MARGIN_BIG);

        groupPanel.add(new WebLabel(mChat.isGroupChat()
                ? Tr.tr("Edit Group Chat")
                : Tr.tr("Edit Chat")).setBoldFont());
        groupPanel.add(new WebSeparator(true, true));

        // editable fields
        mSubjectField = new ComponentUtils.EditableTextField(mChat.getSubject(),
                View.MAX_SUBJ_LENGTH, mChat.isAdministratable(), 16, this);
        if (mChat instanceof GroupChat) {
            GroupChat groupChat = (GroupChat) mChat;
            groupPanel.add(new GroupPanel(View.GAP_DEFAULT,
                    new WebLabel(Tr.tr("Subject:")), mSubjectField));

            groupPanel.add(new WebLabel(Tr.tr("Participants:")));
            MemberList mParticipantsList = new MemberList(false);
            List<Member> chatMember = Utils.memberList(mChat);
            mParticipantsList.setMembers(chatMember);
            mParticipantsList.setVisibleRowCount(Math.min(chatMember.size(), 5));
            groupPanel.add(new ComponentUtils.ScrollPane(mParticipantsList, false).setPreferredWidth(160));

            WebButton leaveButton = new WebButton(Tr.tr("Leave group"));
            leaveButton.setEnabled(chat.isValid());
            TooltipManager.addTooltip(leaveButton,
                    groupChat.containsMe() ? Tr.tr("Leave this group chat")
                            : Tr.tr("You are not member of this group"));

            leaveButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    boolean left = ChatDetails.this.leave(groupChat);
                    if (left)
                        ChatDetails.this.setVisible(false);
                }
            });
            groupPanel.add(leaveButton);

            groupPanel.add(new WebSeparator(true, true));
        }

        final WebSlider colorSlider = new WebSlider(WebSlider.HORIZONTAL);

        groupPanel.add(new WebLabel(Tr.tr("Custom Background")));
        mColorOpt = new WebRadioButton(Tr.tr("Color:") + " ");
        Color bgColor = mChat.getViewSettings().getBGColor().orElse(null);
        mColorOpt.setSelected(bgColor != null);
        mColorOpt.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                colorSlider.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
            }
        });
        mColor = new WebButton();
        mColor.setMinimumHeight(25);
        Color oldColor = bgColor != null ? bgColor : DEFAULT_BG;
        mColor.setBottomBgColor(oldColor);
        groupPanel.add(new GroupPanel(GroupingType.fillLast,
                mColorOpt,
                mColor));

        colorSlider.setMinimum(0);
        colorSlider.setMaximum(100);
        colorSlider.setPaintTicks(false);
        colorSlider.setPaintLabels(false);
        colorSlider.setEnabled(bgColor != null);
        final GradientData gradientData = GradientData.getDefaultValue();
        // TODO set location for color
        gradientData.getColor(0);
        colorSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                float v = colorSlider.getValue() / (float) 100;
                Color c = gradientData.getColorForLocation(v);
                mColor.setBottomBgColor(c);
                mColor.repaint();
            }
        });
        groupPanel.add(colorSlider);

        mImgOpt = new WebRadioButton(Tr.tr("Image:") + " ");
        String imgPath = mChat.getViewSettings().getImagePath();
        mImgOpt.setSelected(!imgPath.isEmpty());
        mImgOpt.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                mImgChooser.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
                mImgChooser.getChooseButton().setEnabled(e.getStateChange() == ItemEvent.SELECTED);
            }
        });
        mImgChooser = Utils.createImageChooser(!imgPath.isEmpty(), imgPath);
        mImgChooser.setPreferredWidth(1);
        groupPanel.add(new GroupPanel(GroupingType.fillLast,
                mImgOpt,
                mImgChooser));
        UnselectableButtonGroup.group(mColorOpt, mImgOpt);
        groupPanel.add(new WebSeparator());

        String xmppID = mChat.getXMPPID();
        if (!xmppID.isEmpty()) {
            WebTextArea xmppIDArea = new WebTextArea().setBoldFont();
            xmppIDArea.setEditable(false);
            xmppIDArea.setOpaque(false);
            xmppIDArea.setText(StringUtils.abbreviate(xmppID, View.MAX_XMPP_ID_LENGTH));
            TooltipManager.addTooltip(xmppIDArea,
                    Tr.tr("XMPP chat ID:") + " " + xmppID);
            WebLabel xmppIDLabel = new WebLabel(Tr.tr("Chat ID:"));
            groupPanel.add(new GroupPanel(View.GAP_DEFAULT,
                    xmppIDLabel, xmppIDArea));
        }

        final WebButton saveButton = new WebButton(Tr.tr("Save"));
        this.add(groupPanel, BorderLayout.CENTER);

        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ChatDetails.this.save();
                ChatDetails.this.setVisible(false);
            }
        });
        //this.getRootPane().setDefaultButton(saveButton);

        GroupPanel buttonPanel = new GroupPanel(2, saveButton);
        buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
        this.add(buttonPanel, BorderLayout.SOUTH);
    }

    boolean leave(GroupChat chat) {
        String warningText =
                Tr.tr("You won't be able to enter this group again after you leave.");
        int selectedOption = WebOptionPane.showConfirmDialog(this,
                warningText,
                Tr.tr("Please Confirm"),
                WebOptionPane.OK_CANCEL_OPTION,
                WebOptionPane.WARNING_MESSAGE);

        if (selectedOption == WebOptionPane.OK_OPTION) {
            mView.getControl().leaveGroupChat(chat);
            return true;
        }
        return false;
    }

    private void save() {
        String subj = mSubjectField.getText();
        if (subj.length() > 0
                && !mSubjectField.getText().equals(mChat.getSubject())
                && mChat instanceof GroupChat) {
            mView.getControl().setChatSubject((GroupChat) mChat, mSubjectField.getText());
        }
//        List<?> participants = mParticipantsList.getCheckedValues();
//        Set<Contact> chatContact = new HashSet<>();
//        for (Object o: participants) {
//            chatContact.add(((ContactElement) o).contact);
//        }
//        mChat.setContact(chatContact);

        Chat.ViewSettings newSettings;
        if (mColorOpt.isSelected()) {
            newSettings = new Chat.ViewSettings(mColor.getBottomBgColor());
        } else if (mImgOpt.isSelected() && !mImgChooser.getSelectedFiles().isEmpty()) {
            newSettings = new Chat.ViewSettings(mImgChooser.getSelectedFiles().get(0).getAbsolutePath());
        } else {
            newSettings = new Chat.ViewSettings();
        }

        if (!newSettings.equals(mChat.getViewSettings())) {
            mChat.setViewSettings(newSettings);
        }
    }
}

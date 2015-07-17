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

import com.alee.extended.filechooser.WebFileChooserField;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.GroupingType;
import com.alee.laf.button.WebButton;
import com.alee.laf.colorchooser.WebColorChooserDialog;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.radiobutton.WebRadioButton;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.text.WebTextField;
import com.alee.utils.swing.DialogOptions;
import com.alee.utils.swing.UnselectableButtonGroup;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Optional;
import org.apache.commons.lang.StringUtils;
import org.kontalk.model.KonThread;
import org.kontalk.model.User;
import org.kontalk.util.Tr;

/**
 * Show and edit thread/chat settings.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class ThreadDetails extends WebPanel {

    private static final Color DEFAULT_BG = Color.WHITE;

    private final KonThread mThread;
    private final WebTextField mSubjectField;
    private final WebRadioButton mColorButton;
    private final WebButton mColorChooserButton;
    private final WebColorChooserDialog mColorChooser;
    private final WebRadioButton mImgButton;
    private final WebFileChooserField mImgChooser;
    // TODO group chat
    //WebCheckBoxList mParticipantsList;

    ThreadDetails(KonThread thread) {
        mThread = thread;

        GroupPanel groupPanel = new GroupPanel(View.GAP_BIG, false);
        groupPanel.setMargin(View.MARGIN_BIG);

        groupPanel.add(new WebLabel(Tr.tr("Edit Chat")).setBoldFont());
        groupPanel.add(new WebSeparator(true, true));

        // editable fields
        groupPanel.add(new WebLabel(Tr.tr("Subject:")));
        String subj = mThread.getSubject();
        mSubjectField = new WebTextField(subj, 22);
        mSubjectField.setInputPrompt(subj);
        mSubjectField.setHideInputPromptOnFocus(false);
        groupPanel.add(mSubjectField);
        groupPanel.add(new WebSeparator(true, true));

        groupPanel.add(new WebLabel(Tr.tr("Custom Background")));
        mColorButton = new WebRadioButton(Tr.tr("Color:")+" ");
        Optional<Color> optBGColor = mThread.getViewSettings().getBGColor();
        mColorButton.setSelected(optBGColor.isPresent());
        mColorButton.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                mColorChooserButton.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
            }
        });
        mColorChooserButton = new WebButton();
        mColorChooserButton.setEnabled(optBGColor.isPresent());
        mColorChooserButton.setMinimumHeight(25);
        Color oldColor = optBGColor.orElse(DEFAULT_BG);
        mColorChooserButton.setBottomBgColor(oldColor);
        mColorChooserButton.addActionListener(new ActionListener () {
            @Override
            public void actionPerformed(ActionEvent e ) {
                ThreadDetails.this.editColor();
            }
        } );
        mColorChooser = new WebColorChooserDialog(this);
        mColorChooser.setColor(oldColor);
        groupPanel.add(new GroupPanel(GroupingType.fillLast,
                mColorButton,
                mColorChooserButton));

        mImgButton = new WebRadioButton(Tr.tr("Image:")+" ");
        String imgPath = mThread.getViewSettings().getImagePath();
        mImgButton.setSelected(!imgPath.isEmpty());
        mImgButton.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                mImgChooser.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
                mImgChooser.getChooseButton().setEnabled(e.getStateChange() == ItemEvent.SELECTED);
            }
        });
        mImgChooser = Utils.createImageChooser(!imgPath.isEmpty(), imgPath);
        groupPanel.add(new GroupPanel(GroupingType.fillLast,
                mImgButton,
                mImgChooser));
        UnselectableButtonGroup.group(mColorButton, mImgButton);
        groupPanel.add(new WebSeparator());

//        groupPanel.add(new WebLabel(Tr.tr("Participants:")));
//        mParticipantsList = new WebCheckBoxList();
//        mParticipantsList.setVisibleRowCount(10);
//        for (User oneUser : UserList.getInstance().getAll()) {
//            boolean selected = mThread.getUser().contains(oneUser);
//            mParticipantsList.getCheckBoxListModel().addCheckBoxElement(
//                    new UserElement(oneUser),
//                    selected);
//        }
        final WebButton saveButton = new WebButton(Tr.tr("Save"));
//        mParticipantsList.getModel().addListDataListener(new ListDataListener() {
//            @Override
//            public void intervalAdded(ListDataEvent e) {
//            }
//            @Override
//            public void intervalRemoved(ListDataEvent e) {
//            }
//            @Override
//            public void contentsChanged(ListDataEvent e) {
//                saveButton.setEnabled(!mParticipantsList.getCheckedValues().isEmpty());
//            }
//        });
//
//        groupPanel.add(new WebScrollPane(mParticipantsList));
//        groupPanel.add(new WebSeparator(true, true));

        this.add(groupPanel, BorderLayout.CENTER);

        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
//                if (mParticipantsList.getCheckedValues().size() > 1) {
//                        String infoText = Tr.t/r("More than one receiver not supported (yet).");
//                        WebOptionPane.showMessageDialog(ThreadListView.this,
//                                infoText,
//                                Tr.t/r("Sorry"),
//                                WebOptionPane.INFORMATION_MESSAGE);
//                    return;
//                }
                ThreadDetails.this.saveThread();
//                ThreadDetails.this.dispose();
            }
        });
        //this.getRootPane().setDefaultButton(saveButton);

        GroupPanel buttonPanel = new GroupPanel(2, saveButton);
        buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
        this.add(buttonPanel, BorderLayout.SOUTH);
    }

    private void editColor() {
        mColorChooser.setVisible(true);
        if (mColorChooser.getResult () == DialogOptions.OK_OPTION) {
            mColorChooserButton.setBottomBgColor(mColorChooser.getColor());
        }
    }

    private void saveThread() {
        if (!mSubjectField.getText().equals(mThread.getSubject())) {
            mThread.setSubject(mSubjectField.getText());
        }
//        List<?> participants = mParticipantsList.getCheckedValues();
//        Set<User> threadUser = new HashSet<>();
//        for (Object o: participants) {
//            threadUser.add(((UserElement) o).user);
//        }
//        mThread.setUser(threadUser);

        KonThread.ViewSettings newSettings;
        if (mColorButton.isSelected())
            newSettings = new KonThread.ViewSettings(mColorChooser.getColor());
        else if (mImgButton.isSelected() && !mImgChooser.getSelectedFiles().isEmpty())
            newSettings = new KonThread.ViewSettings(mImgChooser.getSelectedFiles().get(0).getAbsolutePath());
        else
            newSettings = new KonThread.ViewSettings();

        if (!newSettings.equals(mThread.getViewSettings())) {
             mThread.setViewSettings(newSettings);
        }
    }

    private class UserElement {
        User user;

        UserElement(User user) {
            this.user = user;
        }

        @Override
        public String toString() {
            String jid = "<" + Utils.shortenJID(user.getJID(), 40) + ">";
            String name = StringUtils.abbreviate(user.getName(), 24);
            return name.isEmpty() ? jid : name +" " + jid;
        }
    }
}

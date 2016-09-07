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

import com.alee.extended.panel.GroupPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.label.WebLabel;
import com.alee.laf.list.WebList;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.text.WebTextField;
import com.alee.managers.tooltip.TooltipManager;
import org.kontalk.model.Model;
import org.kontalk.persistence.Config;
import org.kontalk.system.Control;
import org.kontalk.util.Tr;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.kontalk.client.FeatureDiscovery;
import org.kontalk.model.Avatar;
import org.kontalk.view.AvatarLoader.AvatarImg;

/**
 * The User profile page. With avatar and status text.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class ProfileDialog extends WebDialog {

    private final View mView;
    private final ComponentUtils.EditableAvatarImage mAvatarImage;
    private final WebTextField mStatusField;
    private final WebList mStatusList;

    ProfileDialog(View view, Model model) {
        mView = view;

        this.setTitle(Tr.tr("User Profile"));
        this.setResizable(false);
        this.setModal(true);

        GroupPanel groupPanel = new GroupPanel(View.GAP_DEFAULT, false);
        groupPanel.setMargin(View.MARGIN_BIG);

        groupPanel.add(new WebLabel(Tr.tr("Edit your profile")).setBoldFont());
        groupPanel.add(new WebSeparator(true, true));

        // avatar
        groupPanel.add(new WebLabel(Tr.tr("Your profile picture:")));

        // permanent, user has to re-open the dialog on change
        final boolean supported = mView.serverFeatures().contains(FeatureDiscovery.Feature.USER_AVATAR);
        mAvatarImage = new ComponentUtils.EditableAvatarImage(View.AVATAR_PROFILE_SIZE, supported,
                Avatar.UserAvatar.get().flatMap(userAvatar -> userAvatar.loadImage())) {
            @Override
            AvatarImg defaultImage() {
                return AvatarLoader.loadFallback(View.AVATAR_PROFILE_SIZE);
            }
            @Override
            boolean canRemove() {
                return Avatar.UserAvatar.get().isPresent();
            }
            @Override
            protected String tooltipText() {
                return supported ? super.tooltipText() :
                        mView.currentStatus() != Control.Status.CONNECTED ?
                        Tr.tr("Not connected") :
                        mView.tr_not_supported;
            }
        };

        //mAvatarImage.setDisplayType(DisplayType.fitComponent);
        //setTransferHandler ( new ImageDragHandler ( image1, i1 ) );

        groupPanel.add(mAvatarImage);
        groupPanel.add(new GroupPanel(
                new WebLabel(Tr.tr("Note:")+" ").setBoldFont(),
                new WebLabel(Tr.tr("the profile picture is publicly visible"))));
        groupPanel.add(new WebSeparator(true, true));

        // status text

        String[] strings = Config.getInstance().getStringArray(Config.NET_STATUS_LIST);
        List<String> stats = new ArrayList<>(Arrays.<String>asList(strings));
        String currentStatus = !stats.isEmpty() ? stats.remove(0) : "";

        groupPanel.add(new WebLabel(Tr.tr("Your current status:")));
        mStatusField = new WebTextField(currentStatus, 30);
        TooltipManager.addTooltip(mStatusField,
                Tr.tr("Set status text send to other user"));
        groupPanel.add(mStatusField);

        groupPanel.add(new WebLabel(Tr.tr("Previously used:")));
        mStatusList = new WebList(stats);
        mStatusList.setMultiplySelectionAllowed(false);
        mStatusList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting())
                    return;
                mStatusField.setText(mStatusList.getSelectedValue().toString());
            }
        });
        WebScrollPane listScrollPane = new ComponentUtils.ScrollPane(mStatusList);
        groupPanel.add(listScrollPane);
        this.add(groupPanel, BorderLayout.CENTER);

        // buttons
        WebButton cancelButton = new WebButton(Tr.tr("Cancel"));
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ProfileDialog.this.dispose();
            }
        });
        final WebButton saveButton = new WebButton(Tr.tr("Save"));
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ProfileDialog.this.save();
                ProfileDialog.this.dispose();
            }
        });
        this.getRootPane().setDefaultButton(saveButton);

        GroupPanel buttonPanel = new GroupPanel(2, cancelButton, saveButton);
        buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
        this.add(buttonPanel, BorderLayout.SOUTH);

        this.pack();
    }

    private void save() {
        if (mAvatarImage.imageChanged()) {
            BufferedImage img = mAvatarImage.getAvatarImage().orElse(null);
            if (img != null) {
                mView.getControl().setUserAvatar(img);
            } else {
                mView.getControl().unsetUserAvatar();
            }
        }

        mView.getControl().setStatusText(mStatusField.getText());
    }
}

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

import com.alee.extended.image.WebImage;
import com.alee.extended.panel.GroupPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.filechooser.WebFileChooser;
import com.alee.laf.label.WebLabel;
import com.alee.laf.list.WebList;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.text.WebTextField;
import com.alee.managers.tooltip.TooltipManager;
import com.alee.utils.ImageUtils;
import com.alee.utils.filefilter.ImageFilesFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.apache.commons.lang.ObjectUtils;
import org.kontalk.client.FeatureDiscovery;
import org.kontalk.model.Model;
import org.kontalk.persistence.Config;
import org.kontalk.system.Control;
import org.kontalk.util.MediaUtils;
import org.kontalk.util.Tr;

/**
 * The User profile page. With avatar and status text.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class ProfileDialog extends WebDialog {

    private static final int AVATAR_SIZE = 150;

    private final View mView;
    private final WebFileChooser mImgChooser;
    private final WebImage mAvatarImage;
    private final BufferedImage mOldImage;
    private final WebTextField mStatusField;
    private final WebList mStatusList;

    private BufferedImage mNewImage = null;

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
        mImgChooser = new WebFileChooser();
        mImgChooser.setFileFilter(new ImageFilesFilter());

        groupPanel.add(new WebLabel(Tr.tr("Your profile picture:")));

        mAvatarImage = new WebImage();
        mOldImage = mNewImage = model.userAvatar().loadImage().orElse(null);
        this.setImage(mOldImage);

        //mAvatarImage.setDisplayType(DisplayType.fitComponent);
        //setTransferHandler ( new ImageDragHandler ( image1, i1 ) );

        // permanent, user has to re-open the dialog on change
        final boolean supported = mView.serverFeatures().contains(FeatureDiscovery.Feature.USER_AVATAR);
        mAvatarImage.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                check(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                check(e);
            }
            private void check(MouseEvent e) {
                if (supported && e.isPopupTrigger()) {
                    ProfileDialog.this.showPopupMenu(e);
                }
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                if (supported && e.getButton() == MouseEvent.BUTTON1) {
                    ProfileDialog.this.chooseAvatar();
                }
            }
        });
        mAvatarImage.setEnabled(supported);
        if (!supported)
            TooltipManager.addTooltip(mAvatarImage,
                    mView.currentStatus() != Control.Status.CONNECTED ?
                            Tr.tr("Not connected") :
                            mView.tr_not_supported);

        groupPanel.add(mAvatarImage);
        groupPanel.add(new WebSeparator(true, true));

        // status text
        String[] strings = Config.getInstance().getStringArray(Config.NET_STATUS_LIST);
        List<String> stats = new ArrayList<>(Arrays.<String>asList(strings));
        String currentStatus = !stats.isEmpty() ? stats.remove(0) : "";

        groupPanel.add(new WebLabel(Tr.tr("Your current status:")));
        mStatusField = new WebTextField(currentStatus, 30);
        mStatusField.setToolTipText(Tr.tr("Set status text send to other user"));
        groupPanel.add(mStatusField);
        groupPanel.add(new WebSeparator(true, true));

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

    private void setImage(BufferedImage avatar) {
        mAvatarImage.setImage(avatar != null ?
                avatar :
                AvatarLoader.createFallback(AVATAR_SIZE));
        }

    private void chooseAvatar() {
        int state = mImgChooser.showOpenDialog(this);
        if (state != WebFileChooser.APPROVE_OPTION)
            return;

        File imgFile = mImgChooser.getSelectedFile();
        if (!imgFile.isFile())
            return;

        BufferedImage img = MediaUtils.readImage(imgFile).orElse(null);
        if (img == null)
            return;

        mNewImage = ImageUtils.createPreviewImage(img, AVATAR_SIZE);
        mAvatarImage.setImage(mNewImage);
    }

    private void save() {
        if (!ObjectUtils.equals(mOldImage, mNewImage)) {
            if (mNewImage != null) {
                mView.getControl().setUserAvatar(mNewImage);
            } else {
                mView.getControl().unsetUserAvatar();
            }
        }

        mView.getControl().setStatusText(mStatusField.getText());
    }

    private void showPopupMenu(MouseEvent e) {
        WebPopupMenu menu = new WebPopupMenu();
        WebMenuItem removeItem = new WebMenuItem(Tr.tr("Remove"));
        removeItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    ProfileDialog.this.setImage(mNewImage = null);
                }
            });
        removeItem.setEnabled(mNewImage != null);
        menu.add(removeItem);
        menu.show(mAvatarImage, e.getX(), e.getY());
    }
}

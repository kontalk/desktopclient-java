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
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.text.WebTextField;
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
import org.kontalk.system.Config;
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
    private final WebTextField mStatusField;
    private final WebList mStatusList;

    private BufferedImage mAvatar = null;

    ProfileDialog(View view) {
        mView = view;

        this.setTitle(Tr.tr("Status"));
        this.setResizable(false);
        this.setModal(true);

        GroupPanel groupPanel = new GroupPanel(View.GAP_DEFAULT, false);
        groupPanel.setMargin(View.MARGIN_BIG);

        groupPanel.add(new WebLabel(Tr.tr("Setup your profile")).setBoldFont());
        groupPanel.add(new WebSeparator(true, true));

        mImgChooser = new WebFileChooser();
        mImgChooser.setFileFilter(new ImageFilesFilter());

        groupPanel.add(new WebLabel(Tr.tr("Your profile picture:")));
        BufferedImage avatar = mView.getControl().getUserAvatar().orElse(null);
        mAvatarImage = new WebImage(avatar != null ?
                avatar :
                AvatarLoader.createFallback(AVATAR_SIZE));

        //mAvatarImage.setDisplayType(DisplayType.fitComponent);
        //setTransferHandler ( new ImageDragHandler ( image1, i1 ) );
        mAvatarImage.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    ProfileDialog.this.chooseAvatar();
                }
            }

        });
        groupPanel.add(mAvatarImage);
        groupPanel.add(new WebSeparator(true, true));

        String[] strings = Config.getInstance().getStringArray(Config.NET_STATUS_LIST);
        List<String> stats = new ArrayList<>(Arrays.<String>asList(strings));
        String currentStatus = "";
        if (!stats.isEmpty())
            currentStatus = stats.remove(0);

        stats.remove("");

        groupPanel.add(new WebLabel(Tr.tr("Your current status:")));
        mStatusField = new WebTextField(currentStatus, 30);
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
                ProfileDialog.this.saveStatus();
                ProfileDialog.this.dispose();
            }
        });
        this.getRootPane().setDefaultButton(saveButton);

        GroupPanel buttonPanel = new GroupPanel(2, cancelButton, saveButton);
        buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
        this.add(buttonPanel, BorderLayout.SOUTH);

        this.pack();
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

        mAvatar = ImageUtils.createPreviewImage(img, AVATAR_SIZE);

        mAvatarImage.setImage(mAvatar);
    }

    private void saveStatus() {
        if (mAvatar != null) {
            mView.getControl().setUserAvatar(mAvatar);
        }

        mView.getControl().setStatusText(mStatusField.getText());
    }

}

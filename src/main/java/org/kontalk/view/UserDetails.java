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

import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.GroupingType;
import com.alee.laf.button.WebButton;
import com.alee.laf.checkbox.WebCheckBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.text.WebTextArea;
import com.alee.laf.text.WebTextField;
import com.alee.managers.tooltip.TooltipManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Observable;
import java.util.Observer;
import javax.swing.Box;
import javax.swing.SwingUtilities;
import org.kontalk.model.User;
import org.kontalk.util.Tr;

/**
 * Show and edit contact details.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class UserDetails extends WebPanel implements Observer {

    private final View mView;
    private final User mUser;
    private final WebTextField mNameField;
    private final WebLabel mKeyLabel;
    private final WebLabel mFPLabel;
    private final WebTextArea mFPArea;
    private final WebCheckBox mEncryptionBox;
    private String mJID;

    UserDetails(View view, User user) {
        mView = view;
        mUser = user;

        GroupPanel groupPanel = new GroupPanel(15, false);
        groupPanel.setMargin(15);

        groupPanel.add(new WebLabel(Tr.tr("Contact details")).setBoldFont());
        groupPanel.add(new WebSeparator(true, true));

        // editable fields
        WebPanel namePanel = new WebPanel();
        namePanel.setLayout(new BorderLayout(10, 5));
        namePanel.add(new WebLabel(Tr.tr("Display Name:")), BorderLayout.WEST);
        mNameField = new WebTextField();
        mNameField.setHideInputPromptOnFocus(false);
        namePanel.add(mNameField, BorderLayout.CENTER);
        groupPanel.add(namePanel);

        final int l = 50;
        mJID = mUser.getJID();
        final WebTextField jidField = new WebTextField(Utils.shortenJID(mJID, l));
        jidField.setDrawBorder(false);
        jidField.setMinimumHeight(20);
        jidField.setInputPrompt(mJID);
        jidField.setHideInputPromptOnFocus(false);
        jidField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                jidField.setText(mJID);
                jidField.setDrawBorder(true);
            }
            @Override
            public void focusLost(FocusEvent e) {
                mJID = jidField.getText();
                jidField.setText(Utils.shortenJID(mJID, l));
                jidField.setDrawBorder(false);
            }
        });
        String jidText = Tr.tr("The unique address of this contact");
        TooltipManager.addTooltip(jidField, jidText);
        groupPanel.add(new GroupPanel(GroupingType.fillLast,
                10,
                new WebLabel("JID:"),
                jidField));

        groupPanel.add(new WebSeparator(true, true));

        mKeyLabel = new WebLabel();
        WebButton updButton = new WebButton(Utils.getIcon("ic_ui_refresh.png"));
        String updText = Tr.tr("Update key");
        TooltipManager.addTooltip(updButton, updText);
        updButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mView.callRequestKey(UserDetails.this.mUser);
            }
        });
        groupPanel.add(new GroupPanel(6, mKeyLabel, updButton));

        mFPLabel = new WebLabel(Tr.tr("Fingerprint:")+" ");
        mFPArea = Utils.createFingerprintArea();
        String fpText = Tr.tr("The unique ID of this contact's key");
        TooltipManager.addTooltip(mFPArea, fpText);
        mFPLabel.setAlignmentY(Component.TOP_ALIGNMENT);
        GroupPanel fpLabelPanel = new GroupPanel(false, mFPLabel, Box.createGlue());
        groupPanel.add(new GroupPanel(10, fpLabelPanel, mFPArea));
        this.updateOnEDT();

        mEncryptionBox = new WebCheckBox(Tr.tr("Use Encryption"));
        mEncryptionBox.setAnimated(false);
        mEncryptionBox.setSelected(mUser.getEncrypted());
        String encText = Tr.tr("Encrypt and sign all messages send to this contact");
        TooltipManager.addTooltip(mEncryptionBox, encText);
        groupPanel.add(new GroupPanel(mEncryptionBox, Box.createGlue()));

        this.add(groupPanel, BorderLayout.CENTER);
    }

    @Override
    public void update(Observable o, final Object arg) {
        if (SwingUtilities.isEventDispatchThread()) {
            this.updateOnEDT();
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                UserDetails.this.updateOnEDT();
            }
        });
    }

    private void updateOnEDT() {
        // may have changed: user name and/or key
        mNameField.setText(mUser.getName());
        mNameField.setInputPrompt(mUser.getName());
        String hasKey = "<html>"+Tr.tr("Encryption Key")+": ";
        if (mUser.hasKey()) {
            hasKey += Tr.tr("Available")+"</html>";
            TooltipManager.removeTooltips(mKeyLabel);
            mFPArea.setText(Utils.formatFingerprint(mUser.getFingerprint()));
            mFPLabel.setVisible(true);
            mFPArea.setVisible(true);
        } else {
            hasKey += "<font color='red'>"+Tr.tr("Not Available")+"</font></html>";
            String keyText = Tr.tr("The key for this user could not yet be received");
            TooltipManager.addTooltip(mKeyLabel, keyText);
            mFPLabel.setVisible(false);
            mFPArea.setVisible(false);
        }
        mKeyLabel.setText(hasKey);
    }

    private void save() {
        String newName = mNameField.getText();
        if (!newName.equals(mUser.getName())) {
            mUser.setName(mNameField.getText());
        }
        mUser.setEncrypted(mEncryptionBox.isSelected());
        if (!mJID.isEmpty() && !mJID.equals(mUser.getJID())) {
            String warningText =
                    Tr.tr("Changing the JID is only useful in very rare cases. Are you sure?");
            int selectedOption = WebOptionPane.showConfirmDialog(this,
                    warningText,
                    Tr.tr("Please Confirm"),
                    WebOptionPane.OK_CANCEL_OPTION,
                    WebOptionPane.WARNING_MESSAGE);
            if (selectedOption == WebOptionPane.OK_OPTION)
                mUser.setJID(mJID);
        }
    }

    void onClose() {
        this.save();

        this.mUser.deleteObserver(this);
    }
}

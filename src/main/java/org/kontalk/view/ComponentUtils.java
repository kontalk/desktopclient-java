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
import com.alee.laf.list.WebList;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.text.WebPasswordField;
import com.alee.laf.text.WebTextField;
import com.alee.managers.tooltip.TooltipManager;
import com.alee.utils.swing.DocumentChangeListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.kontalk.system.Config;
import org.kontalk.util.Tr;
import org.kontalk.util.XMPPUtils;

/**
 * Some own component classes used in view.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class ComponentUtils {

    private ComponentUtils() {}

    static class StatusDialog extends WebDialog {

        private final View mView;
        private final WebTextField mStatusField;
        private final WebList mStatusList;

        StatusDialog(View view) {
            mView = view;

            this.setTitle(Tr.tr("Status"));
            this.setResizable(false);
            this.setModal(true);

            GroupPanel groupPanel = new GroupPanel(View.GAP_DEFAULT, false);
            groupPanel.setMargin(View.MARGIN_BIG);

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
            WebScrollPane listScrollPane = new ScrollPane(mStatusList);
            groupPanel.add(listScrollPane);
            this.add(groupPanel, BorderLayout.CENTER);

            // buttons
            WebButton cancelButton = new WebButton(Tr.tr("Cancel"));
            cancelButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    StatusDialog.this.dispose();
                }
            });
            final WebButton saveButton = new WebButton(Tr.tr("Save"));
            saveButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    StatusDialog.this.saveStatus();
                    StatusDialog.this.dispose();
                }
            });
            this.getRootPane().setDefaultButton(saveButton);

            GroupPanel buttonPanel = new GroupPanel(2, cancelButton, saveButton);
            buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
            this.add(buttonPanel, BorderLayout.SOUTH);

            this.pack();
        }

        private void saveStatus() {
            String newStatus = mStatusField.getText();

            Config conf = Config.getInstance();
            String[] strings = conf.getStringArray(Config.NET_STATUS_LIST);
            List<String> stats = new ArrayList<>(Arrays.asList(strings));

            stats.remove(newStatus);

            stats.add(0, newStatus);

            if (stats.size() > 20)
                stats = stats.subList(0, 20);

            conf.setProperty(Config.NET_STATUS_LIST, stats.toArray());
            mView.getControl().sendStatusText();
        }
    }

    static class AddUserPanel extends WebPanel {

        private final View mView;
        private final WebTextField mNameField;
        private final WebTextField mJIDField;
        private final WebCheckBox mEncryptionBox;

        AddUserPanel(View view, final Component focusGainer) {
            mView = view;

            GroupPanel groupPanel = new GroupPanel(View.GAP_BIG, false);
            groupPanel.setMargin(View.MARGIN_BIG);

            groupPanel.add(new WebLabel(Tr.tr("Add Contact")).setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            final WebButton mSaveButton = new WebButton(Tr.tr("Create"));

            // editable fields
            mNameField = new WebTextField(20);
            mNameField.getDocument().addDocumentListener(new DocumentChangeListener() {
                @Override
                public void documentChanged(DocumentEvent e) {
                    mSaveButton.setEnabled(XMPPUtils.isValid(mJIDField.getText()));
                }
            });
            groupPanel.add(new GroupPanel(View.GAP_DEFAULT,
                    new WebLabel(Tr.tr("Name:")), mNameField));

            mJIDField = new WebTextField();
            mJIDField.setInputPrompt("username@jabber-server.com");
            mJIDField.getDocument().addDocumentListener(new DocumentChangeListener() {
                @Override
                public void documentChanged(DocumentEvent e) {
                    mSaveButton.setEnabled(XMPPUtils.isValid(mJIDField.getText()));
                }
            });
            groupPanel.add(new GroupPanel(GroupingType.fillLast, View.GAP_DEFAULT,
                    new WebLabel("Jabber ID:"), mJIDField));
            groupPanel.add(new WebSeparator(true, true));

            mEncryptionBox = new WebCheckBox(Tr.tr("Encryption"));
            mEncryptionBox.setAnimated(false);
            mEncryptionBox.setSelected(true);
            groupPanel.add(mEncryptionBox);
            groupPanel.add(new WebSeparator(true, true));

            this.add(groupPanel, BorderLayout.CENTER);

            mSaveButton.setEnabled(false);
            mSaveButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    AddUserPanel.this.saveUser(mJIDField.getText(),
                            mNameField.getText(),
                            mEncryptionBox.isSelected());
                    focusGainer.requestFocus();
                }
            });

            GroupPanel buttonPanel = new GroupPanel(mSaveButton);
            buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
            this.add(buttonPanel, BorderLayout.SOUTH);
        }

        void saveUser(String name, String jid, boolean encrypt) {
            mView.getControl().createNewUser(jid, name, encrypt);
        }
    }

    abstract static class PassPanel extends WebPanel {

        private final boolean mPassSet;
        private final WebCheckBox mSetPass;
        private final WebPasswordField mOldPassField;
        private final WebLabel mWrongPassLabel;
        private final WebPasswordField mNewPassField;
        private final WebPasswordField mConfirmPassField;

        PassPanel(boolean passSet) {
            mPassSet = passSet;

            GroupPanel groupPanel = new GroupPanel(View.GAP_DEFAULT, false);
            groupPanel.setMargin(View.MARGIN_SMALL);

            DocumentListener docListener = new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    PassPanel.this.checkDoneButton();
                }
                @Override
                public void removeUpdate(DocumentEvent e) {
                    PassPanel.this.checkDoneButton();
                }
                @Override
                public void changedUpdate(DocumentEvent e) {
                    PassPanel.this.checkDoneButton();
                }
            };

            mOldPassField = new WebPasswordField(30);
            mWrongPassLabel = new WebLabel(Tr.tr("Wrong password"));
            if (mPassSet) {
                groupPanel.add(new WebLabel(Tr.tr("Current password:")));
                mOldPassField.getDocument().addDocumentListener(docListener);
                groupPanel.add(mOldPassField);
                mWrongPassLabel.setBoldFont();
                mWrongPassLabel.setForeground(Color.RED);
                mWrongPassLabel.setVisible(false);
                groupPanel.add(mWrongPassLabel);
                groupPanel.add(new WebSeparator());
            }

            mSetPass = new WebCheckBox(Tr.tr("Set key password"));
            String setPassText = Tr.tr("If not set, key is saved unprotected!");
            TooltipManager.addTooltip(mSetPass, setPassText);
            groupPanel.add(new GroupPanel(mSetPass, new WebSeparator()));
            mSetPass.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    boolean selected = e.getStateChange() == ItemEvent.SELECTED;
                    mNewPassField.setEnabled(selected);
                    mConfirmPassField.setEnabled(selected);
                    PassPanel.this.checkDoneButton();
                }
            });
            mNewPassField = new WebPasswordField(30);
            mNewPassField.setInputPrompt(Tr.tr("Enter new password"));
            mNewPassField.setEnabled(false);
            mNewPassField.setHideInputPromptOnFocus(false);
            mNewPassField.getDocument().addDocumentListener(docListener);
            groupPanel.add(mNewPassField);
            mConfirmPassField = new WebPasswordField(30);
            mConfirmPassField.setInputPrompt(Tr.tr("Confirm password"));
            mConfirmPassField.setEnabled(false);
            mConfirmPassField.setHideInputPromptOnFocus(false);
            mConfirmPassField.getDocument().addDocumentListener(docListener);
            groupPanel.add(mConfirmPassField);

            this.checkDoneButton();

            this.add(groupPanel);
        }

        private void checkDoneButton() {
            if (mPassSet && mOldPassField.getPassword().length < 1) {
                this.onInvalidInput();
                return;
            }
            if (!mSetPass.isSelected()) {
                this.onValidInput();
                return;
            }
            char[] newPass = mNewPassField.getPassword();
            if (newPass.length > 0 &&
                    Arrays.equals(newPass, mConfirmPassField.getPassword())) {
                this.onValidInput();
            } else {
                this.onInvalidInput();
            }
        }

        char[] getOldPassword() {
            return mOldPassField.getPassword();
        }

        Optional<char[]> getNewPassword() {
            if (!mSetPass.isSelected())
                return Optional.of(new char[0]);

            char[] newPass = mNewPassField.getPassword();
            // better check again
            if (!Arrays.equals(newPass, mConfirmPassField.getPassword()))
                Optional.empty();

            return Optional.of(newPass);
        }

        void showWrongPassword() {
            mWrongPassLabel.setVisible(true);
        }

        abstract void onValidInput();

        abstract void onInvalidInput();
    }

    abstract static class EditableTextField extends WebTextField {

        public EditableTextField(int width, final Component focusGainer) {
            super(width, false);

            this.setMinimumHeight(20);
            this.setHideInputPromptOnFocus(false);
            this.addFocusListener(new FocusListener() {
                @Override
                public void focusGained(FocusEvent e) {
                    EditableTextField.this.setEdit();
                }
                @Override
                public void focusLost(FocusEvent e) {
                    EditableTextField.this.onFocusLost();
                    EditableTextField.this.setLabel();
                }
            });
            this.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    focusGainer.requestFocus();
                }
            });

            this.setLabel();
        }

        private void setEdit() {
            String text = this.editText();
            this.setInputPrompt(text);
            this.setText(text);
            this.setDrawBorder(true);
        }

        private void setLabel() {
            this.setText(this.labelText());
            this.setDrawBorder(false);
        }

        abstract protected String labelText();

        abstract protected String editText();

        abstract protected void onFocusLost();
    }
}

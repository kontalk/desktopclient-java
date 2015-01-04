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
import com.alee.laf.button.WebButton;
import com.alee.laf.checkbox.WebCheckBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.tabbedpane.WebTabbedPane;
import com.alee.laf.text.WebFormattedTextField;
import com.alee.laf.text.WebTextField;
import com.alee.managers.tooltip.TooltipManager;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.text.DecimalFormat;
import javax.swing.JFrame;
import org.kontalk.KonConf;
import org.kontalk.KonException;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.model.Account;

/**
 * Dialog for showing and changing all application options.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
final class ConfigurationDialog extends WebDialog {

    private static enum ConfPage {MAIN, ACCOUNT};

    private final KonConf mConf = KonConf.getInstance();
    private final View mViewModel;

    ConfigurationDialog(JFrame owner, final View viewModel) {
        super(owner);

        mViewModel = viewModel;
        this.setTitle("Preferences");
        this.setSize(550, 500);
        this.setResizable(false);
        this.setModal(true);
        this.setLayout(new BorderLayout(5, 5));

        WebTabbedPane tabbedPane = new WebTabbedPane(WebTabbedPane.LEFT);
        final MainPanel mainPanel = new MainPanel();
        final AccountPanel accountPanel = new AccountPanel();
        final PrivacyPanel privacyPanel = new PrivacyPanel();
        tabbedPane.addTab("Main", mainPanel);
        tabbedPane.addTab("Account", accountPanel);
        tabbedPane.addTab("Privacy", privacyPanel);

        this.add(tabbedPane, BorderLayout.CENTER);

        // buttons
        WebButton cancelButton = new WebButton("Cancel");
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ConfigurationDialog.this.dispose();
            }
        });
        WebButton saveButton = new WebButton("Save");
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mainPanel.saveConfiguration();
                accountPanel.saveConfiguration();
                privacyPanel.saveConfiguration();
                ConfigurationDialog.this.dispose();
            }
        });

        GroupPanel buttonPanel = new GroupPanel(2, cancelButton, saveButton);
        buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
        this.add(buttonPanel, BorderLayout.SOUTH);
    }

    private class MainPanel extends WebPanel {

        WebCheckBox mConnectStartupBox;
        WebCheckBox mTrayBox;
        WebCheckBox mCloseTrayBox;
        WebCheckBox mEnterSendsBox;

        public MainPanel() {
            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            groupPanel.add(new WebLabel("Main Settings").setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            mConnectStartupBox = new WebCheckBox("Connect on startup");
            mConnectStartupBox.setSelected(false);
            mConnectStartupBox.setSelected(mConf.getBoolean(KonConf.MAIN_CONNECT_STARTUP));
            groupPanel.add(mConnectStartupBox);

            mCloseTrayBox = new WebCheckBox("Close to tray");
            mCloseTrayBox.setAnimated(false);
            mCloseTrayBox.setSelected(mConf.getBoolean(KonConf.MAIN_TRAY_CLOSE));

            mTrayBox = new WebCheckBox("Show tray icon");
            mTrayBox.setAnimated(false);
            mTrayBox.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    mCloseTrayBox.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
                }
            });
            mTrayBox.setSelected(mConf.getBoolean(KonConf.MAIN_TRAY));

            GroupPanel buttonPanel = new GroupPanel(10, mTrayBox, mCloseTrayBox);
            groupPanel.add(buttonPanel);

            mEnterSendsBox = new WebCheckBox("Enter key sends");
            mEnterSendsBox.setAnimated(false);
            mEnterSendsBox.setSelected(mConf.getBoolean(KonConf.MAIN_ENTER_SENDS));
            String enterSendsToolText = "Enter key sends text, Control+Enter adds new line "
                    + "- or vice versa";
            TooltipManager.addTooltip(mEnterSendsBox, enterSendsToolText);
            groupPanel.add(mEnterSendsBox);

            this.add(groupPanel);
        }

        private void saveConfiguration() {
            mConf.setProperty(KonConf.MAIN_CONNECT_STARTUP, mConnectStartupBox.isSelected());
            mConf.setProperty(KonConf.MAIN_TRAY, mTrayBox.isSelected());
            mConf.setProperty(KonConf.MAIN_TRAY_CLOSE, mCloseTrayBox.isSelected());
            mViewModel.setTray();
            mConf.setProperty(KonConf.MAIN_ENTER_SENDS, mEnterSendsBox.isSelected());
            mViewModel.setHotkeys();
        }
    }

    private class AccountPanel extends WebPanel {

        private final WebTextField mServerField;
        private final WebLabel mFingerprintLabel;

        public AccountPanel() {
            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            groupPanel.add(new WebLabel("Account Configuration").setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            // server text field
            groupPanel.add(new WebLabel("Server address:"));
            GroupPanel serverPanel = new GroupPanel(5);
            mServerField = new WebTextField(mConf.getString(KonConf.SERV_HOST), 24);
            mServerField.setInputPrompt(KonConf.DEFAULT_SERV_HOST);
            mServerField.setInputPromptFont(mServerField.getFont().deriveFont(Font.ITALIC));
            mServerField.setHideInputPromptOnFocus(false);
            serverPanel.add(mServerField);
            serverPanel.add(new WebLabel(" Port:"));
            int port = mConf.getInt(KonConf.SERV_PORT, KonConf.DEFAULT_SERV_PORT);
            // TODO min / max value
            // SpinnerModel spinnerModel = new SpinnerNumberModel(port, 1, 65535, 1);
            WebFormattedTextField portField = new WebFormattedTextField(new DecimalFormat("#####"));
            portField.setColumns(5);
            portField.setValue(port);
            serverPanel.add(portField);
            groupPanel.add(serverPanel);

            groupPanel.add(new WebSeparator(true, true));
            mFingerprintLabel = new WebLabel();
            this.updateFingerprint();
            groupPanel.add(mFingerprintLabel);

            WebButton importButton = new WebButton("Import new Account");
            importButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    mViewModel.showImportWizard();
                    AccountPanel.this.updateFingerprint();
                }
            });
            groupPanel.add(importButton);

            this.add(groupPanel, BorderLayout.CENTER);


            WebButton okButton = new WebButton("Save & Connect");
            okButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    AccountPanel.this.saveConfiguration();
                    ConfigurationDialog.this.dispose();
                    mViewModel.connect();
                }
            });

            GroupPanel buttonPanel = new GroupPanel(okButton);
            buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
            this.add(buttonPanel, BorderLayout.SOUTH);
        }

        private void updateFingerprint() {
            PersonalKey personalKey = null;
            try {
                personalKey = Account.getInstance().getPersonalKey();
            } catch (KonException ex) {
                // ignore
            }
            String fingerprint = "- no key loaded -";
            if (personalKey != null)
                fingerprint = personalKey.getFingerprint();
            mFingerprintLabel.setText("Key fingerprint: "+fingerprint);
        }

        private void saveConfiguration() {
            mConf.setProperty(KonConf.SERV_HOST, mServerField.getText());
        }

    }

    private class PrivacyPanel extends WebPanel {

        WebCheckBox mChatStateBox;

        public PrivacyPanel() {
            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            groupPanel.add(new WebLabel("Privacy Settings").setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            mChatStateBox = new WebCheckBox("Send chatstate notification");
            mChatStateBox.setAnimated(false);
            mChatStateBox.setSelected(mConf.getBoolean(KonConf.NET_SEND_CHAT_STATE));

            groupPanel.add(mChatStateBox);

            this.add(groupPanel);
        }

        private void saveConfiguration() {
            mConf.setProperty(KonConf.NET_SEND_CHAT_STATE, mChatStateBox.isSelected());
        }
    }
}

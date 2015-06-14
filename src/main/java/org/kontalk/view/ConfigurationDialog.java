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
import java.text.NumberFormat;
import java.util.Optional;
import javax.swing.JFrame;
import javax.swing.text.NumberFormatter;
import org.kontalk.system.Config;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.system.AccountLoader;
import org.kontalk.util.Tr;

/**
 * Dialog for showing and changing all application options.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
final class ConfigurationDialog extends WebDialog {

    private static enum ConfPage {MAIN, ACCOUNT};

    private final Config mConf = Config.getInstance();
    private final View mView;

    ConfigurationDialog(JFrame owner, final View view) {
        super(owner);

        mView = view;
        this.setTitle(Tr.tr("Preferences"));
        this.setSize(550, 500);
        this.setResizable(false);
        this.setModal(true);
        this.setLayout(new BorderLayout(5, 5));

        WebTabbedPane tabbedPane = new WebTabbedPane(WebTabbedPane.LEFT);
        final MainPanel mainPanel = new MainPanel();
        final AccountPanel accountPanel = new AccountPanel();
        final PrivacyPanel privacyPanel = new PrivacyPanel();
        tabbedPane.addTab(Tr.tr("Main"), mainPanel);
        tabbedPane.addTab(Tr.tr("Account"), accountPanel);
        tabbedPane.addTab(Tr.tr("Privacy"), privacyPanel);

        this.add(tabbedPane, BorderLayout.CENTER);

        // buttons
        WebButton cancelButton = new WebButton(Tr.tr("Cancel"));
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ConfigurationDialog.this.dispose();
            }
        });
        WebButton saveButton = new WebButton(Tr.tr("Save"));
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

        private final WebCheckBox mConnectStartupBox;
        private final WebCheckBox mTrayBox;
        private final WebCheckBox mCloseTrayBox;
        private final WebCheckBox mEnterSendsBox;
        private final WebCheckBox mBGBox;
        private final WebFileChooserField mBGChooser;

        MainPanel() {
            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            groupPanel.add(new WebLabel(Tr.tr("Main Settings")).setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            mConnectStartupBox = new WebCheckBox(Tr.tr("Connect on startup"));
            mConnectStartupBox.setAnimated(false);
            mConnectStartupBox.setSelected(mConf.getBoolean(Config.MAIN_CONNECT_STARTUP));
            groupPanel.add(mConnectStartupBox);

            mTrayBox = new WebCheckBox(Tr.tr("Show tray icon"));
            mTrayBox.setAnimated(false);
            mTrayBox.setSelected(mConf.getBoolean(Config.MAIN_TRAY));
            mTrayBox.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    mCloseTrayBox.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
                }
            });
            mCloseTrayBox = new WebCheckBox(Tr.tr("Close to tray"));
            mCloseTrayBox.setAnimated(false);
            mCloseTrayBox.setSelected(mConf.getBoolean(Config.MAIN_TRAY_CLOSE));
            mCloseTrayBox.setEnabled(mTrayBox.isSelected());
            groupPanel.add(new GroupPanel(10, mTrayBox, mCloseTrayBox));

            mEnterSendsBox = new WebCheckBox(Tr.tr("Enter key sends"));
            mEnterSendsBox.setAnimated(false);
            mEnterSendsBox.setSelected(mConf.getBoolean(Config.MAIN_ENTER_SENDS));
            String enterSendsToolText =
                    Tr.tr("Enter key sends text, Control+Enter adds new line - or vice versa");
            TooltipManager.addTooltip(mEnterSendsBox, enterSendsToolText);
            groupPanel.add(new GroupPanel(mEnterSendsBox, new WebSeparator()));

            mBGBox = new WebCheckBox(Tr.tr("Custom background:")+" ");
            mBGBox.setAnimated(false);
            String bgPath = mConf.getString(Config.VIEW_THREAD_BG);
            mBGBox.setSelected(!bgPath.isEmpty());
            mBGBox.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    mBGChooser.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
                    mBGChooser.getChooseButton().setEnabled(e.getStateChange() == ItemEvent.SELECTED);
                }
            });

            mBGChooser = View.createImageChooser(mBGBox.isSelected(), bgPath);

            groupPanel.add(new GroupPanel(GroupingType.fillLast, mBGBox, mBGChooser));

            this.add(groupPanel);
        }

        private void saveConfiguration() {
            mConf.setProperty(Config.MAIN_CONNECT_STARTUP, mConnectStartupBox.isSelected());
            mConf.setProperty(Config.MAIN_TRAY, mTrayBox.isSelected());
            mConf.setProperty(Config.MAIN_TRAY_CLOSE, mCloseTrayBox.isSelected());
            mView.setTray();
            mConf.setProperty(Config.MAIN_ENTER_SENDS, mEnterSendsBox.isSelected());
            mView.setHotkeys();
            String bgPath;
            if (mBGBox.isSelected() && !mBGChooser.getSelectedFiles().isEmpty()) {
                bgPath = mBGChooser.getSelectedFiles().get(0).getAbsolutePath();
            } else {
                bgPath = "";
            }
            String oldBGPath = mConf.getString(Config.VIEW_THREAD_BG);
            if (!bgPath.equals(oldBGPath)) {
                mConf.setProperty(Config.VIEW_THREAD_BG, bgPath);
                mView.reloadThreadBG();
            }
        }
    }

    private class AccountPanel extends WebPanel {

        private final WebTextField mServerField;
        private final WebFormattedTextField mPortField;
        private final WebCheckBox mDisableCertBox;
        private final WebTextField mFingerprintField;

        AccountPanel() {
            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            groupPanel.add(new WebLabel(Tr.tr("Account Configuration")).setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            // server text field
            groupPanel.add(new WebLabel(Tr.tr("Server address:")));
            WebPanel serverPanel = new WebPanel(false);
            mServerField = new WebTextField(mConf.getString(Config.SERV_HOST));
            mServerField.setInputPrompt(Config.DEFAULT_SERV_HOST);
            mServerField.setInputPromptFont(mServerField.getFont().deriveFont(Font.ITALIC));
            mServerField.setHideInputPromptOnFocus(false);
            serverPanel.add(mServerField);
            int port = mConf.getInt(Config.SERV_PORT, Config.DEFAULT_SERV_PORT);
            NumberFormat format = new DecimalFormat("#####");
            NumberFormatter formatter = new NumberFormatter(format);
            formatter.setMinimum(1);
            formatter.setMaximum(65535);
            mPortField = new WebFormattedTextField(formatter);
            mPortField.setColumns(4);
            mPortField.setValue(port);
            serverPanel.add(new GroupPanel(new WebLabel("  "+Tr.tr("Port:")), mPortField),
                    BorderLayout.EAST);
            groupPanel.add(serverPanel);
            mDisableCertBox = new WebCheckBox(Tr.tr("Disable certificate validation"));
            mDisableCertBox.setAnimated(false);
            mDisableCertBox.setSelected(!mConf.getBoolean(Config.SERV_CERT_VALIDATION));
            String disableCertText = Tr.tr("Disable SSL certificate server validation");
            TooltipManager.addTooltip(mDisableCertBox, disableCertText);
            groupPanel.add(new GroupPanel(mDisableCertBox, new WebSeparator()));

            groupPanel.add(new WebSeparator(true, true));
            mFingerprintField = View.createTextField("");
            this.updateFingerprint();
            WebLabel fpLabel = new WebLabel(Tr.tr("Key fingerprint:")+" ");
            groupPanel.add(new GroupPanel(fpLabel, mFingerprintField));

            WebButton importButton = new WebButton(Tr.tr("Import new Account"));
            importButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    mView.showImportWizard(false);
                    AccountPanel.this.updateFingerprint();
                }
            });
            groupPanel.add(importButton);

            this.add(groupPanel, BorderLayout.CENTER);


            WebButton okButton = new WebButton(Tr.tr("Save & Connect"));
            okButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    AccountPanel.this.saveConfiguration();
                    ConfigurationDialog.this.dispose();
                    mView.callConnect();
                }
            });

            GroupPanel buttonPanel = new GroupPanel(okButton);
            buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
            this.add(buttonPanel, BorderLayout.SOUTH);
        }

        private void updateFingerprint() {
            Optional<PersonalKey> optKey = AccountLoader.getInstance().getPersonalKey();
            mFingerprintField.setText(optKey.isPresent() ?
                    optKey.get().getFingerprint() :
                    "- " + Tr.tr("no key loaded") + " -");
        }

        private void saveConfiguration() {
            mConf.setProperty(Config.SERV_HOST, mServerField.getText());
            int port = Integer.parseInt(mPortField.getText());
            mConf.setProperty(Config.SERV_PORT, port);
            mConf.setProperty(Config.SERV_CERT_VALIDATION, !mDisableCertBox.isSelected());
        }

    }

    private class PrivacyPanel extends WebPanel {

        private final WebCheckBox mChatStateBox;

        PrivacyPanel() {
            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            groupPanel.add(new WebLabel(Tr.tr("Privacy Settings")).setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            mChatStateBox = new WebCheckBox(Tr.tr("Send chatstate notification"));
            mChatStateBox.setAnimated(false);
            mChatStateBox.setSelected(mConf.getBoolean(Config.NET_SEND_CHAT_STATE));
            String chatStateText = Tr.tr("Send chat activity (typing,...) to other user");
            TooltipManager.addTooltip(mChatStateBox, chatStateText);
            groupPanel.add(new GroupPanel(mChatStateBox, new WebSeparator()));

            this.add(groupPanel);
        }

        private void saveConfiguration() {
            mConf.setProperty(Config.NET_SEND_CHAT_STATE, mChatStateBox.isSelected());
        }
    }
}

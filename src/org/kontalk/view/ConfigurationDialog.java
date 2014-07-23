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

import com.alee.extended.filechooser.FilesSelectionListener;
import com.alee.extended.filechooser.WebFileChooserField;
import com.alee.extended.panel.GroupPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.checkbox.WebCheckBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.tabbedpane.WebTabbedPane;
import com.alee.laf.text.WebTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.List;
import javax.swing.JFrame;
import org.kontalk.KonConf;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public final class ConfigurationDialog extends WebDialog {

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

    private static WebFileChooserField createFileChooser(String path) {
        final WebFileChooserField fileChooser = new WebFileChooserField();
        fileChooser.setPreferredWidth(100);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setShowFileShortName(false);
        fileChooser.setShowRemoveButton(false);
        // TODO does not work?
        //fileChooser.getWebFileChooser().setFileSelectionMode(JFileChooser.FILES_ONLY);
        File file = new File(path);
        if (file.exists()) {
            fileChooser.setSelectedFile(new File(path));
        } else {
            fileChooser.setBorderColor(Color.RED);
        }

        if (file.getParentFile().exists())
            fileChooser.getWebFileChooser().setCurrentDirectory(file.getParentFile());

        fileChooser.addSelectedFilesListener(new FilesSelectionListener() {
            @Override
            public void selectionChanged(List<File> files) {
                for (File file : files) {
                    if (file.exists()) {
                        fileChooser.setBorderColor(Color.BLACK);
                    }
                }
            }
        });

        return fileChooser;
    }

    private class MainPanel extends WebPanel {

        WebCheckBox mConnectStartupBox;
        WebCheckBox mTrayBox;
        WebCheckBox mCloseTrayBox;

        public MainPanel() {
            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            groupPanel.add(new WebLabel("Main Settings").setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            mConnectStartupBox = new WebCheckBox("Connect on startup");
            mConnectStartupBox.setSelected(mConf.getBoolean(KonConf.MAIN_CONNECT_STARTUP));
            groupPanel.add(mConnectStartupBox);

            mCloseTrayBox = new WebCheckBox("Close to tray");
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

            this.add(groupPanel);
        }

        private void saveConfiguration() {
            mConf.setProperty(KonConf.MAIN_CONNECT_STARTUP, mConnectStartupBox.isSelected());
            mConf.setProperty(KonConf.MAIN_TRAY, mTrayBox.isSelected());
            mConf.setProperty(KonConf.MAIN_TRAY_CLOSE, mCloseTrayBox.isSelected());
            mViewModel.setTray();
        }
    }

    private class AccountPanel extends WebPanel {

            private final WebTextField mServerField;
            private final WebFileChooserField mPublicKeyChooser;
            private final WebFileChooserField mPrivateKeyChooser;
            private final WebFileChooserField mBridgeCertChooser;
            private final WebTextField mPassField;

        public AccountPanel() {
            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            groupPanel.add(new WebLabel("Account Configuration").setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            // server text field
            groupPanel.add(new WebLabel("Server address:"));
            mServerField = new WebTextField(mConf.getString(KonConf.SERV_HOST), 24);
            mServerField.setInputPrompt(KonConf.DEFAULT_SERV_HOST);
            mServerField.setInputPromptFont(mServerField.getFont().deriveFont(Font.ITALIC));
            mServerField.setHideInputPromptOnFocus(false);

            groupPanel.add(mServerField);
            groupPanel.add(new WebSeparator(true, true));

            // file chooser for key files
            groupPanel.add(new WebLabel("Public key file:"));
            mPublicKeyChooser = createFileChooser(mConf.getString(KonConf.ACC_PUB_KEY));
            groupPanel.add(mPublicKeyChooser);
            groupPanel.add(new WebSeparator(true, true));

            groupPanel.add(new WebLabel("Private key file:"));
            mPrivateKeyChooser = createFileChooser(mConf.getString(KonConf.ACC_PRIV_KEY));
            groupPanel.add(mPrivateKeyChooser);
            groupPanel.add(new WebSeparator(true, true));

            groupPanel.add(new WebLabel("Bridge certificate file:"));
            mBridgeCertChooser = createFileChooser(mConf.getString(KonConf.ACC_BRIDGE_CERT));
            groupPanel.add(mBridgeCertChooser);
            groupPanel.add(new WebSeparator(true, true));

            // text field for passphrase
            groupPanel.add(new WebLabel("Passphrase for key:"));
            mPassField = new WebTextField(42);
            if (mConf.getString(KonConf.ACC_PASS).isEmpty()) {
                mPassField.setInputPrompt("Enter passphrase...");
                mPassField.setHideInputPromptOnFocus(false);
            } else {
                mPassField.setText(mConf.getString(KonConf.ACC_PASS));
            }
            groupPanel.add(mPassField);
            groupPanel.add(new WebSeparator(true, true));

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

        private void saveConfiguration() {
            mConf.setProperty(KonConf.SERV_HOST, mServerField.getText());

            if (!mPublicKeyChooser.getSelectedFiles().isEmpty()) {
                File file = mPublicKeyChooser.getSelectedFiles().get(0);
                mConf.setProperty(KonConf.ACC_PUB_KEY, file.getAbsolutePath());
            }
            if (!mPrivateKeyChooser.getSelectedFiles().isEmpty()) {
                File file = mPrivateKeyChooser.getSelectedFiles().get(0);
                mConf.setProperty(KonConf.ACC_PRIV_KEY, file.getAbsolutePath());
            }
            if (!mBridgeCertChooser.getSelectedFiles().isEmpty()) {
                File file = mBridgeCertChooser.getSelectedFiles().get(0);
                mConf.setProperty(KonConf.ACC_BRIDGE_CERT, file.getAbsolutePath());
            }

            mConf.setProperty(KonConf.ACC_PASS, mPassField.getText());
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

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
import com.alee.laf.button.WebButton;
import com.alee.laf.checkbox.WebCheckBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.tabbedpane.WebTabbedPane;
import com.alee.laf.text.WebTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.JFrame;
import org.kontalk.KonConf;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class ConfigurationDialog extends WebDialog {

    private static enum ConfPage {MAIN, ACCOUNT};

    private final KonConf mConf = KonConf.getInstance();
    private final View mViewModel;

    ConfigurationDialog(JFrame owner, final View viewModel, String helpText) {
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
        tabbedPane.addTab("Main", mainPanel);
        tabbedPane.addTab("Account", accountPanel);

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
                ConfigurationDialog.this.dispose();
            }
        });

        GroupPanel buttonPanel = new GroupPanel(2, cancelButton, saveButton);
        buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
        this.add(buttonPanel, BorderLayout.SOUTH);
    }

    private WebFileChooserField createFileChooser(String path){
        WebFileChooserField fileChooser = new WebFileChooserField (this);
        fileChooser.setPreferredWidth(100);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setShowFileShortName(false);
        fileChooser.setShowRemoveButton(false);
        //fileChooser.setSelectedFile(File.listRoots()[0]);
        fileChooser.setSelectedFile(new File(path));
        fileChooser.getWebFileChooser().setCurrentDirectory(System.getProperty("user.dir"));
        return fileChooser;
    }

    private class MainPanel extends WebPanel {

        WebCheckBox mTrayBox;

        public MainPanel() {
            //"Account configuration"
            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            groupPanel.add(new WebLabel("Main Settings").setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            mTrayBox = new WebCheckBox("Show tray icon");
            mTrayBox.setAnimated(false);
            mTrayBox.setSelected(mConf.getBoolean(KonConf.MAIN_TRAY));

            groupPanel.add(mTrayBox);

            this.add(groupPanel);
        }

        private void saveConfiguration() {
            mConf.setProperty(KonConf.MAIN_TRAY, mTrayBox.isSelected());
        }
    }

    private class AccountPanel extends WebPanel {


            private final WebTextField serverField;
            private final WebFileChooserField publicKeyChooser;
            private final WebFileChooserField privateKeyChooser;
            private final WebFileChooserField bridgeCertChooser;
            private final WebTextField passField;

        public AccountPanel() {
            //"Account configuration"
            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            groupPanel.add(new WebLabel("Account Configuration").setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            // server text field
            groupPanel.add(new WebLabel("Server:"));
            serverField = new WebTextField(mConf.getString(KonConf.SERV_HOST), 24);
            serverField.setInputPrompt(KonConf.DEFAULT_SERV_HOST);
            serverField.setInputPromptFont(serverField.getFont().deriveFont(Font.ITALIC));
            serverField.setHideInputPromptOnFocus(false);

            groupPanel.add(serverField);
            groupPanel.add(new WebSeparator(true, true));

            // file chooser for key files
            groupPanel.add(new WebLabel("Choose public key:"));
            publicKeyChooser = createFileChooser(mConf.getString(KonConf.ACC_PUB_KEY));
            groupPanel.add(publicKeyChooser);
            groupPanel.add(new WebSeparator(true, true));

            groupPanel.add(new WebLabel("Choose private key:"));
            privateKeyChooser = createFileChooser(mConf.getString(KonConf.ACC_PRIV_KEY));
            groupPanel.add(privateKeyChooser);
            groupPanel.add(new WebSeparator(true, true));

            groupPanel.add(new WebLabel("Choose bridge certificate:"));
            bridgeCertChooser = createFileChooser(mConf.getString(KonConf.ACC_BRIDGE_CERT));
            groupPanel.add(bridgeCertChooser);
            groupPanel.add(new WebSeparator(true, true));

            // text field for passphrase
            groupPanel.add(new WebLabel("Passphrase:"));
            passField = new WebTextField(42);
            if (mConf.getString(KonConf.ACC_PASS).isEmpty()) {
                passField.setInputPrompt("Enter passphrase...");
                passField.setHideInputPromptOnFocus(false);
            } else {
                passField.setText(mConf.getString(KonConf.ACC_PASS));
            }
            groupPanel.add(passField);
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
            mConf.setProperty(KonConf.SERV_HOST, serverField.getText());

            File file = publicKeyChooser.getSelectedFiles().get(0);
            mConf.setProperty(KonConf.ACC_PUB_KEY, file.getAbsolutePath());
            file = privateKeyChooser.getSelectedFiles().get(0);
            mConf.setProperty(KonConf.ACC_PRIV_KEY, file.getAbsolutePath());
            file = bridgeCertChooser.getSelectedFiles().get(0);
            mConf.setProperty(KonConf.ACC_BRIDGE_CERT, file.getAbsolutePath());

            mConf.setProperty(KonConf.ACC_PASS, passField.getText());
        }
    }
}

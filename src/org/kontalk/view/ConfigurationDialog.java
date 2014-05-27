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
import com.alee.laf.label.WebLabel;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.text.WebTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.JFrame;
import org.kontalk.KontalkConfiguration;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class ConfigurationDialog extends WebDialog {

    private final KontalkConfiguration mConf = KontalkConfiguration.getInstance();
    private final WebTextField serverField;
    private final WebFileChooserField publicKeyChooser;
    private final WebFileChooserField privateKeyChooser;
    private final WebFileChooserField bridgeCertChooser;
    private final WebTextField passField;

    ConfigurationDialog(JFrame owner, final View viewModel, String helpText) {
        super(owner);

        this.setTitle("Connection configuration");
        this.setResizable(false);
        this.setModal(true);

        GroupPanel groupPanel = new GroupPanel(10, false);
        groupPanel.setMargin(5);

        // server text field
        groupPanel.add(new WebLabel("Server:"));
        serverField = new WebTextField(mConf.getString(KontalkConfiguration.SERV_HOST), 24);
        serverField.setInputPrompt(KontalkConfiguration.DEFAULT_SERV_HOST);
        serverField.setInputPromptFont(serverField.getFont().deriveFont(Font.ITALIC));
        serverField.setHideInputPromptOnFocus(false);

        groupPanel.add(serverField);
        groupPanel.add(new WebSeparator(true, true));

        // file chooser for key files
        groupPanel.add(new WebLabel("Choose public key:"));
        publicKeyChooser = createFileChooser(mConf.getString(KontalkConfiguration.ACC_PUB_KEY));
        groupPanel.add(publicKeyChooser);
        groupPanel.add(new WebSeparator(true, true));

        groupPanel.add(new WebLabel("Choose private key:"));
        privateKeyChooser = createFileChooser(mConf.getString(KontalkConfiguration.ACC_PRIV_KEY));
        groupPanel.add(privateKeyChooser);
        groupPanel.add(new WebSeparator(true, true));

        groupPanel.add(new WebLabel("Choose bridge certificate:"));
        bridgeCertChooser = createFileChooser(mConf.getString(KontalkConfiguration.ACC_BRIDGE_CERT));
        groupPanel.add(bridgeCertChooser);
        groupPanel.add(new WebSeparator(true, true));

        // text field for passphrase
        groupPanel.add(new WebLabel("Passphrase:"));
        passField = new WebTextField(42);
        if (mConf.getString(KontalkConfiguration.ACC_PASS).isEmpty()) {
            passField.setInputPrompt("Enter passphrase...");
            passField.setHideInputPromptOnFocus(false);
        } else {
            passField.setText(mConf.getString(KontalkConfiguration.ACC_PASS));
        }
        groupPanel.add(passField);
        groupPanel.add(new WebSeparator(true, true));

        this.add(groupPanel, BorderLayout.CENTER);

        // buttons
        WebButton cancelButton = new WebButton("Cancel");
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        WebButton saveButton = new WebButton("Save");
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveConfiguration();
                dispose();
            }
        });
        WebButton okButton = new WebButton("Save & Connect");
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveConfiguration();
                dispose();
                viewModel.connect();
            }
        });

        GroupPanel buttonPanel = new GroupPanel(2, cancelButton, saveButton, okButton);
        buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
        this.add(buttonPanel, BorderLayout.SOUTH);

        this.pack();
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

    private void saveConfiguration() {

        mConf.setProperty(KontalkConfiguration.SERV_HOST, serverField.getText());

        File file = publicKeyChooser.getSelectedFiles().get(0);
        mConf.setProperty(KontalkConfiguration.ACC_PUB_KEY, file.getAbsolutePath());
        file = privateKeyChooser.getSelectedFiles().get(0);
        mConf.setProperty(KontalkConfiguration.ACC_PRIV_KEY, file.getAbsolutePath());
        file = bridgeCertChooser.getSelectedFiles().get(0);
        mConf.setProperty(KontalkConfiguration.ACC_BRIDGE_CERT, file.getAbsolutePath());

        mConf.setProperty(KontalkConfiguration.ACC_PASS, passField.getText());
    }

}

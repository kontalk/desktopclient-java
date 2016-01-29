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
import com.alee.laf.text.WebTextArea;
import com.alee.laf.text.WebTextField;
import com.alee.managers.tooltip.TooltipManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Box;
import javax.swing.JFrame;
import javax.swing.text.NumberFormatter;
import org.kontalk.system.Config;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.misc.KonException;
import org.kontalk.model.Account;
import org.kontalk.util.Tr;

/**
 * Dialog for showing and changing all application options.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class ConfigurationDialog extends WebDialog {
    private static final Logger LOGGER = Logger.getLogger(ConfigurationDialog.class.getName());

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
        this.setLayout(new BorderLayout(View.GAP_SMALL, View.GAP_SMALL));

        WebTabbedPane tabbedPane = new WebTabbedPane(WebTabbedPane.LEFT);
        tabbedPane.setFontSize(View.FONT_SIZE_NORMAL);
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

        GroupPanel buttonPanel = new GroupPanel(saveButton, cancelButton);
        buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
        this.add(buttonPanel, BorderLayout.SOUTH);
    }

    private class MainPanel extends WebPanel {

        private final WebCheckBox mConnectStartupBox;
        private final WebCheckBox mTrayBox;
        private final WebCheckBox mCloseTrayBox;
        private final WebCheckBox mEnterSendsBox;
        private final WebCheckBox mRequestAvatars;
        private final WebCheckBox mBGBox;
        private final WebFileChooserField mBGChooser;

        MainPanel() {
            GroupPanel groupPanel = new GroupPanel(View.GAP_DEFAULT, false);
            groupPanel.setMargin(View.MARGIN_BIG);

            groupPanel.add(new WebLabel(Tr.tr("Main Settings")).setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            mConnectStartupBox = createCheckBox(Tr.tr("Connect on startup"),
                    "",
                    mConf.getBoolean(Config.MAIN_CONNECT_STARTUP));
            groupPanel.add(mConnectStartupBox);

            mTrayBox = createCheckBox(Tr.tr("Show tray icon"),
                    "",
                    mConf.getBoolean(Config.MAIN_TRAY));
            mTrayBox.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    mCloseTrayBox.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
                }
            });
            mCloseTrayBox = createCheckBox(Tr.tr("Close to tray"),
                    "",
                    mConf.getBoolean(Config.MAIN_TRAY_CLOSE));
            mCloseTrayBox.setEnabled(mTrayBox.isSelected());
            groupPanel.add(new GroupPanel(View.GAP_DEFAULT, mTrayBox, mCloseTrayBox));

            mEnterSendsBox = createCheckBox(Tr.tr("Enter key sends"),
                    Tr.tr("Enter key sends text, Control+Enter adds new line - or vice versa"),
                    mConf.getBoolean(Config.MAIN_ENTER_SENDS));
            groupPanel.add(new GroupPanel(mEnterSendsBox, new WebSeparator()));

            mRequestAvatars = createCheckBox(Tr.tr("Download avatar images"),
                    Tr.tr("Download contact avatar images"),
                    mConf.getBoolean(Config.NET_REQUEST_AVATARS));
            groupPanel.add(new GroupPanel(mRequestAvatars, new WebSeparator()));

            String bgPath = mConf.getString(Config.VIEW_CHAT_BG);
            mBGBox = createCheckBox(Tr.tr("Custom background:")+" ",
                    "",
                    !bgPath.isEmpty());
            mBGBox.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    mBGChooser.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
                    mBGChooser.getChooseButton().setEnabled(e.getStateChange() == ItemEvent.SELECTED);
                }
            });

            mBGChooser = Utils.createImageChooser(mBGBox.isSelected(), bgPath);

            groupPanel.add(new GroupPanel(GroupingType.fillLast, mBGBox, mBGChooser));

            this.add(groupPanel);
        }

        private void saveConfiguration() {
            mConf.setProperty(Config.MAIN_CONNECT_STARTUP, mConnectStartupBox.isSelected());
            mConf.setProperty(Config.MAIN_TRAY, mTrayBox.isSelected());
            mConf.setProperty(Config.MAIN_TRAY_CLOSE, mCloseTrayBox.isSelected());
            mView.updateTray();
            mConf.setProperty(Config.MAIN_ENTER_SENDS, mEnterSendsBox.isSelected());
            mView.setHotkeys();
            mConf.setProperty(Config.NET_REQUEST_AVATARS, mRequestAvatars.isSelected());
            String bgPath;
            if (mBGBox.isSelected() && !mBGChooser.getSelectedFiles().isEmpty()) {
                bgPath = mBGChooser.getSelectedFiles().get(0).getAbsolutePath();
            } else {
                bgPath = "";
            }
            String oldBGPath = mConf.getString(Config.VIEW_CHAT_BG);
            if (!bgPath.equals(oldBGPath)) {
                mConf.setProperty(Config.VIEW_CHAT_BG, bgPath);
                mView.reloadChatBG();
            }
        }
    }

    private class AccountPanel extends WebPanel {

        private final WebTextField mServerField;
        private final WebFormattedTextField mPortField;
        private final WebCheckBox mDisableCertBox;
        private final WebTextArea mFingerprintArea;

        AccountPanel() {
            GroupPanel groupPanel = new GroupPanel(View.GAP_DEFAULT, false);
            groupPanel.setMargin(View.MARGIN_BIG);

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
            mDisableCertBox = createCheckBox(Tr.tr("Disable certificate validation"),
                    Tr.tr("Disable SSL certificate server validation"),
                    !mConf.getBoolean(Config.SERV_CERT_VALIDATION));
            groupPanel.add(new GroupPanel(mDisableCertBox, new WebSeparator()));

            groupPanel.add(new WebSeparator(true, true));
            WebLabel fpLabel = new WebLabel(Tr.tr("Key fingerprint:")+" ");
            fpLabel.setAlignmentY(Component.TOP_ALIGNMENT);
            GroupPanel fpLabelPanel = new GroupPanel(false, fpLabel, Box.createGlue());
            mFingerprintArea = Utils.createFingerprintArea();
            this.updateFingerprint();
            groupPanel.add(new GroupPanel(View.GAP_DEFAULT, fpLabelPanel, mFingerprintArea));

            final WebButton passButton = new WebButton(getPassTitle());
            passButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    WebDialog passDialog = createPassDialog(ConfigurationDialog.this);
                    passDialog.setVisible(true);
                    passButton.setText(getPassTitle());
                }
            });
            groupPanel.add(passButton);

            WebButton importButton = new WebButton(Tr.tr("Import new Account"));
            importButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    mView.showImportWizard(false);
                    AccountPanel.this.updateFingerprint();
                    passButton.setText(getPassTitle());
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
                    mView.getControl().connect();
                }
            });

            GroupPanel buttonPanel = new GroupPanel(okButton);
            buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
            this.add(buttonPanel, BorderLayout.SOUTH);
        }

        private void updateFingerprint() {
            PersonalKey key = Account.getInstance().getPersonalKey().orElse(null);
            mFingerprintArea.setText(key != null ?
                    Utils.fingerprint(key.getFingerprint()) :
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
        private final WebCheckBox mRosterNameBox;
        private final WebCheckBox mSubscriptionBox;

        PrivacyPanel() {
            GroupPanel groupPanel = new GroupPanel(View.GAP_DEFAULT, false);
            groupPanel.setMargin(View.MARGIN_BIG);

            groupPanel.add(new WebLabel(Tr.tr("Privacy Settings")).setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            mSubscriptionBox = createCheckBox(Tr.tr("Automatically grant authorization"),
                     Tr.tr("Automatically grant online status authorization requests from other users"),
                    mConf.getBoolean(Config.NET_AUTO_SUBSCRIPTION));
            groupPanel.add(new GroupPanel(mSubscriptionBox, new WebSeparator()));

            mChatStateBox = createCheckBox(Tr.tr("Send chatstate notification"),
                    Tr.tr("Send chat activity (typing,…) to other users"),
                    mConf.getBoolean(Config.NET_SEND_CHAT_STATE));
            groupPanel.add(new GroupPanel(mChatStateBox, new WebSeparator()));

            mRosterNameBox = createCheckBox(Tr.tr("Upload contact names"),
                    Tr.tr("Upload your contact names to server for client synchronization"),
                    mConf.getBoolean(Config.NET_SEND_ROSTER_NAME));
            groupPanel.add(new GroupPanel(mRosterNameBox, new WebSeparator()));

            this.add(groupPanel);
        }

        private void saveConfiguration() {
            mConf.setProperty(Config.NET_SEND_CHAT_STATE, mChatStateBox.isSelected());
            mConf.setProperty(Config.NET_SEND_ROSTER_NAME, mRosterNameBox.isSelected());
            mConf.setProperty(Config.NET_AUTO_SUBSCRIPTION, mSubscriptionBox.isSelected());
        }
    }

    private static WebCheckBox createCheckBox(String title, String tooltip, boolean selected) {
        WebCheckBox checkBox = new WebCheckBox(Tr.tr(title));
        checkBox.setAnimated(false);
        checkBox.setSelected(selected);
        String rosterNameText = Tr.tr(tooltip);
        if (!tooltip.isEmpty())
            TooltipManager.addTooltip(checkBox, rosterNameText);
        return checkBox;
    }

    private static String getPassTitle() {
        return Account.getInstance().isPasswordProtected() ?
                Tr.tr("Change key password") :
                Tr.tr("Set key password");
    }

    private static WebDialog createPassDialog(WebDialog parent) {
        final WebDialog passDialog = new WebDialog(parent, getPassTitle(), true);
        passDialog.setLayout(new BorderLayout(View.GAP_DEFAULT, View.GAP_DEFAULT));
        passDialog.setResizable(false);

        final WebButton saveButton = new WebButton(Tr.tr("Save"));

        boolean passSet = Account.getInstance().isPasswordProtected();
        final ComponentUtils.PassPanel passPanel = new ComponentUtils.PassPanel(passSet) {
           @Override
           void onValidInput() {
               saveButton.setEnabled(true);
           }
           @Override
           void onInvalidInput() {
               saveButton.setEnabled(false);
           }
        };
        passDialog.add(passPanel, BorderLayout.CENTER);

        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                char[] oldPassword = passPanel.getOldPassword();
                char[] newPassword = passPanel.getNewPassword().orElse(null);
                if (newPassword == null) {
                    LOGGER.warning("can't get new password");
                    return;
                }
                try {
                    Account.getInstance().setPassword(oldPassword, newPassword);
                } catch(KonException ex) {
                    LOGGER.log(Level.WARNING, "can't set new password", ex);
                    if (ex.getError() == KonException.Error.CHANGE_PASS_COPY)
                        passPanel.showWrongPassword();
                    return;
                }
                passDialog.dispose();
            }
        });

        WebButton cancelButton = new WebButton(Tr.tr("Cancel"));
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                passDialog.dispose();
            }
        });

        GroupPanel buttonPanel = new GroupPanel(2, saveButton, cancelButton);
        buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
        passDialog.add(buttonPanel, BorderLayout.SOUTH);

        passDialog.pack();
        return passDialog;
    }
}

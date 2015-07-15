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
import com.alee.extended.filefilter.ImageFilesFilter;
import com.alee.extended.panel.GroupPanel;
import com.alee.laf.checkbox.WebCheckBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.text.WebPasswordField;
import com.alee.laf.text.WebTextField;
import com.alee.managers.tooltip.TooltipManager;
import java.awt.Color;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Logger;
import javax.net.ssl.SSLHandshakeException;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.sasl.SASLErrorException;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.Kontalk;
import org.kontalk.misc.KonException;
import org.kontalk.util.Tr;

/**
 * Various utilities used in view.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public class Utils {
    private final static Logger LOGGER = Logger.getLogger(Utils.class.getName());

    private Utils() {
        throw new AssertionError();
    }

    static WebFileChooserField createImageChooser(boolean enabled, String path) {
        WebFileChooserField chooser = new WebFileChooserField();
        chooser.setEnabled(enabled);
        chooser.getChooseButton().setEnabled(enabled);
        if (!path.isEmpty())
            chooser.setSelectedFile(new File(path));
        chooser.setMultiSelectionEnabled(false);
        chooser.setShowRemoveButton(true);
        chooser.getWebFileChooser().setFileFilter(new ImageFilesFilter());
        File file = new File(path);
        if (file.exists()) {
            chooser.setSelectedFile(file);
        }
        if (file.getParentFile() != null && file.getParentFile().exists())
            chooser.getWebFileChooser().setCurrentDirectory(file.getParentFile());
        return chooser;
    }

    static WebTextField createTextField(final String text) {
        final WebTextField field = new WebTextField(text, false);
        field.setEditable(false);
        field.setBackground(null);
        field.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                check(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                check(e);
            }
            private void check(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    WebPopupMenu popupMenu = new WebPopupMenu();
                    popupMenu.add(createCopyMenuItem(field.getText(), ""));
                    popupMenu.show(field, e.getX(), e.getY());
                }
            }
        });
        return field;
    }

    static WebMenuItem createCopyMenuItem(final String copyText, String toolTipText) {
        WebMenuItem item = new WebMenuItem(Tr.tr("Copy"));
        if (!toolTipText.isEmpty())
            item.setToolTipText(toolTipText);
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
                clip.setContents(new StringSelection(copyText), null);
            }
        });
        return item;
    }

    static String getErrorText(KonException ex) {
        String eol = " " + System.getProperty("line.separator");
        String errorText = Tr.tr("Unknown error!?");
        switch (ex.getError()) {
            case IMPORT_ARCHIVE:
                errorText = Tr.tr("Can't open key archive.");
                break;
            case IMPORT_READ_FILE:
                errorText = Tr.tr("Can't load keyfile(s) from archive.");
                break;
            case IMPORT_KEY:
                errorText = Tr.tr("Can't create personal key from key files.") + " ";
                if (ex.getExceptionClass().equals(IOException.class)) {
                    errorText += eol + Tr.tr("Is the public key file valid?");
                }
                if (ex.getExceptionClass().equals(CertificateException.class)) {
                    errorText += eol + Tr.tr("Are all key files valid?");
                }
                break;
            case CHANGE_PASS:
                errorText = Tr.tr("Can't change password. Internal error(!?)");
                break;
            case WRITE_FILE:
                errorText = Tr.tr("Can't write key files to configuration directory.");
                break;
            case READ_FILE:
            case LOAD_KEY:
                switch (ex.getError()) {
                    case READ_FILE:
                        errorText = Tr.tr("Can't read key files from configuration directory.");
                        break;
                    case LOAD_KEY:
                        errorText = Tr.tr("Can't load key files from configuration directory.");
                        break;
                }
                errorText += " " + Tr.tr("Please reimport your key.");
                break;
            case LOAD_KEY_DECRYPT:
                errorText = Tr.tr("Can't decrypt key. Is the passphrase correct?");
                break;
            case CLIENT_CONNECTION:
                errorText = Tr.tr("Can't create connection");
                break;
            case CLIENT_CONNECT:
                errorText = Tr.tr("Can't connect to server.");
                if (ex.getExceptionClass().equals(SmackException.ConnectionException.class)) {
                    errorText += eol + Tr.tr("Is the server address correct?");
                }
                if (ex.getExceptionClass().equals(SSLHandshakeException.class)) {
                    errorText += eol + Tr.tr("The server rejects the key.");
                }
                if (ex.getExceptionClass().equals(SmackException.NoResponseException.class)) {
                    errorText += eol + Tr.tr("The server does not respond.");
                }
                break;
            case CLIENT_LOGIN:
                errorText = Tr.tr("Can't login to server.");
                if (ex.getExceptionClass().equals(SASLErrorException.class)) {
                    errorText += eol + Tr.tr("The server rejects the account. Is the specified server correct and the account valid?");
                }
                break;
            case CLIENT_ERROR:
                errorText = Tr.tr("Connection to server closed on error.");
                // TODO more details
                break;
        }
        return errorText;
    }

    static String shortenUserName(String jid, int maxLength) {
        String local = XmppStringUtils.parseLocalpart(jid);
        local = StringUtils.abbreviate(local, maxLength);
        String domain = XmppStringUtils.parseDomain(jid);
        return XmppStringUtils.completeJidFrom(local, domain);
    }

    static String shortenJID(String jid, int maxLength) {
        if (jid.length() > maxLength) {
            String local = XmppStringUtils.parseLocalpart(jid);
            local = StringUtils.abbreviate(local, (int) (maxLength * 0.4));
            String domain = XmppStringUtils.parseDomain(jid);
            domain = StringUtils.abbreviate(domain, (int) (maxLength * 0.6));
            jid = XmppStringUtils.completeJidFrom(local, domain);
        }
        return jid;
    }

    static Icon getIcon(String fileName) {
        return new ImageIcon(getImage(fileName));
    }

    static Image getImage(String fileName) {
        URL imageUrl = ClassLoader.getSystemResource(Kontalk.RES_PATH + fileName);
        if (imageUrl == null) {
            LOGGER.warning("can't find icon image resource");
            return new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        }
        return Toolkit.getDefaultToolkit().createImage(imageUrl);
    }

    static abstract class PassPanel extends WebPanel {

        private final boolean mPassSet;
        private final WebCheckBox mSetPass;
        private final WebPasswordField mOldPassField;
        private final WebLabel mWrongPassLabel;
        private final WebPasswordField mNewPassField;
        private final WebPasswordField mConfirmPassField;

        PassPanel(boolean passSet) {
            mPassSet = passSet;

            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

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
}

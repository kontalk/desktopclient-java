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

import com.alee.extended.label.WebLinkLabel;
import com.alee.extended.layout.FormLayout;
import com.alee.extended.panel.GroupPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.button.WebToggleButton;
import com.alee.laf.checkbox.WebCheckBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.list.WebList;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.tabbedpane.WebTabbedPane;
import com.alee.laf.text.WebPasswordField;
import com.alee.laf.text.WebTextField;
import com.alee.managers.popup.PopupAdapter;
import com.alee.managers.popup.WebPopup;
import com.alee.managers.tooltip.TooltipManager;
import com.alee.utils.SwingUtils;
import com.alee.utils.swing.DocumentChangeListener;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.swing.AbstractButton;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.Icon;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JRootPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.model.Contact;
import org.kontalk.model.ContactList;
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

    static abstract class PopupPanel extends WebPanel {

        abstract void onShow();

    }

    static class ToggleButton extends WebToggleButton {

        private final PopupPanel mPanel;
        private WebPopup mPopup = new WebPopup();

        ToggleButton(Icon icon, String tooltip, PopupPanel panel) {
            super(icon);
            mPanel = panel;
            this.setShadeWidth(0).setRound(0);
            TooltipManager.addTooltip(this, tooltip);
            this.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (!mPopup.isShowing())
                        ToggleButton.this.showAddContactPopup();
                }
            });
        }

        private void showAddContactPopup() {
            mPopup = new WebPopup();
            mPopup.setCloseOnFocusLoss(true);
            mPopup.addPopupListener(new PopupAdapter() {
                @Override
                public void popupWillBeClosed() {
                    ToggleButton.this.doClick();
                }
            });
            mPanel.onShow();
            mPopup.add(mPanel);
            //mPopup.packPopup();
            mPopup.showAsPopupMenu(this);
        }
    }

    static class AddContactPanel extends PopupPanel {

        private final View mView;

        private final WebTabbedPane mTabbedPane;
        private final WebTextField mNameField;

        private final WebTextField mJIDField;

        private final WebTextField mServerField;
        private final WebTextField mNumberField;
        private final WebTextField mPrefixField;

        private final WebCheckBox mEncryptionBox;
        private final WebButton mSaveButton;

        AddContactPanel(View view, final Component focusGainer) {
            mView = view;

            GroupPanel groupPanel = new GroupPanel(View.GAP_BIG, false);
            groupPanel.setMargin(View.MARGIN_BIG);

            groupPanel.add(new WebLabel(Tr.tr("Add Contact")).setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            // editable fields
            mNameField = new WebTextField(20);
            addListener(this, mNameField);
            groupPanel.add(new GroupPanel(View.GAP_DEFAULT,
                    new WebLabel(Tr.tr("Name:")), mNameField));

            mSaveButton = new WebButton(Tr.tr("Create"));

            mTabbedPane = new WebTabbedPane();
            mTabbedPane.addChangeListener(new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                    AddContactPanel.this.checkSaveButton();
                }
            });

            WebPanel kontalkPanel = new WebPanel(new FormLayout(false, false,
                    View.GAP_DEFAULT, View.GAP_DEFAULT));
            kontalkPanel.setMargin(View.MARGIN_BIG);
            kontalkPanel.add(new WebLabel("Country code:"));
            PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
            int countryCode = phoneUtil.getCountryCodeForRegion(
                    Locale.getDefault().getCountry());
            String prefix = "+"+Integer.toString(countryCode);
            mPrefixField = new WebTextField(prefix, 3);
            mPrefixField.setInputPrompt(prefix);
            addListener(this, mPrefixField);
            kontalkPanel.add(mPrefixField);
            kontalkPanel.add(new WebLabel("Number:"));
            mNumberField = new WebTextField(12);
            mNumberField.setInputPrompt("0123-9876543");
            addListener(this, mNumberField);
            kontalkPanel.add(mNumberField);
            kontalkPanel.add(new WebLabel("Server:"));
            String serverText = XmppStringUtils.parseDomain(
                    Config.getInstance().getString(Config.SERV_HOST));
            mServerField = new WebTextField(serverText, 16);
            mServerField.setInputPrompt(serverText);
            addListener(this, mServerField);
            kontalkPanel.add(mServerField);
            mTabbedPane.addTab(Tr.tr("Kontalk Contact"), kontalkPanel);

            WebPanel jabberPanel = new WebPanel(new FormLayout(false, false,
                    View.GAP_DEFAULT, View.GAP_DEFAULT));
            jabberPanel.setMargin(View.MARGIN_BIG);
            jabberPanel.add(new WebLabel("Jabber ID:"));
            mJIDField = new WebTextField(20);
            mJIDField.setInputPrompt(Tr.tr("username")+"@jabber-server.com");
            addListener(this, mJIDField);
            jabberPanel.add(mJIDField);
            mTabbedPane.addTab(Tr.tr("Jabber Contact"), jabberPanel);
            groupPanel.add(mTabbedPane);
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
                    AddContactPanel.this.saveContact();
                    focusGainer.requestFocus();
                }
            });

            GroupPanel buttonPanel = new GroupPanel(mSaveButton);
            buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
            this.add(buttonPanel, BorderLayout.SOUTH);
        }

        private void checkSaveButton() {
            boolean enable;
            if (mTabbedPane.getSelectedIndex() == 0) {
                enable = !XMPPUtils.phoneNumberToKontalkLocal(
                        mPrefixField.getText()+mNumberField.getText()).isEmpty() &&
                        !mServerField.getText().isEmpty();
            } else {
                enable = XMPPUtils.isValid(mJIDField.getText());
            }
            mSaveButton.setEnabled(enable);
        }

        private void saveContact() {
            String jid;
            if (mTabbedPane.getSelectedIndex() == 0) {
                String kontalkLocal = XMPPUtils.phoneNumberToKontalkLocal(
                    mPrefixField.getText()+mNumberField.getText());
                if (kontalkLocal.isEmpty()) {
                    // huh?
                    return;
                }
                jid = kontalkLocal + "@" + mServerField.getText();
            } else {
                jid = mJIDField.getText();
            }
            mView.getControl().createContact(jid,
                    mNameField.getText(),
                    mEncryptionBox.isSelected());
        }

        private static void addListener(final AddContactPanel panel,
                WebTextField field) {
            field.getDocument().addDocumentListener(new DocumentChangeListener() {
                @Override
                public void documentChanged(DocumentEvent e) {
                    panel.checkSaveButton();
                }
            });
        }

        @Override
        void onShow() {
        }
    }

    static class AddGroupChatPanel extends PopupPanel {

        private final View mView;
        private final WebTextField mSubjectField;
        private final ContactSelectionList mList;

        private final WebButton mCreateButton;

        AddGroupChatPanel(View view, final Component focusGainer) {
            mView = view;

            GroupPanel groupPanel = new GroupPanel(View.GAP_BIG, false);
            groupPanel.setMargin(View.MARGIN_BIG);

            groupPanel.add(new WebLabel(Tr.tr("Create Group")).setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            // editable fields
            mSubjectField = new WebTextField(20);
            mSubjectField.setDocument(new TextLimitDocument(View.MAX_SUBJ_LENGTH));
            mSubjectField.getDocument().addDocumentListener(new DocumentChangeListener() {
                @Override
                public void documentChanged(DocumentEvent e) {
                    AddGroupChatPanel.this.checkSaveButton();
                }
            });
            groupPanel.add(new GroupPanel(View.GAP_DEFAULT,
                    new WebLabel(Tr.tr("Subject:"+" ")), mSubjectField));

            mList = new ContactSelectionList();
            mList.setVisibleRowCount(10);
            mList.addListSelectionListener(new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent e) {
                    AddGroupChatPanel.this.checkSaveButton();
                }
            });
            groupPanel.add(new ScrollPane(mList).setPreferredWidth(200));

            this.add(groupPanel, BorderLayout.CENTER);

            mCreateButton = new WebButton(Tr.tr("Create"));
            mCreateButton.setEnabled(false);
            mCreateButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    AddGroupChatPanel.this.createGroup();
                    focusGainer.requestFocus();
                }
            });

            GroupPanel buttonPanel = new GroupPanel(mCreateButton);
            buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
            this.add(buttonPanel, BorderLayout.SOUTH);
        }

        private void checkSaveButton() {
            mCreateButton.setEnabled(!mSubjectField.getText().isEmpty() &&
                    // TODO
                    mList.getSelectedContacts().length > 0);
        }

        private void createGroup() {
            mView.getControl().createGroupChat(mList.getSelectedContacts(),
                    mSubjectField.getText());
        }

        @Override
        void onShow() {
            mList.reload();
        }
    }

    // Note: https://github.com/mgarin/weblaf/issues/153
    static class ContactSelectionList extends WebList {

        private final DefaultListModel<Contact> mModel;

        @SuppressWarnings("unchecked")
        ContactSelectionList() {
            mModel = new DefaultListModel<>();
            this.setModel(mModel);

            this.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            this.setSelectionModel(new DefaultListSelectionModel() {
                @Override
                public void setSelectionInterval(int index0, int index1) {
                    if(super.isSelectedIndex(index0)) {
                        super.removeSelectionInterval(index0, index1);
                    } else {
                        super.addSelectionInterval(index0, index1);
                    }
                }
            });

            this.setCellRenderer(new CellRenderer());
        }

        void reload() {
            mModel.clear();

            Set<Contact> allContacts = ContactList.getInstance().getAll();
            List<Contact> contacts = new LinkedList<>();
            for (Contact c : allContacts) {
                if (XMPPUtils.isKontalkContact(c))
                    contacts.add(c);
            }

            contacts.sort(new Comparator<Contact>() {
                @Override
                public int compare(Contact c1, Contact c2) {
                    return Utils.compareContacts(c1, c2);
                }
            });

            for (Contact contact : contacts) {
                mModel.addElement(contact);
            }
        }

        @SuppressWarnings("unchecked")
        Contact[] getSelectedContacts() {
            return (Contact[]) this.getSelectedValuesList().toArray(new Contact[0]);
        }

        private class CellRenderer extends WebLabel implements ListCellRenderer<Contact> {
            @Override
            public Component getListCellRendererComponent(JList list,
                    Contact contact,
                    int index,
                    boolean isSelected,
                    boolean cellHasFocus) {
                this.setText(Utils.nameOrJID(contact, 40));
                return this;
            }
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

        public EditableTextField(int columns, final Component focusGainer) {
            super(columns, false);

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

        void setLabel() {
            this.setText(this.labelText());
            this.setDrawBorder(false);
        }

        abstract protected String labelText();

        abstract protected String editText();

        abstract protected void onFocusLost();
    }

    static class ModalPopup extends WebPopup {

        private final AbstractButton mInvoker;
        private final WebPanel layerPanel;

        public ModalPopup(AbstractButton invokerButton) {
            super();
            mInvoker = invokerButton;

            layerPanel = new WebPanel();
            layerPanel.setOpaque(false);
            layerPanel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {

                }
            });

            JRootPane rootPane = SwingUtils.getRootPane(mInvoker);
            if (rootPane == null) {
                throw new IllegalStateException("not on UI start, dummkopf!");
            }
            installPopupLayer(layerPanel, rootPane);
            layerPanel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    ModalPopup.this.close();
                }
            });
        }

        public void showPopup() {
            layerPanel.setVisible(true);
            this.showAsPopupMenu(mInvoker);
        }

        public void close() {
            this.hidePopup();
            mInvoker.setSelected(false);
            layerPanel.setVisible(false);
        }

        // taken from com.alee.managers.popup.PopupManager
        private static void installPopupLayer(final WebPanel popupLayer,
                JRootPane rootPane) {
            final JLayeredPane layeredPane = rootPane.getLayeredPane();
            popupLayer.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
            layeredPane.add(popupLayer, JLayeredPane.DEFAULT_LAYER);
            layeredPane.revalidate();

            layeredPane.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(final ComponentEvent e) {
                    popupLayer.setBounds(0, 0, layeredPane.getWidth(),
                            layeredPane.getHeight());
                    popupLayer.revalidate();
                }
            });

            final Window window = SwingUtils.getWindowAncestor(rootPane);
            window.addWindowStateListener(new WindowStateListener() {
                @Override
                public void windowStateChanged(final WindowEvent e) {
                    popupLayer.setBounds(0, 0, layeredPane.getWidth(),
                            layeredPane.getHeight());
                    popupLayer.revalidate();
                }
            });
        }
    }

    static class AttachmentPanel extends GroupPanel {

        private final WebLabel mStatus;
        private final WebLinkLabel mAttLabel;
        private String mImagePath = "";

        AttachmentPanel() {
           super(View.GAP_SMALL, false);

           mStatus = new WebLabel().setItalicFont();
           this.add(mStatus);

           mAttLabel = new WebLinkLabel();
           this.add(mAttLabel);
        }

        void setImage(String path) {
            if (path.equals(mImagePath))
                return;

            mImagePath = path;
            // file should be present and should be an image, show it
            ImageLoader.setImageIconAsync(mAttLabel, mImagePath);
        }

        void setStatus(String text) {
            mStatus.setText(Tr.tr("Attachment:") + " " + text);
        }

        void setLink(String text, Path linkPath) {
            mAttLabel.setLink(text, Utils.createLinkRunnable(linkPath));
            mStatus.setText("");
        }
    }

    // Source: http://www.rgagnon.com/javadetails/java-0198.html
    static class TextLimitDocument extends PlainDocument {
        private final int mLimit;

        TextLimitDocument(int limit) {
            super();
            this.mLimit = limit;
        }

        @Override
        public void insertString( int offset, String  str, AttributeSet attr)
                throws BadLocationException {
            if (str == null) return;

            if ((this.getLength() + str.length()) <= mLimit) {
                super.insertString(offset, str, attr);
            }
        }
    }
}

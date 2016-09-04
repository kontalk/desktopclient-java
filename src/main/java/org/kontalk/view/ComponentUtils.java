/*
 *  Kontalk Java client
 *  Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>
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

import com.alee.extended.image.WebDecoratedImage;
import com.alee.extended.image.WebImage;
import com.alee.extended.label.WebLinkLabel;
import com.alee.extended.layout.FormLayout;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.GroupingType;
import com.alee.laf.button.WebButton;
import com.alee.laf.button.WebToggleButton;
import com.alee.laf.checkbox.WebCheckBox;
import com.alee.laf.filechooser.WebFileChooser;
import com.alee.laf.label.WebLabel;
import com.alee.laf.list.WebList;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.tabbedpane.WebTabbedPane;
import com.alee.laf.text.WebPasswordField;
import com.alee.laf.text.WebTextField;
import com.alee.managers.popup.PopupAdapter;
import com.alee.managers.popup.WebPopup;
import com.alee.managers.tooltip.TooltipManager;
import com.alee.utils.ImageUtils;
import com.alee.utils.SwingUtils;
import com.alee.utils.filefilter.ImageFilesFilter;
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
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.Icon;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JRootPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import org.kontalk.misc.JID;
import org.kontalk.model.Contact;
import org.kontalk.model.Model;
import org.kontalk.model.chat.Chat;
import org.kontalk.model.chat.GroupChat;
import org.kontalk.model.chat.Member;
import org.kontalk.persistence.Config;
import org.kontalk.util.MediaUtils;
import org.kontalk.util.Tr;
import org.kontalk.util.XMPPUtils;

/**
 * Some own component classes used in view.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class ComponentUtils {

    private ComponentUtils() {}

    static class ScrollPane extends WebScrollPane {

        ScrollPane(Component component) {
            this(component, true);
        }

        ScrollPane(Component component, boolean border) {
            super(component);

            if (!border)
                this.setBorder(BorderFactory.createMatteBorder(1, 1, 0, 1, Color.LIGHT_GRAY));

            this.setHorizontalScrollBarPolicy(
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            this.getVerticalScrollBar().setUnitIncrement(25);
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
            String serverText = Config.getInstance().getString(Config.SERV_HOST);
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
            mSaveButton.setEnabled(this.inputToJID().isValid());
        }

        private void saveContact() {
            JID jid = this.inputToJID();

            if (!jid.isValid())
                // this shouldnt happen
                return;

            mView.getControl().createContact(jid,
                    mNameField.getText(),
                    mEncryptionBox.isSelected());

            // reset fields
            mNameField.setText("");
            mJIDField.setText("");
            mNumberField.setText("");
        }

        private JID inputToJID() {
            return mTabbedPane.getSelectedIndex() == 0 ?
                    JID.bare(XMPPUtils.phoneNumberToKontalkLocal(
                            mPrefixField.getText()+mNumberField.getText()),
                            mServerField.getText()) :
                    JID.bare(mJIDField.getText());
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
        private final Model mModel;
        private final WebTextField mSubjectField;
        private final ParticipantsList mList;

        private final WebButton mCreateButton;

        AddGroupChatPanel(View view, Model model, final Component focusGainer) {
            mView = view;
            mModel = model;

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
                    new WebLabel(Tr.tr("Subject:")+" "), mSubjectField));

            groupPanel.add(new WebLabel(Tr.tr("Select participants:")+" "));

            mList = new ParticipantsList();
            mList.setVisibleRowCount(6);
            mList.addListSelectionListener(new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent e) {
                    AddGroupChatPanel.this.checkSaveButton();
                }
            });
            groupPanel.add(new ScrollPane(mList).setPreferredWidth(160));

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
                    !mList.getSelectedContacts().isEmpty());
        }

        private void createGroup() {
            GroupChat newChat = mView.getControl().createGroupChat(
                    mList.getSelectedContacts(),
                    mSubjectField.getText()).orElse(null);

            if (newChat != null)
                mView.showChat(newChat);

            mSubjectField.setText("");
        }

        @Override
        void onShow() {
            List<Contact> contacts = new LinkedList<>();
            for (Contact c : Utils.allContacts(mModel.contacts(), false)) {
                if (c.isKontalkUser() && !c.isMe())
                    contacts.add(c);
            }

            contacts.sort(new Comparator<Contact>() {
                @Override
                public int compare(Contact c1, Contact c2) {
                    return Utils.compareContacts(c1, c2);
                }
            });

            mList.setContacts(contacts);
        }
    }

    // Note: https://github.com/mgarin/weblaf/issues/153
    static class ParticipantsList extends WebList {

        private final DefaultListModel<Contact> mModel;

        @SuppressWarnings("unchecked")
        ParticipantsList() {
            mModel = new DefaultListModel<>();
            this.setModel(mModel);
            this.setFixedCellHeight(25);

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

        void setContacts(List<Contact> contacts) {
            mModel.clear();

            for (Contact contact : contacts)
                    mModel.addElement(contact);
        }

        @SuppressWarnings("unchecked")
        List<Contact> getSelectedContacts() {
            return this.getSelectedValuesList();
        }

        private class CellRenderer extends WebLabel implements ListCellRenderer<Contact> {
            @Override
            public Component getListCellRendererComponent(JList list,
                    Contact contact,
                    int index,
                    boolean isSelected,
                    boolean cellHasFocus) {
                this.setText(" " + Utils.displayName(contact));

                this.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,Color.LIGHT_GRAY));

                return this;
            }
        }
    }

    // NOTE: https://github.com/mgarin/weblaf/issues/153
    static class MemberList extends WebList {

        private final DefaultListModel<Member> mModel;

        public MemberList() {
            this(true);
        }

        @SuppressWarnings("unchecked")
        MemberList(boolean selectable) {
            mModel = new DefaultListModel<>();
            this.setModel(mModel);
            this.setFixedCellHeight(25);

            this.setEnabled(selectable);
            if (selectable) {
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
            }

            this.setCellRenderer(new CellRenderer());
        }

        void setMembers(List<Member> members) {
            mModel.clear();

            for (Member member : members)
                    mModel.addElement(member);
        }

        private class CellRenderer extends WebPanel implements ListCellRenderer<Member> {
            private final WebLabel mNameLabel;
            private final WebLabel mRoleLabel;

            public CellRenderer() {
                mNameLabel = new WebLabel();
                mRoleLabel = new WebLabel();
                mRoleLabel.setForeground(View.DARK_GREEN);

                this.setMargin(View.MARGIN_DEFAULT);
                this.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

                this.add(new GroupPanel(GroupingType.fillMiddle, View.GAP_DEFAULT,
                        mNameLabel, Box.createGlue(), mRoleLabel),
                        BorderLayout.CENTER);
            }

            @Override
            public Component getListCellRendererComponent(JList list,
                    Member member,
                    int index,
                    boolean isSelected,
                    boolean cellHasFocus) {
                mNameLabel.setText(
                        Utils.displayName(member.getContact(), View.MAX_NAME_IN_GROUP_LENGTH));
                mRoleLabel.setText(Utils.role(member.getRole()));

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

    static class EditableTextField extends WebTextField {

        EditableTextField(int maxTextLength, int columns, final Component focusGainer) {
            this("", maxTextLength, true, columns, focusGainer);
        }

        EditableTextField(String text, int maxTextLength, boolean editable,
                int columns, final Component focusGainer) {
            super(new ComponentUtils.TextLimitDocument(maxTextLength), text, columns);

            this.setTrailingComponent(new WebImage(Utils.getIcon("ic_ui_edit.png")));

            this.setEditable(editable);
            this.setFocusable(editable);

            // edit mode is higher than label mode
            this.setMinimumHeight(27);

            this.setHideInputPromptOnFocus(false);
            this.addFocusListener(new FocusListener() {
                @Override
                public void focusGained(FocusEvent e) {
                    EditableTextField.this.switchToEditMode();
                }
                @Override
                public void focusLost(FocusEvent e) {
                    EditableTextField.this.onFocusLost();
                    EditableTextField.this.switchToLabelMode();
                }
            });
            this.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    focusGainer.requestFocus();
                }
            });

            this.switchToLabelMode();
        }

        private void switchToEditMode() {
            String text = this.editText();
            this.setInputPrompt(text);
            this.setText(text);
            this.setDrawBorder(true);
            this.getTrailingComponent().setVisible(false);
        }

        private void switchToLabelMode() {
            this.setText(this.labelText());
            this.setDrawBorder(false);
            this.getTrailingComponent().setVisible(true);
        }

        protected String labelText() {
            return this.getText();
        }

        protected String editText() {
            return this.getText();
        }

        protected void onFocusLost() {};
    }

    static class ModalPopup extends WebPopup {

        private final AbstractButton mInvoker;
        private final WebPanel layerPanel;

        ModalPopup(AbstractButton invokerButton) {
            mInvoker = invokerButton;

            layerPanel = new WebPanel();
            layerPanel.setOpaque(false);

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

            this.setRequestFocusOnShow(false);
        }

        void showPopup() {
            layerPanel.setVisible(true);
            this.showAsPopupMenu(mInvoker);
        }

        void close() {
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
        private Path mImagePath = Paths.get("");

        AttachmentPanel() {
           super(View.GAP_SMALL, false);

           mStatus = new WebLabel().setItalicFont();
           this.add(mStatus);

           mAttLabel = new WebLinkLabel();
           this.add(mAttLabel);
        }

        void setImage(Path path) {
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

    // NOTE: no option to adjust image to component size like for WebImage,
    // -> component size depends on image size
    static class AvatarImage extends WebDecoratedImage {

        private final int mSize;

        AvatarImage(int size) {
            mSize = size;

            this.setRound(0);
        }

        void setAvatarImage(Contact c) {
            this.setImage(AvatarLoader.load(c, mSize));
        }

        void setAvatarImage(Chat c) {
            this.setImage(AvatarLoader.load(c, mSize));
        }
    }

    static abstract class EditableAvatarImage extends WebDecoratedImage {

        private final int mSize;
        private final WebFileChooser mImgChooser;

        private BufferedImage mImage = null;
        private boolean mImageChanged = false;

        EditableAvatarImage(int size, boolean enabled, Optional<BufferedImage> image) {
            mSize = size;

            mImgChooser = new WebFileChooser();
            mImgChooser.setFileFilter(new ImageFilesFilter());

            mImage = image.orElse(null);

            this.setRound(0);
            this.setGrayscale(!enabled);
            this.setEnabled(enabled);

            this.setImage(mImage != null ? mImage : this.defaultImage());

            this.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    check(e);
                }
                @Override
                public void mouseReleased(MouseEvent e) {
                    check(e);
                }
                private void check(MouseEvent e) {
                    if (e.isPopupTrigger() && enabled) {
                        EditableAvatarImage.this.showPopupMenu(e);
                    }
                }
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1 && enabled) {
                        EditableAvatarImage.this.chooseImage();
                    }
                }
            });

            TooltipManager.setTooltip(this, this.tooltipText());
        }

        private void changeImage(BufferedImage image) {
            mImage = image;
            mImageChanged = true;
            this.setImage(image != null ? image : this.defaultImage());
            this.onImageChange(Optional.ofNullable(image));
            TooltipManager.setTooltip(this, this.tooltipText());
        }

        void onImageChange(Optional<BufferedImage> optImage) {}

        boolean imageChanged() {
            return mImageChanged;
        }

        Optional<BufferedImage> getAvatarImage() {
            return Optional.ofNullable(mImage);
        }

        abstract BufferedImage defaultImage();

        abstract boolean canRemove();

        protected String tooltipText() {
            return this.canRemove() ?
                    Tr.tr("Right click to unset") :
                    Tr.tr("Click to choose image");
        }

        protected void update() {
            mImage = this.defaultImage();
            mImageChanged = false;
            this.setImage(mImage);
        }

        private void chooseImage() {
            int state = mImgChooser.showOpenDialog(this);
            if (state != WebFileChooser.APPROVE_OPTION)
                return;

            File imgFile = mImgChooser.getSelectedFile();
            if (!imgFile.isFile())
                return;

            BufferedImage img = MediaUtils.readImage(imgFile).orElse(null);
            if (img == null)
                return;

            this.changeImage(ImageUtils.createPreviewImage(img, mSize));
        }

        private void showPopupMenu(MouseEvent e) {
            WebPopupMenu menu = new WebPopupMenu();
            WebMenuItem removeItem = new WebMenuItem(Tr.tr("Remove"));
            removeItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        EditableAvatarImage.this.changeImage(null);
                    }
                });
            removeItem.setEnabled(EditableAvatarImage.this.canRemove());
            menu.add(removeItem);
            menu.show(this, e.getX(), e.getY());
        }
    }
}

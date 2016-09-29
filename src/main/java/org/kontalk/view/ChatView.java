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

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;

import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.GroupingType;
import com.alee.extended.panel.WebOverlay;
import com.alee.laf.button.WebButton;
import com.alee.laf.button.WebToggleButton;
import com.alee.laf.filechooser.WebFileChooser;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.text.WebTextArea;
import com.alee.laf.viewport.WebViewport;
import com.alee.managers.hotkey.Hotkey;
import com.alee.managers.hotkey.HotkeyData;
import com.alee.managers.language.data.TooltipWay;
import com.alee.managers.tooltip.TooltipManager;
import com.alee.utils.filefilter.AllFilesFilter;
import com.alee.utils.filefilter.CustomFileFilter;
import com.alee.utils.swing.DocumentChangeListener;
import org.apache.commons.io.FileUtils;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.kontalk.client.FeatureDiscovery;
import org.kontalk.model.Contact;
import org.kontalk.model.chat.Chat;
import org.kontalk.model.chat.GroupChat;
import org.kontalk.persistence.Config;
import org.kontalk.system.AttachmentManager;
import org.kontalk.system.Control;
import org.kontalk.util.EncodingUtils;
import org.kontalk.util.MediaUtils;
import org.kontalk.util.Tr;

/**
 * Panel showing the currently selected chat.
 *
 * One view object for all chats.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class ChatView extends WebPanel implements Observer {

    private final View mView;

    private final ComponentUtils.AvatarImage mAvatar;
    private final WebLabel mTitleLabel;
    private final WebLabel mSubTitleLabel;
    private final WebScrollPane mScrollPane;
    private final FileDropHandler mDropHandler;
    private final WebTextArea mSendTextArea;
    private final WebOverlay mOverlay;
    private final WebLabel mOverlayLabel;
    private final WebLabel mEncryptionStatus;
    private final WebButton mSendButton;
    private final WebFileChooser mFileChooser;
    private final WebButton mFileButton;

    private final Map<Chat, MessageList> mMessageListCache = new HashMap<>();

    private Background mDefaultBG;

    private boolean mScrollDown = false;

    ChatView(View view) {
        mView = view;

        this.setLayout(new BorderLayout(View.GAP_SMALL, View.GAP_SMALL));

        WebPanel titlePanel = new WebPanel(new BorderLayout(View.GAP_DEFAULT, 0));
        titlePanel.setMargin(View.MARGIN_SMALL, View.MARGIN_SMALL, 0, View.MARGIN_SMALL);

        mAvatar = new ComponentUtils.AvatarImage(View.AVATAR_CHAT_SIZE);
        titlePanel.add(mAvatar, BorderLayout.WEST);

        mTitleLabel = new WebLabel();
        mTitleLabel.setFontSize(View.FONT_SIZE_HUGE);
        mTitleLabel.setDrawShade(true);
        mSubTitleLabel = new WebLabel();
        mSubTitleLabel.setFontSize(View.FONT_SIZE_TINY);
        mSubTitleLabel.setForeground(Color.GRAY);
        titlePanel.add(new GroupPanel(View.GAP_SMALL, false, mTitleLabel, mSubTitleLabel)
                        .setMargin(View.MARGIN_SMALL, 0, 0, 0),
                BorderLayout.CENTER);

        WebToggleButton editButton = new ComponentUtils.ToggleButton(
                Utils.getIcon("ic_ui_menu.png"),
                Tr.tr("Edit this chat")) {
            @Override
            Optional<ComponentUtils.PopupPanel> getPanel() {
                return ChatView.this.getPopupPanel();
            }
        };
        editButton.setTopBgColor(titlePanel.getBackground());
        editButton.setBottomBgColor(titlePanel.getBackground());

        titlePanel.add(editButton, BorderLayout.EAST);
        this.add(titlePanel, BorderLayout.NORTH);

        mScrollPane = new ComponentUtils.ScrollPane(this).setShadeWidth(0);
        mScrollPane.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {
            @Override
            public void adjustmentValueChanged(AdjustmentEvent e) {
                // this is not perfect at all: after adding all items, they still
                // dont have any content and so their height is unknown
                // (== very small). While rendering, content is added and we force
                // scrolling down WHILE rendering until the final bottom is reached
                if (e.getValueIsAdjusting())
                    mScrollDown = false;
                if (mScrollDown)
                    e.getAdjustable().setValue(e.getAdjustable().getMaximum());
            }
        });
        mScrollPane.setViewport(new WebViewport() {
            @Override
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                BufferedImage bg =
                        ChatView.this.getCurrentBackground().updateNowOrLater().orElse(null);
                // if there is something to draw, draw it now even if its old
                if (bg != null)
                    g.drawImage(bg, 0, 0, this.getWidth(), this.getHeight(), null);
            }
        });
        this.add(mScrollPane, BorderLayout.CENTER);

        // bottom panel...
        WebPanel bottomPanel = new WebPanel(new BorderLayout(View.GAP_SMALL, View.GAP_SMALL));

        // file chooser button
        mFileChooser = new WebFileChooser();
        mFileChooser.setMultiSelectionEnabled(false);
        mFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        mFileChooser.setFileFilter(new CustomFileFilter(AllFilesFilter.ICON,
                Tr.tr("Supported files")) {
            @Override
            public boolean accept(File file) {
                return isAllowed(file);
            }
        });
        mFileButton = new WebButton(Tr.tr("File"), Utils.getIcon("ic_ui_attach.png"))
                .setBottomBgColor(titlePanel.getBackground());

        mFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ChatView.this.showFileDialog();
            }
        });
        // encryption status label
        mEncryptionStatus = new WebLabel();

        // send button
        mSendButton = new WebButton(Tr.tr("Send"))
                .setBottomBgColor(titlePanel.getBackground())
                .setFontStyle(true, false);
        TooltipManager.addTooltip(mSendButton, Tr.tr("Send Message"));
        mSendButton.setEnabled(false);
        mSendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Component focusOwner = SwingUtilities.getWindowAncestor(ChatView.this).getFocusOwner();
                if (focusOwner != mSendTextArea && focusOwner != mSendButton)
                    return;

                ChatView.this.sendMsg();
                mSendTextArea.requestFocusInWindow();
            }
        });

        bottomPanel.add(new GroupPanel(GroupingType.fillMiddle, View.GAP_DEFAULT,
                mFileButton,
                Box.createGlue(),
                new GroupPanel(View.GAP_DEFAULT, mEncryptionStatus, mSendButton)),
                BorderLayout.NORTH);

        // text area
        mSendTextArea = new WebTextArea();
        mSendTextArea.setMargin(View.MARGIN_SMALL);
        mSendTextArea.setBorder(null);
        mSendTextArea.setLineWrap(true);
        mSendTextArea.setWrapStyleWord(true);
        mSendTextArea.setFontSize(View.FONT_SIZE_NORMAL);
        mSendTextArea.setInputPrompt(Tr.tr("Type to compose"));
        mSendTextArea.setInputPromptHorizontalPosition(SwingConstants.LEFT);
        mSendTextArea.setComponentPopupMenu(Utils.createCopyMenu(true));
        mSendTextArea.getDocument().addDocumentListener(new DocumentChangeListener() {
            @Override
            public void documentChanged(DocumentEvent e) {
                ChatView.this.onKeyTypeEvent(e.getDocument().getLength() == 0);
            }
        });

        // text area scroll pane
        WebScrollPane textScrollPane = new ComponentUtils.GrowingScrollPane(mSendTextArea, this);
        textScrollPane.setBorder(null);

        // text area overlay
        mOverlayLabel = new WebLabel().setBoldFont();
        mOverlay = new WebOverlay(textScrollPane,
                mOverlayLabel, SwingConstants.CENTER, SwingConstants.CENTER);
        mOverlay.setUndecorated(false)
                .setMargin(View.MARGIN_SMALL)
                .setWebColoredBackground(false);
        bottomPanel.add(mOverlay, BorderLayout.CENTER);

        // old transfer handler of text area is fallback for bottom panel
        mDropHandler = new FileDropHandler(mSendTextArea.getTransferHandler());
        bottomPanel.setTransferHandler(mDropHandler);
        mSendTextArea.setTransferHandler(mDropHandler);

        this.add(bottomPanel, BorderLayout.SOUTH);

        this.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                mSendTextArea.requestFocusInWindow();
            }
        });

        this.loadDefaultBG();
    }

    private MessageList currentMessageListOrNull() {
        Component view = mScrollPane.getViewport().getView();
        if (view == null || !(view instanceof MessageList))
            return null;
        return (MessageList) view;
    }

    Optional<Chat> getCurrentChat() {
        MessageList view = this.currentMessageListOrNull();
        return view == null ?
                Optional.<Chat>empty() :
                Optional.of(view.getChat());
    }

    void filterCurrentChat(String searchText) {
        MessageList view = this.currentMessageListOrNull();
        if (view == null)
            return;
        view.filterItems(searchText);
    }

    void showChat(Chat chat) {
        Chat oldChat = this.getCurrentChat().orElse(null);
        if (oldChat != null)
            oldChat.deleteObserver(this);

        chat.addObserver(this);

        if (!mMessageListCache.containsKey(chat)) {
            MessageList newMessageList = new MessageList(mView, this, chat);
            chat.addObserver(newMessageList);
            mMessageListCache.put(chat, newMessageList);
        }
        // set to current chat
        mScrollPane.getViewport().setView(mMessageListCache.get(chat));
        this.onChatChange();

        chat.setRead();
    }

    void setColor(Color color) {
        mScrollPane.getViewport().setBackground(color);
    }

    void loadDefaultBG() {
        String imagePath = Config.getInstance().getString(Config.VIEW_CHAT_BG);
        mDefaultBG = !imagePath.isEmpty() ?
                new Background(mScrollPane.getViewport(), imagePath) :
                new Background(mScrollPane.getViewport());
        mScrollPane.getViewport().repaint();
    }

    private Background getCurrentBackground() {
        MessageList view = this.currentMessageListOrNull();
        if (view == null)
            return mDefaultBG;
        Background bg = view.getBG().orElse(null);
        return bg == null ? mDefaultBG : bg;
    }

    Optional<Background> createBG(Chat.ViewSettings s) {
        JViewport p = mScrollPane.getViewport();
        if (s.getBGColor().isPresent()) {
            Color c = s.getBGColor().get();
            // TODO color not used
            return Optional.empty();
        } else if (!s.getImagePath().isEmpty()) {
            return Optional.of(new Background(p, s.getImagePath()));
        } else {
            return Optional.empty();
        }
    }

    void setScrollDown() {
        // does still not work
//        SwingUtilities.invokeLater(new Runnable() {
//            @Override
//            public void run() {
//                WebScrollBar verticalBar = mScrollPane.getWebVerticalScrollBar();
//                verticalBar.setValue(verticalBar.getMaximum());
//            }
//        });
        mScrollDown = true;
    }

    void setHotkeys(final boolean enterSends) {
        for (KeyListener l : mSendTextArea.getKeyListeners())
            mSendTextArea.removeKeyListener(l);

        mSendTextArea.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (enterSends && e.getKeyCode() == KeyEvent.VK_ENTER &&
                        e.getModifiersEx() == KeyEvent.CTRL_DOWN_MASK) {
                    e.consume();
                    mSendTextArea.append(EncodingUtils.EOL);
                }
                if (enterSends && e.getKeyCode() == KeyEvent.VK_ENTER &&
                        e.getModifiers() == 0) {
                    // only ignore
                    e.consume();
                }
            }
            @Override
            public void keyReleased(KeyEvent e) {
            }
            @Override
            public void keyTyped(KeyEvent e) {
            }
        });

        mSendButton.removeHotkeys();
        HotkeyData sendHotkey = enterSends ? Hotkey.ENTER : Hotkey.CTRL_ENTER;
        mSendButton.addHotkey(sendHotkey, TooltipWay.up);
    }

    void onStatusChange(Control.Status status, EnumSet<FeatureDiscovery.Feature> serverFeature) {
        Boolean supported = null;
        switch(status) {
            case CONNECTED:
                this.setColor(Color.WHITE);
                supported = serverFeature.contains(
                        FeatureDiscovery.Feature.HTTP_FILE_UPLOAD);
                break;
            case DISCONNECTED:
            case ERROR:
                this.setColor(Color.LIGHT_GRAY);
                // don't know, but assume it
                supported = true;
                break;
        }
        if (supported != null) {
            TooltipManager.setTooltip(mFileButton, Tr.tr("Send File") + " - " + (supported ?
                            Tr.tr("max. size:") + " " +
                            FileUtils.byteCountToDisplaySize(AttachmentManager.MAX_ATT_SIZE) :
                            mView.tr_not_supported));
            mFileButton.setForeground(supported ? Color.BLACK : Color.RED);
        }
    }

    @Override
    public void update(Observable o, final Object arg) {
        if (SwingUtilities.isEventDispatchThread()) {
            this.updateOnEDT(arg);
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ChatView.this.updateOnEDT(arg);
            }
        });
    }

    private void updateOnEDT(Object arg) {
        if (arg instanceof Chat) {
            Chat chat = (Chat) arg;
            if (chat.isDeleted()) {
                MessageList viewList = mMessageListCache.remove(chat);
                if (viewList != null) {
                    viewList.clearItems();
                    chat.deleteObserver(viewList);
                }
            }
        }

        if (arg == Chat.ViewChange.SUBJECT || arg == Chat.ViewChange.CONTACT ||
                arg == Chat.ViewChange.MEMBERS) {
            this.onChatChange();
        }
    }

    private void onChatChange() {
        Chat chat = this.getCurrentChat().orElse(null);
        if (chat == null)
            return;

        // avatar
        mAvatar.setAvatarImage(chat);

        // chat titles
        mTitleLabel.setText(Utils.chatTitle(chat));
        List<Contact> contacts = Utils.contactList(chat);
        mSubTitleLabel.setText(contacts.isEmpty() ? "(" + Tr.tr("No members") + ")"
                : chat.isGroupChat() ? Utils.displayNames(contacts, View.MAX_NAME_IN_LIST_LENGTH)
                        : Utils.mainStatus(contacts.iterator().next(), true));

        // text area
        boolean enabled = chat.isValid();
        mSendTextArea.setEnabled(enabled);
        Color textBG = enabled ? Color.WHITE : Color.LIGHT_GRAY;
        mSendTextArea.setBackground(textBG);
        mOverlay.setBackground(textBG);
        mOverlayLabel.setText(chat instanceof GroupChat && !((GroupChat) chat).containsMe()
                ? Tr.tr("You are not member of this group") : "");

        // send button
        this.updateEnabledButtons();

        // encryption status
        boolean isEncrypted = chat.isSendEncrypted();
        String encryption = isEncrypted ?
                Tr.tr("Encrypted") :
                Tr.tr("Not encrypted");

        mEncryptionStatus.setText(encryption);
        mEncryptionStatus.setForeground(isEncrypted != chat.canSendEncrypted() ?
                Color.RED :
                Color.BLACK);
    }

    private Optional<ComponentUtils.PopupPanel> getPopupPanel() {
        Chat chat = ChatView.this.getCurrentChat().orElse(null);
        return chat == null ? Optional.empty() : Optional.of(new ChatDetails(mView, chat));
    }

    private void onKeyTypeEvent(boolean empty) {
        this.updateEnabledButtons();

        Chat chat = this.getCurrentChat().orElse(null);
        if (chat == null)
            return;

        // workaround: clearing the text area is not a key event
        if (!empty)
            mView.getControl().handleOwnChatStateEvent(chat, ChatState.composing);
    }

    private void updateEnabledButtons() {
        Chat chat = this.getCurrentChat().orElse(null);
        if (chat == null)
            return;

        // enable if chat is valid...
        boolean canSendMessage = chat.isValid() &&
                // ...and encrypted messages can be send
                (!chat.isSendEncrypted() || chat.canSendEncrypted());

        mFileButton.setEnabled(canSendMessage);
        mSendButton.setEnabled(canSendMessage &&
                // + there is text to send...
                !mSendTextArea.getText().trim().isEmpty());
        mDropHandler.setDropEnabled(canSendMessage);
    }

    private void sendMsg() {
        Chat chat = this.getCurrentChat().orElse(null);
        if (chat == null)
            // no current chat
            return;

        // TODO sending text AND attachment (?)
       //List<File> attachments = mAttField.getSelectedFiles();
//       if (!attachments.isEmpty())
//           mView.getControl().sendAttachment(optChat.get(), attachments.get(0).toPath());
//       else
        mView.getControl().sendText(chat, mSendTextArea.getText());

        mSendTextArea.setText("");
    }

    private void showFileDialog() {
        int option = mFileChooser.showOpenDialog(ChatView.this);
        if (option != WebFileChooser.APPROVE_OPTION)
            return;

        File file = mFileChooser.getSelectedFile();
        mFileChooser.setCurrentDirectory(file.toPath().getParent().toString());
        this.sendFile(file);
    }

    private void sendFile(File file) {
        Chat chat = this.getCurrentChat().orElse(null);
        if (chat == null)
            return;

        mView.getControl().sendAttachment(chat, file.toPath());
    }

    /** A background image of chat view with efficient async reloading. */
    final class Background implements ImageObserver {
        private final Component mParent;
        // background image from resource or user selected
        private final Image mOrigin;
        // cached background with size of viewport
        private BufferedImage mCached = null;

        private Background(Component parent, Image origin) {
            mParent = parent;
            mOrigin = origin;
        }

        /** Default, no chat specific settings. */
        Background(Component parent) {
            this(parent, Utils.getImage("chat_bg.png"));
        }

        /** Image set by user (global or only for chat). */
        Background(Component parent, String imagePath) {
            // image loaded async!
            this(parent, Toolkit.getDefaultToolkit().createImage(imagePath));
        }

        /**
         * Update the background image for this parent. Returns immediately, but
         * repaints parent if updating is done asynchronously.
         * @return if synchronized update is possible the updated image, else an
         * old image if present
         */
        Optional<BufferedImage> updateNowOrLater() {
            if (mCached == null ||
                    mCached.getWidth() != mParent.getWidth() ||
                    mCached.getHeight() != mParent.getHeight()) {
                if (this.loadOrigin()) {
                    // goto 2
                    this.scaleOrigin();
                }
            }
            return Optional.ofNullable(mCached);
        }

        // step 1: ensure original image is loaded (if present)
        private boolean loadOrigin() {
            if (mOrigin == null)
                return true;
            return mOrigin.getWidth(this) != -1;
        }

        // step 2: scale image (if present)
        private boolean scaleOrigin() {
            if (mOrigin == null) {
                // goto 3
                this.updateCachedBG(null);
                return true;
            }
            Image scaledImage = MediaUtils.scaleMaxAsync(mOrigin,
                    mParent.getWidth(),
                    mParent.getHeight());
            if (scaledImage.getWidth(this) != -1) {
                // goto 3
                this.updateCachedBG(scaledImage);
                return true;
            }
            return false;
        }

        // step 3: paint cache from scaled image (if present)
        private void updateCachedBG(Image scaledImage) {
            int width = mParent.getWidth();
            int height = mParent.getHeight();
            mCached = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D cachedG = mCached.createGraphics();
            if (scaledImage == null)
                return;
            // tiling
            int iw = scaledImage.getWidth(null);
            int ih = scaledImage.getHeight(null);
            for (int x = 0; x < width; x += iw) {
                for (int y = 0; y < height; y += ih) {
                    cachedG.drawImage(scaledImage, x, y, iw, ih, null);
                }
            }
        }

        @Override
        public boolean imageUpdate(Image img, int infoflags, int x, int y, int w, int h) {
            // ignore if image is not completely loaded
            if ((infoflags & ImageObserver.ALLBITS) == 0) {
                return true;
            }

            if (img.equals(mOrigin)) {
                // original image done loading, goto 2
                boolean sync = this.scaleOrigin();
                if (sync)
                    mParent.repaint();
                return false;
            } else {
                // scaling done, goto 3
                this.updateCachedBG(img);
                mParent.repaint();
                return false;
            }
        }
    }

    private final class FileDropHandler extends TransferHandler {

        private final TransferHandler mTextHandler;

        private boolean mDropEnabled = true;

        public FileDropHandler(TransferHandler textHandler) {
            mTextHandler = textHandler;
        }

        void setDropEnabled(boolean enabled) {
            mDropEnabled = enabled;
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (support.isDrop() && !mDropEnabled)
                return false;

            if (support.getComponent() == mSendTextArea
                    && mTextHandler.canImport(support))
                return true;

            for (DataFlavor flavor : support.getDataFlavors()) {
                if (flavor.isFlavorJavaFileListType()) {
                    return true;
                }
            }

            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean importData(TransferHandler.TransferSupport support) {
            if (!this.canImport(support))
                return false;

            if (support.getComponent() == mSendTextArea &&
                    mTextHandler.importData(support)) {
                // dropping text on text area was handled
                return true;
            }

            List<File> files;
            try {
                files = (List<File>) support.getTransferable()
                        .getTransferData(DataFlavor.javaFileListFlavor);
            } catch (UnsupportedFlavorException | IOException ex) {
                // should never happen (or JDK is buggy)
                return false;
            }

            for (File file: files) {
                if (isAllowed(file)) {
                    ChatView.this.sendFile(file);
                }
            }
            return !files.isEmpty();
        }

        @Override
        public void exportToClipboard(JComponent comp, Clipboard clip, int action) throws IllegalStateException {
            mTextHandler.exportToClipboard(comp, clip, action);
        }
    }

    private static boolean isAllowed(File file) {
        return file.length() <= AttachmentManager.MAX_ATT_SIZE;
    }
}

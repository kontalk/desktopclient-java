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
import com.alee.extended.panel.GroupingType;
import com.alee.laf.button.WebButton;
import com.alee.laf.button.WebToggleButton;
import com.alee.laf.filechooser.WebFileChooser;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.splitpane.WebSplitPane;
import com.alee.laf.text.WebTextArea;
import com.alee.laf.viewport.WebViewport;
import com.alee.managers.hotkey.Hotkey;
import com.alee.managers.hotkey.HotkeyData;
import com.alee.managers.language.data.TooltipWay;
import com.alee.managers.tooltip.TooltipManager;
import com.alee.utils.filefilter.AllFilesFilter;
import com.alee.utils.filefilter.CustomFileFilter;
import com.alee.utils.swing.DocumentChangeListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import javax.swing.Box;
import javax.swing.JFileChooser;
import static javax.swing.JSplitPane.VERTICAL_SPLIT;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import org.apache.commons.io.FileUtils;
import org.apache.tika.Tika;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.kontalk.model.Chat;
import org.kontalk.model.ChatList;
import org.kontalk.model.Contact;
import org.kontalk.system.AttachmentManager;
import org.kontalk.system.Config;
import org.kontalk.util.MediaUtils;
import org.kontalk.util.Tr;
import static org.kontalk.view.View.MARGIN_SMALL;

/**
 * Pane that shows the currently selected chat.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class ChatView extends WebPanel implements Observer {

    private static final Tika TIKA_INSTANCE = new Tika();

    private final View mView;

    private final WebLabel mTitleLabel;
    private final WebLabel mSubTitleLabel;
    private final WebScrollPane mScrollPane;
    private final WebTextArea mSendTextArea;
    private final WebButton mSendButton;
    private final WebFileChooser fileChooser;

    private final Map<Integer, MessageList> mChatCache = new HashMap<>();

    private ComponentUtils.ModalPopup mPopup = null;
    private Background mDefaultBG;

    private Chat mCurrentChat = null;
    private boolean mScrollDown = false;

    ChatView(View view) {
        mView = view;

        WebPanel titlePanel = new WebPanel(false,
                new BorderLayout(View.GAP_SMALL, View.GAP_SMALL));
        titlePanel.setMargin(View.MARGIN_DEFAULT);
        mTitleLabel = new WebLabel();
        mTitleLabel.setFontSize(16);
        mTitleLabel.setDrawShade(true);
        mSubTitleLabel = new WebLabel();
        mSubTitleLabel.setFontSize(11);
        mSubTitleLabel.setForeground(Color.GRAY);
        titlePanel.add(new GroupPanel(View.GAP_SMALL, false, mTitleLabel, mSubTitleLabel),
                BorderLayout.CENTER);

        final WebToggleButton editButton = new WebToggleButton(
                Utils.getIcon("ic_ui_menu.png"));
        //editButton.setToolTipText(Tr.tr("Edit this chat"));
        editButton.setTopBgColor(titlePanel.getBackground());
        editButton.setBottomBgColor(titlePanel.getBackground());
        editButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                    ChatView.this.showPopup(editButton);
            }
        });
        titlePanel.add(editButton, BorderLayout.EAST);
        this.add(titlePanel, BorderLayout.NORTH);

        mScrollPane = new ScrollPane(this)
                .setShadeWidth(0);
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
                Optional<BufferedImage> optBG =
                        ChatView.this.getCurrentBackground().updateNowOrLater();
                // if there is something to draw, draw it now even if its old
                if (optBG.isPresent())
                    g.drawImage(optBG.get(), 0, 0, this.getWidth(), this.getHeight(), null);
            }
        });

        // text area
        mSendTextArea = new WebTextArea();
        mSendTextArea.setMargin(View.MARGIN_SMALL);
        mSendTextArea.setLineWrap(true);
        mSendTextArea.setWrapStyleWord(true);
        mSendTextArea.setFontSize(13);
        mSendTextArea.getDocument().addDocumentListener(new DocumentChangeListener() {
            @Override
            public void documentChanged(DocumentEvent e) {
                ChatView.this.handleKeyTypeEvent(e.getDocument().getLength() == 0);
            }
        });
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                mSendTextArea.requestFocusInWindow();
            }
        });

        WebPanel bottomPanel = new WebPanel();
        bottomPanel.setMinimumSize(new Dimension(0, 64));

        // send button
        mSendButton = new WebButton(Tr.tr("Send"))
                .setRound(0)
                //.setShadeWidth(0)
                .setBottomBgColor(titlePanel.getBackground())
                .setMargin(1, MARGIN_SMALL, 1, MARGIN_SMALL)
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
            }
        });
        fileChooser = new WebFileChooser();
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new CustomFileFilter(AllFilesFilter.ICON,
                Tr.tr("Supported files")) {
            @Override
            public boolean accept(File file) {
                return file.length() <= AttachmentManager.MAX_ATT_SIZE &&
                        AttachmentManager.SUPPORTED_MIME_TYPES.contains(
                                TIKA_INSTANCE.detect(file.getName()));
            }
        });
//        mAttField.setPreferredWidth(150);
        WebButton fileButton = new WebButton(Tr.tr("File"), Utils.getIcon("ic_ui_attach.png"))
                .setRound(0)
                .setBottomBgColor(titlePanel.getBackground())
                .setMargin(1, MARGIN_SMALL, 1, MARGIN_SMALL);
        TooltipManager.addTooltip(fileButton,
                Tr.tr("Send File - max. size:") + " " +
                        FileUtils.byteCountToDisplaySize(AttachmentManager.MAX_ATT_SIZE));
        fileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ChatView.this.showFileDialog();
            }
        });

        WebPanel textBarPanel = new GroupPanel(GroupingType.fillMiddle, 0,
                fileButton, Box.createGlue(), mSendButton)
                .setUndecorated(false)
                .setWebColoredBackground(false)
                .setShadeWidth(0);
        textBarPanel.setPaintBottom(false);
        bottomPanel.add(textBarPanel, BorderLayout.NORTH);

        bottomPanel.add(new ScrollPane(mSendTextArea)
                .setShadeWidth(0)
                .setRound(0),
                BorderLayout.CENTER);

        WebSplitPane splitPane = new WebSplitPane(VERTICAL_SPLIT,
                mScrollPane,
                bottomPanel);
        splitPane.setResizeWeight(1.0);
        this.add(splitPane, BorderLayout.CENTER);

        this.loadDefaultBG();
    }

    private Optional<MessageList> getCurrentList() {
        Component view = mScrollPane.getViewport().getView();
        if (view == null || !(view instanceof MessageList))
            return Optional.empty();
        return Optional.of((MessageList) view);
    }

    Optional<Chat> getCurrentChat() {
        Optional<MessageList> optview = this.getCurrentList();
        return optview.isPresent() ?
                Optional.of(optview.get().getChat()) :
                Optional.<Chat>empty();
    }

    void filterCurrentChat(String searchText) {
        Optional<MessageList> optList = this.getCurrentList();
        if (!optList.isPresent())
            return;
        optList.get().filterItems(searchText);
    }

    void showChat(Chat chat) {
        if (mCurrentChat != null)
            mCurrentChat.deleteObserver(this);

        mCurrentChat = chat;
        mCurrentChat.addObserver(this);

        this.updateTitles();
        if (!mChatCache.containsKey(mCurrentChat.getID())) {
            MessageList newMessageList = new MessageList(mView, this, mCurrentChat);
            mCurrentChat.addObserver(newMessageList);
            mChatCache.put(mCurrentChat.getID(), newMessageList);
        }
        MessageList list = mChatCache.get(mCurrentChat.getID());
        mScrollPane.getViewport().setView(list);

        mCurrentChat.setRead();
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
        Optional<MessageList> optView = this.getCurrentList();
        if (!optView.isPresent())
            return mDefaultBG;
        Optional<Background> optBG = optView.get().getBG();
        if (!optBG.isPresent())
            return mDefaultBG;
        return optBG.get();
    }

    Optional<Background> createBG(Chat.ViewSettings s){
        JViewport p = this.mScrollPane.getViewport();
        if (s.getBGColor().isPresent()) {
            Color c = s.getBGColor().get();
            return Optional.of(new Background(p, c));
        } else if (!s.getImagePath().isEmpty()) {
            return Optional.of(new Background(p, s.getImagePath()));
        } else {
            return Optional.empty();
        }
    }

    void setScrolling() {
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
                    mSendTextArea.append(System.getProperty("line.separator"));
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
            if (!ChatList.getInstance().contains(chat.getID())) {
                // chat was deleted
                MessageList viewList = mChatCache.get(chat.getID());
                if (viewList != null)
                    viewList.clearItems();
                chat.deleteObserver(viewList);
                mChatCache.remove(chat.getID());
                if(this.getCurrentChat().orElse(null) == chat) {
                    mScrollPane.setViewportView(null);
                }
            }
        }

        if (arg instanceof String || arg instanceof Contact) {
            this.updateTitles();
        }
    }

    private void updateTitles() {
        if (mCurrentChat == null)
            return;
        mTitleLabel.setText(Utils.chatTitle(mCurrentChat));
        if (mCurrentChat.isGroupChat()) {
            mSubTitleLabel.setText(Utils.contactNameList(mCurrentChat.getContacts()));
        } else {
            Optional<Contact> optContact = mCurrentChat.getSingleContact();
            mSubTitleLabel.setText(optContact.isPresent() ?
                    Utils.mainStatus(optContact.get(), true) :
                    "--no subtitle--");
        }
    }

    private void showPopup(final WebToggleButton invoker) {
        Optional<Chat> optChat = ChatView.this.getCurrentChat();
        if (!optChat.isPresent())
            return;
        if (mPopup == null)
            mPopup = new ComponentUtils.ModalPopup(invoker);

        mPopup.removeAll();
        mPopup.add(new ChatDetails(mPopup, optChat.get()));
        mPopup.showPopup();
    }

    private void handleKeyTypeEvent(boolean empty) {
        mSendButton.setEnabled(!mSendTextArea.getText().trim().isEmpty());

        Optional<Chat> optChat = this.getCurrentChat();
        if (!optChat.isPresent())
            return;

        // workaround: clearing the text area is not a key event
        if (!empty)
            mView.getControl().handleOwnChatStateEvent(optChat.get(), ChatState.composing);
    }

    private void sendMsg() {
        Optional<Chat> optChat = this.getCurrentChat();
        if (!optChat.isPresent())
            // now current chat
            return;

       //List<File> attachments = mAttField.getSelectedFiles();
//       if (!attachments.isEmpty())
//           mView.getControl().sendAttachment(optChat.get(), attachments.get(0).toPath());
//       else
        mView.getControl().sendText(optChat.get(), mSendTextArea.getText());

        mSendTextArea.setText("");
    }

    private void showFileDialog() {
        if (fileChooser.showOpenDialog(ChatView.this) != WebFileChooser.APPROVE_OPTION)
            return;

        File file = fileChooser.getSelectedFile();
        fileChooser.setCurrentDirectory(file.toPath().getParent().toString());

        Optional<Chat> optChat = this.getCurrentChat();
        if (!optChat.isPresent())
            return;

        mView.getControl().sendAttachment(optChat.get(), file.toPath());
    }

    /** A background image of chat view with efficient async reloading. */
    final class Background implements ImageObserver {
        private final Component mParent;
        // background image from resource or user selected
        private final Image mOrigin;
        // background color, can be set by user
        private final Color mCustomColor;
        // cached background with size of viewport
        private BufferedImage mCached = null;

        private Background(Component parent, Image origin, Color color) {
            mParent = parent;
            mOrigin = origin;
            mCustomColor = color;
        }

        /** Default, no chat specific settings. */
        Background(Component parent) {
            //mOrigin = View.getImage("chat_bg.png");
            this(parent, null, new Color(255, 255, 255, 255));
        }

        /** Image set by user (global or only for chat). */
        Background(Component parent, String imagePath) {
            // image loaded async!
            this(parent, Toolkit.getDefaultToolkit().createImage(imagePath), null);
        }

        /** Chat specific color. */
        Background(Component parent, Color bottomColor) {
            this(parent, null, bottomColor);
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
            Image scaledImage = MediaUtils.scale(mOrigin,
                    mParent.getWidth(),
                    mParent.getHeight(),
                    true);
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
            // gradient background of background
            if (mCustomColor != null) {
                GradientPaint p2 = new GradientPaint(
                        0, 0, mCustomColor,
                        width, 0, new Color(0, 0, 0, 0));
                cachedG.setPaint(p2);
                cachedG.fillRect(0, 0, width, ChatView.this.getHeight());
            }
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
}

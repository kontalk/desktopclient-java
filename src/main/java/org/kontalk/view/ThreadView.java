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
import com.alee.extended.panel.GroupPanel;
import com.alee.laf.label.WebLabel;
import com.alee.laf.list.UnselectableListModel;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.text.WebEditorPane;
import com.alee.laf.text.WebTextPane;
import com.alee.laf.viewport.WebViewport;
import com.alee.managers.notification.WebNotificationPopup;
import com.alee.managers.popup.PopupStyle;
import com.alee.managers.tooltip.TooltipManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.logging.Level;
import javax.swing.AbstractCellEditor;
import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.table.TableCellEditor;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BoxView;
import javax.swing.text.ComponentView;
import javax.swing.text.Element;
import javax.swing.text.IconView;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.ViewFactory;
import org.kontalk.crypto.Coder;
import org.kontalk.model.InMessage;
import org.kontalk.model.KonMessage;
import org.kontalk.model.KonThread;
import org.kontalk.model.KonThread.KonChatState;
import org.kontalk.model.MessageContent;
import org.kontalk.model.MessageContent.Attachment;
import org.kontalk.model.User;
import org.kontalk.system.Downloader;
import org.kontalk.system.Config;
import org.kontalk.util.Tr;

/**
 * Pane that shows the currently selected thread.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
final class ThreadView extends ScrollPane {
    private final static Logger LOGGER = Logger.getLogger(ThreadView.class.getName());

    private final static Icon PENDING_ICON = View.getIcon("ic_msg_pending.png");;
    private final static Icon SENT_ICON = View.getIcon("ic_msg_sent.png");
    private final static Icon DELIVERED_ICON = View.getIcon("ic_msg_delivered.png");
    private final static Icon ERROR_ICON = View.getIcon("ic_msg_error.png");
    private final static Icon CRYPT_ICON = View.getIcon("ic_msg_crypt.png");
    private final static Icon UNENCRYPT_ICON = View.getIcon("ic_msg_unencrypt.png");

    private final static SimpleDateFormat SHORT_DATE_FORMAT = new SimpleDateFormat("EEE, HH:mm");
    private final static SimpleDateFormat MID_DATE_FORMAT = new SimpleDateFormat("EEE, d MMM, HH:mm");
    private final static SimpleDateFormat LONG_DATE_FORMAT = new SimpleDateFormat("EEE, d MMM yyyy, HH:mm:ss");

    private final View mView;

    private final Map<Integer, MessageList> mThreadCache = new HashMap<>();
    private Background mDefaultBG;

    private boolean mScrollDown = false;
    private WebNotificationPopup mPopup = null;

    ThreadView(View view) {
        super(null);
        mView = view;

        this.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {
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

        this.setViewport(new WebViewport() {
            @Override
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                Optional<BufferedImage> optBG =
                        ThreadView.this.getCurrentBackground().updateNowOrLater();
                // if there is something to draw, draw it now even if its old
                if (optBG.isPresent())
                    g.drawImage(optBG.get(), 0, 0, this.getWidth(), this.getHeight(), null);
            }
        });

        this.loadDefaultBG();
    }

    private Optional<MessageList> getCurrentView() {
        Component view = this.getViewport().getView();
        if (view == null)
            return Optional.empty();
        return Optional.of((MessageList) view);
    }

    Optional<KonThread> getCurrentThread() {
        Optional<MessageList> optview = this.getCurrentView();
        return optview.isPresent() ?
                Optional.of(optview.get().getThread()) :
                Optional.<KonThread>empty();
    }

    void showThread(KonThread thread) {
        if (!mThreadCache.containsKey(thread.getID())) {
            MessageList newMessageList = new MessageList(thread);
            thread.addObserver(newMessageList);
            mThreadCache.put(thread.getID(), newMessageList);
        }
        MessageList table = mThreadCache.get(thread.getID());
        this.getViewport().setView(table);

        thread.setRead();
    }

    void setColor(Color color) {
        this.getViewport().setBackground(color);
    }

    void loadDefaultBG() {
        String imagePath = Config.getInstance().getString(Config.VIEW_THREAD_BG);
        mDefaultBG = !imagePath.isEmpty() ?
                new Background(this.getViewport(), imagePath) :
                new Background(this.getViewport());
        this.getViewport().repaint();
    }

    private void removeThread(KonThread thread) {
        MessageList viewList = mThreadCache.get(thread.getID());
        if (viewList != null)
            viewList.clearItems();
        thread.deleteObserver(viewList);
        mThreadCache.remove(thread.getID());
        if(this.getCurrentThread().orElse(null) == thread) {
            this.setViewportView(null);
        }
    }

    private Background getCurrentBackground() {
        Optional<MessageList> optView = this.getCurrentView();
        if (!optView.isPresent())
            return mDefaultBG;
        Optional<Background> optBG = optView.get().getBG();
        if (!optBG.isPresent())
            return mDefaultBG;
        return optBG.get();
    }

    /**
     * View all messages of one thread in a left/right MIM style list.
     */
    private final class MessageList extends TableView<MessageList.MessageItem, KonMessage> {

        private final KonThread mThread;
        private Optional<Background> mBackground = Optional.empty();

        private MessageList(KonThread thread) {
            super();
            mThread = thread;

            // use custom editor (for mouse events)
            this.setDefaultEditor(TableView.TableItem.class, new TableEditor());

            //this.setEditable(false);
            //this.setAutoscrolls(true);
            this.setOpaque(false);

            // disable selection
            this.setSelectionModel(new UnselectableListModel());

            // actions triggered by mouse events
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
                    if (e.isPopupTrigger()) {
                        MessageList.this.showPopupMenu(e);
                    }
                }
            });

            this.setBackground(mThread.getViewSettings());

            this.setVisible(false);
            this.updateOnEDT(null);
            this.setVisible(true);
        }

        KonThread getThread() {
            return mThread;
        }

        Optional<Background> getBG() {
            return mBackground;
        }

        @Override
        protected void updateOnEDT(Object arg) {
            if (arg instanceof Set ||
                    arg instanceof String ||
                    arg instanceof Boolean) {
                // users, subject or read status changed, nothing to do here
                return;
            }

            if (arg instanceof KonThread.ViewSettings) {
                this.setBackground((KonThread.ViewSettings) arg);
                if (ThreadView.this.getCurrentThread().orElse(null) == mThread) {
                    ThreadView.this.getViewport().repaint();
                }
                return;
            }

            if (arg instanceof KonMessage) {
                this.insertMessage((KonMessage) arg);
                return;
            }

            if (arg instanceof KonChatState) {
                this.showChatNotification((KonChatState) arg);
                return;
            }

            if (mThread.isDeleted()) {
                ThreadView.this.removeThread(mThread);
                return;
            }

            // check for new messages to add
            if (this.getModel().getRowCount() < mThread.getMessages().size())
                this.insertMessages();

            if (ThreadView.this.getCurrentThread().orElse(null) == mThread) {
                mThread.setRead();
            }
        }

        private void insertMessages() {
            Set<MessageItem> newItems = new HashSet<>();
            for (KonMessage message: mThread.getMessages()) {
                if (!this.containsValue(message)) {
                    newItems.add(new MessageItem(message));
                    // trigger scrolling
                    mScrollDown = true;
                }
            }
            this.sync(mThread.getMessages(), newItems);
        }

        private void insertMessage(KonMessage message) {
            Set<MessageItem> newItems = new HashSet<>();
            newItems.add(new MessageItem(message));
            this.sync(mThread.getMessages(), newItems);
            // trigger scrolling
            mScrollDown = true;
        }

        private void showPopupMenu(MouseEvent e) {
            int row = this.rowAtPoint(e.getPoint());
            if (row < 0)
                return;

            MessageItem messageView = this.getDisplayedItemAt(row);
            WebPopupMenu popupMenu = messageView.getPopupMenu();
            popupMenu.show(this, e.getX(), e.getY());
        }

        private void showChatNotification(KonChatState state) {
            if (ThreadView.this.getCurrentView().orElse(null) != this)
                return;

            String activity = null;
            switch(state.getState()) {
                case composing: activity = Tr.tr("is writing..."); break;
                //case paused: activity = Tr.tr("has paused"); break;
                case inactive: activity = Tr.tr("is inactive"); break;
            }
            if (activity == null)
                return;

            if (mPopup != null)
                mPopup.hidePopup();
            mPopup = new WebNotificationPopup(PopupStyle.dark);
            WebLabel textLabel = new WebLabel(state.getUser().getName()+" "+activity);
            textLabel.setForeground(Color.WHITE);
            textLabel.setMargin(5);
            mPopup.setContent(textLabel);
            mPopup.setDisplayTime(15 * 1000);
            mPopup.revalidate();
            // TODO show notification really inside this message list
            mPopup.showPopup(ThreadView.this, 10, ThreadView.this.getHeight() - 50);
        }

        private void setBackground(KonThread.ViewSettings s) {
            JViewport p = ThreadView.this.getViewport();
            // simply overwrite
            if (s.getBGColor().isPresent()) {
                Color c = s.getBGColor().get();
                mBackground = Optional.of(new Background(p, c));
            } else if (!s.getImagePath().isEmpty()) {
                mBackground = Optional.of(new Background(p, s.getImagePath()));
            } else {
                mBackground = Optional.empty();
            }
        }

        /**
         * View for one message.
         * The content is added to a panel inside this panel. For performance
         * reasons the content is created when the item is rendered in the table
         */
        final class MessageItem extends TableView<MessageItem, KonMessage>.TableItem {

            private WebLabel mFromLabel = null;
            private WebPanel mContentPanel;
            private WebTextPane mTextPane;
            private WebPanel mStatusPanel;
            private WebLabel mStatusIconLabel;
            private int mPreferredTextWidth;
            private boolean mCreated = false;

            MessageItem(KonMessage message) {
                super(message);

                this.setOpaque(false);
                this.setMargin(2);
                //this.setBorder(new EmptyBorder(10, 10, 10, 10));
            }

            private void createContent() {
                if (mCreated)
                    return;
                mCreated = true;

                WebPanel messagePanel = new WebPanel(true);
                messagePanel.setWebColoredBackground(false);
                messagePanel.setMargin(2);
                if (mValue.getDir().equals(KonMessage.Direction.IN))
                    messagePanel.setBackground(Color.WHITE);
                else
                    messagePanel.setBackground(View.LIGHT_BLUE);

                // from label
                if (mValue.getDir().equals(KonMessage.Direction.IN)) {
                    mFromLabel = new WebLabel();
                    mFromLabel.setFontSize(12);
                    mFromLabel.setForeground(Color.BLUE);
                    mFromLabel.setItalicFont();
                    messagePanel.add(mFromLabel, BorderLayout.NORTH);
                }

                mContentPanel = new WebPanel();
                mContentPanel.setOpaque(false);
                mContentPanel.setMargin(5);
                // text area
                mTextPane = new WebTextPane();
                mTextPane.setEditable(false);
                mTextPane.setOpaque(false);
                //mTextPane.setFontSize(12);
                // sets default font
                mTextPane.putClientProperty(WebEditorPane.HONOR_DISPLAY_PROPERTIES, true);
                //for detecting clicks
                mTextPane.addMouseListener(LinkUtils.CLICK_LISTENER);
                //for detecting motion
                mTextPane.addMouseMotionListener(LinkUtils.MOTION_LISTENER);
                // fix word wrap for long words
                mTextPane.setEditorKit(FIX_WRAP_KIT);
                mContentPanel.add(mTextPane, BorderLayout.CENTER);
                messagePanel.add(mContentPanel, BorderLayout.CENTER);

                mStatusPanel = new WebPanel();
                mStatusPanel.setOpaque(false);
                TooltipManager.addTooltip(mStatusPanel, "???");
                mStatusPanel.setLayout(new FlowLayout());
                // icons
                mStatusIconLabel = new WebLabel();

                this.updateOnEDT(null);

                // save the width that is requied to show the text in one line;
                // before line wrap and only once!
                mPreferredTextWidth = mTextPane.getPreferredSize().width;

                mStatusPanel.add(mStatusIconLabel);
                WebLabel encryptIconLabel = new WebLabel();
                if (mValue.getCoderStatus().isSecure()) {
                    encryptIconLabel.setIcon(CRYPT_ICON);
                } else {
                    encryptIconLabel.setIcon(UNENCRYPT_ICON);
                }
                mStatusPanel.add(encryptIconLabel);
                // date label
                WebLabel dateLabel = new WebLabel(SHORT_DATE_FORMAT.format(mValue.getDate()));
                dateLabel.setForeground(Color.GRAY);
                dateLabel.setFontSize(11);
                mStatusPanel.add(dateLabel);

                WebPanel southPanel = new WebPanel();
                southPanel.setOpaque(false);
                southPanel.add(mStatusPanel, BorderLayout.EAST);
                messagePanel.add(southPanel, BorderLayout.SOUTH);

                if (mValue.getDir().equals(KonMessage.Direction.IN)) {
                    this.add(messagePanel, BorderLayout.WEST);
                } else {
                    this.add(messagePanel, BorderLayout.EAST);
                }

                mValue.getUser().addObserver(this);
            }

            @Override
            protected void render(int listWidth, boolean isSelected) {
                this.createContent();

                // note: on the very first call the list width is zero
                int maxWidth = (int)(listWidth * 0.8);
                int width = Math.min(mPreferredTextWidth, maxWidth);
                // height is reset later
                mTextPane.setSize(width, -1);
                // textArea does not need this but textPane does, and editorPane
                // is again totally different; I love Swing
                mTextPane.setPreferredSize(new Dimension(width, mTextPane.getMinimumSize().height));
            }

            /**
             * Update what can change in a message: text, icon and attachment.
             */
            @Override
            protected void updateOnEDT(Object arg) {
                if (!mCreated)
                    return;

                if ((arg == null || arg instanceof User) && mFromLabel != null)
                    mFromLabel.setText(" "+getFromString(mValue));

                if (arg == null || arg instanceof String)
                    this.updateText();

                if (arg == null || arg instanceof KonMessage.Status)
                    this.updateStatus();

                if (arg == null || arg instanceof MessageContent.Attachment)
                    this.updateAttachment();

                // changes are not instantly painted
                MessageList.this.repaint();
            }

            // text in text area, before/after encryption
            private void updateText() {
                boolean encrypted = mValue.getCoderStatus().isEncrypted();
                String text = encrypted ? Tr.tr("[encrypted]") : mValue.getContent().getText();
                mTextPane.setFontStyle(false, encrypted);
                //mTextPane.setText(text);
                LinkUtils.linkify(mTextPane.getStyledDocument(), text);
                // hide area if there is no text
                mTextPane.setVisible(!text.isEmpty());
            }

            // status icon
            private void updateStatus() {
                String sent = Tr.tr("Sent:")+" ";
                final String firstStat;
                final Date firstDate;
                String secStat = null;
                final Date secDate;
                if (mValue.getDir() == KonMessage.Direction.OUT) {
                    firstStat = Tr.tr("Created:")+" ";
                    firstDate = mValue.getDate();
                    secDate = mValue.getServerDate().orElse(null);
                    switch (mValue.getReceiptStatus()) {
                        case PENDING :
                            mStatusIconLabel.setIcon(PENDING_ICON);
                            break;
                        case SENT :
                            mStatusIconLabel.setIcon(SENT_ICON);
                            secStat = sent;
                            break;
                        case RECEIVED:
                            mStatusIconLabel.setIcon(DELIVERED_ICON);
                            secStat = Tr.tr("Delivered:")+" ";
                            break;
                        case ERROR:
                            mStatusIconLabel.setIcon(ERROR_ICON);
                            secStat = Tr.tr("Error report:")+" ";
                            break;
                        default:
                            LOGGER.warning("unknown message receipt status!?");
                    }
                } else {
                    firstStat = sent;
                    firstDate = mValue.getServerDate().orElse(null);
                    secStat = Tr.tr("Received:")+" ";
                    secDate = mValue.getDate();
                }

                // tooltip
                String encryption = Tr.tr("unknown");
                switch (mValue.getCoderStatus().getEncryption()) {
                    case NOT: encryption = Tr.tr("not encrypted"); break;
                    case ENCRYPTED: encryption = Tr.tr("encrypted"); break;
                    case DECRYPTED: encryption = Tr.tr("decrypted"); break;
                }
                String verification = Tr.tr("unknown");
                switch (mValue.getCoderStatus().getSigning()) {
                    case NOT: verification = Tr.tr("not signed"); break;
                    case SIGNED: verification = Tr.tr("signed"); break;
                    case VERIFIED: verification = Tr.tr("verified"); break;
                }
                String problems = "";
                for (Coder.Error error: mValue.getCoderStatus().getErrors()) {
                    problems += error.toString() + " <br> ";
                }

                String html = "<html><body>" + //"<h3>Header</h3>"+
                        "<br>";
                if (firstDate != null)
                    html += firstStat + MID_DATE_FORMAT.format(firstDate) + "<br>";
                if (secStat != null && secDate != null)
                    html += secStat + MID_DATE_FORMAT.format(secDate) + "<br>";
                html += Tr.tr("Security")+": " + encryption + " / " + verification + "<br>";
                if (!problems.isEmpty())
                    html += Tr.tr("Problems")+": " + problems;

                TooltipManager.setTooltip(mStatusPanel, html);
            }

            // attachment / image, note: loading many images is very slow
            private void updateAttachment() {
                // remove possible old component (replacing does not work right)
                BorderLayout layout = (BorderLayout) mContentPanel.getLayout();
                Component oldComp = layout.getLayoutComponent(BorderLayout.SOUTH);
                if (oldComp != null)
                    mContentPanel.remove(oldComp);

                Optional<MessageContent.Attachment> optAttachment =
                        mValue.getContent().getAttachment();
                if (!optAttachment.isPresent())
                    return;

                Attachment att = optAttachment.get();
                String base = Downloader.getInstance().getAttachmentDir();
                String fName = att.getFileName();
                Path path = Paths.get(base, fName);

                // rely on mime type in message
                if (!att.getFileName().isEmpty() &&
                        att.getMimeType().startsWith("image")) {
                    WebLinkLabel imageView = new WebLinkLabel();
                    imageView.setLink("", createLinkRunnable(path));
                    // file should be present and should be an image, show it
                    ImageLoader.setImageIconAsync(imageView, path.toString());
                    mContentPanel.add(imageView, BorderLayout.SOUTH);
                    return;
                }

                // show a link to the file
                WebLabel attLabel;
                if (att.getFileName().isEmpty()) {
                    String statusText = Tr.tr("loading...");
                    switch (att.getDownloadProgress()) {
                        case 0:
                        case -2: statusText = Tr.tr("downloading..."); break;
                        case -3: statusText = Tr.tr("download failed"); break;
                    }
                    attLabel = new WebLabel(statusText);
                } else {
                    WebLinkLabel linkLabel = new WebLinkLabel();
                    linkLabel.setLink(fName, createLinkRunnable(path));
                    attLabel = linkLabel;
                }
                WebLabel labelLabel = new WebLabel(Tr.tr("Attachment:")+" ");
                labelLabel.setItalicFont();
                GroupPanel attachmentPanel = new GroupPanel(4, true, labelLabel, attLabel);
                mContentPanel.add(attachmentPanel, BorderLayout.SOUTH);
            }

            private WebPopupMenu getPopupMenu() {
                WebPopupMenu popupMenu = new WebPopupMenu();
                if (mValue.getCoderStatus().isEncrypted()) {
                    WebMenuItem decryptMenuItem = new WebMenuItem(Tr.tr("Decrypt"));
                    decryptMenuItem.setToolTipText(Tr.tr("Retry decrypting message"));
                    decryptMenuItem.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent event) {
                            KonMessage m = MessageItem.this.mValue;
                            if (!(m instanceof InMessage)) {
                                LOGGER.warning("decrypted message not incoming message");
                                return;
                            }
                            ThreadView.this.mView.callDecrypt((InMessage) m);
                        }
                    });
                    popupMenu.add(decryptMenuItem);
                }
                WebMenuItem cItem = View.createCopyMenuItem(
                        this.toPrettyString(),
                        Tr.tr("Copy message content"));
                popupMenu.add(cItem);
                return popupMenu;
            }

            private String toPrettyString() {
                String date = LONG_DATE_FORMAT.format(mValue.getDate());
                String from = getFromString(mValue);
                return date + " - " + from + " : " + mValue.getContent().getText();
            }

            @Override
            protected boolean contains(String search) {
                return mValue.getContent().getText().toLowerCase().contains(search) ||
                        mValue.getUser().getName().toLowerCase().contains(search) ||
                        mValue.getJID().toLowerCase().contains(search);
            }

            @Override
            protected void onRemove() {
                mValue.getUser().deleteObserver(this);
            }
        }
    }

    /** A background image of thread view with efficient async reloading. */
    private final class Background implements ImageObserver {
        private final Component mParent;
        // background image from resource or user selected
        private Image mOrigin;
        // cached background with size of viewport
        private BufferedImage mCached = null;
        private Color mBottomColor = null;

        /** Default, no thread specific settings. */
        Background(Component parent) {
            mParent = parent;
            mOrigin = View.getImage("thread_bg.png");
            mBottomColor = new Color(255, 255, 255, 255);
        }

        /** Image set by user (global or only for thread). */
        Background(Component parent, String imagePath) {
            mParent = parent;
            // loading async!
            mOrigin = Toolkit.getDefaultToolkit().createImage(imagePath);
        }

        /** Thread specific color. */
        Background(Component parent, Color bottomColor) {
            this(parent);
            mBottomColor = bottomColor;
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

        // step 1: ensure original image is loaded
        private boolean loadOrigin() {
            return mOrigin.getWidth(this) != -1;
        }

        // step 2: scale image
        private boolean scaleOrigin() {
            Image scaledImage = ImageLoader.scale(mOrigin, mParent.getWidth(), mParent.getHeight(), true);
            if (scaledImage.getWidth(this) != -1) {
                // goto 3
                this.updateCachedBG(scaledImage);
                return true;
            }
            return false;
        }

        // step 3: paint cache from scaled image
        private void updateCachedBG(Image scaledImage) {
            int width = mParent.getWidth();
            int height = mParent.getHeight();
            mCached = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D cachedG = mCached.createGraphics();
            // gradient background of background
            if (mBottomColor != null) {
                GradientPaint p2 = new GradientPaint(
                        0, 0, new Color(0, 0, 0, 0),
                        0, height, mBottomColor);
                cachedG.setPaint(p2);
                cachedG.fillRect(0, 0, width, ThreadView.this.getHeight());
            }
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

    // needed for correct mouse behaviour for components in items
    // (and breaks selection behaviour somehow)
    private class TableEditor extends AbstractCellEditor implements TableCellEditor {
        private TableView<?, ?>.TableItem mValue;
        @Override
        public Component getTableCellEditorComponent(JTable table,
                Object value,
                boolean isSelected,
                int row,
                int column) {
            mValue = (TableView.TableItem) value;
            return mValue;
        }
        @Override
        public Object getCellEditorValue() {
            return mValue;
        }
    }

    private static String getFromString(KonMessage message) {
        String from;
        if (!message.getUser().getName().isEmpty()) {
            from = message.getUser().getName();
        } else {
            from = message.getJID();
            if (from.length() > 40)
                from = from.substring(0, 8) + "...";
        }
        return from;
    }

    private static Runnable createLinkRunnable(final Path path) {
        return new Runnable () {
            @Override
            public void run () {
                Desktop dt = Desktop.getDesktop();
                try {
                    dt.open(new File(path.toString()));
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "can't open attachment", ex);
                }
            }
        };
    }

    private static final WrapEditorKit FIX_WRAP_KIT = new WrapEditorKit();

    /**
     * Fix for the infamous "Wrap long words" problem in Java 7+.
     * Source: https://stackoverflow.com/a/13375811
     */
    private static class WrapEditorKit extends StyledEditorKit {
        ViewFactory defaultFactory = new WrapColumnFactory();
        @Override
        public ViewFactory getViewFactory() {
            return defaultFactory;
        }

        private static class WrapColumnFactory implements ViewFactory {
            @Override
            public javax.swing.text.View create(Element elem) {
                String kind = elem.getName();
                if (kind != null) {
                    switch (kind) {
                        case AbstractDocument.ContentElementName:
                            return new WrapLabelView(elem);
                        case AbstractDocument.ParagraphElementName:
                            return new ParagraphView(elem);
                        case AbstractDocument.SectionElementName:
                            return new BoxView(elem, javax.swing.text.View.Y_AXIS);
                        case StyleConstants.ComponentElementName:
                            return new ComponentView(elem);
                        case StyleConstants.IconElementName:
                            return new IconView(elem);
                    }
                }
                // default to text display
                return new LabelView(elem);
            }
        }

        private static class WrapLabelView extends LabelView {
            public WrapLabelView(Element elem) {
                super(elem);
            }
            @Override
            public float getMinimumSpan(int axis) {
                switch (axis) {
                    case javax.swing.text.View.X_AXIS:
                        return 0;
                    case javax.swing.text.View.Y_AXIS:
                        return super.getMinimumSpan(axis);
                    default:
                        throw new IllegalArgumentException("Invalid axis: " + axis);
                }
            }
        }
    }
}

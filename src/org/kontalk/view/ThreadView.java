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
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.text.WebTextArea;
import com.alee.laf.viewport.WebViewport;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.Vector;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import org.kontalk.system.Downloader;
import org.kontalk.crypto.Coder;
import org.kontalk.model.InMessage;
import org.kontalk.model.KonMessage;
import org.kontalk.model.KonThread;
import org.kontalk.model.MessageContent.Attachment;
import org.kontalk.system.KonConf;
import org.kontalk.util.Tr;

/**
 * Pane that shows the currently selected thread.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
final class ThreadView extends WebScrollPane {
    private final static Logger LOGGER = Logger.getLogger(ThreadView.class.getName());

    private final static Icon PENDING_ICON = View.getIcon("ic_msg_pending.png");;
    private final static Icon SENT_ICON = View.getIcon("ic_msg_sent.png");
    private final static Icon DELIVERED_ICON = View.getIcon("ic_msg_delivered.png");
    private final static Icon ERROR_ICON = View.getIcon("ic_msg_error.png");
    private final static Icon CRYPT_ICON = View.getIcon("ic_msg_crypt.png");
    private final static Icon UNENCRYPT_ICON = View.getIcon("ic_msg_unencrypt.png");

    private final static SimpleDateFormat SHORT_DATE_FORMAT = new SimpleDateFormat("EEE, HH:mm");
    private final static SimpleDateFormat LONG_DATE_FORMAT = new SimpleDateFormat("EEE, d MMM yyyy, HH:mm:ss");

    private final View mModel;

    private final Map<Integer, MessageViewList> mThreadCache = new HashMap<>();
    private KonThread mCurrentThread = null;
    // background image from ressource or user selected
    private Image mDefaultBG;
    // cached background with size of viewport
    private BufferedImage mCachedBG = null;

    ThreadView(View model) {
        super(null);

        mModel = model;

        this.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        this.getVerticalScrollBar().setUnitIncrement(25);

        this.setViewport(new ThreadViewPort());

        this.loadDefaultBG();
    }

    Optional<KonThread> getCurrentThread() {
        return Optional.ofNullable(mCurrentThread);
    }

    void showThread(KonThread thread) {
        boolean isNew = false;
        if (!mThreadCache.containsKey(thread.getID())) {
            mThreadCache.put(thread.getID(), new MessageViewList(thread));
            isNew = true;
        }
        MessageViewList table = mThreadCache.get(thread.getID());
        this.setViewportView(table);

        if (table.getRowCount() > 0 && isNew) {
            // trigger scrolling down
            table.mScrollDownOnResize = true;
        }

        mCurrentThread = thread;
        thread.setRead();
    }

    void setColor(Color color) {
        this.getViewport().setBackground(color);
    }

    void loadDefaultBG() {
        String imagePath = KonConf.getInstance().getString(KonConf.VIEW_THREAD_BG);
        mDefaultBG = !imagePath.isEmpty() ?
                Toolkit.getDefaultToolkit().createImage(imagePath) :
                View.getImage("thread_bg.png");
        mCachedBG = null;
        this.getViewport().repaint();
    }

    private void removeThread(KonThread thread) {
        mThreadCache.remove(thread.getID());
        if(mCurrentThread == thread) {
            mCurrentThread = null;
            this.setViewportView(null);
        }
    }

    /**
     * View all messages of one thread in a left/right MIM style list.
     */
    private class MessageViewList extends TableView implements Observer {

        private final KonThread mThread;
        private boolean mScrollDownOnResize = false;

        MessageViewList(KonThread thread) {
            super();

            mThread = thread;

            //this.setEditable(false);
            //this.setAutoscrolls(true);
            this.setOpaque(false);

            this.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    // this table was resized, the size of each item might have
                    // changed and each row height must be adjusted
                    // TODO efficient?
                    MessageViewList table = MessageViewList.this;

                    for (int row = 0; row < table.getRowCount(); row++) {
                        table.setHeight(row);
                    }

                    // another issue: scrolling to a new component in the table
                    // is only possible after the component was rendered (which
                    // is now)
                    if (mScrollDownOnResize) {
                        table.scrollToRow(table.getRowCount() - 1);
                        mScrollDownOnResize = false;
                    }
                }
            });

            // disable selection
            this.setSelectionModel(new UnselectableListModel());

            // insert messages
            for (KonMessage message: mThread.getMessages()) {
                this.addMessage(message);
            }

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
                        MessageViewList.this.showPopupMenu(e);
                    }
                }
            });

            this.updateOnEDT();

            mThread.addObserver(this);
        }

        @Override
        public void update(Observable o, Object arg) {
            if (SwingUtilities.isEventDispatchThread()) {
                this.updateOnEDT();
                return;
            }
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    MessageViewList.this.updateOnEDT();
                }
            });
        }

        private void updateOnEDT() {
            if (mThread.isDeleted()) {
                ThreadView.this.removeThread(mThread);
            }

            // check for new messages to add
            if (mTableModel.getRowCount() < mThread.getMessages().size()) {
                Set<KonMessage> oldMessages = new HashSet<>();
                for (Object vec : mTableModel.getDataVector()) {
                    Object m = ((Vector) vec).elementAt(0);
                    oldMessages.add(((MessageView) m).mMessage);
                }

                for (KonMessage message: mThread.getMessages()) {
                    if (!oldMessages.contains(message)) {
                        // always inserted at the end, timestamp of message is
                        // ignored. Let's call it a feature.
                        this.addMessage(message);
                        // trigger scrolling
                        mScrollDownOnResize = true;
                    }
                }
            }

            if (ThreadView.this.mCurrentThread == mThread) {
                    mThread.setRead();
            }
        }

        private void addMessage(KonMessage message) {
            MessageView newMessageView = new MessageView(message);
            Object[] data = {newMessageView};
            mTableModel.addRow(data);

            this.setHeight(this.getRowCount() -1);
        }

        /**
         * Row height must be adjusted manually to component height.
         * source: https://stackoverflow.com/a/1784601
         * @param row the row that gets set
         */
        private void setHeight(int row) {
            Component comp = this.prepareRenderer(this.getCellRenderer(row, 0), row, 0);
            int height = Math.max(this.getRowHeight(), comp.getPreferredSize().height);
            this.setRowHeight(row, height);
        }

        private void showPopupMenu(MouseEvent e) {
            int row = this.rowAtPoint(e.getPoint());
            if (row < 0)
                return;

            MessageView messageView = (MessageView) mTableModel.getValueAt(row, 0);
            WebPopupMenu popupMenu = messageView.getPopupMenu();
            popupMenu.show(this, e.getX(), e.getY());
        }

        /**
         * View for one message.
         * The content is added to a panel inside this panel.
         */
        private class MessageView extends TableItem implements Observer {

            private final KonMessage mMessage;
            private final WebPanel mContentPanel;
            private final WebTextArea mTextArea;
            private final WebLabel mStatusIconLabel;
            private final int mPreferredTextAreaWidth;

            MessageView(KonMessage message) {
                mMessage = message;

                this.setOpaque(false);
                this.setMargin(2);
                //this.setBorder(new EmptyBorder(10, 10, 10, 10));

                WebPanel messagePanel = new WebPanel(true);
                messagePanel.setWebColoredBackground(false);
                messagePanel.setMargin(5);
                if (mMessage.getDir().equals(KonMessage.Direction.IN))
                    messagePanel.setBackground(Color.WHITE);
                else
                    messagePanel.setBackground(View.LIGHT_BLUE);

                // from label
                if (mMessage.getDir().equals(KonMessage.Direction.IN)) {
                    String from = getFromString(mMessage);
                    WebLabel fromLabel = new WebLabel(" "+from);
                    fromLabel.setFontSize(12);
                    fromLabel.setForeground(Color.BLUE);
                    fromLabel.setItalicFont();
                    messagePanel.add(fromLabel, BorderLayout.NORTH);
                }

                mContentPanel = new WebPanel();
                mContentPanel.setOpaque(false);
                // text area
                mTextArea = new WebTextArea();
                mTextArea.setOpaque(false);
                mTextArea.setFontSize(13);
                mContentPanel.add(mTextArea, BorderLayout.CENTER);
                messagePanel.add(mContentPanel, BorderLayout.CENTER);

                WebPanel statusPanel = new WebPanel();
                statusPanel.setOpaque(false);
                statusPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
                // icons
                mStatusIconLabel = new WebLabel();

                this.update();

                // save the width that is requied to show the text in one line
                // before line wrap and only once!
                mPreferredTextAreaWidth = mTextArea.getPreferredSize().width;

                mTextArea.setLineWrap(true);
                mTextArea.setWrapStyleWord(true);

                statusPanel.add(mStatusIconLabel);
                WebLabel encryptIconLabel = new WebLabel();
                if (message.getCoderStatus().isSecure()) {
                    encryptIconLabel.setIcon(CRYPT_ICON);
                } else {
                    encryptIconLabel.setIcon(UNENCRYPT_ICON);
                }
                statusPanel.add(encryptIconLabel);
                // date label
                WebLabel dateLabel = new WebLabel(SHORT_DATE_FORMAT.format(mMessage.getDate()));
                dateLabel.setForeground(Color.GRAY);
                dateLabel.setFontSize(11);
                statusPanel.add(dateLabel);
                messagePanel.add(statusPanel, BorderLayout.SOUTH);

                if (mMessage.getDir().equals(KonMessage.Direction.IN)) {
                    this.add(messagePanel, BorderLayout.WEST);
                } else {
                    this.add(messagePanel, BorderLayout.EAST);
                }

                mMessage.addObserver(this);
            }

            @Override
            protected void resize(int listWidth) {
                // note: on the very first call the list width is zero
                int maxWidth = (int)(listWidth * 0.8);
                int width = Math.min(mPreferredTextAreaWidth, maxWidth);
                // height is reset later
                mTextArea.setSize(width, mTextArea.getPreferredSize().height);
            }

            /**
             * Update what can change in a message: text, icon and attachment.
             */
            private void update() {
                // text in text area
                boolean encrypted = mMessage.getCoderStatus().isEncrypted();
                String text = encrypted ? Tr.tr("[encrypted]") : mMessage.getContent().getText();
                mTextArea.setFontStyle(false, encrypted);
                mTextArea.setText(text);
                // hide area if there is no text
                mTextArea.setVisible(!text.isEmpty());

                // status icon
                if (mMessage.getDir() == KonMessage.Direction.OUT) {
                    switch (mMessage.getReceiptStatus()) {
                        case PENDING :
                            mStatusIconLabel.setIcon(PENDING_ICON);
                            break;
                        case SENT :
                            mStatusIconLabel.setIcon(SENT_ICON);
                            break;
                        case RECEIVED:
                            mStatusIconLabel.setIcon(DELIVERED_ICON);
                            break;
                        case ERROR:
                            mStatusIconLabel.setIcon(ERROR_ICON);
                            break;
                        default:
                            LOGGER.warning("unknown message receipt status!?");
                    }
                }

                // attachment
                // TODO loading many images can is very slow
                // remove possible old component (replacing does not work right)
                BorderLayout layout = (BorderLayout) mContentPanel.getLayout();
                Component oldComp = layout.getLayoutComponent(BorderLayout.SOUTH);
                if (oldComp != null)
                    mContentPanel.remove(oldComp);

                Optional<Attachment> optAttachment = mMessage.getContent().getAttachment();
                if (optAttachment.isPresent()) {
                    String base = Downloader.getInstance().getAttachmentDir();
                    String fName = optAttachment.get().getFileName();
                    Path path = Paths.get(base, fName);

                    // rely on mime type in message
                    if (!optAttachment.get().getFileName().isEmpty() &&
                            optAttachment.get().getMimeType().startsWith("image")) {
                        // file should be present and should be an image, show it
                        BufferedImage image = readImage(path.toString());
                        Image scaledImage = scale(image, 300, 200, false);
                        WebLinkLabel imageView = new WebLinkLabel();
                        imageView.setLink("", linkRunnable(path));
                        imageView.setIcon(new ImageIcon(scaledImage));
                        mContentPanel.add(imageView, BorderLayout.SOUTH);
                    } else {
                        // show a link to the file
                        WebLabel attLabel;
                        if (optAttachment.get().getFileName().isEmpty()) {
                            attLabel = new WebLabel(Tr.tr("?"));
                        } else {
                            WebLinkLabel linkLabel = new WebLinkLabel();
                            linkLabel.setLink(fName, linkRunnable(path));
                            attLabel = linkLabel;
                        }
                        WebLabel labelLabel = new WebLabel(Tr.tr("Attachment:")+" ");
                        labelLabel.setItalicFont();
                        GroupPanel attachmentPanel = new GroupPanel(4, true, labelLabel, attLabel);
                        mContentPanel.add(attachmentPanel, BorderLayout.SOUTH);
                    }
                }
            }

            private WebPopupMenu getPopupMenu() {
                WebPopupMenu popupMenu = new WebPopupMenu();
                if (mMessage.getCoderStatus().isEncrypted()) {
                    WebMenuItem decryptMenuItem = new WebMenuItem(Tr.tr("Decrypt"));
                    decryptMenuItem.setToolTipText(Tr.tr("Retry decrypting message"));
                    decryptMenuItem.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent event) {
                            KonMessage m = MessageView.this.mMessage;
                            if (!(m instanceof InMessage)) {
                                LOGGER.warning("decrypted message not incoming message");
                                return;
                            }
                            ThreadView.this.mModel.callDecrypt((InMessage) m);
                        }
                    });
                    popupMenu.add(decryptMenuItem);
                }
                WebMenuItem copyMenuItem = new WebMenuItem(Tr.tr("Copy"));
                copyMenuItem.setToolTipText(Tr.tr("Copy message content"));
                copyMenuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        String messageText = MessageView.this.toPrettyString();
                        Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
                        clip.setContents(new StringSelection(messageText), null);
                    }
                });
                popupMenu.add(copyMenuItem);
                return popupMenu;
            }

            private String toPrettyString() {
                String date = LONG_DATE_FORMAT.format(mMessage.getDate());
                String from = getFromString(mMessage);
                return date + " - " + from + " : " + mMessage.getContent().getText();
            }

            @Override
            public void update(Observable o, Object arg) {
                if (SwingUtilities.isEventDispatchThread()) {
                    this.updateOnEDT();
                    return;
                }
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        MessageView.this.updateOnEDT();
                    }
                });
            }

            private void updateOnEDT() {
                this.update();

                // find row of item...
                MessageViewList table = MessageViewList.this;
                int row;
                for (row = table.getRowCount()-1; row >= 0; row--)
                    if (this == table.getModel().getValueAt(row, 0))
                        break;
                // ...set height...
                table.setHeight(row);
                // ...and scroll down
                if (row == table.getRowCount()-1)
                    table.mScrollDownOnResize = true;
            }

            @Override
            protected String getTooltipText() {
                String encryption = Tr.tr("unknown");
                switch (mMessage.getCoderStatus().getEncryption()) {
                    case NOT: encryption = Tr.tr("not encrypted"); break;
                    case ENCRYPTED: encryption = Tr.tr("encrypted"); break;
                    case DECRYPTED: encryption = Tr.tr("decrypted"); break;
                }
                String verification = Tr.tr("unknown");
                switch (mMessage.getCoderStatus().getSigning()) {
                    case NOT: verification = Tr.tr("not signed"); break;
                    case SIGNED: verification = Tr.tr("signed"); break;
                    case VERIFIED: verification = Tr.tr("verified"); break;
                }
                String problems = "";
                if (mMessage.getCoderStatus().getErrors().isEmpty()) {
                    problems = Tr.tr("none");
                } else {
                  for (Coder.Error error: mMessage.getCoderStatus().getErrors()) {
                      problems += error.toString() + " <br> ";
                  }
                }

                String html = "<html><body>" +
                        //"<h3>Header</h3>" +
                        "<br>" +
                        Tr.tr("Security")+": " + encryption + " / " + verification + "<br>" +
                        Tr.tr("Problems")+": " + problems;

                return html;
            }

            @Override
            protected boolean contains(String search) {
                return mTextArea.getText().toLowerCase().contains(search) ||
                        mMessage.getUser().getName().toLowerCase().contains(search) ||
                        mMessage.getJID().toLowerCase().contains(search);
            }
        }
    }

    /** Custom viewport for efficient reloading of background image. */
    private class ThreadViewPort extends WebViewport {
        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (mCachedBG == null ||
                    mCachedBG.getWidth() != this.getWidth() ||
                    mCachedBG.getHeight() != this.getHeight()) {
                Image scaledImage = scale(mDefaultBG, this.getWidth(), this.getHeight(), true);
                // if scaling is performed async, we continue updating the
                // background from imageUpdate()
                if (scaledImage.getWidth(this) != -1)
                    this.updateCachedBG(scaledImage);
            }
            // if there is something to draw, draw it
            if (mCachedBG != null)
                g.drawImage(mCachedBG, 0, 0, this.getWidth(), this.getHeight(), null);
        }
        @Override
        public boolean imageUpdate(Image img, int infoflags, int x, int y, int w, int h) {
            if ((infoflags & ImageObserver.ALLBITS) == 0) {
                return true;
            }
            // completely loaded
            this.updateCachedBG(img);
            return false;
        }
        private void updateCachedBG(Image scaledImage) {
            mCachedBG = new BufferedImage(this.getWidth(),
                    this.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D cachedG = mCachedBG.createGraphics();
            // gradient background of background
//                GradientPaint p2 = new GradientPaint(
//                        0, 0, new Color(0, 0, 0, 0),
//                        0, this.getHeight(), Color.BLUE);
//                cachedG.setPaint(p2);
//                cachedG.fillRect(0, 0, this.getWidth(), getHeight());
            // tiling
            int iw = scaledImage.getWidth(this);
            int ih = scaledImage.getHeight(this);
            if (iw > 0 && ih > 0) {
                for (int x = 0; x < this.getWidth(); x += iw) {
                    for (int y = 0; y < this.getHeight(); y += ih) {
                        cachedG.drawImage(scaledImage, x, y, iw, ih, this);
                    }
                }
            }
            this.repaint();
        }
    }

    private static BufferedImage readImage(String path) {
        try {
             BufferedImage image = ImageIO.read(new File(path));
             if (image != null)
                 return image;
        } catch(IOException ex) {
            LOGGER.log(Level.WARNING, "can't read image", ex);
        }
        return new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
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

    private static Runnable linkRunnable(final Path path) {
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

    /** Scale image down to maximum or minimum of width / height, preserving
     * ratio.
     * @param max specifies if image is scaled to maximum or minimum of width/height
     */
    private static Image scale(Image image, int width, int height, boolean max) {
        int iw = image.getWidth(null);
        int ih = image.getHeight(null);
        if (max && (iw <= width || ih <= height) ||
                !max && (iw <= width && ih <= height))
            return image;
        double sw = width / (iw * 1.0);
        double sh = height / (ih * 1.0);
        double scale = max ? Math.max(sw, sh) : Math.min(sw, sh);
        return image.getScaledInstance(
                (int) (iw * scale),
                (int) (ih * scale),
                Image.SCALE_FAST);
    }
}

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
import com.alee.managers.tooltip.TooltipManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractCellEditor;
import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
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
import org.kontalk.model.MessageContent;
import org.kontalk.model.User;
import org.kontalk.system.Downloader;
import org.kontalk.util.Tr;
import org.kontalk.view.ThreadView.Background;


/**
 * View all messages of one thread in a left/right MIM style list.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class MessageList extends Table<MessageList.MessageItem, KonMessage> {
    private static final Logger LOGGER = Logger.getLogger(MessageList.class.getName());

    private static final Icon PENDING_ICON = Utils.getIcon("ic_msg_pending.png");;
    private static final Icon SENT_ICON = Utils.getIcon("ic_msg_sent.png");
    private static final Icon DELIVERED_ICON = Utils.getIcon("ic_msg_delivered.png");
    private static final Icon ERROR_ICON = Utils.getIcon("ic_msg_error.png");
    private static final Icon WARNING_ICON = Utils.getIcon("ic_msg_warning.png");
    private static final Icon CRYPT_ICON = Utils.getIcon("ic_msg_crypt.png");
    private static final Icon UNENCRYPT_ICON = Utils.getIcon("ic_msg_unencrypt.png");

    private final ThreadView mThreadView;
    private final KonThread mThread;
    private Optional<Background> mBackground = Optional.empty();

    MessageList(View view, ThreadView threadView, KonThread thread) {
        super(view);
        mThreadView = threadView;
        mThread = thread;

        // use custom editor (for mouse events)
        this.setDefaultEditor(Table.TableItem.class, new TableEditor());

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
                arg instanceof Boolean ||
                arg instanceof KonThread.KonChatState) {
            // users, subject, read status or chat state changed, nothing
            // to do here
            return;
        }

        if (arg instanceof KonThread.ViewSettings) {
            this.setBackground((KonThread.ViewSettings) arg);
            if (mThreadView.getCurrentThread().orElse(null) == mThread) {
                //mThreadView.mScrollPane.getViewport().repaint();
                mThreadView.repaint();
            }
            return;
        }

        if (arg instanceof KonMessage) {
            this.insertMessage((KonMessage) arg);
        } else {
            // check for new messages to add
            if (this.getModel().getRowCount() < mThread.getMessages().size())
                this.insertMessages();
        }

        if (mThreadView.getCurrentThread().orElse(null) == mThread) {
            mThread.setRead();
        }
    }

    private void insertMessages() {
        Set<MessageItem> newItems = new HashSet<>();
        for (KonMessage message: mThread.getMessages()) {
            if (!this.containsValue(message)) {
                newItems.add(new MessageItem(message));
                // trigger scrolling
                mThreadView.setScrolling();
            }
        }
        this.sync(mThread.getMessages(), newItems);
    }

    private void insertMessage(KonMessage message) {
        Set<MessageItem> newItems = new HashSet<>();
        newItems.add(new MessageItem(message));
        this.sync(mThread.getMessages(), newItems);
        // trigger scrolling
        mThreadView.setScrolling();
    }

    private void showPopupMenu(MouseEvent e) {
        int row = this.rowAtPoint(e.getPoint());
        if (row < 0)
            return;

        MessageItem messageView = this.getDisplayedItemAt(row);
        WebPopupMenu popupMenu = messageView.getPopupMenu();
        popupMenu.show(this, e.getX(), e.getY());
    }

    private void setBackground(KonThread.ViewSettings s) {
        // simply overwrite
        mBackground = mThreadView.createBG(s);
    }

    /**
     * View for one message.
     * The content is added to a panel inside this panel. For performance
     * reasons the content is created when the item is rendered in the table
     */
    final class MessageItem extends Table<MessageItem, KonMessage>.TableItem {

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
            mContentPanel.setMargin(View.MARGIN_SMALL);
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
            WebLabel dateLabel = new WebLabel(Utils.SHORT_DATE_FORMAT.format(mValue.getDate()));
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
            // TODO height problem for new messages again
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

        private void updateStatus() {
            boolean isOut = mValue.getDir() == KonMessage.Direction.OUT;
            // status icon
            if (isOut) {
                switch (mValue.getReceiptStatus()) {
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
            } else { // IN message
                if (!mValue.getCoderStatus().getErrors().isEmpty()) {
                    mStatusIconLabel.setIcon(WARNING_ICON);
                }
            }

            // tooltip
            String html = "<html><body>" + /*"<h3>Header</h3>"+*/ "<br>";

            if (isOut) {
                Date createDate = mValue.getDate();
                String create = Utils.MID_DATE_FORMAT.format(createDate);
                Optional<Date> serverDate = mValue.getServerDate();
                String status = serverDate.isPresent() ?
                        Utils.MID_DATE_FORMAT.format(serverDate.get()) :
                        null;
                if (!create.equals(status))
                    html += Tr.tr("Created:")+ " " + create + "<br>";
                if (status != null) {
                    String secStat = null;
                    switch (mValue.getReceiptStatus()) {
                        case SENT :
                            secStat = Tr.tr("Sent:");
                            break;
                        case RECEIVED:
                            secStat = Tr.tr("Delivered:");
                            break;
                        case ERROR:
                            secStat = Tr.tr("Error report:");
                            break;
                        default:
                            LOGGER.warning("unexpected msg status: "+mValue.getReceiptStatus());
                    }
                    if (secStat != null)
                        html += secStat + " " + status + "<br>";
                }
            } else { // IN message
                Date receivedDate = mValue.getDate();
                String rec = Utils.MID_DATE_FORMAT.format(receivedDate);
                Optional<Date> sentDate = mValue.getServerDate();
                if (sentDate.isPresent()) {
                    String sent = Utils.MID_DATE_FORMAT.format(sentDate.get());
                    if (!sent.equals(rec))
                        html += Tr.tr("Sent:")+ " " + sent + "<br>";
                }
                html += Tr.tr("Received:")+ " " + rec + "<br>";
            }

            Coder.Encryption enc = mValue.getCoderStatus().getEncryption();
            Coder.Signing sign = mValue.getCoderStatus().getSigning();
            String sec = null;
            // usual states
            if (enc == Coder.Encryption.NOT && sign == Coder.Signing.NOT)
                sec = Tr.tr("not encrypted");
            else if (enc == Coder.Encryption.DECRYPTED &&
                    ((isOut && sign == Coder.Signing.SIGNED) ||
                    (!isOut && sign == Coder.Signing.VERIFIED))) {
                        sec = Tr.tr("safe");
            }
            if (sec == null) {
                // unusual states
                String encryption = Tr.tr("unknown");
                switch (enc) {
                    case NOT: encryption = Tr.tr("not encrypted"); break;
                    case ENCRYPTED: encryption = Tr.tr("encrypted"); break;
                    case DECRYPTED: encryption = Tr.tr("decrypted"); break;
                }
                String verification = Tr.tr("unknown");
                switch (sign) {
                    case NOT: verification = Tr.tr("not signed"); break;
                    case SIGNED: verification = Tr.tr("signed"); break;
                    case VERIFIED: verification = Tr.tr("verified"); break;
                }
                sec = encryption + " / " + verification;
            }
            html += Tr.tr("Security")+": " + sec + "<br>";

            String problems = "";
            for (Coder.Error error: mValue.getCoderStatus().getErrors()) {
                problems += error.toString() + " <br> ";
            }
            if (!problems.isEmpty())
                html += Tr.tr("Problems")+": " + problems;

            // TODO temporary catching for tracing bug
            try {
                TooltipManager.setTooltip(mStatusPanel, html);
            } catch (NullPointerException ex) {
                LOGGER.log(Level.WARNING, "cant set tooltip", ex);
                LOGGER.warning("statusPanel="+mStatusPanel+",html="+html);
                LOGGER.warning("edt: "+SwingUtilities.isEventDispatchThread());
            }
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

            MessageContent.Attachment att = optAttachment.get();
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
                        mView.getControl().decryptAndDownload((InMessage) m);
                    }
                });
                popupMenu.add(decryptMenuItem);
            }
            WebMenuItem cItem = Utils.createCopyMenuItem(
                    this.toPrettyString(),
                    Tr.tr("Copy message content"));
            popupMenu.add(cItem);
            return popupMenu;
        }

        private String toPrettyString() {
            String date = Utils.LONG_DATE_FORMAT.format(mValue.getDate());
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

        @Override
        public int compareTo(TableItem o) {
            int idComp = Integer.compare(mValue.getID(), o.mValue.getID());
            int dateComp = mValue.getDate().compareTo(mValue.getDate());
            return (idComp == 0 || dateComp == 0) ? idComp : dateComp;
        }
    }

    // needed for correct mouse behaviour for components in items
    // (and breaks selection behaviour somehow)
    private class TableEditor extends AbstractCellEditor implements TableCellEditor {
        private Table<?, ?>.TableItem mValue;
        @Override
        public Component getTableCellEditorComponent(JTable table,
                Object value,
                boolean isSelected,
                int row,
                int column) {
            mValue = (Table.TableItem) value;
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

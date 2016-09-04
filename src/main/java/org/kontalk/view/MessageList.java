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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
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
import org.kontalk.misc.JID;
import org.kontalk.model.message.InMessage;
import org.kontalk.model.message.KonMessage;
import org.kontalk.model.chat.Chat;
import org.kontalk.model.message.MessageContent.Attachment;
import org.kontalk.model.message.MessageContent.GroupCommand;
import org.kontalk.model.message.OutMessage;
import org.kontalk.model.message.Transmission;
import org.kontalk.util.Tr;
import org.kontalk.view.ChatView.Background;
import org.kontalk.view.ComponentUtils.AttachmentPanel;


/**
 * View all messages of one chat in a left/right MIM style list.
 *
 * TODO performance when loading
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class MessageList extends ListView<MessageList.MessageItem, KonMessage> {
    private static final Logger LOGGER = Logger.getLogger(MessageList.class.getName());

    private static final Icon PENDING_ICON = Utils.getIcon("ic_msg_pending.png");;
    private static final Icon SENT_ICON = Utils.getIcon("ic_msg_sent.png");
    private static final Icon DELIVERED_ICON = Utils.getIcon("ic_msg_delivered.png");
    private static final Icon ERROR_ICON = Utils.getIcon("ic_msg_error.png");
    private static final Icon WARNING_ICON = Utils.getIcon("ic_msg_warning.png");
    private static final Icon CRYPT_ICON = Utils.getIcon("ic_msg_crypt.png");
    private static final Icon UNENCRYPT_ICON = Utils.getIcon("ic_msg_unencrypt.png");
    private static final Icon CRYPT_WARNING_ICON = Utils.getIcon("ic_msg_crypt_warning.png");

    private final ChatView mChatView;
    private final Chat mChat;
    private Optional<Background> mBackground = Optional.empty();

    MessageList(View view, ChatView chatView, Chat chat) {
        super(view, false);
        mChatView = chatView;
        mChat = chat;

        // disable selection
        this.setSelectionModel(new UnselectableListModel());

        // use custom editor (for mouse events)
        this.setDefaultEditor(ListView.TableItem.class, new TableEditor());

        //this.setEditable(false);
        //this.setAutoscrolls(true);
        this.setOpaque(false);

        // hide grid
        this.setShowGrid(false);

        this.setVisible(false);
        this.updateOnEDT(null);
        this.setVisible(true);
    }

    Chat getChat() {
        return mChat;
    }

    Optional<Background> getBG() {
        return mBackground;
    }

    @Override
    protected void updateOnEDT(Object arg) {
        if (arg == null || arg == Chat.ViewChange.VIEW_SETTINGS) {
            this.setBackground(mChat.getViewSettings());
            if (mChatView.getCurrentChat().orElse(null) == mChat) {
                //mChatView.mScrollPane.getViewport().repaint();
                mChatView.repaint();
            }
        }

        // check for new messages to add
        if ((arg == null || arg == Chat.ViewChange.NEW_MESSAGE) &&
                this.getModel().getRowCount() < mChat.getMessages().size()) {
            this.insertMessages();
        }

        if ((arg == null || arg == Chat.ViewChange.READ) &&
                !mChat.isRead() && mChatView.getCurrentChat().orElse(null) == mChat) {
            mChat.setRead();
        }
    }

    private void insertMessages() {
        boolean newAdded = this.sync(mChat.getMessages().getAll());
        if (newAdded)
            // trigger scrolling
            mChatView.setScrolling();
    }

    @Override
    protected MessageItem newItem(KonMessage value) {
        return new MessageItem(value);
    }

    private void setBackground(Chat.ViewSettings s) {
        // simply overwrite
        mBackground = mChatView.createBG(s);
    }

    @Override
    protected WebPopupMenu rightClickMenu(MessageItem item) {
        WebPopupMenu menu = new WebPopupMenu();

        final KonMessage m = item.mValue;
        if (m instanceof InMessage) {
            InMessage im = (InMessage) m;
            if (m.isEncrypted()) {
                WebMenuItem decryptMenuItem = new WebMenuItem(Tr.tr("Decrypt"));
                decryptMenuItem.setToolTipText(Tr.tr("Retry decrypting message"));
                decryptMenuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        mView.getControl().decryptAgain(im);
                    }
                });
                menu.add(decryptMenuItem);
            }
            Attachment att = m.getContent().getAttachment().orElse(null);
            if (att != null &&
                    att.getFilePath().toString().isEmpty()) {
                WebMenuItem attMenuItem = new WebMenuItem(Tr.tr("Load"));
                attMenuItem.setToolTipText(Tr.tr("Retry downloading attachment"));
                attMenuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        mView.getControl().downloadAgain(im);
                    }
                });
                menu.add(attMenuItem);
            }
        } else if (m instanceof OutMessage) {
            if (m.getStatus() == KonMessage.Status.ERROR) {
                WebMenuItem sendMenuItem = new WebMenuItem(Tr.tr("Retry"));
                sendMenuItem.setToolTipText(Tr.tr("Retry sending message"));
                sendMenuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        mView.getControl().sendAgain((OutMessage) m);
                    }
                });
                menu.add(sendMenuItem);
            }
        }

        WebMenuItem cItem = Utils.createCopyMenuItem(
                toCopyString(m),
                Tr.tr("Copy message content"));
        menu.add(cItem);

        return menu;
    }

    /**
     * View for one message.
     * The content is added to a panel inside this panel. For performance
     * reasons the content is created when the item is rendered in the table
     */
    final class MessageItem extends ListView<MessageItem, KonMessage>.TableItem {

        private WebPanel mPanel;
        private WebLabel mFromLabel = null;
        private WebPanel mContentPanel;
        private WebTextPane mTextPane;
        private WebPanel mStatusPanel;
        private WebLabel mStatusIconLabel;
        private WebLabel mEncryptIconLabel;
        private AttachmentPanel mAttPanel = null;
        private int mPreferredTextWidth;
        private boolean mCreated = false;

        MessageItem(KonMessage message) {
            super(message);

            this.setOpaque(false);
            //this.setBorder(new EmptyBorder(10, 10, 10, 10));
        }

        private void createContent() {
            if (mCreated)
                return;
            mCreated = true;

            mPanel = new WebPanel(true);
            mPanel.setWebColoredBackground(false);
            mPanel.setMargin(2);
            if (mValue.isInMessage())
                mPanel.setBackground(Color.WHITE);
            else
                mPanel.setBackground(View.LIGHT_BLUE);

            // from label
            if (mValue.isInMessage() && mValue.getChat().isGroupChat()) {
                mFromLabel = new WebLabel();
                mFromLabel.setFontSize(View.FONT_SIZE_SMALL);
                mFromLabel.setForeground(Color.BLUE);
                mFromLabel.setItalicFont();
                mPanel.add(mFromLabel, BorderLayout.NORTH);
            }

            mContentPanel = new WebPanel();
            mContentPanel.setOpaque(false);
            mContentPanel.setMargin(View.MARGIN_SMALL);
            // text area
            mTextPane = new WebTextPane();
            mTextPane.setEditable(false);
            mTextPane.setOpaque(false);
            //mTextPane.setFontSize(View.FONT_SIZE_SMALL);
            // sets default font
            mTextPane.putClientProperty(WebEditorPane.HONOR_DISPLAY_PROPERTIES, true);
            //for detecting clicks
            mTextPane.addMouseListener(LinkUtils.CLICK_LISTENER);
            //for detecting motion
            mTextPane.addMouseMotionListener(LinkUtils.MOTION_LISTENER);
            // fix word wrap for long words
            mTextPane.setEditorKit(FIX_WRAP_KIT);
            mContentPanel.add(mTextPane, BorderLayout.CENTER);
            mPanel.add(mContentPanel, BorderLayout.CENTER);

            mStatusPanel = new WebPanel();
            mStatusPanel.setOpaque(false);
            mStatusPanel.setLayout(new FlowLayout());
            // icons
            mStatusIconLabel = new WebLabel();
            mEncryptIconLabel = new WebLabel();

            this.updateOnEDT(null);

            // save the width that is requied to show the text in one line;
            // before line wrap and only once!
            mPreferredTextWidth = mTextPane.getPreferredSize().width;

            mStatusPanel.add(mStatusIconLabel);
            mStatusPanel.add(mEncryptIconLabel);

            // date label
            Date statusDate = mValue.isInMessage() ?
                    mValue.getServerDate().orElse(mValue.getDate()) :
                    mValue.getDate();
            WebLabel dateLabel = new WebLabel(
                    Utils.SHORT_DATE_FORMAT.format(statusDate));
            dateLabel.setForeground(Color.GRAY);
            dateLabel.setFontSize(View.FONT_SIZE_TINY);
            mStatusPanel.add(dateLabel);

            WebPanel southPanel = new WebPanel();
            southPanel.setOpaque(false);
            southPanel.add(mStatusPanel, BorderLayout.EAST);
            mPanel.add(southPanel, BorderLayout.SOUTH);

            if (mValue.isInMessage()) {
                this.add(mPanel, BorderLayout.WEST);
            } else {
                this.add(mPanel, BorderLayout.EAST);
            }

            for (Transmission t: mValue.getTransmissions()) {
                t.getContact().addObserver(this);
            }
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

            // TODO "From" title not updated if contact name changes
            if (arg == null || arg == KonMessage.ViewChange.CONTENT)
                this.updateTitle();

            if (arg == null || arg == KonMessage.ViewChange.CONTENT)
                this.updateText();

            if (arg == null || arg == KonMessage.ViewChange.STATUS)
                this.updateStatus();

            if (arg == null || arg == KonMessage.ViewChange.ATTACHMENT)
                this.updateAttachment();

            // TODO height problem for new messages again
        }

        private void updateTitle() {
            if (mFromLabel == null)
                return;

            mFromLabel.setText(!mValue.getContent().getGroupCommand().isPresent() &&
                    mValue instanceof InMessage ?
                    " "+getFromString((InMessage) mValue) :
                    "");
        }

        // text in text area, before/after encryption
        private void updateText() {
            boolean encrypted = mValue.isEncrypted();
            GroupCommand com = mValue.getContent().getGroupCommand().orElse(null);
            String text = "";
            if (com != null) {
                InMessage inMessage = mValue instanceof InMessage ?
                        (InMessage) mValue : null;
                String somebody = inMessage != null ?
                        getFromString(inMessage)+" " : Tr.tr("You")+" ";
                switch(com.getOperation()) {
                    case CREATE:
                        text = somebody + Tr.tr("created this group");
                        break;
                    case LEAVE:
                        text = somebody + Tr.tr("left this group");
                        break;
                    case SET:
                        String subject = com.getSubject();
                        if (!subject.isEmpty()) {
                            text = somebody + Tr.tr("set the subject to")
                                    + " \"" + subject + "\"";
                        }
                        List<JID> added = com.getAdded();
                        if (!added.isEmpty()) {
                            text = somebody + Tr.tr("added") + " "
                                    + mView.names(added);
                        }
                        List<JID> removed = com.getRemoved();
                        if (!removed.isEmpty()) {
                            text = somebody + Tr.tr("removed") + " "
                                    + mView.names(removed);
                        }
                        if (text.isEmpty()) {
                            text = "did something wrong";
                        }
                        break;
                }
                mTextPane.setText(text);
                mTextPane.setFontStyle(false, true);
                mPanel.setBackground(View.LIGHT_GREEN);
            } else {
                text = encrypted ?
                        Tr.tr("[encrypted]") :
                        // removing whitespace (Pidgin adds weird tab characters)
                        mValue.getContent().getText().trim();
                mTextPane.setFontStyle(false, encrypted);
                LinkUtils.linkify(mTextPane.getStyledDocument(), text);
            }

            // hide area if there is no text
            mTextPane.setVisible(!text.isEmpty());
        }

        private void updateStatus() {
            boolean isOut = !mValue.isInMessage();

            Date deliveredDate = null;
            Set<Transmission> transmissions = mValue.getTransmissions();
            if (transmissions.size() == 1)
                deliveredDate = transmissions.stream().findFirst().get().getReceivedDate().orElse(null);

            // status icon
            if (isOut) {
                if (deliveredDate != null) {
                    mStatusIconLabel.setIcon(DELIVERED_ICON);
                } else {
                    switch (mValue.getStatus()) {
                        case PENDING :
                            mStatusIconLabel.setIcon(PENDING_ICON);
                            break;
                        case SENT :
                            mStatusIconLabel.setIcon(SENT_ICON);
                            break;
                        case RECEIVED:
                            // legacy
                            mStatusIconLabel.setIcon(DELIVERED_ICON);
                            break;
                        case ERROR:
                            mStatusIconLabel.setIcon(ERROR_ICON);
                            break;
                        default:
                            LOGGER.warning("unknown message receipt status!?");
                    }
                }
            } else { // IN message
                if (!mValue.getCoderStatus().getErrors().isEmpty()) {
                    mStatusIconLabel.setIcon(WARNING_ICON);
                }
            }

            //encryption icon
            Coder.Encryption enc = mValue.getCoderStatus().getEncryption();
            Coder.Signing sign = mValue.getCoderStatus().getSigning();
            boolean noSecurity = enc == Coder.Encryption.NOT && sign == Coder.Signing.NOT;
            boolean fullSecurity = enc == Coder.Encryption.DECRYPTED &&
                    ((isOut && sign == Coder.Signing.SIGNED) ||
                    (!isOut && sign == Coder.Signing.VERIFIED));

            mEncryptIconLabel.setIcon(
                    noSecurity ? UNENCRYPT_ICON :
                    fullSecurity ? CRYPT_ICON :
                    CRYPT_WARNING_ICON);

            // tooltip
            String html = "<html><body>" + /*"<h3>Header</h3>"+*/ "<br>";

            if (isOut) {
                String secStat = null;
                Date statusDate;
                if (deliveredDate != null) {
                    secStat = Tr.tr("Delivered:");
                    statusDate = deliveredDate;
                } else {
                    statusDate = mValue.getServerDate().orElse(null);
                    switch (mValue.getStatus()) {
                        case PENDING:
                            break;
                        case SENT:
                            secStat = Tr.tr("Sent:");
                            break;
                        // legacy
                        case RECEIVED:
                            secStat = Tr.tr("Delivered:");
                            break;
                        case ERROR:
                            secStat = Tr.tr("Error report:");
                            break;
                        default:
                            LOGGER.warning("unexpected msg status: "+mValue.getStatus());
                    }
                }

                String status = statusDate != null ?
                        Utils.MID_DATE_FORMAT.format(statusDate) :
                        null;

                String create = Utils.MID_DATE_FORMAT.format(mValue.getDate());
                if (!create.equals(status))
                    html += Tr.tr("Created:")+ " " + create + "<br>";

                if (status != null && secStat != null)
                    html += secStat + " " + status + "<br>";
            } else { // IN message
                Date receivedDate = mValue.getDate();
                String rec = Utils.MID_DATE_FORMAT.format(receivedDate);
                Date sentDate = mValue.getServerDate().orElse(null);
                if (sentDate != null) {
                    String sent = Utils.MID_DATE_FORMAT.format(sentDate);
                    if (!sent.equals(rec))
                        html += Tr.tr("Sent:")+ " " + sent + "<br>";
                }
                html += Tr.tr("Received:")+ " " + rec + "<br>";
            }

            // usual states
            String sec = noSecurity ? Tr.tr("Not encrypted") :
                    fullSecurity ? Tr.tr("Secure") :
                    null;
            if (sec == null) {
                // unusual states
                String encryption = Tr.tr("Unknown");
                switch (enc) {
                    case NOT: encryption = Tr.tr("Not encrypted"); break;
                    case ENCRYPTED: encryption = Tr.tr("Encrypted"); break;
                    case DECRYPTED: encryption = Tr.tr("Decrypted"); break;
                }
                String verification = Tr.tr("Unknown");
                switch (sign) {
                    case NOT: verification = Tr.tr("Not signed"); break;
                    case SIGNED: verification = Tr.tr("Not verified"); break;
                    case VERIFIED: verification = Tr.tr("Verified"); break;
                }
                sec = encryption + " / " + verification;
            }
            html += Tr.tr("Encryption")+": " + sec + "<br>";

            String errors = "";
            for (Coder.Error error: mValue.getCoderStatus().getErrors()) {
                errors += error.toString() + " <br> ";
            }
            if (!errors.isEmpty())
                html += Tr.tr("Security errors")+": " + errors;

            String serverErrText = mValue.getServerError().text;
            if (!serverErrText.isEmpty())
                html += Tr.tr("Server error")+": " + serverErrText + " <br> ";

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
            Attachment att = mValue.getContent().getAttachment().orElse(null);
            if (att == null)
                return;

            if (mAttPanel == null) {
                mAttPanel = new AttachmentPanel();
                mContentPanel.add(mAttPanel, BorderLayout.SOUTH);
            }

            // image thumbnail preview
            Path imagePath = mView.getControl().getImagePath(mValue).orElse(Paths.get(""));
            mAttPanel.setImage(imagePath);

            // link to the file
            Path linkPath = mView.getControl().getFilePath(att);
            if (!linkPath.toString().isEmpty()) {
                mAttPanel.setLink(imagePath.toString().isEmpty() ?
                        linkPath.getFileName().toString() :
                        "",
                        linkPath);
            } else {
                // status text
                String statusText = Tr.tr("loading…");
                switch (att.getDownloadProgress()) {
                    case -1: statusText = Tr.tr("stalled"); break;
                    case 0:
                    case -2: statusText = Tr.tr("downloading…"); break;
                    case -3: statusText = Tr.tr("download failed"); break;
                }
                mAttPanel.setStatus(statusText);
            }
        }

        @Override
        protected boolean contains(String search) {
            if (mValue.getContent().getText().toLowerCase().contains(search))
                return true;
            for (Transmission t: mValue.getTransmissions()) {
                if (t.getContact().getName().toLowerCase().contains(search) ||
                        t.getContact().getJID().string().toLowerCase().contains(search))
                    return true;
            }

            return false;
        }

        @Override
        protected void onRemove() {
            for (Transmission t: mValue.getTransmissions()) {
                t.getContact().deleteObserver(this);
            }
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
        private ListView<?, ?>.TableItem mValue;
        @Override
        public Component getTableCellEditorComponent(JTable table,
                Object value,
                boolean isSelected,
                int row,
                int column) {
            mValue = (ListView.TableItem) value;
            return mValue;
        }
        @Override
        public Object getCellEditorValue() {
            return mValue;
        }
    }

    private static String getFromString(InMessage message) {
        return Utils.displayName(message.getContact(), message.getJID(), View.MAX_NAME_IN_FROM_LABEL);
    }

    private static String toCopyString(KonMessage m) {
        String date = Utils.LONG_DATE_FORMAT.format(m.getDate());
        String from = m instanceof InMessage ?
                getFromString((InMessage) m) :
                Tr.tr("me");
        return date + " - " + from + " : " + m.getContent().getText();
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

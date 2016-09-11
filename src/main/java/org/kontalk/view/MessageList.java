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
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.SwingUtilities;
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
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class MessageList extends FlyweightListView<KonMessage> {
    private static final Logger LOGGER = Logger.getLogger(MessageList.class.getName());

    private static final Icon PENDING_ICON = Utils.getIcon("ic_msg_pending.png");;
    private static final Icon SENT_ICON = Utils.getIcon("ic_msg_sent.png");
    private static final Icon DELIVERED_ICON = Utils.getIcon("ic_msg_delivered.png");
    private static final Icon ERROR_ICON = Utils.getIcon("ic_msg_error.png");
    private static final Icon WARNING_ICON = Utils.getIcon("ic_msg_warning.png");
    private static final Icon CRYPT_ICON = Utils.getIcon("ic_msg_crypt.png");
    private static final Icon UNENCRYPT_ICON = Utils.getIcon("ic_msg_unencrypt.png");
    private static final Icon CRYPT_WARNING_ICON = Utils.getIcon("ic_msg_crypt_warning.png");

    private static final WrapEditorKit FIX_WRAP_KIT = new WrapEditorKit();
    private static final WebPopupMenu TEXT_COPY_MENU = Utils.createCopyMenu(false);

    private final ChatView mChatView;
    private final Chat mChat;

    private Optional<Background> mBackground = Optional.empty();

    MessageList(View view, ChatView chatView, Chat chat) {
        // render and editor item are equal (but not the same!)
        super(view,
                new MessageListFlyWeightItem(view),
                new MessageListFlyWeightItem(view),
                comparator(),
                false);
        mChatView = chatView;
        mChat = chat;

        // disable selection
        this.setSelectionModel(new UnselectableListModel());

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

    private void setBackground(Chat.ViewSettings s) {
        // simply overwrite
        mBackground = mChatView.createBG(s);
    }

    @Override
    protected WebPopupMenu rightClickMenu(KonMessage item) {
        WebPopupMenu menu = new WebPopupMenu();

        final KonMessage m = item;
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
     * Flyweight render item for message items.
     * The content is added to a panel inside this panel.
     */
    private static class MessageListFlyWeightItem
            extends FlyweightListView.FlyweightItem<KonMessage> {

        private final View mView;
        private final WebPanel mPanel;
        private final WebLabel mFromLabel;
        private final WebTextPane mTextPane;
        // container for status icons and date, with own tooltip
        private final WebPanel mStatusPanel;
        // TODO use WebImages
        private final WebLabel mStatusIconLabel;
        private final WebLabel mEncryptIconLabel;
        private final WebLabel dateLabel;

        private final AttachmentPanel mAttPanel;

        private KonMessage mLastValue = null;

        MessageListFlyWeightItem(View view) {
            mView = view;

            this.setOpaque(false);
            //this.setBorder(new EmptyBorder(10, 10, 10, 10));

            mPanel = new WebPanel(true);
            mPanel.setWebColoredBackground(false);
            mPanel.setMargin(2);

            mFromLabel = new WebLabel();
            mFromLabel.setFontSize(View.FONT_SIZE_SMALL);
            mFromLabel.setForeground(Color.BLUE);
            mFromLabel.setItalicFont();

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
            // right click menu
            mTextPane.setComponentPopupMenu(TEXT_COPY_MENU);

            // icons
            mStatusIconLabel = new WebLabel();
            mEncryptIconLabel = new WebLabel();

            // date label
            dateLabel = new WebLabel();
            dateLabel.setForeground(Color.GRAY);
            dateLabel.setFontSize(View.FONT_SIZE_TINY);

            // attachment
            mAttPanel = new AttachmentPanel();

            // layout...

            mPanel.add(mFromLabel, BorderLayout.NORTH);

            WebPanel mContentPanel = new WebPanel();
            mContentPanel.setOpaque(false);
            mContentPanel.setMargin(View.MARGIN_SMALL);
            mContentPanel.add(mTextPane, BorderLayout.CENTER);
            mContentPanel.add(mAttPanel, BorderLayout.SOUTH);
            mPanel.add(mContentPanel, BorderLayout.CENTER);

            // TODO make south panel obsolete
            WebPanel southPanel = new WebPanel();
            southPanel.setOpaque(false);
            mStatusPanel = new WebPanel();
            mStatusPanel.setOpaque(false);
            TooltipManager.addTooltip(mStatusPanel, "???");
            mStatusPanel.setLayout(new FlowLayout());
            mStatusPanel.add(mStatusIconLabel);
            mStatusPanel.add(mEncryptIconLabel);
            mStatusPanel.add(dateLabel);
            southPanel.add(mStatusPanel, BorderLayout.EAST);
            mPanel.add(southPanel, BorderLayout.SOUTH);

            // FlowLayout to toggle left/right position of panel (see below)
            this.setLayout(new FlowLayout(FlowLayout.TRAILING, 0, 0));
            this.add(mPanel);
        }

        // TODO rename mValue
        @Override
        protected void render(KonMessage value, int listWidth) {
            if (value == mLastValue)
                return; // performance
            mLastValue = value;

            // background
            boolean hasGroupCommand = value.getContent().getGroupCommand().isPresent();
            mPanel.setBackground(hasGroupCommand ? View.LIGHT_GREEN :
                    value.isInMessage() ?
                    Color.WHITE :
                    View.LIGHT_BLUE);

            // from label
            mFromLabel.setVisible(value.isInMessage() && value.getChat().isGroupChat());

            // icon
            if (value.getCoderStatus().isSecure()) {
                mEncryptIconLabel.setIcon(CRYPT_ICON);
            } else {
                mEncryptIconLabel.setIcon(UNENCRYPT_ICON);
            }

            // date label
            dateLabel.setText(Utils.SHORT_DATE_FORMAT.format(value.isInMessage() ?
                            value.getServerDate().orElse(value.getDate()) :
                            value.getDate()));

            // title
            mFromLabel.setText(!value.getContent().getGroupCommand().isPresent() &&
                    value instanceof InMessage ?
                    " "+getFromString((InMessage) value) :
                    "");

            // text in text area, before/after encryption
            boolean encrypted = value.isEncrypted();
            GroupCommand com = value.getContent().getGroupCommand().orElse(null);
            String text = "";
            if (com != null) {
                InMessage inMessage = value instanceof InMessage ?
                        (InMessage) value : null;
                String somebody = inMessage != null ?
                        getFromString(inMessage) : Tr.tr("You");
                switch(com.getOperation()) {
                    case CREATE:
                        text = String.format(Tr.tr("%1$s created this group"), somebody);
                        break;
                    case LEAVE:
                        text = String.format(Tr.tr("%1$s left this group"), somebody);
                        break;
                    case SET:
                        String subject = com.getSubject();
                        if (!subject.isEmpty()) {
                            text = String.format(Tr.tr("%1$s set the subject to \"%2$s\""), somebody, subject);
                        }
                        List<JID> added = com.getAdded();
                        if (!added.isEmpty()) {
                            text = String.format(Tr.tr("%1$s added %2$s"), somebody, mView.names(added));
                        }
                        List<JID> removed = com.getRemoved();
                        if (!removed.isEmpty()) {
                            text = String.format(Tr.tr("%1$s removed %2$s"), somebody, mView.names(removed));
                        }
                        if (text.isEmpty()) {
                            text = "did something wrong";
                        }
                        break;
                }
                mTextPane.setText(text);
                mTextPane.setFontStyle(false, true);
            } else {
                text = encrypted ?
                        Tr.tr("[encrypted]") :
                        // removing whitespace (Pidgin adds weird tab characters)
                        value.getContent().getText().trim();
                mTextPane.setFontStyle(false, encrypted);
                LinkUtils.linkify(mTextPane.getStyledDocument(), text);
            }

            // hide area if there is no text
            mTextPane.setVisible(!text.isEmpty());

            // status
            Date deliveredDate = null;
            Set<Transmission> transmissions = value.getTransmissions();
            if (transmissions.size() == 1)
                deliveredDate = transmissions.stream().findFirst().get().getReceivedDate().orElse(null);

            // status icon
            Icon statusIcon = null;
            boolean isOut = !value.isInMessage();
            if (isOut) {
                if (deliveredDate != null) {
                    statusIcon = DELIVERED_ICON;
                } else {
                    switch (value.getStatus()) {
                        case PENDING :
                            statusIcon = PENDING_ICON;
                            break;
                        case SENT :
                            statusIcon = SENT_ICON;
                            break;
                        case RECEIVED:
                            // legacy
                            statusIcon = DELIVERED_ICON;
                            break;
                        case ERROR:
                            statusIcon = ERROR_ICON;
                            break;
                        default:
                            LOGGER.warning("unknown message receipt status!?");
                    }
                }
            } else { // IN message
                if (!value.getCoderStatus().getErrors().isEmpty()) {
                    statusIcon = WARNING_ICON;
                }
            }
            mStatusIconLabel.setIcon(statusIcon);

            // encryption icon
            Coder.Encryption enc = value.getCoderStatus().getEncryption();
            Coder.Signing sign = value.getCoderStatus().getSigning();
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
                    statusDate = value.getServerDate().orElse(null);
                    switch (value.getStatus()) {
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
                            LOGGER.warning("unexpected msg status: "+value.getStatus());
                    }
                }

                String status = statusDate != null ?
                        Utils.MID_DATE_FORMAT.format(statusDate) :
                        null;

                String create = Utils.MID_DATE_FORMAT.format(value.getDate());
                if (!create.equals(status))
                    html += Tr.tr("Created:")+ " " + create + "<br>";

                if (status != null && secStat != null)
                    html += secStat + " " + status + "<br>";
            } else { // IN message
                Date receivedDate = value.getDate();
                String rec = Utils.MID_DATE_FORMAT.format(receivedDate);
                Date sentDate = value.getServerDate().orElse(null);
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
            for (Coder.Error error: value.getCoderStatus().getErrors()) {
                errors += error.toString() + " <br> ";
            }
            if (!errors.isEmpty())
                html += Tr.tr("Security errors")+": " + errors;

            String serverErrText = value.getServerError().text;
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

            // attachment / image; NOTE: loading many (big) images is very slow
            Attachment att = value.getContent().getAttachment().orElse(null);
            mAttPanel.setVisible(att != null);
            if (att != null) {
                // image thumbnail preview
                Path imagePath = mView.getControl().getImagePath(value).orElse(Paths.get(""));
                mAttPanel.setImage(imagePath);

                // link to the file
                Path linkPath = mView.getControl().getFilePath(att);
                mAttPanel.setLink(
                        // if there is a preview, no link text needed
                        !imagePath.toString().isEmpty() ?
                                "" :linkPath.getFileName().toString(),
                        linkPath);

                // status text
                String statusText;
                if (!linkPath.toString().isEmpty()) {
                    // file should exist, no status needed
                    statusText = "";
                } else {
                    statusText = Tr.tr("Attachment:") + " ";
                    switch (att.getDownloadProgress()) {
                        case -1: statusText += Tr.tr("stalled"); break;
                        case 0:
                        case -2: statusText += Tr.tr("downloading…"); break;
                        case -3: statusText += Tr.tr("download failed"); break;
                        default: statusText += Tr.tr("loading…");
                    }
                }
                mAttPanel.setStatus(statusText);
            }

            // resetting size
            mTextPane.setSize(Short.MAX_VALUE, Short.MAX_VALUE);
            mTextPane.setPreferredSize(null);

            // calculate preferred width
            // NOTE: on the very first call the list width is zero (?)
            int maxWidth = (int)(listWidth * 0.8);
            mTextPane.setSize(Short.MAX_VALUE, Short.MAX_VALUE);
            //mTextPane.setText(content); // already done
            int prefWidth = mTextPane.getPreferredSize().width;

            // calculate preferred height now with fixed width
            int width = Math.min(prefWidth, maxWidth);
            mTextPane.setSize(width, Short.MAX_VALUE);
            int height = mTextPane.getPreferredSize().height;

            Dimension prefSize = new Dimension(width, height);

            mTextPane.setSize(prefSize);
            // textArea does not need this but textPane does, and editorPane
            // is again totally different; I love Swing
            mTextPane.setPreferredSize(prefSize);

            // toggle left/right position
            this.setComponentOrientation(isOut ?
                    ComponentOrientation.LEFT_TO_RIGHT:
                    ComponentOrientation.RIGHT_TO_LEFT);
        }
    }

    private static Comparator<KonMessage> comparator() {
        return new Comparator<KonMessage>() {
            @Override
            public int compare(KonMessage o1, KonMessage o2) {
                int idComp = Integer.compare(o1.getID(), o2.getID());
                int dateComp = o1.getDate().compareTo(o2.getDate());
                return (idComp == 0 || dateComp == 0) ? idComp : dateComp;
            }
        };
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

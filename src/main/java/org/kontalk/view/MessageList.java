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

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.BoxView;
import javax.swing.text.ComponentView;
import javax.swing.text.Element;
import javax.swing.text.IconView;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.ViewFactory;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.alee.extended.panel.FlowPanel;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.GroupingType;
import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.text.WebEditorPane;
import com.alee.laf.text.WebTextPane;
import com.alee.managers.tooltip.TooltipManager;
import org.apache.commons.lang.time.DateUtils;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.kontalk.crypto.Coder;
import org.kontalk.misc.JID;
import org.kontalk.model.Contact;
import org.kontalk.model.chat.Chat;
import org.kontalk.model.message.InMessage;
import org.kontalk.model.message.KonMessage;
import org.kontalk.model.message.MessageContent.Attachment;
import org.kontalk.model.message.MessageContent.GroupCommand;
import org.kontalk.model.message.MessageContent.InAttachment;
import org.kontalk.model.message.OutMessage;
import org.kontalk.model.message.Transmission;
import org.kontalk.persistence.Config;
import org.kontalk.util.Tr;
import org.kontalk.view.ChatView.Background;
import org.kontalk.view.ComponentUtils.AttachmentPanel;

/**
 * View all messages of one chat in a left/right MIM style list.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class MessageList extends ListView<KonMessage> {
    private static final Logger LOGGER = Logger.getLogger(MessageList.class.getName());

    private static final Icon PENDING_ICON = Utils.getIcon("ic_msg_pending.png");
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

    private Background mBackground = null;

    MessageList(View view, ChatView chatView, Chat chat) {
        // render and editor item are equal (but not the same!)
        super(view,
                new MessageListFlyWeightItem(view),
                new MessageListFlyWeightItem(view),
                // allow multiple selections for "copy" action
                ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                true,
                false);

        mChatView = chatView;
        mChat = chat;

        //this.setEditable(false);
        //this.setAutoscrolls(true);
        this.setOpaque(false);

        // hide grid
        this.setShowGrid(false);

        // copy values to clipboard using the in-build 'copy' action, invoked by custom right-click
        // menu or default ctrl+c shortcut
        this.setTransferHandler(new CopyTransferHandler(mView));

        this.updateOnEDT(null);
    }

    Chat getChat() {
        return mChat;
    }

    Optional<Background> getBG() {
        return Optional.ofNullable(mBackground);
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
                !mChat.isRead() && mView.chatIsVisible(mChat)) {
            mChat.setRead();
        }

        if (arg == Chat.ViewChange.MEMBER_STATE) {
            // show/hide "is writing..." for last message
            // or hide "is writing..." for -now- second to last message after new message was added
            this.updateRowRendering(this.getRowCount() - 2, this.getRowCount() - 1);
            this.scrollToRow(this.getRowCount() - 1);
            this.repaint(); // swing...
        }
    }

    private void insertMessages() {
        boolean newAdded = this.sync(mChat.getMessages().getAll());
        if (newAdded) {
            //this.scrollToRow(this.getRowCount() -1);
            mChatView.setScrollDown();
        }
    }

    private void setBackground(Chat.ViewSettings s) {
        // simply overwrite
        mBackground = mChatView.createBGOrNull(s);
    }

    void updateMessageFontSize() {
        mRenderItem.configUpdate();
        mEditorItem.configUpdate();
        this.updateRowRendering(0, this.getRowCount() - 1);
    }

    @Override
    protected WebPopupMenu rightClickMenu(List<KonMessage> selectedValues) {
        WebPopupMenu menu = new WebPopupMenu();

        Action copyAction = new AbstractAction(Tr.tr("Copy")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                Action copy = MessageList.this.getActionMap().get("copy");
                ActionEvent ae = new ActionEvent(MessageList.this, ActionEvent.ACTION_PERFORMED, "");
                copy.actionPerformed(ae);
            }
        };
        copyAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control C"));
        menu.add(copyAction);

        if (selectedValues.isEmpty()) {
            LOGGER.warning("no values");
            return menu;
        }

        if (selectedValues.size() > 1)
            return menu;

        final KonMessage m = selectedValues.get(0);
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
            InAttachment att = m.getContent().getInAttachment().orElse(null);
            if (att != null && att.getFilename().isEmpty()) {
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

        return menu;
    }

    @Override
    public int compare(KonMessage o1, KonMessage o2) {
        int idComp = Integer.compare(o1.getID(), o2.getID());
        int dateComp = o1.getDate().compareTo(o2.getDate());
        return (idComp == 0 || dateComp == 0) ? idComp : dateComp;
    }

    /**
     * Flyweight render item for message items.
     * The content is added to a panel inside this panel.
     * Above the content panel a centered date panel is drawn (if appropriate).
     */
    private static class MessageListFlyWeightItem extends FlyweightItem<KonMessage> {

        private final View mView;
        private final WebPanel mDateMarginPanel;
        private final WebPanel mDatePanel;
        private final WebLabel mDateLabel;
        private final WebPanel mFlowPanel;
        private final WebPanel mPanel;
        private final WebLabel mFromLabel;
        private final WebTextPane mTextPane;
        // container for status icons and date, with own tooltip
        private final WebPanel mStatusPanel;
        // TODO use WebImages
        private final WebLabel mStatusIconLabel;
        private final WebLabel mEncryptIconLabel;
        private final WebLabel mTimeLabel;
        private final WebPanel mWritingPanel;
        private final WebLabel mWritingLabel;

        private final AttachmentPanel mAttPanel;

        private final LinkUtils.Linkifier mLinkifier;
        private final Style mMeCommandStyle;

        MessageListFlyWeightItem(View view) {
            mView = view;

            this.setOpaque(false);

            mDatePanel = new WebPanel();
            mDatePanel.setRound(View.ROUND);
            mDatePanel.setWebColoredBackground(false);
            mDatePanel.setBackground(View.BLUE);
            mDatePanel.setBorderColor(View.BLUE);
            mDateLabel = new WebLabel();
            mDateLabel.setForeground(Color.WHITE);
            mDatePanel.add(mDateLabel, BorderLayout.CENTER);
            mDateMarginPanel = new GroupPanel(mDatePanel);
            this.add(new GroupPanel(GroupingType.fillFirstAndLast,
                                           Box.createGlue(), mDateMarginPanel, Box.createGlue())
                             .setMargin(0),
                    BorderLayout.NORTH);

            // FlowLayout to toggle left/right position of panel (see below)
            mFlowPanel = new FlowPanel(0);
            //this.setBorder(new EmptyBorder(10, 10, 10, 10));
            mFlowPanel.setBackground(View.BLUE); // seen when selected

            mPanel = new WebPanel(true);
            mPanel.setRound(View.ROUND);
            mPanel.setWebColoredBackground(false);
            mPanel.setMargin(View.MARGIN_TINY);

            mFromLabel = new WebLabel();
            mFromLabel.setFontSize(View.FONT_SIZE_SMALL);
            mFromLabel.setForeground(Color.BLUE);
            mFromLabel.setItalicFont();
            mPanel.add(mFromLabel, BorderLayout.NORTH);

            // text area
            mTextPane = new WebTextPane();
            mTextPane.setEditable(false);
            mTextPane.setOpaque(false);
            // sets default font
            mTextPane.putClientProperty(WebEditorPane.HONOR_DISPLAY_PROPERTIES, true);
            // for detecting link clicks
            mTextPane.addMouseListener(LinkUtils.CLICK_LISTENER);
            // for detecting motion
            mTextPane.addMouseMotionListener(LinkUtils.MOTION_LISTENER);
            // fix word wrap for long words
            mTextPane.setEditorKit(FIX_WRAP_KIT);
            // right click menu
            mTextPane.setComponentPopupMenu(TEXT_COPY_MENU);

            // text styling
            mLinkifier = new LinkUtils.Linkifier(mTextPane.getStyledDocument());
            mMeCommandStyle = mTextPane.addStyle(null, null);
            StyleConstants.setForeground(mMeCommandStyle, View.GREEN);

            // attachment
            mAttPanel = new AttachmentPanel();

            // layout: content panel
            WebPanel mContentPanel = new WebPanel();
            mContentPanel.setOpaque(false);
            mContentPanel.setMargin(View.MARGIN_SMALL);
            mContentPanel.add(mTextPane, BorderLayout.CENTER);
            mContentPanel.add(mAttPanel, BorderLayout.SOUTH);
            mPanel.add(mContentPanel, BorderLayout.CENTER);

            // status panel...
            mStatusPanel = new WebPanel();
            mStatusPanel.setOpaque(false);
            mStatusPanel.setLayout(new FlowLayout());

            mStatusIconLabel = new WebLabel();
            mStatusPanel.add(mStatusIconLabel);

            mEncryptIconLabel = new WebLabel();
            mStatusPanel.add(mEncryptIconLabel);

            mTimeLabel = new WebLabel();
            mTimeLabel.setForeground(Color.GRAY);
            mStatusPanel.add(mTimeLabel);

            WebPanel southPanel = new WebPanel();
            southPanel.setOpaque(false);
            southPanel.add(mStatusPanel, BorderLayout.EAST);
            mPanel.add(southPanel, BorderLayout.SOUTH);

            mFlowPanel.add(mPanel);
            this.add(mFlowPanel, BorderLayout.CENTER);

            mWritingPanel = new WebPanel();
            mWritingPanel.setRound(View.ROUND);
            mWritingPanel.setWebColoredBackground(false);
            mWritingPanel.setBackground(Color.WHITE);
            mWritingPanel.setBorderColor(Color.WHITE);
            mWritingLabel = new WebLabel();
            mWritingLabel.setForeground(View.DARK_RED);
            mWritingPanel.add(mWritingLabel, BorderLayout.CENTER);
            this.add(new GroupPanel(GroupingType.fillLast, mWritingPanel, Box.createGlue())
                             .setMargin(0),
                    BorderLayout.SOUTH);

            // set font size
            this.configUpdate();
        }

        @Override
        protected void configUpdate() {
            int textFontSize;
            int timeFontSize;
            switch(Config.getInstance().getInt(Config.VIEW_MESSAGE_FONT_SIZE)) {
                case 1:
                    textFontSize = timeFontSize = View.FONT_SIZE_TINY; break;
                case 2:
                    textFontSize = View.FONT_SIZE_NORMAL;
                    timeFontSize = View.FONT_SIZE_SMALL; break;
                case 3:
                    textFontSize = View.FONT_SIZE_BIG;
                    timeFontSize = View.FONT_SIZE_NORMAL; break;
                default:
                    textFontSize = View.FONT_SIZE_SMALL;
                    timeFontSize = View.FONT_SIZE_TINY;
            }
            mDateLabel.setFontSize(textFontSize);
            mTextPane.setFontSize(textFontSize);
            mTimeLabel.setFontSize(timeFontSize);
        }

        @Override
        protected void render(KonMessage value, int listWidth, boolean isSelected, boolean isLast) {
            KonMessage last = value.getPredecessor().orElse(null);
            boolean showDateSeparator = last == null ||
                    !DateUtils.isSameDay(last.getDate(), value.getDate());
            mDatePanel.setVisible(showDateSeparator); // otherwise visible on mouse over (?)
            mDateMarginPanel.setMargin(showDateSeparator ? View.MARGIN_SMALL : 0);
            boolean consecutive = last == null || last.getSender().equals(value.getSender());
            mDatePanel.setMargin(showDateSeparator || !consecutive ? View.MARGIN_SMALL : 0);
            // decoration consumes space, even if nothing is visible in panel
            mDatePanel.setUndecorated(!showDateSeparator);
            mDateLabel.setText(showDateSeparator ?
                                       Utils.getDateSeparatorText(value.getDate()) : "");

            // background (flow item panel)
            mFlowPanel.setOpaque(isSelected);

            boolean isOut = !value.isInMessage();

            // toggle left/right position
            mFlowPanel.setComponentOrientation(!isOut ?
                   ComponentOrientation.LEFT_TO_RIGHT : ComponentOrientation.RIGHT_TO_LEFT);

            // background (message panel)
            boolean hasGroupCommand = value.getContent().getGroupCommand().isPresent();
            Color color =
                    hasGroupCommand ? View.LIGHT_GREEN :
                    value.isInMessage() ? Color.WHITE : View.LIGHT_BLUE;
            mPanel.setBackground(color);
            mPanel.setBorderColor(color);

            // from label
            mFromLabel.setVisible(value.isInMessage() && value.getChat().isGroupChat());

            // icon
            if (value.getCoderStatus().isSecure()) {
                mEncryptIconLabel.setIcon(CRYPT_ICON);
            } else {
                mEncryptIconLabel.setIcon(UNENCRYPT_ICON);
            }

            // date label
            mTimeLabel.setText(Utils.SHORT_DATE_FORMAT.format(value.isInMessage() ?
                            value.getServerDate().orElse(value.getDate()) :
                            value.getDate()));

            // title
            mFromLabel.setText(!value.getContent().getGroupCommand().isPresent() &&
                    value instanceof InMessage ?
                    " " + getFromString((InMessage) value) :
                    "");

            // text in text area
            String text = messageToString(value, mView, false);
            if (value.getContent().getGroupCommand().isPresent()) {
                mTextPane.setText(text);
                mTextPane.setFontStyle(false, true);
            } else {
                mTextPane.setFontStyle(false, value.isEncrypted());
                StyledDocument document = mTextPane.getStyledDocument();
                try {
                    document.remove(0, document.getLength());
                    // output implementation of the "/me" command, XEP-0245
                    if (text.startsWith(View.THE_ME_COMMAND)) {
                        Contact sender = value.getSender().orElse(null);
                        // NOTE: not updated if sender name changes, people have to live with it
                        String meName = (sender == null ? Tr.tr("Me") : sender.getName()) + " ";
                        document.insertString(0, meName, mMeCommandStyle);
                        text = text.substring(View.THE_ME_COMMAND.length());
                    }
                    mLinkifier.linkify(text);
                } catch (BadLocationException ex) {
                    LOGGER.log(Level.WARNING, "can't set styled document text", ex);
                }
            }

            // hide area if there is no text
            mTextPane.setVisible(!text.isEmpty());

            // status
            Date deliveredDate = null;
            Set<Transmission> transmissions = value.getTransmissions();
            if (transmissions.size() == 1)
                deliveredDate = transmissions.iterator().next().getReceivedDate().orElse(null);

            // status icon
            Icon statusIcon = null;
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
                    secStat = Tr.tr("Received:");
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
                            secStat = Tr.tr("Received:");
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
                Path imagePath = value.getContent().getPreview()
                        .map(p -> p.getImagePath(value.getID())).orElse(null);
                Path linkPath = att.getFilePath();
                if (imagePath != null && !imagePath.toString().isEmpty())
                    mAttPanel.setAttachment(imagePath, linkPath);
                else
                    mAttPanel.setAttachment(linkPath.getFileName().toString(), linkPath);

                // status text
                String statusText;
                if (!linkPath.toString().isEmpty() && !att.isEncrypted()) {
                    // file should exist, no status needed
                    statusText = "";
                } else {
                    statusText = Tr.tr("Attachment:") + " ";
                    if (att.isEncrypted()) {
                        statusText += Tr.tr("encrypted");
                    } else {
                        switch (att.getDownloadProgress()) {
                            case -1: statusText += Tr.tr("stalled"); break;
                            case 0:
                            case -2: statusText += Tr.tr("downloading…"); break;
                            case -3: statusText += Tr.tr("download failed"); break;
                            default: statusText += Tr.tr("loading…");
                        }
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

            boolean showWriting = isLast
                    && value.getChat().getAllMembers().stream()
                    .anyMatch(m -> m.getState() == ChatState.composing);
            mWritingPanel.setMargin(showWriting ? View.MARGIN_SMALL : 0);
            // decoration consumes space, even if nothing is visible in panel
            mWritingPanel.setUndecorated(!showWriting);
            mWritingLabel.setText(showWriting ? Tr.tr("is writing...") : "");
        }
    }

    private static String getFromString(InMessage message) {
        return Utils.displayName(message.getContact(), message.getJID(), View.MAX_NAME_IN_FROM_LABEL);
    }

    /**
     * Fix for the infamous "Wrap long words" problem in Java 7+.
     * Source: https://stackoverflow.com/a/13375811
     */
    private static class WrapEditorKit extends StyledEditorKit {
        final ViewFactory defaultFactory = new WrapColumnFactory();
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

    // overwriting non-public BasicTableUI.TableTransferHandler for copy action
    private static class CopyTransferHandler extends TransferHandler {

        private final View mView;

        private CopyTransferHandler(View view) {
            mView = view;
        }

        protected Transferable createTransferable(JComponent c) {
            if (!(c instanceof MessageList)) {
                return null;
            }

            List<KonMessage> messages = ((MessageList) c).getSelectedValues();
            if (messages.isEmpty()) {
                return null;
            }

            StringBuilder plainBuf = new StringBuilder();
            for (KonMessage m : messages) {
                String val = messageToString(m, mView, true);
                plainBuf.append(val).append("\n"); // NOTE: newline after last line
            }

            //return new BasicTransferable(plainBuf.toString(), htmlBuf.toString());
            return new StringSelection(plainBuf.toString());
        }

        public int getSourceActions(JComponent c) {
            return COPY;
        }
    }

    private static String messageToString(KonMessage message, View view, boolean copy) {
        String pre = "";
        if (copy) {
            String date = Utils.LONG_DATE_FORMAT.format(message.getDate());
            String from = message instanceof InMessage ?
                    getFromString((InMessage) message) :
                    Tr.tr("me"); // TODO get my name
            Attachment att = message.getContent().getAttachment().orElse(null);
            String as = att == null ? "" : "[" + att.getFilename() + "] ";
            pre = date + " - " + from + " : " + as;
        }

        String text = "";
        GroupCommand com = message.getContent().getGroupCommand().orElse(null);
        if (com != null) {
            InMessage inMessage = message instanceof InMessage ?
                    (InMessage) message : null;
            String somebody = inMessage != null ?
                    getFromString(inMessage) : Tr.tr("You");
            switch (com.getOperation()) {
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
                        text = String.format(Tr.tr("%1$s added %2$s"), somebody, view.names(added));
                    }
                    List<JID> removed = com.getRemoved();
                    if (!removed.isEmpty()) {
                        text = String.format(Tr.tr("%1$s removed %2$s"), somebody, view.names(removed));
                    }
                    if (text.isEmpty()) {
                        text = somebody + " did something wrong";
                    }
                    break;
            }
            if (copy)
                text = "[" + text + "]";
        } else {
            text = message.isEncrypted() ?
                    Tr.tr("[encrypted]") :
                    // removing whitespace (Pidgin adds weird tab characters)
                    message.getContent().getText().trim();
        }
        return pre + text;
    }
}

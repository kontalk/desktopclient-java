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

import javax.swing.JComponent;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.IOException;
import java.util.List;

import com.alee.extended.panel.WebOverlay;
import com.alee.laf.label.WebLabel;
import com.alee.laf.text.WebTextArea;
import com.alee.utils.swing.DocumentChangeListener;
import org.kontalk.util.EncodingUtils;
import org.kontalk.util.Tr;

/**
 * Text composing area for writing a message.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class ComposingArea {

    private final ChatView mChatView;

    private final WebTextArea mTextArea;
    private final ComponentUtils.ScrollPane mScrollPane;
    private final WebOverlay mOverlay;
    private final WebLabel mOverlayLabel;
    private final FileDropHandler mDropHandler;

    ComposingArea(ChatView chatView) {
        mChatView = chatView;

        mTextArea = new WebTextArea();
        mTextArea.setMargin(View.MARGIN_SMALL);
        mTextArea.setBorder(null);
        mTextArea.setLineWrap(true);
        mTextArea.setWrapStyleWord(true);
        mTextArea.setFontSize(View.FONT_SIZE_NORMAL);
        mTextArea.setInputPrompt(Tr.tr("Type to compose"));
        mTextArea.setInputPromptHorizontalPosition(SwingConstants.LEFT);
        mTextArea.setComponentPopupMenu(Utils.createCopyMenu(true));
        mTextArea.getDocument().addDocumentListener(new DocumentChangeListener() {
            @Override
            public void documentChanged(DocumentEvent e) {
                mChatView.onKeyTypeEvent(e.getDocument().getLength() == 0);
            }
        });

        mScrollPane = new ComponentUtils.ScrollPane(mTextArea, false);
        mScrollPane.setBorder(null);

        // text area overlay
        mOverlayLabel = new WebLabel().setBoldFont();

        mOverlay = new WebOverlay(mScrollPane, mOverlayLabel,
                                         SwingConstants.CENTER, SwingConstants.CENTER);
        mOverlay.setUndecorated(false)
                .setMargin(View.MARGIN_SMALL)
                .setWebColoredBackground(false);

        // old transfer handler of text area is fallback for bottom panel
        mDropHandler = new FileDropHandler(mTextArea.getTransferHandler());
        mTextArea.setTransferHandler(mDropHandler);

        // when text changed...
        mTextArea.getDocument().addDocumentListener(new DocumentChangeListener() {
            @Override
            public void documentChanged(DocumentEvent e) {
                // these are strange times
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        ComposingArea.this.adjustSize();
                    }
                });
            }
        });
        ((AbstractDocument) mTextArea.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void replace(FilterBypass fb, int offset, int length, String string,
                                AttributeSet attr) throws BadLocationException {
                // input implementation of the "/me" command, XEP-0245
                if (length == 0 && offset == 0 && string.equals("/")) {
                    fb.insertString(0, View.THE_ME_COMMAND, attr);
                    return;
                }
                super.replace(fb, offset, length, string, attr);
            }
        });

        // ...or window is resized
        mChatView.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                ComposingArea.this.adjustSize();
            }
        });
    }

    private void adjustSize() {
        int newHeight = mTextArea.getPreferredSize().height;
        int maxHeight = mChatView.getHeight() / 3;

        mScrollPane.setPreferredSize(new Dimension(mScrollPane.getWidth(),
                                                          newHeight < maxHeight ?
                                                                  // grow
                                                                  newHeight + 1 : // +1 for border
                                                                  // fixed height
                                                                  maxHeight));

        // swing does not figure this out itself
        mChatView.revalidate();
    }

    FileDropHandler getDropHandler() {
        return mDropHandler;
    }

    void focus() {
        mTextArea.requestFocusInWindow();
    }

    void setHotkeys(boolean enterSends) {
        for (KeyListener l : mTextArea.getKeyListeners())
            mTextArea.removeKeyListener(l);

        mTextArea.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (enterSends && e.getKeyCode() == KeyEvent.VK_ENTER &&
                            e.getModifiersEx() == KeyEvent.CTRL_DOWN_MASK) {
                    e.consume();
                    mTextArea.append(EncodingUtils.EOL);
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
    }

    void setEnabled(boolean enabled, boolean isMember) {
        mTextArea.setEnabled(enabled);
        Color textBG = enabled ? Color.WHITE : Color.LIGHT_GRAY;
        mTextArea.setBackground(textBG);
        mOverlay.setBackground(textBG);
        mOverlayLabel.setText(isMember ? Tr.tr("You are not member of this group") : "");
    }

    String getText() {
        return mTextArea.getText();
    }

    void reset() {
        mTextArea.setText("");
    }

    boolean isFocused() {
        return SwingUtilities.getWindowAncestor(mChatView).getFocusOwner() == mTextArea;
    }

    public Component getComponent() {
        return mOverlay;
    }

    final class FileDropHandler extends TransferHandler {

        private final TransferHandler mTextHandler;

        private boolean mDropEnabled = true;

        private FileDropHandler(TransferHandler textHandler) {
            mTextHandler = textHandler;
        }

        void setDropEnabled(boolean enabled) {
            mDropEnabled = enabled;
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (support.isDrop() && !mDropEnabled)
                return false;

            if (support.getComponent() == mTextArea
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
        public boolean importData(TransferSupport support) {
            if (!this.canImport(support))
                return false;

            if (support.getComponent() == mTextArea &&
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

            for (File file : files) {
                if (Utils.isAllowedAttachmentFile(file)) {
                    mChatView.sendFile(file);
                }
            }
            return !files.isEmpty();
        }

        @Override
        public void exportToClipboard(JComponent comp, Clipboard clip, int action) throws IllegalStateException {
            mTextHandler.exportToClipboard(comp, clip, action);
        }
    }
}

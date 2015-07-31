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
import com.alee.laf.button.WebToggleButton;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.splitpane.WebSplitPane;
import com.alee.laf.viewport.WebViewport;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import static javax.swing.JSplitPane.VERTICAL_SPLIT;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import org.kontalk.model.KonThread;
import org.kontalk.model.ThreadList;
import org.kontalk.model.User;
import org.kontalk.system.Config;
import org.kontalk.util.Tr;

/**
 * Pane that shows the currently selected thread.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class ThreadView extends WebPanel implements Observer {

    private final View mView;

    private final WebLabel mTitleLabel;
    private final WebLabel mSubLabel;
    private final WebScrollPane mScrollPane;
    private final Map<Integer, MessageList> mThreadCache = new HashMap<>();
    private ComponentUtils.ModalPopup mPopup = null;
    private Background mDefaultBG;

    private boolean mScrollDown = false;

    ThreadView(View view, Component sendTextField, Component sendButton) {
        mView = view;

        WebPanel titlePanel = new WebPanel(false,
                new BorderLayout(View.GAP_SMALL, View.GAP_SMALL));
        titlePanel.setMargin(View.MARGIN_DEFAULT);
        mTitleLabel = new WebLabel();
        mTitleLabel.setFontSize(16);
        mTitleLabel.setDrawShade(true);
        mSubLabel = new WebLabel();
        mSubLabel.setFontSize(11);
        mSubLabel.setForeground(Color.GRAY);
        titlePanel.add(new GroupPanel(View.GAP_SMALL, false, mTitleLabel, mSubLabel), BorderLayout.CENTER);

        final WebToggleButton editButton = new WebToggleButton(
                Utils.getIcon("ic_ui_menu.png"));
        //editButton.setToolTipText(Tr.tr("Edit this chat"));
        editButton.setTopBgColor(titlePanel.getBackground());
        editButton.setBottomBgColor(titlePanel.getBackground());
        editButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                    ThreadView.this.showPopup(editButton);
            }
        });
        titlePanel.add(editButton, BorderLayout.EAST);
        this.add(titlePanel, BorderLayout.NORTH);

        mScrollPane = new ScrollPane(this);
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
                        ThreadView.this.getCurrentBackground().updateNowOrLater();
                // if there is something to draw, draw it now even if its old
                if (optBG.isPresent())
                    g.drawImage(optBG.get(), 0, 0, this.getWidth(), this.getHeight(), null);
            }
        });

        WebPanel bottomPanel = new WebPanel();
        WebScrollPane textFieldScrollPane = new ScrollPane(sendTextField);
        bottomPanel.add(textFieldScrollPane, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        bottomPanel.setMinimumSize(new Dimension(0, 32));
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

    Optional<KonThread> getCurrentThread() {
        Optional<MessageList> optview = this.getCurrentList();
        return optview.isPresent() ?
                Optional.of(optview.get().getThread()) :
                Optional.<KonThread>empty();
    }

    void filterCurrentThread(String searchText) {
        Optional<MessageList> optList = this.getCurrentList();
        if (!optList.isPresent())
            return;
        optList.get().filterItems(searchText);
    }

    void showThread(KonThread thread) {
        List<User> user = new ArrayList<>(thread.getUser());
        mTitleLabel.setText(user.size() == 1 ? Utils.name(user.get(0)) :
                !thread.getSubject().isEmpty() ? thread.getSubject() :
                Tr.tr("Group Chat"));
        // TODO update
        mSubLabel.setText(user.size() == 1 ?
                Utils.mainStatus(user.get(0)) :
                Utils.userNameList(thread.getUser()));
        if (!mThreadCache.containsKey(thread.getID())) {
            MessageList newMessageList = new MessageList(mView, this, thread);
            thread.addObserver(newMessageList);
            mThreadCache.put(thread.getID(), newMessageList);
        }
        MessageList list = mThreadCache.get(thread.getID());
        mScrollPane.getViewport().setView(list);

        thread.setRead();
    }

    void setColor(Color color) {
        mScrollPane.getViewport().setBackground(color);
    }

    void loadDefaultBG() {
        String imagePath = Config.getInstance().getString(Config.VIEW_THREAD_BG);
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

    Optional<Background> createBG(KonThread.ViewSettings s){
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

    @Override
    public void update(Observable o, final Object arg) {
        if (SwingUtilities.isEventDispatchThread()) {
            this.updateOnEDT(arg);
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ThreadView.this.updateOnEDT(arg);
            }
        });
    }

    private void updateOnEDT(Object arg) {
        if (arg instanceof KonThread) {
            KonThread thread = (KonThread) arg;
            if (!ThreadList.getInstance().contains(thread.getID())) {
                // thread was deleted
                MessageList viewList = mThreadCache.get(thread.getID());
                if (viewList != null)
                    viewList.clearItems();
                thread.deleteObserver(viewList);
                mThreadCache.remove(thread.getID());
                if(this.getCurrentThread().orElse(null) == thread) {
                    mScrollPane.setViewportView(null);
                }
            }
        }
    }

    private void showPopup(final WebToggleButton invoker) {
        Optional<KonThread> optThread = ThreadView.this.getCurrentThread();
        if (!optThread.isPresent())
            return;
        if (mPopup == null)
            mPopup = new ComponentUtils.ModalPopup(invoker);

        mPopup.add(new ThreadDetails(mPopup, optThread.get()));
        mPopup.showPopup();
    }

    /** A background image of thread view with efficient async reloading. */
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

        /** Default, no thread specific settings. */
        Background(Component parent) {
            //mOrigin = View.getImage("thread_bg.png");
            this(parent, null, new Color(255, 255, 255, 255));
        }

        /** Image set by user (global or only for thread). */
        Background(Component parent, String imagePath) {
            // image loaded async!
            this(parent, Toolkit.getDefaultToolkit().createImage(imagePath), null);
        }

        /** Thread specific color. */
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
            Image scaledImage = ImageLoader.scale(mOrigin,
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
                cachedG.fillRect(0, 0, width, ThreadView.this.getHeight());
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

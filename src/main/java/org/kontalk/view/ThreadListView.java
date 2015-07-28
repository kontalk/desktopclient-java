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
import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.Timer;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.kontalk.system.Config;
import org.kontalk.model.KonMessage;
import org.kontalk.model.KonThread;
import org.kontalk.model.KonThread.KonChatState;
import org.kontalk.model.ThreadList;
import org.kontalk.model.User;
import org.kontalk.util.Tr;
import org.kontalk.view.ThreadListView.ThreadItem;

/**
 * Show a brief list of all threads.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class ThreadListView extends Table<ThreadItem, KonThread> {

    private final ThreadList mThreadList;
    private final WebPopupMenu mPopupMenu;

    ThreadListView(final View view, ThreadList threadList) {
        super(view, true);
        mThreadList = threadList;

        this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // right click popup menu
        mPopupMenu = new WebPopupMenu();

        WebMenuItem deleteMenuItem = new WebMenuItem(Tr.tr("Delete Chat"));
        deleteMenuItem.setToolTipText(Tr.tr("Delete this chat"));
        deleteMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                ThreadListView.this.deleteSelectedThread();
            }
        });
        mPopupMenu.add(deleteMenuItem);

        // actions triggered by selection
        this.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            KonThread lastThread = null;

            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting())
                    return;
                Optional<KonThread> optThread = ThreadListView.this.getSelectedValue();
                if (!optThread.isPresent())
                    return;
                // if event is caused by filtering, dont do anything
                if (lastThread == optThread.get())
                    return;

                mView.clearSearch();
                mView.showThread(optThread.get());
                lastThread = optThread.get();
            }
        });

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
                    int row = ThreadListView.this.rowAtPoint(e.getPoint());
                    ThreadListView.this.setSelectedItem(row);
                    ThreadListView.this.showPopupMenu(e);
                }
            }
        });

        this.updateOnEDT(null);
    }

    @Override
    protected void updateOnEDT(Object arg) {
        // TODO, performance
        Set<ThreadItem> newItems = new HashSet<>();
        Set<KonThread> threads = mThreadList.getAll();
        for (KonThread thread: threads)
            if (!this.containsValue(thread))
                newItems.add(new ThreadItem(thread));
        this.sync(threads, newItems);
    }

    void selectLastThread() {
        int i = Config.getInstance().getInt(Config.VIEW_SELECTED_THREAD);
        if (i < 0) i = 0;
        this.setSelectedItem(i);
    }

    void save() {
        Config.getInstance().setProperty(Config.VIEW_SELECTED_THREAD,
                this.getSelectedRow());
    }

    private void showPopupMenu(MouseEvent e) {
           mPopupMenu.show(this, e.getX(), e.getY());
    }

    private void deleteSelectedThread() {
        ThreadItem t = this.getSelectedItem();
        if (t.mValue.getMessages().getAll().size() != 0) {
            String text = Tr.tr("Permanently delete all messages in this chat?");
            if (!Utils.confirmDeletion(this, text))
                return;
        }
        ThreadItem threadItem = this.getSelectedItem();
        mView.getControl().deleteThread(threadItem.mValue);
    }

    protected final class ThreadItem extends Table<ThreadItem, KonThread>.TableItem {

        private final WebLabel mNameLabel;
        private final WebLabel mSubjectLabel;
        private final WebLabel mStatusLabel;
        private final WebLabel mChatStateLabel;
        private Color mBackground;

        ThreadItem(KonThread thread) {
            super(thread);

            this.setLayout(new BorderLayout(View.GAP_DEFAULT, View.GAP_SMALL));
            this.setMargin(View.MARGIN_SMALL);

            mNameLabel = new WebLabel();
            mNameLabel.setFontSize(14);
            mSubjectLabel = new WebLabel();
            mSubjectLabel.setForeground(Color.GRAY);
            mSubjectLabel.setFontSize(13);
            this.add(new GroupPanel(View.GAP_DEFAULT, mNameLabel, mSubjectLabel),
                    BorderLayout.NORTH);

            mStatusLabel = new WebLabel();
            mStatusLabel.setForeground(Color.GRAY);
            mStatusLabel.setFontSize(11);
            this.add(mStatusLabel, BorderLayout.CENTER);

            mChatStateLabel = new WebLabel();
            mChatStateLabel.setForeground(View.GREEN);
            mChatStateLabel.setFontSize(13);
            mChatStateLabel.setBoldFont();
            //mChatStateLabel.setMargin(0, 5, 0, 5);
            this.add(mChatStateLabel, BorderLayout.EAST);

            this.updateView(null);

            this.setBackground(mBackground);
        }

        @Override
        protected void render(int tableWidth, boolean isSelected) {
            if (isSelected)
                this.setBackground(View.BLUE);
            else
                this.setBackground(mBackground);
        }

        @Override
        protected String getTooltipText() {
            return "<html><body>" + lastActivity(mValue, false) + "<br>" + "";
        }

        @Override
        protected void updateOnEDT(Object arg) {
            this.updateView(arg);
            // needed for background repaint
            ThreadListView.this.repaint();
        }

        private void updateView(Object arg) {
            if (arg == null || arg instanceof User) {
                Optional<User> optUser = mValue.getSingleUser();
                if (optUser.isPresent())
                    mNameLabel.setText(Utils.name(optUser.get()));
            }

            if (arg == null || arg instanceof String) {
                mSubjectLabel.setText(mValue.getSubject());
            }

            if (arg == null || arg instanceof Set) {
                // TODO group chat
//                List<String> nameList = new ArrayList<>(mValue.getUser().size());
//                for (User user : mValue.getUser()) {
//                    nameList.add(user.getName().isEmpty() ?
//                            Tr.tr("<unknown>") :
//                            user.getName());
//                }
//                mUserLabel.setText(StringUtils.join(nameList, ", "));
            }

            if (arg == null || arg instanceof KonMessage) {
                this.updateBG();

                mStatusLabel.setText(lastActivity(mValue, true));
                ThreadListView.this.updateSorting();
            } else if (arg instanceof Boolean) {
                this.updateBG();
            } else if (arg instanceof Timer) {
                mStatusLabel.setText(lastActivity(mValue, true));
            }

            if (arg instanceof KonThread.KonChatState) {
                KonChatState state = (KonChatState) arg;
                String stateText = null;
                switch(state.getState()) {
                    case composing: stateText = Tr.tr("is writing..."); break;
                    //case paused: activity = T/r.tr("stopped typing"); break;
                    //case inactive: stateText = T/r.tr("is inactive"); break;
                }
                if (stateText == null) {
                    // 'inactive' is default
                    mChatStateLabel.setText("");
                    return;
                }

                if (mValue.getUser().size() > 1)
                    stateText = state.getUser().getName() + " " + stateText;

                mChatStateLabel.setText(stateText + " ");
            }
        }

        private void updateBG() {
            mBackground = !mValue.isRead() ? View.LIGHT_BLUE : Color.WHITE;
        }

        @Override
        protected boolean contains(String search) {
            // always show entry for current thread
            Optional<KonThread> optThread = mView.getCurrentShownThread();
            if (optThread.isPresent() && optThread.get() == mValue)
                return true;

            for (User user: mValue.getUser()) {
                if (user.getName().toLowerCase().contains(search) ||
                        user.getJID().toLowerCase().contains(search))
                    return true;
            }
            return mValue.getSubject().toLowerCase().contains(search);
        }

        @Override
        public int compareTo(TableItem o) {
            SortedSet<KonMessage> messages = this.mValue.getMessages().getAll();
            if (messages.isEmpty())
                return -1;
            SortedSet<KonMessage> oMessages = o.mValue.getMessages().getAll();
            if (oMessages.isEmpty())
                return 1;

            return -messages.last().getDate().compareTo(oMessages.last().getDate());
        }
    }

    private static String lastActivity(KonThread thread, boolean pretty) {
        SortedSet<KonMessage> messageSet = thread.getMessages().getAll();
        String lastActivity = messageSet.isEmpty() ? Tr.tr("no messages yet") :
                pretty ? Utils.PRETTY_TIME.format(messageSet.last().getDate()) :
                Utils.MID_DATE_FORMAT.format(messageSet.last().getDate());

        return Tr.tr("Last activity")+": " + lastActivity;
    }
}

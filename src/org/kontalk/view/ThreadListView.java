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

import com.alee.extended.list.WebCheckBoxList;
import com.alee.extended.panel.GroupPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.text.WebTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.JDialog;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.apache.commons.lang.StringUtils;
import org.kontalk.KonConf;
import org.kontalk.model.KonMessage;
import org.kontalk.model.KonThread;
import org.kontalk.model.ThreadList;
import org.kontalk.model.User;
import org.kontalk.model.UserList;
import static org.kontalk.view.ListView.TOOLTIP_DATE_FORMAT;

/**
 * Show a brief list of all threads.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
class ThreadListView extends ListView implements Observer {

    private final ThreadList mThreadList;
    private final WebPopupMenu mPopupMenu;

    ThreadListView(final View modelView, ThreadList threadList) {
        mThreadList = threadList;
        mThreadList.addObserver(this);

        this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // right click popup menu
        mPopupMenu = new WebPopupMenu();
        WebMenuItem editMenuItem = new WebMenuItem("Edit Thread");
        editMenuItem.setToolTipText("Edit this thread");
        editMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                ThreadItemView t = (ThreadItemView) mListModel.get(getSelectedIndex());
                JDialog editUserDialog = new EditThreadDialog(t);
                editUserDialog.setVisible(true);
            }
        });
        mPopupMenu.add(editMenuItem);

        WebMenuItem deleteMenuItem = new WebMenuItem("Delete Thread");
        deleteMenuItem.setToolTipText("Delete this thread");
        deleteMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                String warningText = "Permanently delete all messages in this thread?";
                int selectedOption = WebOptionPane.showConfirmDialog(ThreadListView.this,
                        warningText,
                        "Please Confirm",
                        WebOptionPane.OK_CANCEL_OPTION,
                        WebOptionPane.WARNING_MESSAGE);
                if (selectedOption == WebOptionPane.OK_OPTION) {
                    ThreadItemView threadView = (ThreadItemView) mListModel.get(getSelectedIndex());
                    mThreadList.deleteThreadWithID(threadView.getThread().getID());
                }
            }
        });
        mPopupMenu.add(deleteMenuItem);

        // actions triggered by selection
        this.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting())
                    return;
                modelView.selectedThreadChanged(getSelectedThread());
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
                    setSelectedIndex(locationToIndex(e.getPoint()));
                    showPopupMenu(e);
                }
            }
        });
    }

    void selectThread(int threadID) {
        Enumeration e = mListModel.elements();
        for(Enumeration<ThreadItemView> threads = e; e.hasMoreElements();) {
            ThreadItemView threadView = threads.nextElement();
            if (threadView.getThread().getID() == threadID)
                this.setSelectedValue(threadView);
        }
    }

    KonThread getSelectedThread() {
        if (this.getSelectedIndex() == -1)
            return null;
        ThreadItemView t = (ThreadItemView) mListModel.get(this.getSelectedIndex());
        return t.getThread();
    }

    @Override
    public void update(Observable o, Object arg) {
        // TODO
        mListModel.clear();
        for (KonThread thread: mThreadList.getThreads()) {
            ThreadItemView newThreadView = new ThreadItemView(thread);
            mListModel.addElement(newThreadView);
        }
    }

    void selectLastThread() {
        int i = KonConf.getInstance().getInt(KonConf.VIEW_SELECTED_THREAD);
        if (i >= 0)
            this.setSelectedIndex(i);
    }

    void save() {
        KonConf.getInstance().setProperty(
                KonConf.VIEW_SELECTED_THREAD,
                this.getSelectedIndex());
    }

    private void showPopupMenu(MouseEvent e) {
           mPopupMenu.show(this, e.getX(), e.getY());
    }

    private class ThreadItemView extends ListItem implements Observer {

        private final KonThread mThread;
        WebLabel mSubjectLabel;
        WebLabel mUserLabel;
        private Color mBackround;

        ThreadItemView(KonThread thread) {
            mThread = thread;

            mThread.addObserver(this);

            this.setMargin(5);
            this.setLayout(new BorderLayout(10, 5));

            this.add(new WebLabel(Integer.toString(thread.getID())), BorderLayout.WEST);

            mSubjectLabel = new WebLabel();
            mSubjectLabel.setFontSize(14);
            // if too long, draw three dots at the end
            Dimension size = mSubjectLabel.getPreferredSize();
            mSubjectLabel.setMinimumSize(size);
            mSubjectLabel.setPreferredSize(size);
            this.add(mSubjectLabel, BorderLayout.CENTER);

            mUserLabel = new WebLabel();
            mUserLabel.setForeground(Color.GRAY);
            mUserLabel.setFontSize(11);
            this.add(mUserLabel, BorderLayout.SOUTH);

            this.update();

            this.setBackground(mBackround);
        }

        KonThread getThread() {
            return mThread;
        }

        @Override
        void repaint(boolean isSelected) {
            if (isSelected)
                this.setBackground(View.BLUE);
            else
                this.setBackground(mBackround);
        }

        @Override
        String getTooltipText() {
            TreeSet<KonMessage> messageSet = mThread.getMessages();
            String lastActivity = messageSet.isEmpty() ? "no messages yet" :
                        TOOLTIP_DATE_FORMAT.format(messageSet.last().getDate());

            String html = "<html><body>" +
                    "<br>" +
                    "Last activity: " + lastActivity + "<br>" +
                    "";
            return html;
        }

        @Override
        public void update(Observable o, Object arg) {
            this.update();
            // need to repaint parent to see changes
            ThreadListView.this.repaint();
            // TODO maybe this is better
            //ThreadListView.this.mListModel.update(this);
        }

        private void update() {
            mBackround = !mThread.isRead() ? View.LIGHT_BLUE : Color.WHITE;

            String subject = mThread.getSubject() != null ? mThread.getSubject(): "<unnamed>";
            mSubjectLabel.setText(subject);

            List<String> nameList = new ArrayList(mThread.getUser().size());
            for (User user : mThread.getUser())
                nameList.add(user.getName() == null ? "<unknown>" : user.getName());
            mUserLabel.setText(StringUtils.join(nameList, ", "));
        }

        @Override
        protected boolean contains(String search) {
            for (User user: mThread.getUser()) {
                if (user.getName().toLowerCase().contains(search) ||
                        user.getJID().toLowerCase().contains(search))
                    return true;
            }
            if (mThread.getSubject() != null)
                return mThread.getSubject().toLowerCase().contains(search);
            else
                return false;
        }
    }

    private class EditThreadDialog extends WebDialog {

        private final ThreadItemView mThreadView;
        private final WebTextField mSubjectField;
        WebCheckBoxList mParticipantsList;

        public EditThreadDialog(ThreadItemView threadView) {

            mThreadView = threadView;

            this.setTitle("Edit Thread");
            this.setResizable(false);
            this.setModal(true);

            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            // editable fields
            groupPanel.add(new WebLabel("Subject:"));
            mSubjectField = new WebTextField(mThreadView.getThread().getSubject(), 22);
            mSubjectField.setInputPrompt(mThreadView.getThread().getSubject());
            mSubjectField.setHideInputPromptOnFocus(false);
            groupPanel.add(mSubjectField);
            groupPanel.add(new WebSeparator(true, true));

            groupPanel.add(new WebLabel("Participants:"));
            mParticipantsList = new WebCheckBoxList();
            mParticipantsList.setVisibleRowCount(10);
            for (User oneUser : UserList.getInstance().getUser()) {
                boolean selected = threadView.getThread().getUser().contains(oneUser);
                mParticipantsList.getCheckBoxListModel().addCheckBoxElement(oneUser, selected);
            }
            final WebButton saveButton = new WebButton("Save");
            mParticipantsList.getModel().addListDataListener(new ListDataListener() {
                @Override
                public void intervalAdded(ListDataEvent e) {
                }
                @Override
                public void intervalRemoved(ListDataEvent e) {
                }
                @Override
                public void contentsChanged(ListDataEvent e) {
                    saveButton.setEnabled(!mParticipantsList.getCheckedValues().isEmpty());
                }
            });

            groupPanel.add(new WebScrollPane(mParticipantsList));
            groupPanel.add(new WebSeparator(true, true));

            this.add(groupPanel, BorderLayout.CENTER);

            // buttons
            WebButton cancelButton = new WebButton("Cancel");
            cancelButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    EditThreadDialog.this.dispose();
                }
            });

            saveButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (mParticipantsList.getCheckedValues().size() > 1) {
                        String infoText = "More than one receiver not supported (yet).";
                        WebOptionPane.showMessageDialog(ThreadListView.this,
                                infoText,
                                "Sorry",
                                WebOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    EditThreadDialog.this.saveThread();
                    EditThreadDialog.this.dispose();
                }
            });

            GroupPanel buttonPanel = new GroupPanel(2, cancelButton, saveButton);
            buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
            this.add(buttonPanel, BorderLayout.SOUTH);

            this.pack();
        }

        private void saveThread() {
            if (!mSubjectField.getText().isEmpty()) {
                mThreadView.getThread().setSubject(mSubjectField.getText());
            }
            List participants = mParticipantsList.getCheckedValues();
            Set<User> threadUser = new HashSet();
            for (Object o: participants) {
                threadUser.add((User) o);
            }
            mThreadView.getThread().setUser(threadUser);
        }
    }
}

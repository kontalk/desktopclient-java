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
import com.alee.laf.button.WebButton;
import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.rootpane.WebDialog;
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
import java.util.List;
import java.util.TreeSet;
import javax.swing.JDialog;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.apache.commons.lang.StringUtils;
import org.kontalk.KonConf;
import org.kontalk.model.KonMessage;
import org.kontalk.model.KonThread;
import org.kontalk.model.ThreadList;
import org.kontalk.model.User;
import static org.kontalk.view.ListView.TOOLTIP_DATE_FORMAT;

/**
 * Show a brief list of all threads.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class ThreadListView extends ListView implements ChangeListener {

    private final ThreadList mThreadList;
    private final WebPopupMenu mPopupMenu;

    ThreadListView(final View modelView, ThreadList threadList) {
        mThreadList = threadList;
        mThreadList.addListener(this);

        this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // right click popup menu
        mPopupMenu = new WebPopupMenu();
        WebMenuItem editMenuItem = new WebMenuItem("Edit Thread");
        editMenuItem.setToolTipText("Edit this thread");
        editMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                ThreadView t = (ThreadView) mListModel.get(getSelectedIndex());
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
                // TODO
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

    void selectThread(int threadID){
        Enumeration e = mListModel.elements();
        for(Enumeration<ThreadView> threads = e; e.hasMoreElements();){
            ThreadView threadView = threads.nextElement();
            if (threadView.getThread().getID() == threadID)
                this.setSelectedValue(threadView);
        }
    }

    KonThread getSelectedThread() {
        if (this.getSelectedIndex() == -1)
            return null;
        ThreadView t = (ThreadView) mListModel.get(this.getSelectedIndex());
        return t.getThread();
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        mListModel.clear();
        for (KonThread thread: mThreadList.getThreads()) {
            ThreadView newThreadView = new ThreadView(thread);
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

    private class ThreadView extends ListItem {

        private final KonThread mThread;
        private final WebLabel mSubjectLabel;
        private final WebLabel mUserLabel;

        ThreadView(KonThread thread) {
            mThread = thread;

            this.setMargin(5);
            this.setLayout(new BorderLayout(10, 5));

            this.add(new WebLabel(Integer.toString(thread.getID())), BorderLayout.WEST);
            mSubjectLabel = new WebLabel();
            mSubjectLabel.setFontSize(14);
            this.add(mSubjectLabel, BorderLayout.CENTER);

            mUserLabel = new WebLabel();
            mUserLabel.setForeground(Color.GRAY);
            mUserLabel.setFontSize(11);
            this.add(mUserLabel, BorderLayout.SOUTH);

            updateView();
        }

        KonThread getThread() {
            return mThread;
        }

        @Override
        void repaint(boolean isSelected){
            if (isSelected)
                this.setBackground(View.BLUE);
            else
                this.setBackground(Color.WHITE);
        }

        private void updateView() {
           // if too long, draw three dots at the end
           Dimension size = mSubjectLabel.getPreferredSize();
           mSubjectLabel.setMinimumSize(size);
           mSubjectLabel.setPreferredSize(size);

           String subject = mThread.getSubject() != null ? mThread.getSubject(): "<unnamed>";
           mSubjectLabel.setText(subject);

           List<String> nameList = new ArrayList(mThread.getUser().size());
           for (User user : mThread.getUser())
               nameList.add(user.getName() == null ? "<unknown>" : user.getName());
           mUserLabel.setText(StringUtils.join(nameList, ", "));
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
    }

    private class EditThreadDialog extends WebDialog {

        private final ThreadView mThreadView;
        private final WebTextField mSubjectField;

        public EditThreadDialog(ThreadView threadView) {

            mThreadView = threadView;

            this.setTitle("Edit Thread");
            this.setResizable(false);
            this.setModal(true);

            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            // editable fields
            groupPanel.add(new WebLabel("Subject:"));
            mSubjectField = new WebTextField(mThreadView.getThread().getSubject(), 16);
            mSubjectField.setInputPrompt(mThreadView.getThread().getSubject());
            mSubjectField.setHideInputPromptOnFocus(false);
            groupPanel.add(mSubjectField);
            groupPanel.add(new WebSeparator(true, true));

            groupPanel.add(new WebLabel("Add User:"));
            // TODO
            groupPanel.add(new WebSeparator(true, true));

            groupPanel.add(new WebLabel("Remove User:"));
            // TODO
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
            final WebButton saveButton = new WebButton("Save");
            saveButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
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
            mThreadView.updateView();
        }
    }

}

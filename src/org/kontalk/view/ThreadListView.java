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
import com.alee.extended.panel.GroupingType;
import com.alee.laf.button.WebButton;
import com.alee.laf.colorchooser.WebColorChooserDialog;
import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.text.WebTextField;
import com.alee.utils.swing.DialogOptions;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.SortedSet;
import javax.swing.JDialog;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.apache.commons.lang.StringUtils;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.system.KonConf;
import org.kontalk.model.KonMessage;
import org.kontalk.model.KonThread;
import org.kontalk.model.ThreadList;
import org.kontalk.model.User;
import org.kontalk.model.UserList;
import org.kontalk.util.Tr;
import static org.kontalk.view.ListView.TOOLTIP_DATE_FORMAT;
import org.kontalk.view.ThreadListView.ThreadItem;

/**
 * Show a brief list of all threads.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
final class ThreadListView extends ListView<ThreadItem, KonThread> {

    private final static Color DEFAULT_BG = Color.WHITE;

    private final ThreadList mThreadList;
    private final WebPopupMenu mPopupMenu;

    ThreadListView(final View view, ThreadList threadList) {
        mThreadList = threadList;

        this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // right click popup menu
        mPopupMenu = new WebPopupMenu();
        WebMenuItem editMenuItem = new WebMenuItem(Tr.tr("Edit Thread"));
        editMenuItem.setToolTipText(Tr.tr("Edit this thread"));
        editMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                ThreadItem t = ThreadListView.this.getSelectedListItem();
                JDialog editUserDialog = new EditThreadDialog(t);
                editUserDialog.setVisible(true);
            }
        });
        mPopupMenu.add(editMenuItem);

        WebMenuItem deleteMenuItem = new WebMenuItem(Tr.tr("Delete Thread"));
        deleteMenuItem.setToolTipText(Tr.tr("Delete this thread"));
        deleteMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                String warningText =Tr.tr( "Permanently delete all messages in this thread?");
                int selectedOption = WebOptionPane.showConfirmDialog(ThreadListView.this,
                        warningText,
                        Tr.tr("Please Confirm"),
                        WebOptionPane.OK_CANCEL_OPTION,
                        WebOptionPane.WARNING_MESSAGE);
                if (selectedOption == WebOptionPane.OK_OPTION) {
                    ThreadItem threadView = ThreadListView.this.getSelectedListItem();
                    mThreadList.deleteThreadWithID(threadView.getValue().getID());
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
                view.selectedThreadChanged(ThreadListView.this.getSelectedListValue());
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
                    ThreadListView.this.setSelectedIndex(
                            ThreadListView.this.locationToIndex(e.getPoint()));
                    ThreadListView.this.showPopupMenu(e);
                }
            }
        });

        this.updateOnEDT();

        mThreadList.addObserver(this);
    }

    @Override
    protected void updateOnEDT() {
        // TODO, performance
        KonThread currentThread = this.getSelectedListValue();
        this.clearModel();
        for (KonThread thread: mThreadList.getThreads()) {
            ThreadItem newThreadView = new ThreadItem(thread);
            this.addItem(newThreadView);
        }
        // reselect thread
        if (currentThread != null)
            this.selectItem(currentThread);
    }

    void selectLastThread() {
        int i = KonConf.getInstance().getInt(KonConf.VIEW_SELECTED_THREAD);
        if (i < 0) i = 0;
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

    protected class ThreadItem extends ListView<ThreadItem, KonThread>.ListItem implements Observer {

        WebLabel mSubjectLabel;
        WebLabel mUserLabel;
        private Color mBackround;

        ThreadItem(KonThread thread) {
            super(thread);

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

            mValue.addObserver(this);
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
            SortedSet<KonMessage> messageSet = this.getValue().getMessages();
            String lastActivity = messageSet.isEmpty() ? Tr.tr("no messages yet") :
                        TOOLTIP_DATE_FORMAT.format(messageSet.last().getDate());

            String html = "<html><body>" +
                    "<br>" +
                    Tr.tr("Last activity")+": " + lastActivity + "<br>" +
                    "";
            return html;
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
                    ThreadItem.this.updateOnEDT();
                }
            });
        }

        private void updateOnEDT() {
            this.update();
            // needed for background repaint
            ThreadListView.this.repaint();
        }

        private void update() {
            mBackround = !mValue.isRead() ? View.LIGHT_BLUE : Color.WHITE;
            String subject = mValue.getSubject();
            if (subject.isEmpty()) subject = Tr.tr("<unnamed>");
            mSubjectLabel.setText(subject);

            List<String> nameList = new ArrayList<>(mValue.getUser().size());
            for (User user : mValue.getUser())
                nameList.add(user.getName().isEmpty() ? Tr.tr("<unknown>") : user.getName());
            mUserLabel.setText(StringUtils.join(nameList, ", "));
        }

        @Override
        protected boolean contains(String search) {
            for (User user: mValue.getUser()) {
                if (user.getName().toLowerCase().contains(search) ||
                        user.getJID().toLowerCase().contains(search))
                    return true;
            }
            return mValue.getSubject().toLowerCase().contains(search);
        }
    }

    private class EditThreadDialog extends WebDialog {

        private final ThreadItem mThreadView;
        private final WebTextField mSubjectField;
        private final WebButton mColorChooserButton;
        private final WebColorChooserDialog mColorChooser;
        WebCheckBoxList mParticipantsList;

        EditThreadDialog(ThreadItem threadView) {

            mThreadView = threadView;

            this.setTitle(Tr.tr("Edit Thread"));
            this.setResizable(false);
            this.setModal(true);

            GroupPanel groupPanel = new GroupPanel(10, false);
            groupPanel.setMargin(5);

            // editable fields
            groupPanel.add(new WebLabel(Tr.tr("Subject:")));
            String subj = mThreadView.getValue().getSubject();
            mSubjectField = new WebTextField(subj, 22);
            mSubjectField.setInputPrompt(subj);
            mSubjectField.setHideInputPromptOnFocus(false);
            groupPanel.add(mSubjectField);
            groupPanel.add(new WebSeparator(true, true));

            mColorChooserButton = new WebButton();
            mColorChooserButton.setMinimumHeight(25);
            Color oldColor =
                    mThreadView.getValue().getViewSettings().getBGColor().orElse(DEFAULT_BG);
            mColorChooserButton.setBottomBgColor(oldColor);
            mColorChooserButton.addActionListener(new ActionListener () {
                @Override
                public void actionPerformed(ActionEvent e ) {
                    EditThreadDialog.this.editColor();
                }
            } );
            mColorChooser = new WebColorChooserDialog(this);
            mColorChooser.setColor(oldColor);
            groupPanel.add(new GroupPanel(GroupingType.fillLast,
                    new WebLabel(Tr.tr("Color:")+" "),
                    mColorChooserButton));
            groupPanel.add(new WebSeparator());

            groupPanel.add(new WebLabel(Tr.tr("Participants:")));
            mParticipantsList = new WebCheckBoxList();
            mParticipantsList.setVisibleRowCount(10);
            for (User oneUser : UserList.getInstance().getAll()) {
                boolean selected = threadView.getValue().getUser().contains(oneUser);
                mParticipantsList.getCheckBoxListModel().addCheckBoxElement(
                        new UserElement(oneUser),
                        selected);
            }
            final WebButton saveButton = new WebButton(Tr.tr("Save"));
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
            WebButton cancelButton = new WebButton(Tr.tr("Cancel"));
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
                        String infoText = Tr.tr("More than one receiver not supported (yet).");
                        WebOptionPane.showMessageDialog(ThreadListView.this,
                                infoText,
                                Tr.tr("Sorry"),
                                WebOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    EditThreadDialog.this.saveThread();
                    EditThreadDialog.this.dispose();
                }
            });
            this.getRootPane().setDefaultButton(saveButton);

            GroupPanel buttonPanel = new GroupPanel(2, cancelButton, saveButton);
            buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
            this.add(buttonPanel, BorderLayout.SOUTH);

            this.pack();
        }

        private void editColor() {
            mColorChooser.setVisible(true);
            if (mColorChooser.getResult () == DialogOptions.OK_OPTION) {
                mColorChooserButton.setBottomBgColor(mColorChooser.getColor());
            }
        }

        private void saveThread() {
            if (!mSubjectField.getText().isEmpty()) {
                mThreadView.getValue().setSubject(mSubjectField.getText());
            }
            List<?> participants = mParticipantsList.getCheckedValues();
            Set<User> threadUser = new HashSet<>();
            for (Object o: participants) {
                threadUser.add(((UserElement) o).user);
            }
            mThreadView.getValue().setUser(threadUser);
            if (!mColorChooser.getColor().equals(DEFAULT_BG))
                mThreadView.getValue().getViewSettings().setBGColor(mColorChooser.getColor());
        }

        private class UserElement {
            User user;

            UserElement(User user) {
                this.user = user;
            }

            @Override
            public String toString() {
                String jid = user.getJID();
                if (jid.length() > 25) {
                    String local = shorten(XmppStringUtils.parseLocalpart(jid), 16);
                    String domain = shorten(XmppStringUtils.parseDomain(jid), 24);
                    jid = "<" +XmppStringUtils.completeJidFrom(local, domain) + ">";
                }
                String name = shorten(user.getName(), 24);
                return name.isEmpty() ? jid : name +" "+jid;
            }
        }
    }

    private static String shorten(String s, int max_length) {
        if (max_length < 6) max_length = 6;
        return s.length() >= max_length ? s.substring(0, max_length / 2) + "..." : s;
    }
}

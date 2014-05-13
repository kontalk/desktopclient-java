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

import com.alee.laf.label.WebLabel;
import com.alee.laf.list.WebList;
import com.alee.laf.panel.WebPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.Enumeration;
import java.util.logging.Logger;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.kontalk.model.KontalkThread;
import org.kontalk.model.ThreadList;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class ThreadListView extends WebList {
    private final static Logger LOGGER = Logger.getLogger(ThreadListView.class.getName());
    
    private final DefaultListModel<ThreadView> mListModel = new DefaultListModel();
    
    ThreadListView(final View modelView) {
        //super();
        
        this.setModel(mListModel);
        this.setCellRenderer(new ThreadListRenderer());
        
        //this.setPreferredWidth(150);
        this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        this.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting())
                    return;
                modelView.selectedThreadChanged(getSelectedThreadID());
            }
        });
    }
    
    void selectThread(int threadID){
        Enumeration e = mListModel.elements();
        for(Enumeration<ThreadView> threads = e; e.hasMoreElements();){
            ThreadView threadView = threads.nextElement();
            if (threadView.getID() == threadID)
                this.setSelectedValue(threadView);
        }
    }

    void modelChanged(ThreadList threads) {
        mListModel.clear();
        for (KontalkThread thread: threads.values()) {
            ThreadView newThreadView = new ThreadView(thread.getID(), thread.getXMPPID(), thread.getSubject());
            mListModel.addElement(newThreadView);
        }
    }
    
    int getSelectedThreadID() {
        if (this.getSelectedIndex() == -1)
            return -1;
        return mListModel.get(this.getSelectedIndex()).mID;
    }
    
    private class ThreadView extends WebPanel{
        
        private final int mID;
        private final String mXMPPID;
        private final String mSubject;
     
        ThreadView(int id, String xmppID, String subject) {
            mID = id;
            mXMPPID = xmppID;
            mSubject = subject;
            
            this.setMargin(5);
            
            this.add(new WebLabel(mXMPPID), BorderLayout.NORTH);
            this.add(new WebLabel(Integer.toString(mID)), BorderLayout.WEST);
            this.add(new WebLabel(mSubject), BorderLayout.CENTER);
        }
        
        int getID(){
            return mID;
        }
        
        void paintSelected(boolean isSelected){
            if (isSelected) 
                this.setBackground(View.BLUE);
            else
                this.setBackground(Color.WHITE);
        }

    }
    
    private class ThreadListRenderer extends DefaultListCellRenderer {
        
        @Override
        public Component getListCellRendererComponent(JList list,
                                                Object value,
                                                int index,
                                                boolean isSelected,
                                                boolean hasFocus) {
            if (value instanceof ThreadView) {
                ThreadView threadView = (ThreadView) value;
                threadView.paintSelected(isSelected);
                return threadView;
            } else {
                return new WebPanel(new WebLabel("ERRROR"));
            }
        }
    }

}

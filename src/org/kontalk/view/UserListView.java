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
import java.util.logging.Logger;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.kontalk.model.User;
import org.kontalk.model.UserList;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class UserListView extends WebList {
    private final static Logger LOGGER = Logger.getLogger(UserListView.class.getName());
    
    private final DefaultListModel<UserView> mListModel = new DefaultListModel();
    
    UserListView(final View modelView) {
        super();
        
        this.setModel(mListModel);
        this.setCellRenderer(new UserListRenderer());
        
        //this.setPreferredWidth(150);
        this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        this.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting())
                    return;
                modelView.selectedUserChanged(getSelectedUserID());
            }
        });
    }

    void modelChanged(UserList user) {
        mListModel.clear();
        for (User oneUser: user.values())
            mListModel.addElement(new UserView(oneUser.getID(), oneUser.getJID()));
    }
    
    int getSelectedUserID() {
        if (getSelectedIndex() == -1)
            return -1;
        return mListModel.get(getSelectedIndex()).getID();
    }
    
    private class UserView extends WebPanel{
        
        private final int mID;
        private final String mJID;
     
        UserView(int id, String jid) {
            mID = id;
            mJID = jid;
            
            //this.setPaintFocus(true);
            this.setMargin(5);
            
            this.add(new WebLabel(Integer.toString(mID)), BorderLayout.NORTH);
            this.add(new WebLabel(mJID), BorderLayout.CENTER);
        }

        int getID() {
            return mID;
        }
        
        void paintSelected(boolean isSelected){
            if (isSelected) 
                this.setBackground(View.BLUE);
            else
                this.setBackground(Color.WHITE);
        }
        
    }
    
    private class UserListRenderer extends DefaultListCellRenderer {
        
        @Override
        public Component getListCellRendererComponent(JList list,
                                                Object value,
                                                int index,
                                                boolean isSelected,
                                                boolean hasFocus) {
            if (value instanceof UserView) {
                UserView userView = (UserView) value;
                userView.paintSelected(isSelected);
                return userView;
            } else {
                return new WebPanel(new WebLabel("ERRROR"));
            }
        }
    }

}

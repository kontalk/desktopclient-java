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

import com.alee.laf.list.WebList;
import com.alee.laf.list.WebListCellRenderer;
import com.alee.laf.list.WebListModel;
import com.alee.laf.panel.WebPanel;
import com.alee.managers.tooltip.TooltipManager;
import com.alee.managers.tooltip.TooltipWay;
import com.alee.managers.tooltip.WebCustomTooltip;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import javax.swing.JList;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

/**
 * A generic list view for subclassing.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
class ListView extends WebList {

    protected final WebListModel<ListItem> mListModel = new WebListModel<>();

    private final WebListModel<ListItem> mFilteredListModel = new WebListModel<>();

    final static SimpleDateFormat TOOLTIP_DATE_FORMAT =
            new SimpleDateFormat("EEE, MMM d yyyy, HH:mm");

    private WebCustomTooltip mTip = null;

    // using legacy lib, raw types extend Object
    @SuppressWarnings("unchecked")
    ListView() {
        mListModel.addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
                ListView.this.resetFiltering();
            }
            @Override
            public void intervalRemoved(ListDataEvent e) {
                ListView.this.resetFiltering();
            }
            @Override
            public void contentsChanged(ListDataEvent e) {
                ListView.this.resetFiltering();
            }
        });

        this.setModel(mFilteredListModel);

        this.setCellRenderer(new ListRenderer());

        // actions triggered by mouse events
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                if (mTip != null)
                    mTip.closeTooltip();
            }
        });
    }

    void filter(String search) {
        mFilteredListModel.clear();
        for (ListItem listItem : mListModel.getElements()) {
            if (listItem.contains(search.toLowerCase()))
                mFilteredListModel.addElement(listItem);
        }
    }

    private void resetFiltering() {
        mFilteredListModel.setElements(mListModel.getElements());
    }

    private void showTooltip(ListItem messageView) {
        if (mTip != null)
            mTip.closeTooltip();

        WebCustomTooltip tip = TooltipManager.showOneTimeTooltip(this,
                this.getMousePosition(),
                messageView.getTooltipText(),
                TooltipWay.down);
        mTip = tip;
    }

    public abstract class ListItem extends WebPanel {

        void resize(int listWidth) {};

        void repaint(boolean isSelected) {};

        abstract String getTooltipText();

        protected abstract boolean contains(String search);

        // catch the event, when a tooltip should be shown for this item and
        // create a own one
        @Override
        public String getToolTipText(MouseEvent event) {
            ListView.this.showTooltip(this);
            return null;
        }
    }

    private class ListRenderer extends WebListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList list,
                                                Object value,
                                                int index,
                                                boolean isSelected,
                                                boolean hasFocus) {
            ListItem panel = (ListItem) value;
            panel.resize(list.getWidth());
            panel.repaint(isSelected);
            return panel;
        }
    }
}

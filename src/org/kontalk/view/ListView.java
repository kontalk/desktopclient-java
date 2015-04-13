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
import java.util.Observable;
import java.util.Observer;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.kontalk.view.ListView.ListItem;
import org.ocpsoft.prettytime.PrettyTime;

/**
 * A generic list view for subclassing.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 * @param <I> the view item in this list
 * @param <V> the value of one view item
 */
abstract class ListView<I extends ListView<I, V>.ListItem, V> extends WebList implements Observer {

    private final WebListModel<I> mListModel = new WebListModel<>();

    private final WebListModel<I> mFilteredListModel = new WebListModel<>();

    final static PrettyTime TOOLTIP_DATE_FORMAT = new PrettyTime();

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

        // TODO JLists in Swing don't forward mouse events to their items, we
        // need to do this manually here
    }

    protected void clearModel() {
        mListModel.clear();
    }

    protected I getSelectedListItem() {
        return mListModel.get(this.getSelectedIndex());
    }

    // nullable
    protected V getSelectedListValue() {
        if (this.getSelectedIndex() == -1)
            return null;
        ListItem listItem = this.getSelectedListItem();
        return listItem.getValue();
    }

    protected void addItem(I newItem) {
        mListModel.addElement(newItem);
    }

    void filter(String search) {
        mFilteredListModel.clear();
        for (I listItem : mListModel.getElements()) {
            if (listItem.contains(search.toLowerCase()))
                mFilteredListModel.addElement(listItem);
        }
    }

    void selectItem(V value) {
        // TODO performance
        for (I listItem: mListModel.getElements()) {
            if (listItem.getValue() == value) {
                this.setSelectedValue(listItem);
            }
        }

        if (this.getSelectedListValue() != value) {
            // fallback
            this.setSelectedIndex(0);
        }
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
                ListView.this.updateOnEDT();
            }
        });
    }

    abstract protected void updateOnEDT();

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

    abstract class ListItem extends WebPanel implements Observer {

        protected final V mValue;

        protected ListItem(V value) {
            mValue = value;
        }

        V getValue() {
            return mValue;
        };

        void resize(int listWidth) {};

        void repaint(boolean isSelected) {};

        abstract String getTooltipText();

        protected abstract boolean contains(String search);

        @Override
        public void update(Observable o, Object arg) {
            if (SwingUtilities.isEventDispatchThread()) {
                this.updateOnEDT();
                return;
            }
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    ListItem.this.updateOnEDT();
                }
            });
        }

        protected abstract void updateOnEDT();

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
        @SuppressWarnings("rawtypes")
        public Component getListCellRendererComponent(JList list,
                                                Object value,
                                                int index,
                                                boolean isSelected,
                                                boolean hasFocus) {
            ListView<?, ?>.ListItem panel = (ListView<?, ?>.ListItem) value;
            panel.resize(list.getWidth());
            panel.repaint(isSelected);
            return panel;
        }
    }
}

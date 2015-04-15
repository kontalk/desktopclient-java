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

import com.alee.laf.panel.WebPanel;
import com.alee.laf.table.WebTable;
import com.alee.laf.table.renderers.WebTableCellRenderer;
import com.alee.managers.tooltip.TooltipManager;
import com.alee.managers.tooltip.TooltipWay;
import com.alee.managers.tooltip.WebCustomTooltip;
import java.awt.Component;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.Collection;
import java.util.Observable;
import java.util.Observer;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.ocpsoft.prettytime.PrettyTime;

/**
 * A generic list view for subclassing.
 * Implemented as table with one column.
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 * @param <I> the view item in this list
 * @param <V> the value of one view item
 */
abstract class TableView<I extends TableView<I, V>.TableItem, V extends Observable> extends WebTable implements Observer {

    /** The items in this list . */
    private final SortedMap<V, I> mItems = new TreeMap<>();
    /** The currently displayed items. A subset of mItems */
    private final DefaultTableModel mFilteredTableModel = new DefaultTableModel(0, 1);

    /** The current search string. */
    private String mSearch = "";

    protected final static PrettyTime TOOLTIP_DATE_FORMAT = new PrettyTime();

    private WebCustomTooltip mTip = null;

    // using legacy lib, raw types extend Object
    @SuppressWarnings("unchecked")
    TableView() {
        this.setModel(mFilteredTableModel);

        // hide header
        this.setTableHeader(null);

        // hide grid
        this.setShowGrid(false);

        // use custom renderer
        this.setDefaultRenderer(TableItem.class, new TableRenderer());

        this.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    // this table was resized, the size of each item might have
                    // changed and each row height must be adjusted
                    // TODO efficient?
                    TableView<?, ?> table = TableView.this;
                    for (int row = 0; row < table.getRowCount(); row++) {
                        table.setHeight(row);
                    }
                }
            });

        // trigger editing to forward mouse events
        this.addMouseMotionListener(new MouseMotionListener() {
                @Override
                public void mouseDragged(MouseEvent e) {
                }
                @Override
                public void mouseMoved(MouseEvent e) {
                    int row = TableView.this.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        TableView.this.editCellAt(row, 0);
                    }
                }
        });

        // actions triggered by mouse events
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                if (mTip != null)
                    mTip.closeTooltip();
            }
        });
    }

    protected Collection<I> getItems() {
        return mItems.values();
    }

    protected boolean containsValue(V value) {
        return mItems.containsKey(value);
    }

    protected void addItem(I item) {
        item.mValue.addObserver(item);
        mItems.put(item.mValue, item);
        if (item.contains(mSearch.toLowerCase()))
            mFilteredTableModel.addRow(new Object[]{item});
    }

    @SuppressWarnings("unchecked")
    protected I getDisplayedItemAt(int i) {
        return (I) mFilteredTableModel.getValueAt(i, 0);
    }

    protected void clearItems() {
        for (TableItem i : this.getItems()) {
            i.mValue.deleteObserver(i);
        }
        mItems.clear();
        this.filterItems("");
    }

    @SuppressWarnings("unchecked")
    protected I getSelectedItem() {
        return (I) mFilteredTableModel.getValueAt(this.getSelectedRow(), 0);
    }

    // nullable
    protected V getSelectedValue() {
        if (this.getSelectedRow() == -1)
            return null;
        TableItem item = this.getSelectedItem();
        return item.mValue;
    }

    /** Resets filtering and selects the item containing the value specified. */
    void setSelectedItem(V value) {
        // TODO performance
        this.filterItems("");
        for (int i=0; i< mItems.size(); i++) {
            if (this.getDisplayedItemAt(i).mValue == value) {
                this.setSelectedItem(i);
                break;
            }
        }

        if (this.getSelectedValue() != value)
            // fallback
            this.setSelectedItem(0);
    }

    protected void setSelectedItem(int i) {
        if (i >= mFilteredTableModel.getRowCount())
            return;
        this.setSelectedRow(i);
    }

    void filterItems(String search) {
        mSearch = search;

        // TODO performance
        mFilteredTableModel.setRowCount(0);
        for (I item : this.getItems()) {
            if (item.contains(search.toLowerCase()))
                mFilteredTableModel.addRow(new Object[]{item});
        }
    }

    /**
     * Row height must be adjusted manually to component height.
     * source: https://stackoverflow.com/a/1784601
     * @param row the row that gets set
     */
    protected void setHeight(int row) {
        Component comp = this.prepareRenderer(this.getCellRenderer(row, 0), row, 0);
        int height = Math.max(this.getRowHeight(), comp.getPreferredSize().height);
        this.setRowHeight(row, height);
    }

    private void showTooltip(TableItem item) {
        if (mTip != null)
            mTip.closeTooltip();

        WebCustomTooltip tip = TooltipManager.showOneTimeTooltip(this,
                this.getMousePosition(),
                item.getTooltipText(),
                TooltipWay.down);
        mTip = tip;
    }

    // JTabel uses this to determine the renderer
    @Override
    public Class<?> getColumnClass(int column) {
        // always the same
        return TableItem.class;
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
                TableView.this.updateOnEDT(arg);
            }
        });
    }

    abstract protected void updateOnEDT(Object arg);

    abstract class TableItem extends WebPanel implements Observer {

        protected final V mValue;

        protected TableItem(V value) {
            mValue = value;
        }

        void resize(int listWidth) {};

        void repaint(boolean isSelected) {};

        protected abstract String getTooltipText();

        /**
         * Return if the content of the item contains the search string.
         * Used for filtering.
         */
        protected abstract boolean contains(String search);

        @Override
        public void update(Observable o, final Object arg) {
            if (SwingUtilities.isEventDispatchThread()) {
                this.updateOnEDT(arg);
                return;
            }
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    TableItem.this.updateOnEDT(arg);
                }
            });
        }

        protected abstract void updateOnEDT(Object arg);

        // catch the event, when a tooltip should be shown for this item and
        // create a own one
        // note: together with the cell renderer the tooltip can be added
        // directly the item, but the behaviour is buggy so we keep this
        @Override
        public String getToolTipText(MouseEvent event) {
            TableView.this.showTooltip(this);
            return null;
        }
    }

    private class TableRenderer extends WebTableCellRenderer {
        // return for each item (value) in the list/table the component to
        // render - which is the item itself here
        @Override
        @SuppressWarnings("unchecked")
        public Component getTableCellRendererComponent(JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column) {
            TableItem panel = (TableItem) value;
            // TODO do this here?
            panel.resize(table.getWidth());
            panel.repaint(isSelected);
            return panel;
        }
    }
}

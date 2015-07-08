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
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.RowFilter.Entry;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import org.ocpsoft.prettytime.PrettyTime;

/**
 * A generic list view for subclassing.
 * Implemented as table with one column.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 * @param <I> the view item in this list
 * @param <V> the value of one view item
 */
abstract class TableView<I extends TableView<I, V>.TableItem, V extends Observable & Comparable<V>> extends WebTable implements Observer {
    private final static Logger LOGGER = Logger.getLogger(TableView.class.getName());

    private final DefaultTableModel mModel;
    private final TableRowSorter<DefaultTableModel> mRowSorter;
    /** Map synced with model for faster access. */
    private final SortedMap<V, I> mItems = new TreeMap<>();

    /** The current search string. */
    private String mSearch = "";

    protected final static PrettyTime TOOLTIP_DATE_FORMAT = new PrettyTime();

    private WebCustomTooltip mTip = null;

    // using legacy lib, raw types extend Object
    @SuppressWarnings("unchecked")
    TableView() {
        // model
        mModel = new DefaultTableModel(0, 1) {
            // row sorter needs this
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return TableView.this.getColumnClass(columnIndex);
            }
        };
        this.setModel(mModel);

        // sorter
        mRowSorter = new TableRowSorter<>(mModel);
        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
        sortKeys.add(new RowSorter.SortKey(0, SortOrder.ASCENDING));
        mRowSorter.setSortKeys(sortKeys);
        mRowSorter.setSortsOnUpdates(true);
        mRowSorter.sort();
        // filter
        RowFilter<DefaultTableModel, Integer> rowFilter = new RowFilter<DefaultTableModel, Integer>() {
        @Override
        public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                I i = (I) entry.getValue(0);
                return i.contains(mSearch);
            }
        };
        mRowSorter.setRowFilter(rowFilter);
        this.setRowSorter(mRowSorter);

        // hide header
        this.setTableHeader(null);

        // hide grid
        this.setShowGrid(false);

        // use custom renderer
        this.setDefaultRenderer(TableItem.class, new TableRenderer());

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

    protected boolean containsValue(V value) {
        return mItems.containsKey(value);
    }

    @SuppressWarnings("unchecked")
    protected void sync(Set<V> values, Set<I> newItems) {
        // TODO performance
        // remove old
        for (int i=0; i < mModel.getRowCount(); i++) {
            I item = (I) mModel.getValueAt(i, 0);
            if (!values.contains(item.mValue)) {
                item.onRemove();
                item.mValue.deleteObserver(item);
                mModel.removeRow(i);
                i--;
            }
        }
        // add new
        for (I item : newItems) {
            item.mValue.addObserver(item);
            mItems.put(item.mValue, item);
            mModel.addRow(new Object[]{item});
        }
    }

    @SuppressWarnings("unchecked")
    protected I getDisplayedItemAt(int i) {
        return (I) mModel.getValueAt(mRowSorter.convertRowIndexToModel(i), 0);
    }

    protected void clearItems() {
        for (TableItem i : mItems.values()) {
            i.mValue.deleteObserver(i);
        }
        mModel.setRowCount(0);
        mItems.clear();
    }

    @SuppressWarnings("unchecked")
    protected I getSelectedItem() {
        return (I) mModel.getValueAt(mRowSorter.convertRowIndexToModel(this.getSelectedRow()), 0);
    }

    protected Optional<V> getSelectedValue() {
        if (this.getSelectedRow() == -1)
            return Optional.empty();
        return Optional.of(this.getSelectedItem().mValue);
    }

    /** Resets filtering and selects the item containing the value specified. */
    void setSelectedItem(V value) {
        // TODO performance
        this.filterItems("");
        for (int i=0; i< mModel.getRowCount(); i++) {
            if (this.getDisplayedItemAt(i).mValue == value) {
                this.setSelectedItem(i);
                break;
            }
        }

        if (this.getSelectedValue().orElse(null) != value)
            // fallback
            this.setSelectedItem(0);
    }

    protected void setSelectedItem(int i) {
        if (i >= mModel.getRowCount())
            return;
        this.setSelectedRow(i);
    }

    void filterItems(String search) {
        mSearch = search;
        mRowSorter.sort();
    }

    private void showTooltip(TableItem item) {
        String text = item.getTooltipText();
        if (text.isEmpty())
            return;

        // weblaf currently cant show tooltips for comps with table/list/...
        // renderer, we need to set the position ourself
        Point p = this.getMousePosition();
        if (p == null)
            return;
        Rectangle rec = this.getCellRect(this.rowAtPoint(p), 0, false);
        Point pos = new Point(rec.x + rec.width, rec.y + rec.height / 2);

        if (mTip != null && pos.equals(mTip.getDisplayLocation()) && mTip.isShowing())
            return;

        if (mTip != null)
            mTip.closeTooltip();

        // TODO temporary catching for tracing bug
        try {
            mTip = TooltipManager.showOneTimeTooltip(this, pos, text, TooltipWay.right);
        } catch (ArrayIndexOutOfBoundsException ex) {
            LOGGER.log(Level.WARNING, "can't show tooltip", ex);
            LOGGER.warning("this="+this+",pos="+pos+",text="+text);
        }
    }

    // JTabel uses this to determine the renderer
    @Override
    public Class<?> getColumnClass(int column) {
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

    abstract class TableItem extends WebPanel implements Observer, Comparable<TableItem> {

        protected final V mValue;

        protected TableItem(V value) {
            mValue = value;
        }

        /** Set internal properties before rendering this item. */
        abstract protected void render(int tableWidth, boolean isSelected);

        protected String getTooltipText() {
            return "";
        };

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
        // directly to the item, but the behaviour is buggy so we keep this
        @Override
        public String getToolTipText(MouseEvent event) {
            TableView.this.showTooltip(this);
            return null;
        }

        protected void onRemove() {};

        @Override
        public int compareTo(TableItem o) {
            return mValue.compareTo(o.mValue);
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
            TableItem item = (TableItem) value;

            item.render(table.getWidth(), isSelected);

            int height = Math.max(table.getRowHeight(), item.getPreferredSize().height);
            // view item needs a little more then it preferres
            height += 1;
            if (height != table.getRowHeight(row))
                // note: this calls resizeAndRepaint()
                table.setRowHeight(row, height);
            return item;
        }
    }
}

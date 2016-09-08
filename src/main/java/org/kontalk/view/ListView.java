/*
 *  Kontalk Java client
 *  Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>
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

import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.table.WebTable;
import com.alee.laf.table.renderers.WebTableCellRenderer;
import com.alee.managers.language.data.TooltipWay;
import com.alee.managers.tooltip.TooltipManager;
import com.alee.managers.tooltip.WebCustomTooltip;
import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.RowFilter.Entry;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

/**
 * A generic list view for subclassing.
 * Implemented as table with one column.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 * @param <I> the view item in this list
 * @param <V> the value of one view item
 */
abstract class ListView<I extends ListView<I, V>.TableItem, V extends Observable> extends WebTable implements Observer {
    private static final Logger LOGGER = Logger.getLogger(ListView.class.getName());

    protected final View mView;

    protected enum Change{
        TIMER
    };

    private final DefaultTableModel mModel;
    private final TableRowSorter<DefaultTableModel> mRowSorter;
    /** Map synced with model for faster access. */
    private final Map<V, I> mItems = new HashMap<>();

    private final Timer mTimer;

    /** The current search string. */
    private String mSearch = "";

    private WebCustomTooltip mTip = null;

    // using legacy lib, raw types extend Object
    @SuppressWarnings("unchecked")
    ListView(View view, boolean activateTimer) {
        mView = view;

        // model
        mModel = new DefaultTableModel(0, 1) {
            // row sorter needs this
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return ListView.this.getColumnClass(columnIndex);
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

        // grid
        this.setGridColor(Color.LIGHT_GRAY);
        this.setShowVerticalLines(false);

        // use custom renderer
        this.setDefaultRenderer(TableItem.class, new TableRenderer());

        // actions triggered by selection
        this.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting())
                    return;

                ListView.this.selectionChanged(ListView.this.getSelectedValue());
            }
        });

        // trigger editing to forward mouse events
        this.addMouseMotionListener(new MouseMotionListener() {
                @Override
                public void mouseDragged(MouseEvent e) {
                }
                @Override
                public void mouseMoved(MouseEvent e) {
                    int row = ListView.this.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        ListView.this.editCellAt(row, 0);
                    }
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
                    int row = ListView.this.rowAtPoint(e.getPoint());
                    ListView.this.setSelectedItem(row);
                    ListView.this.showPopupMenu(e, ListView.this.getSelectedItem());
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (mTip != null)
                    mTip.closeTooltip();
            }
        });

        // actions triggered by key events
        this.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_F2){
                    ListView.this.onRenameEvent();
                }
            }
        });

        if (activateTimer) {
            mTimer = new Timer();
            // update periodically items to be up-to-date with 'last seen' text
            TimerTask statusTask = new TimerTask() {
                        @Override
                        public void run() {
                            ListView.this.timerUpdate();
                        }
                    };
            long timerInterval = TimeUnit.SECONDS.toMillis(60);
            mTimer.schedule(statusTask, timerInterval, timerInterval);
        } else {
            mTimer = null;
        }
    }

    private void showPopupMenu(MouseEvent e, I item) {
        WebPopupMenu menu = this.rightClickMenu(item);
        menu.show(this, e.getX(), e.getY());
    }

    protected void selectionChanged(Optional<V> value){};

    protected abstract WebPopupMenu rightClickMenu(I item);

    @SuppressWarnings("unchecked")
    protected boolean sync(Set<V> values) {
        // remove old
        for (int i=0; i < mModel.getRowCount(); i++) {
            I item = (I) mModel.getValueAt(i, 0);
            if (!values.contains(item.mValue)) {
                item.onRemove();
                item.mValue.deleteObserver(item);
                mModel.removeRow(i);
                i--;
                mItems.remove(item.mValue);
            }
        }
        // add new
        boolean added = false;
        for (V v: values) {
            if (!mItems.containsKey(v)) {
                I item = this.newItem(v);
                item.mValue.addObserver(item);
                mItems.put(item.mValue, item);
                mModel.addRow(new Object[]{item});
                added = true;
            }
        }
        return added;
    }

    protected abstract I newItem(V value);

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

        if (i == this.getSelectedRow())
            return;

        // weblaf does this by "clear+add", triggering two selection events
        // better do this on our own
        //this.setSelectedRow(i);
        this.getSelectionModel().setSelectionInterval(i, i);
    }

    void filterItems(String search) {
        mSearch = search;
        mRowSorter.sort();
    }

    @SuppressWarnings("unchecked")
    private void timerUpdate() {
        for (int i = 0; i < mModel.getRowCount(); i++) {
            I item = (I) mModel.getValueAt(i, 0);
            item.update(null, Change.TIMER);
        }
    }

    protected void updateSorting(){
        if (mModel.getRowCount() == 0)
            return;

        // do no change selection
        //mModel.fireTableDataChanged();
        mModel.fireTableRowsUpdated(0, mModel.getRowCount() -1);
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
                ListView.this.updateOnEDT(arg);
            }
        });
    }

    abstract protected void updateOnEDT(Object arg);

    protected void onRenameEvent() {}

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
                this.update(arg);
                return;
            }
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    TableItem.this.update(arg);
                }
            });
        }

        private void update(Object arg) {
            this.updateOnEDT(arg);

            //mModel.fireTableCellUpdated(?, 0);
            ListView.this.repaint();
        }

        protected abstract void updateOnEDT(Object arg);

        // catch the event, when a tooltip should be shown for this item and
        // create a own one
        // note: together with the cell renderer the tooltip can be added
        // directly to the item, but the behaviour is buggy so we keep this
        @Override
        public String getToolTipText(MouseEvent event) {
            ListView.this.showTooltip(this);
            return null;
        }

        protected void onRemove() {};
    }

    private class TableRenderer extends WebTableCellRenderer {
        // return for each item (value) in the list/table the component to
        // render - which is the item itself here
        // NOTE: table and value can be NULL
        @Override
        @SuppressWarnings("unchecked")
        public Component getTableCellRendererComponent(JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column) {
            TableItem item = (TableItem) value;
            // hopefully return value is not used
            if (table == null || item == null)
                return item;

            item.render(table.getWidth(), isSelected);

            int height = Math.max(table.getRowHeight(), item.getPreferredSize().height);
            // view item needs a little more then it preferres
            height += 1;
            if (height != table.getRowHeight(row))
                // NOTE: this calls resizeAndRepaint()
                table.setRowHeight(row, height);
            return item;
        }
    }
}

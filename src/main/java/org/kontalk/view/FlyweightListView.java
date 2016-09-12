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
import com.alee.managers.tooltip.WebCustomTooltip;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import javax.swing.AbstractCellEditor;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.RowFilter.Entry;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableRowSorter;
import org.kontalk.misc.Searchable;

/**
 * A generic list view for subclassing.
 * Implemented as table with one column.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 * @param <V> the (model) value of one item in the list
 */
abstract class FlyweightListView<V extends Observable & Searchable>
        extends WebTable implements Observer {

    protected enum Change {
        TIMER
    };

    private final Class mVClass;
    protected final View mView;
    private final DefaultTableModel mModel;
    private final TableRowSorter<DefaultTableModel> mRowSorter;
    /** Flyweight item that is used by cell renderer. */
    private final FlyweightItem mRenderItem;
    /** Flyweight item that is used by cell editor. */
    private final FlyweightItem mEditorItem;
    private final Timer mTimer;

    /** The current search string. */
    private String mSearch = "";

    private WebCustomTooltip mTip = null;

    // using legacy lib, raw types extend Object
    @SuppressWarnings("unchecked")
    FlyweightListView(View view,
            FlyweightItem renderItem, FlyweightItem editorItem,
            Comparator<V> comparator,
            boolean activateTimer) {
        // damn Java
        mVClass = (Class<V>) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0];

        mView = view;

        // model
        mModel = new DefaultTableModel(0, 1) {
            // row sorter needs this
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return FlyweightListView.this.getColumnClass(columnIndex);
            }
        };
        this.setModel(mModel);

        // sorter
        mRowSorter = new TableRowSorter<>(mModel);
        mRowSorter.setComparator(0, comparator);
        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
        sortKeys.add(new RowSorter.SortKey(0, SortOrder.ASCENDING));
        mRowSorter.setSortKeys(sortKeys);
        mRowSorter.setSortsOnUpdates(true);
        mRowSorter.sort();
        // filter
        RowFilter<DefaultTableModel, Integer> rowFilter = new RowFilter<DefaultTableModel, Integer>() {
        @Override
        public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                V v = (V) entry.getValue(0);
                return v.contains(mSearch);
            }
        };
        mRowSorter.setRowFilter(rowFilter);
        this.setRowSorter(mRowSorter);

        mRenderItem = renderItem;
        mEditorItem = editorItem;

        // hide header
        this.setTableHeader(null);

        // grid
        this.setGridColor(Color.LIGHT_GRAY);
        this.setShowVerticalLines(false);

        // use custom renderer
        this.setDefaultRenderer(mVClass, new TableRenderer());

        // use custom editor (for mouse interaction)
        this.setDefaultEditor(mVClass, new TableEditor());

        // actions triggered by selection
        this.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting())
                    return;

                FlyweightListView.this.selectionChanged(FlyweightListView.this.getSelectedValue());
            }
        });

        // trigger editing to forward mouse events
        this.addMouseMotionListener(new MouseMotionListener() {
                @Override
                public void mouseDragged(MouseEvent e) {
                }
                @Override
                public void mouseMoved(MouseEvent e) {
                    int row = FlyweightListView.this.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        FlyweightListView.this.editCellAt(row, 0);
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
                    int row = FlyweightListView.this.rowAtPoint(e.getPoint());
                    FlyweightListView.this.setSelectedItem(row);
                    FlyweightListView.this.showPopupMenu(e, FlyweightListView.this.getSelectedItem());
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
                    FlyweightListView.this.onRenameEvent();
                }
            }
        });

        if (activateTimer) {
            mTimer = new Timer();
            // update periodically items to be up-to-date with 'last seen' text
            TimerTask statusTask = new TimerTask() {
                        @Override
                        public void run() {
                            FlyweightListView.this.timerUpdate();
                        }
                    };
            long timerInterval = TimeUnit.SECONDS.toMillis(60);
            mTimer.schedule(statusTask, timerInterval, timerInterval);
        } else {
            mTimer = null;
        }
    }

    private void showPopupMenu(MouseEvent e, V item) {
        WebPopupMenu menu = this.rightClickMenu(item);
        menu.show(this, e.getX(), e.getY());
    }

    protected void selectionChanged(Optional<V> value){};

    protected abstract WebPopupMenu rightClickMenu(V item);

    @SuppressWarnings("unchecked")
    protected boolean sync(Set<V> values) {
        Set<V> items = new HashSet<>();
        // remove old
        for (int i=0; i < mModel.getRowCount(); i++) {
            V item = (V) mModel.getValueAt(i, 0);
            if (!values.contains(item)) {
                item.deleteObserver(this);
                mModel.removeRow(i);
                i--;
            } else {
                items.add(item);
            }
        }

        // add new
        boolean added = false;
        for (V v: values) {
            if (!items.contains(v)) {
                mModel.addRow(new Object[]{v});
                v.addObserver(this);
                added = true;
            }
        }
        return added;
    }

    protected void clearItems() {
        mModel.setRowCount(0);
    }

    @SuppressWarnings("unchecked")
    protected V getDisplayedItemAt(int i) {
        return (V) mModel.getValueAt(mRowSorter.convertRowIndexToModel(i), 0);
    }

    @SuppressWarnings("unchecked")
    protected V getSelectedItem() {
        return (V) mModel.getValueAt(mRowSorter.convertRowIndexToModel(this.getSelectedRow()), 0);
    }

    protected Optional<V> getSelectedValue() {
        if (this.getSelectedRow() == -1)
            return Optional.empty();
        return Optional.of(this.getSelectedItem());
    }

    /** Resets filtering and selects the item containing the value specified. */
    void setSelectedItem(V value) {
        this.filterItems("");
        for (int i=0; i< mModel.getRowCount(); i++) {
            if (this.getDisplayedItemAt(i) == value) {
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
            V item = (V) mModel.getValueAt(i, 0);
            // TODO
            //item.update(null, Change.TIMER);
        }
    }

    protected void updateSorting(){
        if (mModel.getRowCount() == 0)
            return;

        // do no change selection
        //mModel.fireTableDataChanged();
        mModel.fireTableRowsUpdated(0, mModel.getRowCount() -1);
    }

    // JTabel uses this to determine the renderer/editor
    @Override
    public Class<?> getColumnClass(int column) {
        return mVClass;
    }

    @Override
    public void update(Observable o, Object arg) {
        if (SwingUtilities.isEventDispatchThread()) {
            this.updateOnEDT(o, arg);
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                FlyweightListView.this.updateOnEDT(o, arg);
            }
        });
    }

    private void updateOnEDT(Observable o, Object arg) {
        if (mVClass.isAssignableFrom(o.getClass())) {
            // a message changed, render everything again
            mModel.fireTableRowsUpdated(0, mModel.getRowCount() -1);
            return;
        }
        this.updateOnEDT(arg);
    }

    abstract protected void updateOnEDT(Object arg);

    protected void onRenameEvent() {}

    /** Render component used as flyweight object.
     * Each table has only one render item.
     */
    abstract static class FlyweightItem<V> extends WebPanel {
        /** Update before painting. */
        protected abstract void render(V value, int listWidth);
    }

    private class TableRenderer extends WebTableCellRenderer {
        // return for each item (value) in the list/table the component to
        // render - which is the updated render item
        @Override
        public Component getTableCellRendererComponent(JTable table,
                Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            return updateFlyweight(mRenderItem, table, value, row);
        }
    }

    // needed for correct mouse behaviour for components in items
    // (and breaks selection behaviour somehow)
    private class TableEditor extends AbstractCellEditor implements TableCellEditor {
        private V mValue;
        @Override
        public Component getTableCellEditorComponent(JTable table,
                Object value,
                boolean isSelected,
                int row,
                int column) {
            mValue = (V) value;
            return updateFlyweight(mEditorItem, table, value, row);
        }
        @Override
        public Object getCellEditorValue() {
            // no idea what this is used for
            return mValue;
        }
    }

    // NOTE: table and value can be NULL
    @SuppressWarnings("unchecked")
    private FlyweightItem updateFlyweight(FlyweightItem item,
            JTable table, Object value, int row) {
        V valueItem = (V) value;
        // hopefully return value is not used
        if (table == null || valueItem == null) {
            return item;
        }

        item.render(valueItem, table.getWidth());

        int height = Math.max(table.getRowHeight(), item.getPreferredSize().height);
        // view item needs a little more then it preferres
        height += 1;
        if (height != table.getRowHeight(row)) {
            // NOTE: this calls resizeAndRepaint()
            table.setRowHeight(row, height);
        }

        return item;
    }
}

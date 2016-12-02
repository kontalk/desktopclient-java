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

import javax.swing.AbstractCellEditor;
import javax.swing.CellEditor;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableRowSorter;
import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
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

import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.table.WebTable;
import com.alee.laf.table.renderers.WebTableCellRenderer;
import com.alee.managers.language.data.TooltipWay;
import com.alee.managers.tooltip.TooltipManager;
import com.alee.managers.tooltip.WebCustomTooltip;
import org.apache.commons.lang.ArrayUtils;
import org.kontalk.misc.Searchable;

/**
 * A generic list view for subclassing.
 * Implemented as table with one column.
 *
 * Flyweight pattern: One object is re-used for drawing all model values in the list.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 * @param <V> the (model) value type in the list
 */
abstract class ListView<V extends Observable & Searchable>
        extends WebTable implements Observer, Comparator<V> {

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
    ListView(View view,
             FlyweightItem renderItem, FlyweightItem editorItem,
             int selectionMode,
             boolean filterSelected,
             boolean activateTimer) {

        // damn Java
        mVClass = (Class<V>) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0];

        mView = view;

        mRenderItem = renderItem;
        mEditorItem = editorItem;

        this.setSelectionMode(selectionMode);

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
        mRowSorter.setComparator(0, this);
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
                return (!filterSelected && v.equals(ListView.this.getSelectedValue().orElse(null)))
                               || v.contains(mSearch);
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
        this.setDefaultRenderer(mVClass, new TableRenderer());

        // use custom editor (for mouse interaction)
        this.setDefaultEditor(mVClass, new TableEditor());

        // trigger editing to forward mouse events
        this.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = ListView.this.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    ListView.this.editCellAt(row, 0);
                }
            }
        });

        // non-static menu, cannot use this
        //this.setComponentPopupMenu(...);

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
                if (e.isPopupTrigger())
                    ListView.this.onPopupClick(e);
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
            // update periodically values to be up-to-date with 'last seen' text
            TimerTask statusTask = new TimerTask() {
                @Override
                public void run() {
                    ListView.this.update(null, null);
                }
            };
            long timerInterval = TimeUnit.SECONDS.toMillis(60);
            mTimer.schedule(statusTask, timerInterval, timerInterval);
        } else {
            mTimer = null;
        }

        // actions triggered by selection
        this.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                // on selection two events are fired: first adjusting, second not.
                if (e.getValueIsAdjusting()) {
                    // HACK, cell editing blocks item selection (item is not instantly selected on
                    // click), see https://stackoverflow.com/a/17636224
                    CellEditor cellEditor = ListView.this.getCellEditor();
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            if (cellEditor != null)
                                cellEditor.stopCellEditing();
                        }
                    });
                } else {
                    ListView.this.selectionChanged(ListView.this.getSelectedValue());
                }
            }
        });
    }

    @Override
    public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
        // toggle selection with normal click
        boolean newToggle =
                this.getSelectionModel().getSelectionMode() != ListSelectionModel.SINGLE_SELECTION
                        && this.getSelectedRowCount() == 1
                        && this.getSelectedRow() == rowIndex ?
                        true : toggle;
        super.changeSelection(rowIndex, columnIndex, newToggle, extend);
    }

    private void onPopupClick(MouseEvent e) {
        int row = this.rowAtPoint(e.getPoint());

        if (!ArrayUtils.contains(this.getSelectedRows(), row))
            this.setSelectedItem(row);

        WebPopupMenu menu = this.rightClickMenu(this.getSelectedValues());
        menu.show(this, e.getX(), e.getY());
    }

    protected void selectionChanged(Optional<V> value){};

    protected abstract WebPopupMenu rightClickMenu(List<V> selectedValues);

    @SuppressWarnings("unchecked")
    protected boolean sync(Set<V> values) {
        Set<V> oldValues = new HashSet<>();
        // remove old
        for (int i=0; i < mModel.getRowCount(); i++) {
            V value = (V) mModel.getValueAt(i, 0);
            if (!values.contains(value)) {
                value.deleteObserver(this);
                mModel.removeRow(i);
                i--;
            } else {
                oldValues.add(value);
            }
        }

        // add new
        boolean added = false;
        for (V v: values) {
            if (!oldValues.contains(v)) {
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

    protected V getDisplayedValueAt(int row) {
        return this.getValueAtModelIndex(mRowSorter.convertRowIndexToModel(row));
    }

    @SuppressWarnings("unchecked")
    protected V getValueAtModelIndex(int row) {
        return (V) mModel.getValueAt(row, 0);
    }

    protected List<V> getSelectedValues() {
        List<V> values = new ArrayList<>();
        for (int i : this.getSelectedRows()) {
            values.add(this.getDisplayedValueAt(i));
        }
        return values;
    }

    protected Optional<V> getSelectedValue() {
        int row = this.getSelectedRow();
        return row == -1 ?
                Optional.empty() :
                Optional.of(this.getDisplayedValueAt(row));
    }

    /** Resets filtering and selects the item containing the value specified. */
    void setSelectedItem(V value) {
        this.filterItems("");
        for (int i=0; i< mModel.getRowCount(); i++) {
            if (this.getDisplayedValueAt(i) == value) {
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

    @Override
    public void update(Observable o, Object arg) {
        if (SwingUtilities.isEventDispatchThread()) {
            this.updateOnEDT(o, arg);
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ListView.this.updateOnEDT(o, arg);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void updateOnEDT(Observable o, Object arg) {
        if (o == null || mVClass.isAssignableFrom(o.getClass())) {
            // render everything again (and update sorting)
            mModel.fireTableRowsUpdated(0, mModel.getRowCount() -1);
            return;
        }
        this.updateOnEDT(arg);
    }

    abstract protected void updateOnEDT(Object arg);

    // WebLaf's tooltipmanager blocks mouse events, we need to invoke the tooltip manually.
    // Catch the event when a tooltip should be shown and create a own one.
    @Override
    public String getToolTipText(MouseEvent event) {
        int row = this.rowAtPoint(event.getPoint());
        if (row >= 0)
            ListView.this.showTooltip(this.getDisplayedValueAt(row));

        return null;
    }

    protected String getTooltipText(V value) {
        return "";
    };

    private void showTooltip(V value) {
        String text = this.getTooltipText(value);
        if (text.isEmpty())
            return;

        Point p = this.getMousePosition();
        if (p == null)
            return;
        Rectangle rec = this.getCellRect(this.rowAtPoint(p), 0, false);
        Point pos = new Point(rec.x + rec.width, rec.y + rec.height / 2);

        if (mTip != null && pos.equals(mTip.getDisplayLocation()) && mTip.isShowing())
            return;

        if (mTip != null)
            mTip.closeTooltip();

        mTip = TooltipManager.showOneTimeTooltip(this, pos, text, TooltipWay.right);
    }

    // JTabel uses this to determine the renderer/editor
    @Override
    public Class<?> getColumnClass(int column) {
        return mVClass;
    }

    protected void onRenameEvent() {}

    /** View item used as flyweight object. */
    abstract static class FlyweightItem<V> extends WebPanel {
        /** Update before painting. */
        protected abstract void render(V value, int listWidth, boolean isSelected);
    }

    private class TableRenderer extends WebTableCellRenderer {
        // return for each item (value) in the list/table the component to
        // render - which is the updated render item
        @Override
        public Component getTableCellRendererComponent(JTable table,
                Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            return updateFlyweight(mRenderItem, table, value, row, isSelected);
        }
    }

    // needed for correct mouse behaviour for components in items
    // (and breaks selection behaviour somehow)
    @SuppressWarnings("unchecked")
    private class TableEditor extends AbstractCellEditor implements TableCellEditor {
        private V mValue;
        @Override
        public Component getTableCellEditorComponent(JTable table,
                Object value,
                boolean isSelected,
                int row,
                int column) {
            mValue = (V) value;
            return updateFlyweight(mEditorItem, table, value, row, isSelected);
        }
        @Override
        public Object getCellEditorValue() {
            // no idea what this is used for
            return mValue;
        }
    }

    // NOTE: table and value can be NULL
    @SuppressWarnings("unchecked")
    private static <V> FlyweightItem updateFlyweight(FlyweightItem item,
            JTable table, Object value, int row, boolean isSelected) {
        V valueItem = (V) value;
        // hopefully return value is not used
        if (table == null || valueItem == null) {
            return item;
        }

        item.render(valueItem, table.getWidth(), isSelected);

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

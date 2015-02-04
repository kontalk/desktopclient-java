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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.text.SimpleDateFormat;
import java.util.EventObject;
import javax.swing.JTable;
import javax.swing.event.CellEditorListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;

/**
 * A generic list view for subclassing.
 * Implemented as table with one column.
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
class TableView extends WebTable {

    protected final DefaultTableModel mTableModel = new DefaultTableModel(0, 1);

    // TODO
    //private final DefaultTableModel mFilteredTableModel = new DefaultTableModel(0, 1);

    final static SimpleDateFormat TOOLTIP_DATE_FORMAT =
            new SimpleDateFormat("EEE, MMM d yyyy, HH:mm");

    private WebCustomTooltip mTip = null;

    // using legacy lib, raw types extend Object
    @SuppressWarnings("unchecked")
    TableView() {
        mTableModel.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                TableView.this.resetFiltering();
            }
        });

        // TODO
        //this.setModel(mFilteredTableModel);
        this.setModel(mTableModel);

        // hide header
        this.setTableHeader(null);

        // hide grid
        this.setShowGrid(false);

        // use custom renderer
        this.setDefaultRenderer(TableItem.class, new TableRenderer());

        // use custom editor (for mouse events)
        this.setDefaultEditor(TableItem.class, new TableEditor());

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

    void filter(String search) {
        // TODO
    }

    private void resetFiltering() {
        // TODO
    }

    private void showTooltip(TableItem messageView) {
        if (mTip != null)
            mTip.closeTooltip();

        WebCustomTooltip tip = TooltipManager.showOneTimeTooltip(this,
                this.getMousePosition(),
                messageView.getTooltipText(),
                TooltipWay.down);
        mTip = tip;
    }

    // JTabel uses this to determine the renderer
    @Override
    public Class<?> getColumnClass(int column) {
        // always the same
        return TableItem.class;
    }

    public abstract class TableItem extends WebPanel {

        void resize(int listWidth) {};

        void repaint(boolean isSelected) {};

        abstract String getTooltipText();

        /**
         * Return if the content of the item contains the search string.
         * Used for filtering.
         * @param search
         * @return
         */
        protected abstract boolean contains(String search);

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
        @SuppressWarnings("rawtypes")
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

    // used only for correct mouse behaviour for compenents in items
    private class TableEditor implements TableCellEditor {
        @Override
        public Component getTableCellEditorComponent(JTable table,
                Object value,
                boolean isSelected,
                int row,
                int column) {
            return (TableItem) value;
        }
        @Override
        public Object getCellEditorValue() {
            return null;
        }
        @Override
        public boolean isCellEditable(EventObject anEvent) {
            return true;
        }
        @Override
        public boolean shouldSelectCell(EventObject anEvent) {
            return false;
        }
        @Override
        public boolean stopCellEditing() {
            return true;
        }
        @Override
        public void cancelCellEditing() {
        }
        @Override
        public void addCellEditorListener(CellEditorListener l) {
        }
        @Override
        public void removeCellEditorListener(CellEditorListener l) {
        }
    }
}

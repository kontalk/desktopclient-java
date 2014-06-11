/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.kontalk.view;

import com.alee.laf.label.WebLabel;
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

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class ListView extends WebList {

    final WebListModel<ListItem> mListModel = new WebListModel();

    final static SimpleDateFormat TOOLTIP_DATE_FORMAT =
            new SimpleDateFormat("EEE, MMM d yyyy, HH:mm");

    private WebCustomTooltip mTip = null;

    ListView() {
        this.setModel(mListModel);
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
            if (value instanceof ListItem) {
                ListItem panel = (ListItem) value;
                panel.resize(list.getWidth());
                panel.repaint(isSelected);
                return panel;
            } else {
                return new WebPanel(new WebLabel("ERRROR"));
            }
        }
    }
}

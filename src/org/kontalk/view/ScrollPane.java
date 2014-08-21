/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.kontalk.view;

import com.alee.laf.scroll.WebScrollPane;
import java.awt.Component;
import javax.swing.ScrollPaneConstants;

/**
 * A generic scroll pane.
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class ScrollPane extends WebScrollPane {

    public ScrollPane(Component component) {
        super(component);
        
        this.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        this.getVerticalScrollBar().setUnitIncrement(25);
    }
}

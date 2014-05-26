/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.kontalk.model;

import java.util.ArrayList;
import java.util.List;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class ChangeSubject {
    private final List<ChangeListener> mListenerList = new ArrayList();

    /**
     * Add listener to this subject.
     */
    public void addListener(ChangeListener l) {
        mListenerList.add(l);
    }

    /**
     * Notify listeners of changes.
     */
    void changed() {
        for (ChangeListener l: mListenerList) {
            l.stateChanged(new ChangeEvent(this));
        }
    }
}

package org.kontalk.view;

import javax.swing.SwingUtilities;
import java.util.Observable;
import java.util.Observer;

/**
 * @author Alexander Bikadorov {@literal <goto@openmailbox.org>}
 */
public interface ObserverTrait extends Observer {

    @Override
    default void update(Observable o, Object arg) {
        if (SwingUtilities.isEventDispatchThread()) {
            this.updateOnEDT(o, arg);
            return;
        }
        SwingUtilities.invokeLater(() -> ObserverTrait.this.updateOnEDT(o, arg));
    }

    void updateOnEDT(Observable o, Object arg);
}

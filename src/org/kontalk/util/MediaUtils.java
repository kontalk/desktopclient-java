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

package org.kontalk.util;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kontalk.Kontalk;
import org.kontalk.view.View;
import org.newdawn.easyogg.OggClip;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class MediaUtils {
    private final static Logger LOGGER = Logger.getLogger(MediaUtils.class.getName());

    private static OggClip mAudioClip = null;

    public enum Sound{NOTIFICATION}

    private MediaUtils() { throw new AssertionError(); }

    public static void playSound(Sound sound) {
        switch (sound) {
            case NOTIFICATION : play("notification.ogg"); break;
        }
    }

    private static void play(String fileName) {
        if (mAudioClip != null && !mAudioClip.stopped())
            // already playing something
            return;

        try {
            // TODO re-use stream
            // path must be relative to classpath for some reason
            mAudioClip = new OggClip(Kontalk.RES_PATH + fileName);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't create clip", ex);
            return;
        }
        mAudioClip.play();
    }

}

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

package org.kontalk.model;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 * TODO new class here or include in control?
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class UserAvatar {
    private static final Logger LOGGER = Logger.getLogger(UserAvatar.class.getName());

    public static final int SIZE = 150;

    private static final String FILENAME = "avatar";
    public static final String EXT = "jpg";

    private final File mFile;

    private BufferedImage mAvatar = null;

    public UserAvatar(Path appDir) {
        mFile = appDir.resolve(FILENAME + "." + EXT).toFile();
    }

    public BufferedImage get() {
        if (mAvatar != null)
            return mAvatar;

        if (!mFile.exists()) {
            mAvatar = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        } else {
            try {
                mAvatar = ImageIO.read(mFile);
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "can't read avatar", ex);
                mAvatar = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            }
        }

        return mAvatar;
    }

    public void set(BufferedImage avatar) {
        try {
            ImageIO.write(avatar, EXT, mFile);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't save avatar", ex);
        }

        mAvatar = avatar;
    }
}

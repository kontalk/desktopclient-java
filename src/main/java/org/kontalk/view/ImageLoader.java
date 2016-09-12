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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.swing.ImageIcon;
import org.kontalk.system.AttachmentManager;
import org.kontalk.util.MediaUtils;

/**
 * Static utility functions for loading images in Swing.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
class ImageLoader {

    private static final Map<Path, ImageIcon> CACHE = new HashMap<>();

    private ImageLoader() {}

    static ImageIcon imageIcon(Path path) {
        if (CACHE.containsKey(path))
            return CACHE.get(path);

        ImageIcon imageIcon = load(path);
        CACHE.put(path, imageIcon);
        return imageIcon;
    }

    private static ImageIcon load(Path path) {
        return new ImageIcon(
                MediaUtils.scale(
                        MediaUtils.readImage(path),
                        AttachmentManager.THUMBNAIL_DIM.width,
                        AttachmentManager.THUMBNAIL_DIM.height));
    }
}

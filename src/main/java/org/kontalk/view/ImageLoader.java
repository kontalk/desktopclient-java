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

import com.alee.extended.label.WebLinkLabel;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import org.kontalk.system.AttachmentManager;
import org.kontalk.util.MediaUtils;

/**
 * Static utility functions for loading images in Swing.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
class ImageLoader {

    private ImageLoader() {}

    // TODO Swing + async == a damn mess
    static void setImageIconAsync(WebLinkLabel view, Path path) {
        AsyncLoader run = new AsyncLoader(view, path);
        // TODO all at once? queue not that good either
        //new Chat(run).start();
        run.run();
    }

    private static final class AsyncLoader implements Runnable {

        private final WebLinkLabel view;
        private final Path path;

        AsyncLoader(WebLinkLabel view, Path path) {
            this.view = view;
            this.path = path;
        }

        @Override
        public void run() {
            BufferedImage image = MediaUtils.readImage(path);
            Image scaledImage = MediaUtils.scaleAsync(image,
                    AttachmentManager.THUMBNAIL_DIM.width,
                    AttachmentManager.THUMBNAIL_DIM.height,
                    false);

            if (scaledImage.getWidth(view) == -1)
                return;

            this.setOnEDT(scaledImage);
        }

        private void setOnEDT(final Image image) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    view.setIcon(new ImageIcon(image));
                }
            });
        }
    }
}

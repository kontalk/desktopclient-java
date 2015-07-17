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

import com.alee.extended.label.WebLinkLabel;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

/**
 * Static utility functions for loading images in Swing.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
class ImageLoader {
    private static final Logger LOGGER = Logger.getLogger(ImageLoader.class.getName());

    private ImageLoader() {}

    // TODO Swing + async == a damn mess
    static void setImageIconAsync(WebLinkLabel view, String path) {
        AsyncLoader run = new AsyncLoader(view, path);
        // TODO all at once? queue not that good either
        //new Thread(run).start();
        run.run();
    }

    private static BufferedImage readImage(String path) {
        try {
            BufferedImage image = ImageIO.read(new File(path));
            if (image != null) {
                return image;
            }
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't read image", ex);
        }
        return new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
    }

    /**
     * Scale image down to maximum or minimum of width or height, preserving ratio.
     * @param max specifies if image is scaled to maximum or minimum of width/height
     * @return the scaled image, loaded async
     */
    static Image scale(Image image, int width, int height, boolean max) {
        int iw = image.getWidth(null);
        int ih = image.getHeight(null);
        if (iw == -1) {
            LOGGER.warning("image not loaded yet");
        }
        if (max && (iw <= width || ih <= height) || !max && (iw <= width && ih <= height)) {
            return image;
        }
        double sw = width / (iw * 1.0);
        double sh = height / (ih * 1.0);
        double scale = max ? Math.max(sw, sh) : Math.min(sw, sh);
        return image.getScaledInstance((int) (iw * scale), (int) (ih * scale), Image.SCALE_FAST);
    }

    private static final class AsyncLoader implements Runnable, ImageObserver {

        private final WebLinkLabel view;
        private final String path;

        public AsyncLoader(WebLinkLabel view, String path) {
            this.view = view;
            this.path = path;
        }

        @Override
        public void run() {
            BufferedImage image = readImage(path);
            Image scaledImage = scale(image, 300, 200, false);
            if (scaledImage.getWidth(view) == -1)
                return;
            this.setOnEDT(scaledImage);
        }

        @Override
        public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height) {
            // ignore if image is not completely loaded
            if ((infoflags & ImageObserver.ALLBITS) == 0) {
                return true;
            }

            this.setOnEDT(img);
            return false;
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

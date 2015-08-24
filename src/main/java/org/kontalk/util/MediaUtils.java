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

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.apache.commons.lang.StringUtils;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public class MediaUtils {
    private static final Logger LOGGER = Logger.getLogger(MediaUtils.class.getName());

    private static OggClip mAudioClip = null;

    /* contains dot! */
    public static String extensionForMIME(String mimeType) {
        MimeType mime = null;
        try {
            mime = MimeTypes.getDefaultMimeTypes().forName(mimeType);
        } catch (MimeTypeException ex) {
            LOGGER.log(Level.WARNING, "can't find mimetype", ex);
        }
        return StringUtils.defaultIfEmpty(mime != null ? mime.getExtension() : "", ".dat");
    }

    public enum Sound{NOTIFICATION}

    private MediaUtils() {}

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
            // path must be relative to classpath for some reason
            mAudioClip = new OggClip(fileName);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't create clip", ex);
            return;
        }
        mAudioClip.play();
    }

    public static BufferedImage readImage(String path) {
        try {
            BufferedImage image = ImageIO.read(new File(path));
            if (image != null) {
                return image;
            }
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't read image, path: "+path, ex);
        }
        return new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
    }

    public static byte[] imageToByteArray(Image image, String format) {

        BufferedImage bufImage = new BufferedImage(
                image.getWidth(null), image.getHeight(null),
                BufferedImage.TYPE_INT_RGB);

        Graphics2D bGr = bufImage.createGraphics();
        bGr.drawImage(image, 0, 0, null);
        bGr.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean succ;
        try {
            succ = ImageIO.write(bufImage, format, out);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't write image", ex);
            return new byte[0];
        }
        if (!succ) {
            LOGGER.warning("no image writer found, format: "+format);
        }
        return out.toByteArray();
    }

    /**
     * Scale image down to maximum or minimum of width or height, preserving ratio.
     * @param max specifies if image is scaled to maximum or minimum of width/height
     * @return the scaled image, loaded async
     */
    public static Image scale(Image image, int width, int height, boolean max) {
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
}

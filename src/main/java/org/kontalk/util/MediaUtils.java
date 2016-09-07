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

package org.kontalk.util;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.apache.commons.lang.StringUtils;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.kontalk.misc.Callback;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public class MediaUtils {
    private static final Logger LOGGER = Logger.getLogger(MediaUtils.class.getName());

    private MediaUtils() {}

    public static String extensionForMIME(String mimeType) {
        if (mimeType.isEmpty())
            return "unk";

        MimeType mime = null;
        try {
            mime = MimeTypes.getDefaultMimeTypes().forName(mimeType);
        } catch (MimeTypeException ex) {
            LOGGER.log(Level.WARNING, "can't find mimetype", ex);
        }

        String m = mime != null ? mime.getExtension() : "";
        // remove dot
        if (!m.isEmpty())
            m = m.substring(1);
        return StringUtils.defaultIfEmpty(m, "dat");
    }

    public static String mimeForFile(Path path) {
        String mime = null;
        try {
            mime = Files.probeContentType(path);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't probe type", ex);
        }

        if (mime == null) {
            // method above is buggy on windows, try something else
            try(FileInputStream fis = new FileInputStream(path.toFile())) {
                InputStream is = new BufferedInputStream(fis);
                mime = URLConnection.guessContentTypeFromStream(is);
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "can't guess content type", ex);
            }
        }

        if (mime == null)
            LOGGER.warning("can't determine content type: "+path);

        return StringUtils.defaultString(mime);
    }

    public enum Sound{NOTIFICATION}

    private static OggClip mAudioClip = null;

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
            mAudioClip = OggClip.play(fileName);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't create clip", ex);
        }
    }

    public static BufferedImage readImage(Path path) {
        BufferedImage img = readImage(path.toFile()).orElse(null);
        return img != null ?
                img :
                new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
    }

    public static Optional<BufferedImage> readImage(File file) {
        if (!file.exists()) {
            LOGGER.warning("image file does not exist: "+file);
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(ImageIO.read(file));
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't read image, path: "+file.getPath(), ex);
        }
        return Optional.empty();
    }

    public static Optional<BufferedImage> readImage(byte[] imgData) {
        try {
            return Optional.ofNullable(ImageIO.read(new ByteArrayInputStream(imgData)));
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't read image data", ex);
        }
        return Optional.empty();
    }

    public static boolean writeImage(BufferedImage img, String format, File output) {
        boolean succ;
        try {
             succ = ImageIO.write(img, format, output);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't save image", ex);
            return false;
        }
        if (!succ)
            LOGGER.warning("can't find writer for format: "+format);
        return succ;
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
     * Scale image down to max pixels preserving ratio.
     * Blocking
     */
    public static BufferedImage scale(BufferedImage image, int maxPixels) {
        int iw = image.getWidth();
        int ih = image.getHeight();

        double scale = Math.sqrt(maxPixels / (iw * ih * 1.0));

        return toBufferedImage(scaleImage(image, (int) (iw * scale), (int) (ih * scale)));
    }

    /**
     * Scale image down to minimum width/height, preserving ratio.
     * Blocking.
     */
    public static BufferedImage scale(Image image, int width, int height) {
        return toBufferedImage(scaleAsync(image, width, height));
    }

    private static BufferedImage toBufferedImage(Image image) {
        final Callback.Synchronizer syncer = new Callback.Synchronizer();

        ImageObserver observer = new ImageObserver() {
            @Override
            public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height) {
                // ignore if image is not completely loaded
                if ((infoflags & ImageObserver.ALLBITS) == 0) {
                    return true;
                }

                // scaling done, continue with calling thread
                syncer.sync();
                return false;
            }
        };

        if (image.getWidth(observer) == -1) {
            syncer.waitForSync();
        }

        // convert to buffered image, source: https://stackoverflow.com/a/13605411
        if (image instanceof BufferedImage)
            return (BufferedImage) image;

        int iw = image.getWidth(null);
        int ih = image.getHeight(null);
        if (iw == -1) {
            LOGGER.warning("image not loaded yet");
        }

        BufferedImage bimage = new BufferedImage(iw, ih, BufferedImage.TYPE_3BYTE_BGR);

        Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(image, 0, 0, null);
        bGr.dispose();

        return bimage;
    }

    public static Image scaleAsync(Image image, int width, int height) {
        return scaleAsync(image, width, height, false);
    }

    public static Image scaleMaxAsync(Image image, int width, int height) {
        return scaleAsync(image, width, height, true);
    }

    /**
     * Scale image down to maximum or minimum of width or height, preserving ratio.
     * Async: returned image may not be fully loaded.
     */
    private static Image scaleAsync(Image image, int width, int height, boolean max) {
        int iw = image.getWidth(null);
        int ih = image.getHeight(null);
        if (iw == -1 || ih == -1) {
            LOGGER.warning("image not loaded yet");
        }
        if (max && (iw <= width || ih <= height) ||
                !max && (iw <= width && ih <= height)) {
            return image;
        }
        double sw = width / (iw * 1.0);
        double sh = height / (ih * 1.0);
        double scale = max ? Math.max(sw, sh) : Math.min(sw, sh);
        return scaleImage(image, (int) (iw * scale), (int) (ih * scale));
    }

    private static Image scaleImage(Image image, int width, int height) {
        return image.getScaledInstance(width, height, Image.SCALE_FAST);
    }
}

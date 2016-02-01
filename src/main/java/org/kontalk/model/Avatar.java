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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.apache.commons.codec.digest.DigestUtils;
import org.kontalk.Kontalk;
import org.kontalk.util.MediaUtils;


/**
 * Avatar image. Immutable.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public class Avatar {
    private static final Logger LOGGER = Logger.getLogger(Avatar.class.getName());

    private static final String DIR = "avatars";
    protected static final String FORMAT = "png";

    /** SHA1 hash of image data. */
    private final String mID;
    protected final File mFile;

    protected BufferedImage mImage = null;

    /** Saved contact avatar. Used when loading from database. */
    Avatar(String id) {
        this(id, null, null);
    }

    /** New contact avatar. */
    public Avatar(String id, BufferedImage image) {
        this(id, null, image);
    }

    private Avatar(String id, File file, BufferedImage image) {
        mID = id;
        mFile = file != null ?
                file :
                Kontalk.appDir().resolve(DIR).resolve(id + "." + FORMAT).toFile();
        mImage = image;

        if (mImage != null) {
            // save new image
            boolean succ = MediaUtils.writeImage(image, FORMAT, file);
            if (!succ)
                LOGGER.warning("can't save avatar image: "+id);
        }
    }

    private Avatar(File file) {
        mFile = file;
        mImage = file.isFile() ? image(mFile) : null;
        mID = mImage != null ? id(mImage) : "";
    }

    private static BufferedImage image(File file) {
        return MediaUtils.readImage(file).orElse(null);
    }

    public String getID() {
        return mID;
    }

    public Optional<BufferedImage> loadImage() {
        if (mImage == null)
            mImage = image(this.mFile);

        return Optional.ofNullable(mImage);
    }

    void delete() {
        boolean succ = this.mFile.delete();
        if (succ)
            LOGGER.warning("could not delete avatar file: "+this.mID);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof Avatar)) return false;

        Avatar oAvatar = (Avatar) o;
        return mID.equals(oAvatar.mID);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + Objects.hashCode(this.mID);
        return hash;
    }

    public static class UserAvatar extends Avatar {

        private static final int MAX_SIZE = 150;
        private static final String USER_FILENAME = "avatar";

        private static UserAvatar USER_AVATAR = null;

        private byte[] mImageData = null;

        /** Saved user Avatar. */
        UserAvatar() {
            super(userFile());
        }

        /** New user Avatar. ID generated from image. */
        UserAvatar(BufferedImage image) {
            super(id(image), userFile(), image);
        }

        @Override
        public Optional<BufferedImage> loadImage() {
            return mFile.isFile() ?
                    Optional.ofNullable(image(mFile)) :
                    Optional.<BufferedImage>empty();
        }

        public Optional<byte[]> imageData() {
            if (mImageData == null)
                mImageData = Avatar.imageData(mImage);

            return Optional.ofNullable(mImageData);
        }

        private static File userFile() {
            return Kontalk.appDir().resolve(USER_FILENAME + "." + FORMAT).toFile();
        }

        public static UserAvatar instance() {
            if (USER_AVATAR == null) {
                USER_AVATAR = new UserAvatar();
            }
            return USER_AVATAR;
        }

        public static UserAvatar setImage(BufferedImage image) {
            image = MediaUtils.scale(image, MAX_SIZE, MAX_SIZE, true);
            USER_AVATAR = new UserAvatar(image);
            return USER_AVATAR;
        }

        public static void deleteImage() {
            USER_AVATAR.delete();
            USER_AVATAR = new UserAvatar();
        }
    }

    public static void createDir() {
        boolean created = Kontalk.appDir().resolve(DIR).toFile().mkdir();
        if (created)
            LOGGER.info("created avatar directory");
    }

    public static Avatar deleted() {
        return new Avatar("");
    }

    private static String id(BufferedImage image) {
        byte[] imageData = imageData(image);
        return imageData != null ? DigestUtils.sha1Hex(imageData) : "";
    }

    private static byte[] imageData(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, FORMAT, out);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't convert avatar", ex);
            return null;
        }
        return out.toByteArray();

    }
}


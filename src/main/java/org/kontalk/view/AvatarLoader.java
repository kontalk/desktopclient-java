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

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang.ObjectUtils;
import org.kontalk.model.Avatar;
import org.kontalk.model.chat.Chat;
import org.kontalk.model.Contact;
import org.kontalk.model.chat.SingleChat;
import org.kontalk.util.MediaUtils;
import org.kontalk.util.Tr;

/**
 * Static functions for loading avatar pictures.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class AvatarLoader {

    private static final Color LETTER_COLOR = new Color(255, 255, 255);
    private static final Color FALLBACK_COLOR = new Color(220, 220, 220);
    private static final Color GROUP_COLOR = new Color(160, 160, 160);

    private static final Map<Item, AvatarImg> CACHE = new HashMap<>();

    static AvatarImg load(Chat chat, int size) {
        return load(new Item(chat, size));
    }

    static AvatarImg load(Contact contact, int size) {
        return load(new Item(contact, size));
    }

    static AvatarImg loadFallback(int size) {
        return load(new Item(size));
    }

    private AvatarLoader() {}

    private static AvatarImg load(Item item) {
        if (!CACHE.containsKey(item)) {
            CACHE.put(item, item.createImage());
        }
        return CACHE.get(item);
    }

    static class AvatarImg {

        final BufferedImage image;
        final boolean isFallback;

        AvatarImg(BufferedImage img, boolean fallback) {
            this.image = img;
            this.isFallback = fallback;
        }
    }

    private static class Item {
        private final int mSize;

        private final Avatar mAvatar;

        private final String mLetter;
        private final Color mColor;

        private Item(int size) {
            mSize = size;
            mAvatar = null;
            mLetter = fallbackLetter();
            mColor = FALLBACK_COLOR;
        }

        private Item(Contact contact, int size) {
            mSize = size;
            mAvatar = contact.getDisplayAvatar().orElse(null);

            if (mAvatar == null) {
                String name = contact.getName();
                mLetter = labelToLetter(name);
                if (contact.isDeleted()) {
                    mColor = FALLBACK_COLOR;
                } else {
                    int colorcode = hash(contact.getID());
                    int hue = Math.abs(colorcode) % 360;
                    mColor = Color.getHSBColor(hue / 360.0f, 0.9f, 1);
                }
            } else {
                // not used
                mLetter = "";
                mColor = new Color(0);
            }
        }

        private Item(Chat chat, int size) {
            mSize = size;

            String l;
            if (chat.isGroupChat()) {
                // nice to have: group picture
                mAvatar = null;
                // or use number of contacts here?
                l = chat.getSubject();
                mColor = GROUP_COLOR;
            } else {
                Contact c = ((SingleChat) chat).getMember().getContact();
                Item i = new Item(c, size);
                mAvatar = i.mAvatar;
                l = i.mLetter;
                mColor = i.mColor;
            }
            mLetter = labelToLetter(l);
        }

        private String labelToLetter(String label) {
            return label.length() >= 1 ?
                    label.substring(0, 1).toUpperCase() :
                    fallbackLetter();
        }

        private AvatarImg createImage() {
            if (mAvatar != null) {
                BufferedImage img = mAvatar.loadImage().orElse(null);
                if (img != null) {
                    return new AvatarImg(
                            MediaUtils.scale(img, mSize, mSize),
                            false);
                }
            }

            return fallback(mLetter, mColor, mSize);
        }

        @Override
        public final boolean equals(Object o) {
            if (o == this)
                return true;

            if (!(o instanceof Item))
                return false;

            Item oItem = (Item) o;
            return mSize == oItem.mSize &&
                    ObjectUtils.equals(mAvatar, oItem.mAvatar) &&
                    mLetter.equals(oItem.mLetter) &&
                    mColor.equals(oItem.mColor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mSize, mAvatar, mLetter, mColor);
        }
    }

    private static String fallbackLetter() {
        return Tr.tr("?");
    }

    // uniform hash
    // Source: https://stackoverflow.com/a/12996028
    private static int hash(int x) {
        x = ((x >> 16) ^ x) * 0x45d9f3b;
        x = ((x >> 16) ^ x) * 0x45d9f3b;
        x = ((x >> 16) ^ x);
        return x;
    }

    private static AvatarImg fallback(String letter, Color color, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        Graphics2D graphics = img.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, size, size);

        graphics.setFont(new Font(Font.DIALOG, Font.PLAIN, size));
        graphics.setColor(LETTER_COLOR);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        FontMetrics fm = graphics.getFontMetrics();
        Rectangle2D r = fm.getStringBounds(letter, graphics);

        graphics.drawString(letter,
                (size - (int) r.getWidth()) / 2.0f,
                // adjust to font baseline
                // Note: not centered for letters with descent (drawing under
                // the baseline), dont know how to get that
                (size - (int) r.getHeight()) / 2.0f + fm.getAscent());

        return new AvatarImg(img, true);
    }
}

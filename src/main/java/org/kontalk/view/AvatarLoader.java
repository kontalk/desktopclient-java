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
import java.awt.Image;
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
import org.kontalk.util.MediaUtils;
import org.kontalk.util.Tr;

/**
 * Static functions for loading avatar pictures.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class AvatarLoader {

    private static final int IMG_SIZE = 40;

    private static final Color LETTER_COLOR = new Color(255, 255, 255);
    private static final Color FALLBACK_COLOR = new Color(220, 220, 220);
    private static final Color GROUP_COLOR = new Color(160, 160, 160);

    private static final Map<Item, Image> CACHE = new HashMap<>();

    static Image load(Chat chat) {
        return load(new Item(chat));
    }

    static Image load(Contact contact) {
        return load(new Item(contact));
    }

    static BufferedImage createFallback(int size) {
        return fallback(fallbackLetter(), FALLBACK_COLOR, size);
    }

    private AvatarLoader() {};

    private static Image load(Item item) {
        if (!CACHE.containsKey(item)) {
            CACHE.put(item, item.createImage());
        }
        return CACHE.get(item);
    }

    private static class Item {
        private final Avatar avatar;

        private final String letter;
        private final Color color;

        Item(Contact contact) {
            avatar = contact.getAvatar().orElse(null);

            if (avatar == null) {
                String name = contact.getName();
                letter = labelToLetter(name);
                int colorcode = name.isEmpty()? 0 : hash(contact.getID());
                int hue = Math.abs(colorcode) % 360;
                color = Color.getHSBColor(hue / 360.0f, 0.8f, 1);
            } else {
                letter = "";
                color = new Color(0);
            }
        }

        Item(Chat chat) {
            String l = "";
            if (chat.isGroupChat()) {
                // nice to have: group picture
                avatar = null;
                // or use number of contacts here?
                l = chat.getSubject();
                color = GROUP_COLOR;
            } else {
                Contact c = chat.getValidContacts().stream().findFirst().orElse(null);
                if (c != null) {
                    Item i = new Item(c);
                    avatar = i.avatar;
                    l = i.letter;
                    color = i.color;
                } else {
                    avatar = null;
                    color = FALLBACK_COLOR;
                }
            }
            letter = labelToLetter(l);
        }

        private String labelToLetter(String label) {
            return label.length() >= 1 ?
                    label.substring(0, 1).toUpperCase() :
                    fallbackLetter();
        }

        private Image createImage() {
            if (avatar != null) {
                BufferedImage img = avatar.loadImage().orElse(null);
                if (img != null)
                    return MediaUtils.scaleAsync(img, IMG_SIZE, IMG_SIZE, true);
            }

            return fallback(letter, color, IMG_SIZE);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this)
                return true;

            if (!(o instanceof Item))
                return false;

            Item oItem = (Item) o;
            return ObjectUtils.equals(avatar, oItem.avatar) &&
                    letter.equals(oItem.letter) &&
                    color.equals(oItem.color);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 71 * hash + Objects.hashCode(this.avatar);
            hash = 71 * hash + Objects.hashCode(this.letter);
            hash = 71 * hash + Objects.hashCode(this.color);
            return hash;
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

    private static BufferedImage fallback(String letter, Color color, int size) {
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

        return img;
    }
}

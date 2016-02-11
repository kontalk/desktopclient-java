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

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang.ObjectUtils;
import org.kontalk.model.Avatar;
import org.kontalk.model.chat.Chat;
import org.kontalk.model.Contact;
import org.kontalk.util.MediaUtils;

/**
 * Static functions for loading avatar pictures.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class AvatarLoader {

    private static final int IMG_SIZE = 40;

    private static final Color LETTER_COLOR = new Color(255, 255, 255);
    private static final Color FALLBACK_COLOR = new Color(220, 220, 220);
    private static final Color GROUP_COLOR = new Color(160, 160, 160);

    // TODO i18n?
    private static final String FALLBACK_LETTER = "?";

    private static final Map<Item, Image> CACHE = new HashMap<>();

    AvatarLoader() {};

    static Image load(Chat chat) {
        return load(new Item(chat));
    }

    static Image load(Contact contact) {
        return load(new Item(contact));
    }

    private static Image load(Item item) {
        if (!CACHE.containsKey(item)) {
            CACHE.put(item, item.createImage());
        }
        return CACHE.get(item);
    }

    private static class Item {
        private final Avatar avatar;
        private final String label;
        private final int colorCode;
        private final boolean group;

        Item(Contact contact) {
            avatar = contact.getAvatar().orElse(null);
            label = contact.getName();
            colorCode = hash(contact.getID());
            group = false;
        }

        Item(Chat chat) {
            Avatar a = null;
            String l = null;
            Integer cc = null;

            if (chat.isGroupChat()) {
                // or use number of contacts here?
                l = chat.getSubject();
                group = true;
                // nice to have: group picture
            } else {
                Contact[] contacts = chat.getValidContacts();
                if (contacts.length > 0) {
                    Contact c = contacts[0];
                    a = c.getAvatar().orElse(null);
                    l = c.getName();
                    cc = hash(c.getID());
                }
                group = false;
            }

            avatar = a;
            label = l != null ? l : "";
            colorCode = cc != null ? cc : hash(chat.getID());
        }

        Image createImage() {
            if (avatar != null) {
                BufferedImage img = avatar.loadImage().orElse(null);
                if (img != null)
                    return MediaUtils.scaleAsync(img, IMG_SIZE, IMG_SIZE, true);
            }

            return fallback(label, colorCode, IMG_SIZE, group);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this)
                return true;

            if (!(o instanceof Item))
                return false;

            Item oItem = (Item) o;
            return ObjectUtils.equals(avatar, oItem.avatar) &&
                    label.equals(oItem.label) && colorCode == oItem.colorCode &&
                    group == oItem.group;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 37 * hash + Objects.hashCode(this.label);
            hash = 37 * hash + this.colorCode;
            return hash;
        }
    }

    // uniform hash
    // Source: https://stackoverflow.com/a/12996028
    private static int hash(int x) {
        x = ((x >> 16) ^ x) * 0x45d9f3b;
        x = ((x >> 16) ^ x) * 0x45d9f3b;
        x = ((x >> 16) ^ x);
        return x;
    }

    static BufferedImage createFallback(int size) {
        return fallback("", 0, size, false);
    }

    private static BufferedImage fallback(String text, int colorCode, int size, boolean group) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        // color
        Color color;
        if (group) {
            color = GROUP_COLOR;
        } else if (!text.isEmpty()) {
            int hue = Math.abs(colorCode) % 360;
            color = Color.getHSBColor(hue / 360.0f, 0.8f, 1);
        } else {
            color = FALLBACK_COLOR;
        }

        Graphics2D graphics = img.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, size, size);

        // letter
        String letter = text.length() >= 1 ?
                text.substring(0, 1).toUpperCase() :
                FALLBACK_LETTER;

        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, size));
        graphics.setColor(LETTER_COLOR);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        FontMetrics fm = graphics.getFontMetrics();
        int w = fm.stringWidth(letter);
        int h = fm.getHeight();
        int d = fm.getDescent();
        graphics.drawString(letter,
                 (size / 2.0f) - (w / 2.0f),
                 // adjust to font baseline
                 (size / 2.0f) + (h / 2.0f) - d);

        return img;
    }
}

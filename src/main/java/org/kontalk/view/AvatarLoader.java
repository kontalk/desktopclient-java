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
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.swing.ImageIcon;
import org.kontalk.model.Chat;
import org.kontalk.model.Contact;

/**
 * Static functions for loading avatar pictures.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class AvatarLoader {

    private static final int IMG_SIZE = 40;
    private static final Color LETTER_COLOR = new Color(255, 255, 255);
    // TODO i18n?
    private static final String FALLBACK_LETTER = "?";
    private static final Color FALLBACK_COLOR = new Color(220, 220, 220);

    private static final Map<Item, ImageIcon> CACHE = new HashMap<>();

    AvatarLoader() {};

    static ImageIcon load(Chat chat) {
        return load(new Item(chat));
    }

    static ImageIcon load(Contact contact) {
        return load(new Item(contact));
    }

    private static ImageIcon load(Item item) {
        if (!CACHE.containsKey(item)) {
            // TODO
            CACHE.put(item, fallback(item));
        }
        return CACHE.get(item);
    }

    private static ImageIcon fallback(Item item) {
        BufferedImage img = new BufferedImage(IMG_SIZE, IMG_SIZE, BufferedImage.TYPE_INT_RGB);

        // color
        Color color;
        if (!item.label.isEmpty()) {
            int hue = Math.abs(item.colorCode) % 360;
            color = Color.getHSBColor(hue / 360.0f, 1, 1);
        } else {
            color = FALLBACK_COLOR;
        }

        Graphics2D graphics = img.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, IMG_SIZE, IMG_SIZE);

        // letter
        String name = item.label;
        String letter = name.length() > 1 ?
                name.substring(0, 1).toUpperCase() :
                FALLBACK_LETTER;

        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, IMG_SIZE));
        graphics.setColor(LETTER_COLOR);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        FontMetrics fm = graphics.getFontMetrics();
        int w = fm.stringWidth(letter);
        int h = fm.getHeight();
        int d = fm.getDescent();
        graphics.drawString(letter,
                 (IMG_SIZE / 2.0f) - (w / 2.0f),
                 // adjust to font baseline
                 (IMG_SIZE / 2.0f) + (h / 2.0f) - d);

        return new ImageIcon(img);
    }

    private static class Item {
        final String label;
        final int colorCode;

        Item(Contact contact) {
            label = contact.getName();
            colorCode = hash(contact.getID());
        }

        Item(Chat chat) {
            if (chat.isGroupChat()) {
                label = chat.getSubject();
            } else {
                Contact[] contacts = chat.getValidContacts();
                label = contacts.length > 0 ? contacts[0].getName() : "";
            }
            colorCode = hash(chat.getID());
        }

        @Override
        public boolean equals(Object o) {
            if (o == this)
                return true;

            if (!(o instanceof Item))
                return false;

            Item oItem = (Item) o;
            return label.equals(oItem.label) && colorCode == oItem.colorCode;
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
}

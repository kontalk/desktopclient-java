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

import com.alee.laf.text.WebTextPane;
import com.alee.utils.WebUtils;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.event.MouseInputAdapter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

/**
 * Static methods/field for parsing web links in the text of a WebTextPane.
 * Cause Android has Linkify and I have to write this myself .(
 * Some parts taken from: https://community.oracle.com/thread/2089990
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class LinkUtils {
    private static final Logger LOGGER = Logger.getLogger(LinkUtils.class.getName());

    static final TextClickListener CLICK_LISTENER = new TextClickListener();
    static final TextMotionListener MOTION_LISTENER = new TextMotionListener();

    private static final Style DEFAULT_STYLE
            = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
    /** Undoubtedly the best URL regex ever made. */
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(http[s]?://)?" + // scheme
            "(\\w+(-+\\w+)*\\.)+" + // sub- and host-level(s)
            "[a-z]{2,}(:[0-9]+)?" + // TLD and port
            "(/[^\\s?#/]*)*" + // path
            "(\\?[^\\s?#]*)*" + // query
            "(\\#[^\\s?#]*)*", // fragment
            Pattern.CASE_INSENSITIVE);

    private static final String URL_ATT_NAME = "URL";

    static void linkify(StyledDocument doc, String text) {
        // style for all links in a document
        Style urlStyle = doc.addStyle(null, DEFAULT_STYLE);
        StyleConstants.setForeground(urlStyle, Color.BLUE);
        // only for identifying URLs
        urlStyle.addAttribute(URL_ATT_NAME, new Object());
        try {
            doc.remove(0, doc.getLength());
            Matcher m = URL_PATTERN.matcher(text);
            int lastPos = 0;
            while (m.find()) {
                // non-matching
                insertDefault(doc, text.substring(lastPos, m.start()));
                // matching
                doc.insertString(doc.getLength(), m.group(), urlStyle);
                lastPos = m.end();
            }
            // last non-matching
            insertDefault(doc, lastPos >= text.length() ? "" : text.substring(lastPos));
        } catch (BadLocationException ex) {
            LOGGER.log(Level.WARNING, "can't set document text", ex);
        }
    }

    private static void insertDefault(StyledDocument doc, String text)
            throws BadLocationException {
        doc.insertString(doc.getLength(), text, DEFAULT_STYLE);
    }

    private static class TextClickListener extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            WebTextPane textPane = (WebTextPane) e.getComponent();
            StyledDocument doc = textPane.getStyledDocument();
            Element elem = doc.getCharacterElement(textPane.viewToModel(e.getPoint()));
            if (!elem.getAttributes().isDefined(URL_ATT_NAME))
                // not a link
                return;

            int len = elem.getEndOffset() - elem.getStartOffset();
            final String url;
            try {
                url = doc.getText(elem.getStartOffset(), len);
            } catch (BadLocationException ex) {
                LOGGER.log(Level.WARNING, "can't get URL", ex);
                return;
            }

            Runnable run = new Runnable() {
                @Override
                public void run() {
                    WebUtils.browseSiteSafely(fixProto(url));
                }
            };
            new Thread(run).start();
        }
    }

    private static class TextMotionListener extends MouseInputAdapter {
        @Override
        public void mouseMoved(MouseEvent e) {
            WebTextPane textPane = (WebTextPane) e.getComponent();
            StyledDocument doc = textPane.getStyledDocument();
            Element elem = doc.getCharacterElement(textPane.viewToModel(e.getPoint()));
            if (elem.getAttributes().isDefined(URL_ATT_NAME)) {
                textPane.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            } else {
                textPane.setCursor(Cursor.getDefaultCursor());
            }
        }
    }

    private static String fixProto(String url) {
        try {
            new URL(url);
            return url;
        } catch (MalformedURLException ex) {
        }
        url = "http://" + url;
        try {
            new URL(url);
        } catch (MalformedURLException ex) {
            LOGGER.log(Level.WARNING, "invalid url", ex);
        }
        return url;
    }
}

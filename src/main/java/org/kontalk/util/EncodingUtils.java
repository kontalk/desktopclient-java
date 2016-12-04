/*
 * Kontalk Java client
 * Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONObject;

public final class EncodingUtils {
    private static final Logger LOGGER = Logger.getLogger(EncodingUtils.class.getName());

    public static final String EOL = System.getProperty("line.separator");

    private EncodingUtils() { throw new AssertionError(); }

    /**
     * Get an enum set by parsing an integer which represents a bit array.
     * Source: http://stackoverflow.com/questions/2199399/storing-enumset-in-a-database
     * @param <T> type of elements in enum set
     * @param enumClass enum class to determine the type
     * @param decoded integer decoded as
     * @return an enum set containing the enums specified by the integer
     */
    public static <T extends Enum<T>> EnumSet<T> intToEnumSet(Class<T> enumClass, int decoded) {
        EnumSet<T> enumSet = EnumSet.noneOf(enumClass);
        T[] enums = enumClass.getEnumConstants();
        while (decoded != 0) {
            int ordinal = Integer.numberOfTrailingZeros(decoded);
            enumSet.add(enums[ordinal]);
            decoded -= Integer.lowestOneBit(decoded);
        }
        return enumSet;
    }

    /**
     * Encode an enum set to an integer representing a bit array.
     */
    public static int enumSetToInt(EnumSet<?> enumSet) {
        int b = 0;
        for (Object o : enumSet) {
            b += 1 << ((Enum) o).ordinal();
        }
        return b;
    }

    // using legacy lib, raw types extend Object
    @SuppressWarnings("unchecked")
    public static void putJSON(JSONObject json, String key, String value) {
        if (!value.isEmpty())
            json.put(key, value);
    }

    public static String getJSONString(Map<?, ?> map, String key) {
        return StringUtils.defaultString((String) map.get(key));
    }

    public static byte[] base64ToBytes(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    public static String bytesToBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static URI toURI(String str) {
        try {
            return new URI(str);
        } catch (URISyntaxException ex) {
            LOGGER.log(Level.WARNING, "invalid URI", ex);
        }
        return URI.create("");
    }

    public static String randomString(int length) {
        return RandomStringUtils.randomAlphanumeric(length);
    }
}

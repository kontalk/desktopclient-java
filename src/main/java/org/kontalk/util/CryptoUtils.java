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

import javax.crypto.Cipher;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.NoSuchAlgorithmException;
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public class CryptoUtils {
    private static final Logger LOGGER = Logger.getLogger(CryptoUtils.class.getName());

    /**
     * Ugly hack to get “unlimited strength” for the Java Encryption Extension.
     * Source: https://stackoverflow.com/a/22492582
     * And even more evil: http://stackoverflow.com/a/3301720/6286694
     */
    public static boolean removeCryptographyRestrictions() {
        try {
            if (Cipher.getMaxAllowedKeyLength("RC5") >= 256) {
                LOGGER.config("cryptography restrictions removal not needed");
                return true;
            }   } catch (NoSuchAlgorithmException ex) {
            LOGGER.log(Level.WARNING, "can't check for crypto restriction", ex);
        }

        try {
            /*
             * Do the following, but with reflection to bypass access checks:
             *
             * JceSecurity.isRestricted = false;
             * JceSecurity.defaultPolicy.perms.clear();
             * JceSecurity.defaultPolicy.add(CryptoAllPermission.INSTANCE);
             */
            Class<?> jceSecurity = Class.forName("javax.crypto.JceSecurity");
            Class<?> cryptoPermissions = Class.forName("javax.crypto.CryptoPermissions");
            Class<?> cryptoAllPermission = Class.forName("javax.crypto.CryptoAllPermission");

            Field isRestrictedField = jceSecurity.getDeclaredField("isRestricted");
            isRestrictedField.setAccessible(true);

            // "isRestricted" field is final now, remove this
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(isRestrictedField, isRestrictedField.getModifiers() & ~Modifier.FINAL);

            isRestrictedField.set(null, false);

            Field defaultPolicyField = jceSecurity.getDeclaredField("defaultPolicy");
            defaultPolicyField.setAccessible(true);
            PermissionCollection defaultPolicy = (PermissionCollection) defaultPolicyField.get(null);

            Field perms = cryptoPermissions.getDeclaredField("perms");
            perms.setAccessible(true);
            ((Map<?, ?>) perms.get(defaultPolicy)).clear();

            Field instance = cryptoAllPermission.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            defaultPolicy.add((Permission) instance.get(null));

            LOGGER.info("removed cryptography restrictions");
        } catch (ClassNotFoundException |
                NoSuchFieldException |
                SecurityException |
                IllegalArgumentException |
                IllegalAccessException ex) {
            LOGGER.log(Level.WARNING, "can't remove cryptography restrictions", ex);
            return false;
        }
        return true;
    }
}

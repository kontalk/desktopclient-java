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

package org.kontalk.system;

import org.kontalk.persistence.Config;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.apache.commons.codec.digest.DigestUtils;
import org.kontalk.client.Client;
import org.kontalk.misc.JID;
import org.kontalk.model.Avatar;
import org.kontalk.model.Contact;
import org.kontalk.model.Model;
import org.kontalk.util.MediaUtils;

/**
 * Process incoming avatar events
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class AvatarHandler {
    private static final Logger LOGGER = Logger.getLogger(AvatarHandler.class.getName());

    public static final List<String> SUPPORTED_TYPES = Arrays.asList(ImageIO.getReaderMIMETypes());

    private static final int MAX_SIZE = 1024 * 250;

    private final Client mClient;
    private final Model mModel;

    AvatarHandler(Client client, Model model) {
        mClient = client;
        mModel = model;
    }

    public void onNotify(JID jid, String id) {
        if (!Config.getInstance().getBoolean(Config.NET_REQUEST_AVATARS))
            // disabled by user
            return;

        Contact contact = mModel.contacts().get(jid).orElse(null);
        if (contact == null) {
            LOGGER.warning("can't find contact with jid:" + jid);
            return;
        }

        if (id.isEmpty()) {
            // contact disabled avatar publishing
            contact.deleteAvatar();
            return;
        }

        Avatar avatar = contact.getAvatar().orElse(null);
        if (avatar != null && avatar.getID().equals(id))
            // avatar is not new
            return;

        mClient.requestAvatar(jid, id);
    }

    public void onData(JID jid, String id, byte[] avatarData) {
        LOGGER.info("new avatar, jid: "+jid+" id: "+id);

        if (avatarData.length > MAX_SIZE)
            LOGGER.info("avatar data too long: "+avatarData.length);

        Contact contact = mModel.contacts().get(jid).orElse(null);
        if (contact == null) {
            LOGGER.warning("can't find contact with jid:" + jid);
            return;
        }

        if (!id.equals(DigestUtils.sha1Hex(avatarData))) {
            LOGGER.warning("this is not what we wanted");
            return;
        }

        BufferedImage img = MediaUtils.readImage(avatarData).orElse(null);
        if (img == null)
            return;

        contact.setAvatar(new Avatar(id, img));
    }
}

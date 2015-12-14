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

package org.kontalk.system;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.apache.commons.codec.digest.DigestUtils;
import org.kontalk.client.Client;
import org.kontalk.misc.Callback;
import org.kontalk.misc.JID;
import org.kontalk.model.Contact;
import org.kontalk.model.Contact.Avatar;
import org.kontalk.model.ContactList;
import org.kontalk.util.MediaUtils;

/**
 * Process incoming avatar events
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class AvatarHandler {
    private static final Logger LOGGER = Logger.getLogger(AvatarHandler.class.getName());

    public static final List<String> SUPPORTED_TYPES = Arrays.asList(ImageIO.getReaderMIMETypes());

    private static final int MAX_SIZE = 40;
    private static final String STORE_FORMAT = "jpg";

    private final Client mClient;

    AvatarHandler(Client client) {
        mClient = client;
    }

    public void onNotify(JID jid, String id) {
        Optional<Contact> optContact = ContactList.getInstance().get(jid);
        if (!optContact.isPresent()) {
            LOGGER.warning("can't find contact with jid:" + jid);
            return;
        }
        Contact contact = optContact.get();

        if (id.isEmpty()) {
            // contact disabled avatar publishing
            // TODO
        }

        Optional<Avatar> optAvatar = contact.getAvatar();
        if (optAvatar.isPresent() &&
                id.equals(DigestUtils.sha1Hex(optAvatar.get().id)))
            // avatar is not new
            return;

        mClient.requestAvatar(jid, id);
    }

    public void onData(JID jid, final String id, byte[] avatarData) {
        LOGGER.info("new avatar, jid: "+jid+" id: "+id);

        final Optional<Contact> optContact = ContactList.getInstance().get(jid);
        if (!optContact.isPresent()) {
            LOGGER.warning("can't find contact with jid:" + jid);
            return;
        }

        if (!id.equals(DigestUtils.sha1Hex(avatarData))) {
            LOGGER.warning("this is not what we wanted");
            return;
        }

        Optional<BufferedImage> optImg = MediaUtils.readImage(avatarData);
        if (!optImg.isPresent())
            return;

        MediaUtils.scale(optImg.get(), MAX_SIZE, MAX_SIZE, true,
                new Callback.Handler<Image>(){
                    @Override
                    public void handle(Callback<Image> callback) {
                        byte[] data = MediaUtils.imageToByteArray(callback.value, STORE_FORMAT);
                        if (data.length == 0)
                            return;

                        Contact contact = optContact.get();
                        contact.setAvatar(new Avatar(id, data));
                    }
                });
    }

}

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

package org.kontalk.model;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;
import org.kontalk.model.Avatar.UserAvatar;
import org.kontalk.model.chat.ChatList;
import org.kontalk.system.Config;
import org.kontalk.system.Database;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class Model {
    private static final Logger LOGGER = Logger.getLogger(Model.class.getName());

    private final Path mAppDir;

    private final ContactList mContactList;
    private final ChatList mChatList;
    private final Account mAccount;

    private UserAvatar mUserAvatar;

    public Model(Database db, Path appDir) {
        mAppDir = appDir;

        mAccount = new Account(mAppDir, Config.getInstance());
        mContactList = new ContactList(db);
        mChatList = new ChatList(db);

        mUserAvatar = new UserAvatar(mAppDir);
    }

    public Account account() {
        return mAccount;
    }

    public ContactList contacts() {
        return mContactList;
    }

    public ChatList chats() {
        return mChatList;
    }

    public UserAvatar userAvatar() {
        return mUserAvatar;
    }

    public void load() {
        // order matters!
        Map<Integer, Contact> contactMap = mContactList.load();
        mChatList.load(contactMap);
    }

    public UserAvatar newUserAvatar(BufferedImage image) {
        return UserAvatar.create(image, mAppDir);
    }

    public void deleteAvatar() {
        mUserAvatar.delete();
        mUserAvatar = new UserAvatar(mAppDir);
    }
}

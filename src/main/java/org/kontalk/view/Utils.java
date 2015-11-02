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

import com.alee.extended.filechooser.WebFileChooserField;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.text.WebTextArea;
import com.alee.laf.text.WebTextField;
import com.alee.utils.filefilter.ImageFilesFilter;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLHandshakeException;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.sasl.SASLErrorException;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.misc.JID;
import org.kontalk.misc.KonException;
import org.kontalk.model.Chat;
import org.kontalk.model.Contact;
import org.kontalk.util.Tr;
import org.ocpsoft.prettytime.PrettyTime;

/**
 * Various utilities used in view.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class Utils {
    private static final Logger LOGGER = Logger.getLogger(Utils.class.getName());

    static final SimpleDateFormat SHORT_DATE_FORMAT = new SimpleDateFormat("EEE, HH:mm");
    static final SimpleDateFormat MID_DATE_FORMAT = new SimpleDateFormat("EEE, d MMM, HH:mm");
    static final SimpleDateFormat LONG_DATE_FORMAT = new SimpleDateFormat("EEE, d MMM yyyy, HH:mm:ss");
    static final PrettyTime PRETTY_TIME = new PrettyTime();

    private Utils() {}

    /* fields */

    static WebFileChooserField createImageChooser(boolean enabled, String path) {
        WebFileChooserField chooser = new WebFileChooserField();
        if (!path.isEmpty())
            chooser.setSelectedFile(new File(path));
        chooser.setMultiSelectionEnabled(false);
        chooser.setShowRemoveButton(true);
        chooser.getWebFileChooser().setFileFilter(new ImageFilesFilter());
        File file = new File(path);
        if (file.exists()) {
            chooser.setSelectedFile(file);
        }
        if (file.getParentFile() != null && file.getParentFile().exists())
            chooser.getWebFileChooser().setCurrentDirectory(file.getParentFile());
        return chooser;
    }

    static WebTextField createTextField(final String text) {
        final WebTextField field = new WebTextField(text, false);
        field.setEditable(false);
        field.setBackground(null);
        field.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                check(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                check(e);
            }
            private void check(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    WebPopupMenu popupMenu = new WebPopupMenu();
                    popupMenu.add(createCopyMenuItem(field.getText(), ""));
                    popupMenu.show(field, e.getX(), e.getY());
                }
            }
        });
        return field;
    }

    static WebMenuItem createCopyMenuItem(final String copyText, String toolTipText) {
        WebMenuItem item = new WebMenuItem(Tr.tr("Copy"));
        if (!toolTipText.isEmpty())
            item.setToolTipText(toolTipText);
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
                clip.setContents(new StringSelection(copyText), null);
            }
        });
        return item;
    }

    static WebTextArea createFingerprintArea() {
        WebTextArea area = new WebTextArea();
        area.setEditable(false);
        area.setOpaque(false);
        area.setFontName(Font.MONOSPACED);
        area.setFontSizeAndStyle(13, true, false);
        return area;
    }

    static Runnable createLinkRunnable(final Path path) {
        return new Runnable () {
            @Override
            public void run () {
                Desktop dt = Desktop.getDesktop();
                try {
                    dt.open(path.toFile());
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "can't open attachment", ex);
                }
            }
        };
    }

    /* images */

    static Icon getIcon(String fileName) {
        return new ImageIcon(getImage(fileName));
    }

    static Image getImage(String fileName) {
        URL imageUrl = ClassLoader.getSystemResource(fileName);
        if (imageUrl == null) {
            LOGGER.warning("can't find icon image resource");
            return new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        }
        return Toolkit.getDefaultToolkit().createImage(imageUrl);
    }

    /* strings */

    static String name(Contact contact, int maxLength) {
        String name = name_(contact, maxLength);
        return !name.isEmpty() ? name :
                "("+Tr.tr("Unknown")+")";
    }

    static String displayName(Contact contact) {
        return displayName(contact, Integer.MAX_VALUE);
    }

    private static String displayName(Contact contact, int maxLength) {
        return displayName(contact, contact.getJID(), maxLength);
    }

    static String displayName(Contact contact, JID jid, int maxLength) {
        String name = name_(contact, maxLength);
        return !name.isEmpty() ? name : jid(jid, maxLength);
    }

    private static String displayNames(List<Contact> contacts) {
        return displayNames(contacts, Integer.MAX_VALUE);
    }

    static String displayNames(List<Contact> contacts, int maxLength) {
        List<String> nameList = new ArrayList<>(contacts.size());
        for (Contact contact : contacts) {
            nameList.add(displayName(contact, maxLength));
        }
        return StringUtils.join(nameList, ", ");
    }

    private static String name_(Contact contact, int maxLength) {
        return contact.isDeleted() ? "("+Tr.tr("Deleted")+")" :
                contact.isMe() ? Tr.tr("You") :
                StringUtils.abbreviate(contact.getName(), maxLength);
    }

    static String jid(JID jid, int maxLength) {
        String local = jid.local(), domain = jid.domain();
        if (jid.isHash())
            local = "[" + jid.local().substring(0, Math.min(jid.local().length(), 6)) + "]";

        local = StringUtils.abbreviate(local, (int) ((maxLength-1) * 0.4));
        domain = StringUtils.abbreviate(domain, (int) ((maxLength-1) * 0.6));

        return XmppStringUtils.completeJidFrom(local, domain);
    }

    static String chatTitle(Chat chat) {
        if (chat.isGroupChat()) {
            String subj = chat.getSubject();
            return !subj.isEmpty() ? subj : Tr.tr("Group Chat");
        } else {
            return Utils.displayNames(new ArrayList<>(chat.getAllContacts()));
        }
    }

    static String fingerprint(String fp) {
        int m = fp.length() / 2;
        return group(fp.substring(0, m)) + "\n" + group(fp.substring(m));
    }

    private static String group(String s) {
        return StringUtils.join(s.split("(?<=\\G.{" + 4 + "})"), " ");
    }

    static String mainStatus(Contact c, boolean pre) {
        Contact.Subscription subStatus = c.getSubScription();
        return c.isMe() ? Tr.tr("Myself") :
                    c.isBlocked() ? Tr.tr("Blocked") :
                    c.getOnline() == Contact.Online.YES ? Tr.tr("Online") :
                    c.getOnline() == Contact.Online.ERROR ? Tr.tr("Not reachable") :
                    subStatus == Contact.Subscription.UNSUBSCRIBED ? Tr.tr("Not authorized") :
                    subStatus == Contact.Subscription.PENDING ? Tr.tr("Waiting for authorization") :
                    lastSeen(c, true, pre);
    }

    static String lastSeen(Contact contact, boolean pretty, boolean pre) {
        String lastSeen = !contact.getLastSeen().isPresent() ? Tr.tr("never") :
                pretty ? Utils.PRETTY_TIME.format(contact.getLastSeen().get()) :
                Utils.MID_DATE_FORMAT.format(contact.getLastSeen().get());
        return pre ? Tr.tr("Last seen")+": " + lastSeen : lastSeen;
    }

    static String getErrorText(KonException ex) {
        String eol = " " + System.getProperty("line.separator");
        String errorText;
        switch (ex.getError()) {
            case IMPORT_ARCHIVE:
                errorText = Tr.tr("Can't open key archive.");
                break;
            case IMPORT_READ_FILE:
                errorText = Tr.tr("Can't load all keyfiles from archive.");
                break;
            case IMPORT_KEY:
                errorText = Tr.tr("Can't create personal key from key files.") + " ";
                if (ex.getExceptionClass().equals(IOException.class)) {
                    errorText += eol + Tr.tr("Is the public key file valid?");
                }
                if (ex.getExceptionClass().equals(CertificateException.class)) {
                    errorText += eol + Tr.tr("Are all key files valid?");
                }
                break;
            case CHANGE_PASS:
                errorText = Tr.tr("Can't change password. Internal error(!?)");
                break;
            case WRITE_FILE:
                errorText = Tr.tr("Can't write key files to configuration directory.");
                break;
            case READ_FILE:
            case LOAD_KEY:
                errorText = "";
                switch (ex.getError()) {
                    case READ_FILE:
                        errorText = Tr.tr("Can't read key files from configuration directory.");
                        break;
                    case LOAD_KEY:
                        errorText = Tr.tr("Can't load key files from configuration directory.");
                        break;
                }
                errorText += " " + Tr.tr("Please reimport your key.");
                break;
            case LOAD_KEY_DECRYPT:
                errorText = Tr.tr("Can't decrypt key. Is the passphrase correct?");
                break;
            case CLIENT_CONNECTION:
                errorText = Tr.tr("Can't create connection");
                break;
            case CLIENT_CONNECT:
                errorText = Tr.tr("Can't connect to server.");
                if (ex.getExceptionClass().equals(SmackException.ConnectionException.class)) {
                    errorText += eol + Tr.tr("Is the server address correct?");
                }
                if (ex.getExceptionClass().equals(SSLHandshakeException.class)) {
                    errorText += eol + Tr.tr("The server rejects the key.");
                }
                if (ex.getExceptionClass().equals(SmackException.NoResponseException.class)) {
                    errorText += eol + Tr.tr("The server does not respond.");
                }
                break;
            case CLIENT_LOGIN:
                errorText = Tr.tr("Can't login to server.");
                if (ex.getExceptionClass().equals(SASLErrorException.class)) {
                    errorText += eol + Tr.tr("The server rejects the account. Is the specified server correct and the account valid?");
                }
                break;
            case CLIENT_ERROR:
                errorText = Tr.tr("Connection to server closed on error.");
                // TODO more details
                break;
            case DOWNLOAD_CREATE:
            case DOWNLOAD_EXECUTE:
            case DOWNLOAD_RESPONSE:
            case DOWNLOAD_WRITE:
                errorText = Tr.tr("Downloading file failed");
                // TODO more details
                break;
            case UPLOAD_CREATE:
            case UPLOAD_EXECUTE:
            case UPLOAD_RESPONSE:
                errorText = Tr.tr("Uploading file failed");
                // TODO more details
                break;
            default:
                errorText = Tr.tr("Unusual error:")+" "+ex.getError();
        }
        errorText.chars();
        return errorText;
    }

    /* misc */

    static boolean confirmDeletion(Component parent, String text) {
        int selectedOption = WebOptionPane.showConfirmDialog(parent,
                text,
                Tr.tr("Please Confirm"),
                WebOptionPane.OK_CANCEL_OPTION,
                WebOptionPane.WARNING_MESSAGE);
        return selectedOption == WebOptionPane.OK_OPTION;
    }

    static List<Contact> contactList(Chat chat) {
        List<Contact> contacts = new ArrayList<>(chat.getAllContacts());
        contacts.sort(new Comparator<Contact>() {
            @Override
            public int compare(Contact c1, Contact c2) {
                return Utils.compareContacts(c1, c2);
            }
        });
        return contacts;
    }

    static int compareContacts(Contact c1, Contact c2) {
        if (c1.isMe()) return +1;
        if (c2.isMe()) return -1;

        String s1 = StringUtils.defaultIfEmpty(c1.getName(), c1.getJID().string());
        String s2 = StringUtils.defaultIfEmpty(c2.getName(), c2.getJID().string());
        return s1.compareToIgnoreCase(s2);
    }
}

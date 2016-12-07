kontalk-java-client
===================

[![Build Status](https://travis-ci.org/kontalk/desktopclient-java.svg?branch=master)](https://travis-ci.org/kontalk/desktopclient-java)

A platform independent Java client for Kontalk (http://www.kontalk.org). Includes connectivity to the Jabber network!

The desktop client uses your existing Kontalk account from the [Android client](https://github.com/kontalk/androidclient/blob/master/README.md#kontalk-official-android-client). Instructions for exporting the key [here](https://github.com/kontalk/androidclient/wiki/Export-personal-key-to-another-device).

**FAQ:** Common questions are answered [HERE!](https://github.com/kontalk/desktopclient-java/wiki)

User Forum: https://forum.kontalk.org/

### Screenshots

![Conversation screen](/misc/kon_snap1.png?raw=true)

[Contacts screen](/misc/kon_snap2.png?raw=true)

## Software Dependencies

- Java 8

## Key Features

Connect with Kontalk...
- Use the existing Kontalk account from your phone
- Synchronized contact list (=XMPP roster)
- Add new Kontalk users by phone number
- The client automatically requests public keys for safe communication
- Your communication with Kontalk users is encrypted by default

... and beyond:
- Exchange text messages with any Jabber/XMPP users!
- Add new Jabber contacts by JID
- Tested with clients like [Pidgin](https://pidgin.im/) or [Conversations](https://github.com/siacs/Conversations)

**Note: private key and messages are saved unencrypted and can be read by other applications on your computer!**

## Implemented XEP features:
- XEP-0184: Message receipts
- XEP-0085: Chat state notifications
- XEP-0191: User blocking
- XEP-0066: File transfer over server
- XEP-0231: Image thumbnails for attachments
- XEP-0084: Avatar images
- XEP-0363: HTTP File Upload
- XEP-0012: Last activity timestamp
- XEP-0245: The infamous and most essential "/me" command

## Support us

* If you are missing a feature or found a bug [report it!](https://github.com/kontalk/desktopclient-java/issues)

* Help us with [translations](https://translate.kontalk.org) to spread Kontalk around the world!

* Code contributions / pull requests are welcome!

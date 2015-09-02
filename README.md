kontalk-java-client
===================

A platform independent Java client for Kontalk (http://www.kontalk.org). Includes connectivity to the Jabber network!

The desktop client uses your existing Kontalk account from the [Android client](https://github.com/kontalk/androidclient/blob/master/README.md#kontalk-official-android-client). Instructions for exporting the key [here](https://github.com/kontalk/androidclient/wiki/Export-personal-key-to-another-device).

**FAQ:** Common questions are answered [HERE!](https://github.com/kontalk/desktopclient-java/wiki)

### Screenshots

![Conversation screen](/misc/kon_snap1.png?raw=true)

[Contacts screen](/misc/kon_snap2.png?raw=true)

## Software Dependencies

- Java 8

## Key Features

- connecting to Kontalk server with an already existing Kontalk account
- automatically adding XMPP roster entries from server
- manually adding arbitrary Kontalk or Jabber user
- automatically requesting/adding public keys for other Kontalk user
- sending/receiving (encrypted) text messages from/to Kontalk user
- sending/receiving (plain) text messages from/to arbitrary Jabber/XMPP user (clients like [Pidgin](https://pidgin.im/) or [Conversations](https://github.com/siacs/Conversations))
- sending/requesting server receipts according to XMPP extension
- ability to block all messages for specific user
- receiving files send from the Android client

**Note: private key and messages are saved unencrypted and can be read by other
applications on your computer!**

## Support us

* If you are missing a feature or found a bug [report it!](https://github.com/kontalk/desktopclient-java/issues)

* Help us with [translations](https://translate.kontalk.org) to spread Kontalk around the world!

* Code contributions / pull requests are welcome!

kontalk-java-client
===================

A platform independent Java client for Kontalk (http://www.kontalk.org).

This desktop client uses your existing Android Kontalk account.
The Android client version 3.0 is required to export your account-key which
is needed by the desktop client.

You can get the Android-App v3.0Beta from
- [F-Droid](https://f-droid.org/repository/browse/?fdid=org.kontalk)
- [Aptoide] (http://kontalk-devteam.store.aptoide.com/app/market/org.kontalk/34/6938705/Kontalk%20Messenger)
- or the latest apk [here](https://kontalk.raunz.name/files/)

# Current key features:

- connecting to Kontalk server with an already existing Kontalk account
- automatically adding XMPP roster entries from server
- requesting/adding public keys for known user
- sending/receiving (un-)encrypted text messages from/to Kontalk server
- sending/requesting server receipts according to Kontalk XMPP extension
- ability to block all messages for specific user

**Note: private key and messages are saved unencrypted and can be read by other
applications on your computer!**

# Software Dependencies:

- Java 8 / JDK 8

# Included dependencies

- as GIT submodule:
  Kontalk-common-java classes (https://github.com/kontalk/client-common-java)

- JARS included in project:
  - Smack and Smackx (https://igniterealtime.org/projects/smack/index.jsp)
  - WebLaF (http://weblookandfeel.com)
  - Bouncy Castle (provider and PGP) (https://www.bouncycastle.org/java.html)
  - Apache Commons (configuration, lang and logging) (http://commons.apache.org) 
  - SQLite JDBC (https://bitbucket.org/xerial/sqlite-jdbc)
  - and more...

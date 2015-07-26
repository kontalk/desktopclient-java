kontalk-java-client
===================

A platform independent Java client for Kontalk (http://www.kontalk.org).

This desktop client uses your existing Android Kontalk account (for the new Tigase server).
The Android client version 3.0 is required to export your account-key which
is needed by the desktop client.

FAQ: Some questions are answered [here](https://github.com/kontalk/desktopclient-java/wiki) 

You can get the Android-App v3.0Beta4 (or newer) from
- [F-Droid](https://f-droid.org/repository/browse/?fdid=org.kontalk)
- or the latest release [here](https://github.com/kontalk/androidclient/releases)

## Current key features:

- connecting to Kontalk server with an already existing Kontalk account
- automatically adding XMPP roster entries from server
- automatically requesting/adding public keys for known user
- sending/receiving (un-/)encrypted text messages from/to Kontalk server
- sending/requesting server receipts according to Kontalk XMPP extension
- ability to block all messages for specific user
- receiving files send from the Android client

**Note: private key and messages are saved unencrypted and can be read by other
applications on your computer!**

## Software Dependencies

- Java 8 / JDK 8

## Debian package

- [kontalk_3.0.1+git.20150725.affa0726-1_all.deb](https://mega.nz/#!cIw0VJyJ!jUK5Lnxem-X8Xek34hEhsWkQvsUE7aWeAVkGX6gk6Qw)
   
## For building

- [Gradle](http://gradle.org) (wrapper included in project)

## Run the desktop client from source

See [here](https://github.com/kontalk/desktopclient-java/wiki#how-can-i-compile-the-code).

## Included dependencies

### as GIT submodule:

-  Kontalk-common-java classes (https://github.com/kontalk/client-common-java)

### as Maven dependencies:

- Smack (https://igniterealtime.org/projects/smack/index.jsp)
- WebLaF (http://weblookandfeel.com)
- Bouncy Castle (provider and PGP) (https://www.bouncycastle.org/java.html)
- Apache Commons (configuration, lang and logging) (http://commons.apache.org) 
- SQLite JDBC (https://bitbucket.org/xerial/sqlite-jdbc)
- and more...

### as Jar files
 
- easyogg

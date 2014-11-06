kontalk-java-client
===================

A platform independent Java client for Kontalk (http://www.kontalk.org)

Current features:
- connecting to Kontalk server with an already existing Kontalk account
- adding XMPP roster entries from server
- requesting/adding public keys for known user
- sending/receiving unencrypted text messages from/to Kontalk server
- support for receiving encrypted/signed messages
- sending/requesting server receipts according to Kontalk XMPP extension

Dependencies:
- JDK 8 / Java 8

--included as git submodule:
- Kontalk-common-java classes (https://github.com/kontalk/client-common-java)

-- JARS included in project:
- Smack and Smackx (https://igniterealtime.org/projects/smack/index.jsp)
- WebLaF (http://weblookandfeel.com)
- Bouncy Castle (provider and PGP) (https://www.bouncycastle.org/java.html)
- Apache Commons (configuration, lang and logging) (http://commons.apache.org) 
- SQLite JDBC (https://bitbucket.org/xerial/sqlite-jdbc)
- and more...

Wissl
=====
http://msch.fr/wissl

Wissl allows you to play your music over the internet using a web interface,
without uploading it to a third-party.

The server side component is a Java application that indexes music files and
exposes it using HTTP/REST/JSON.

The Wissl server runs by default in an embedded mode that contains a Javascript
web application that can play music on the server using HTML5 or Flash audio.

Wissl is designed with home users in mind and aims to be simple and easy to use.

Quickstart
----------

To build Wissl, you need Java 6+ and Maven.
In the root directory, run `mvn package`.

The generated file `target/wissl-VER.war` is a self executable webapp archive.
You can run it with `java -jar wissl.jar`, it will run the server on `http://localhost:8080`.
To use a different HTTP port, use the following switch: `java -jar wissl.jar -Dwsl.http.port=1234`.

If you want a higher degree of control over deployment, you can also use this WAR file within
any application server such as tomcat.

Screenshot
----------

![screenshot](https://raw.github.com/mschn/wissl/master/screen.jpg)

Libraries used
--------------

* Server
 * [RESTEasy](http://www.jboss.org/resteasy) - JAX-RS framework (LGPL)
 * [H2](http://www.h2database.com/) - Embedded RDBMS (MPL)
 * [c3p0](http://sourceforge.net/projects/c3p0/) - JDBC connection pool (LGPL)
 * [Jetty](http://www.eclipse.org/jetty/) - Embedded servlet container (LGPL)
 * [Jaudiotagger](http://www.jthink.net/jaudiotagger/) - Audio tagging (LGPL)

* Client
 * [SoundManager2](http://www.schillmania.com/projects/soundmanager2/) - Sound playback (BSD)
 * [jQuery](http://jquery.com/) - Javascript framework (MIT)
 * [History.js](https://github.com/balupton/History.js/) - Back button support (BSD)
 * [Fugue Icons](http://p.yusukekamiyamane.com) - (CC BY)

License
-------
Wissl is Copyright (c) 2013, Mathieu Schnoor.

It is licensed under the GNU General Public License (v3),
a copy of which can be found in the LICENSE file or at
this address: http://www.gnu.org/licenses/gpl.html

Wissl
=====

Wissl allows you to play your music over the internet using a web interface,
without uploading it to a third-party.

The server side component is a Java application that indexes music files and
exposes it using HTTP/REST/JSON.

The Wissl server runs by default in an embedded mode that contains a Javascript
web application that can play music on the server using HTML5 or Flash audio.

Wissl is designed with home users in mind and aims to be simple and easy to use.

Howto
-----

Wissl runs on Java 6+.
It requires Apache Ant to be built. To build, simply run `ant` in the root
directory.

To run, either click on the file `wissl.jar` using a file explorer,
or run the command `java -jar wissl.jar`.

A web browser should open. If it does not, simply point it to `http://localhost:8080/`.
You will be asked to create a new administrator account. When this is done,
you can go to the 'Admin' section to add music to you library.

Wissl runs by default on port 8080, which can be tuned by editing the file
`config.ini`.


Libraries used
--------------

* Server
 * [RESTEasy](http://www.jboss.org/resteasy) - JAX-RS framework (LGPL)
 * [H2](http://www.h2database.com/) - Embedded RDBMS (MPL)
 * [c3p0](http://sourceforge.net/projects/c3p0/) - JDBC connection pool (LGPL)
 * [Winstone](http://code.google.com/p/winstone/) - Embedded servlet container (LGPL)
 * [Jaudiotagger](http://www.jthink.net/jaudiotagger/) - Audio tagging (LGPL)

* Client
 * [SoundManager2](http://www.schillmania.com/projects/soundmanager2/) - Sound playback (BSD)
 * [jQuery](http://jquery.com/) - Javascript framework (MIT)
 * [History.js](https://github.com/balupton/History.js/) - Back button support (BSD)
 * [Faenza Icons](http://tiheum.deviantart.com/art/Faenza-Icons-173323228) - (GPL)

License
-------
Wissl is Copyright (c) 2012, Mathieu Schnoor.

It is licensed under the GNU General Public License (v3),
a copy of which can be found in the LICENSE file or at
this address: http://www.gnu.org/licenses/gpl.html

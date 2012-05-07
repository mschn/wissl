Wissl
=====

Wissl allows you to play your music over the internet using a web interface,
without uploading it to a third-party.

The server side component is a Java application that indexes music files and
exposes it using HTTP/REST/JSON.

The Wissl server runs by default in an embedded mode that contains an HTML5
web client that can play music on the server using HTML5 or Flash audio.

Wissl is designed with home users in mind and aims to be simple and easy to use.

Howto
-----

Wissl runs on Java 6+.
It requires Apache Ant to be built. To build, simply run `ant` in the root
directory.

To run, either click on the file `wissl.jar` using a file explorer,
or run the command `java -jar wissl.jar`.

Then, simply point you browser to `http://localhost:8080/` and login using
admin:admin as default credentials. You should then be able to add music and
create new users using the web interface.

Wissl runs by default on port 8080, which can be tuned by editing the file
`config.ini`.


Dependencies
------------

* RESTEasy - LGPL - http://www.jboss.org/resteasy
* H2 - MPL - http://www.h2database.com/
* c3p0 - LGPL - http://sourceforge.net/projects/c3p0/
* Winstone - LGPL - http://code.google.com/p/winstone/
* Jaudiotagger - LGPL - http://www.jthink.net/jaudiotagger/
* SoundManager2 - BSD - http://www.schillmania.com/projects/soundmanager2/
* jQuery - MIT - http://jquery.com/
* History.js - BSD -https://github.com/balupton/History.js/

License
-------
Wissl is Copyright (c) 2012, Mathieu Schnoor.

It is licensed under the GNU General Public License (v3),
a copy of which can be found in the LICENSE file or at
this address: http://www.gnu.org/licenses/gpl.html

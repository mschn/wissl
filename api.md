API
===

The Wissl server exposes a REST API that allows interaction with any client
able to send simple HTTP requests and parse JSON responses.

This document describes this API so that you may write such a client.

Preamble
--------

* All communications with the server use the HTTP/1.1 protocol.
* For all communications with the server, the response will be encoded
  in UTF-8 and use the media-type `application/json`.
* Except when said otherwise, all requests will need to provide authentication
  using a parameter named `sessionId` which will contain the current session
  UUID. This can be achieved with either:
  * an HTTP header, ie:
   `curl -H "sessionId: af0ee222-6ed1-409d-9d99-5654c7802df1" http://localhost/wissl/req`
  * an HTTP query parameter, ie:
  `curl http://localhost/wissl/req?sessionId=af0ee222-6ed1-409d-9d99-5654c7802df1`  
  This sessionId will be provided once upon login.

Data types
----------

Here are defined the data types that will appear in the responses of the server.
Those types will be referenced later to avoid redundancy.

### user

Each registered account is represented by an user.

    {
      // unique user id
      "id": INT,
      // user name
      "username": STRING,
      // 1: admin; 2: regular user
      "auth": INT,
      // total number of bytes downloaded
      "downloaded": INT
    }

### session

When an user is connected, a Session is bound to it.
There can be only one session per user at a time.

    {
      // unique user id
      "user_id": INT,
      // user name
      "username": STRING,
      // milliseconds since last server activity
      "last_activity": INT,
      // milliseconds since session creation
      "created_at": INT,
      // ADMIN ONLY: client IP address
      "origin": STRING,
      // last played song, or empty
      "last_played_song": SONG
    }

### artist

An artist contains several albums.

    {
      // unique artist id
      "id": INT,
      // artist name
      "name": STRING,
      // number of albums
      "albums": INT,
      // number of songs
      "songs": INT,
      // playtime for all songs in seconds
      "playtime": INT
    }

### album

An album contains several songs, and is contained by one artist.

    {
      // unique album id
      "id": INT,
      // album name
      "name": STRING,
      // unique artist id
      "artist": INT,
      // artist name
      "artist_name": STRING,
      // album release date
      "date": STRING,
      // musical genre
      "genre": STRING,
      // number of songs
      "songs": INT,
      // playtime for all songs in seconds
      "playtime": INT,
      // true if the server has an artwork for this album
      "artwork": BOOL
    }

### song

A song is contained by one album and one artist.
Each song can be added to multiple playlists.

    {
      // Unique song id
      "id": INT,
      // song title
      "title": STRING,
      // song position in album
      "position": INT,
      // disc number when multiple volumes
      "disc_no": INT,
      // song duration in seconds
      "duration": INT,
      // audio mimetype, ie 'audio/mp3'
      "format": STRING,
      // unique album id
      "album_id": INT,
      // album name
      "album_name": STRING,
      // unique artist id
      "artist_id": INT,
      // artist name
      "artist_name": STRING,
    }

### playlist

A playlist is a list of songs created by an user.

    {
      // unique playlist id
      "id": INT,
      // playlist name
      "name": STRING,
      // unique user id, playlist owner
      "user": INT,
      // number of songs
      "songs": INT,
      // total duration of all songs in seconds
      "playtime": INT
    }

API methods
-----------

### login
* method: `POST`
* path: `/login`
* param: `username` a valid non-empty user name
* param: `password` cleartext password matching the username
* ex: `curl -d 'username=user&password=pwd' http://localhost/wissl/login`
* returns:<pre>
    {
      // unique user id
      "userId": INT,
      // session UUID
      "sessionId": STRING,
      // 1: admin; 2: regular user
      "auth": INT
    }
</pre>

The password is sent in clear-text.
To prevent eavesdropping, configure the server to use SSL.

Use the session id provided in the response to authenticate for each further call.
The session id can be user either as an HTTP header or an HTTP query parameter,
both named `sessionId`.

If a session associated with the same user already exists, the previous session
will be destroyed and the client will be notified if it tries to reuse the
destroyed session.

### logout
* method: `POST`
* path: `/logout`
* no parameter
* ex: `curl -X POST -H 'sessionId:UUID' http://localhost/wissl/logout`
* does not return any reponse

Destroys the session associated with the UUID in the request.

### has users
* method: `GET`
* path: `/hasusers`
* no parameter
* ex: `curl -H 'sessionId:UUID http://localhost/wissl/hasusers`
* returns:<pre>
    {
      "hasusers": BOOL
    }
</pre>

Checks whether the server already has an user registered.

If it does (`hasusers === true`), then only the registered users can log in.

If it does not, then any client can call `/user/add` to create the initial user without authentication.

### users
* method: `GET`
* path: `/users`
* no parameter
* ex: `curl -H 'sessionId:UUID' http://localhost/wissl/users`
* returns:<pre>
    {
	  // all known users
      "users": [
	    USER, ...
      ],
	  // currently active sessions
	  "sessions": [
        SESSION, ...
	  ]
    }
</pre>

List all users and all sessions.

Allows clients to list users and check which is currently logged in.

### user
* method: `GET`
* path: `/user/{user_id}`
* param: `user_id` unique user id
* ex: `curl -H 'sessionId:UUID' http://localhost/wissl/user/5`
* returns:<pre>
    {
      "user": USER,
      "session": SESSION,
      "playlists": {
        PLAYLIST, ...
      }
    }
</pre>

Retrieve information about a specific user.

The user id used as parameter can be found in the response of the `/users` request.

### add user
* method: `POST`
* path: `/user/add`
* param: `username` name of the user to create
* param: `password` clear text password
* param: `auth` int authorization level. 1: admin, 2: user
* ex: `curl -H 'sessionId:UUID' -d 'username=user&password=pwd&auth=2' http://localhost/wissl/user/add`
* ex: `curl -d 'username=admin&password=pwd&auth=1 http://localhost/wissl/user/add`
* requires admin privileges
* does not return anything

Create a new user account.
Clear-text password is sent as parameter. Configure the server to use SSL to prevent eavesdropping.

As shown by the examples, two distinct scenarios can be distinguished:
* Add the first user of the system right after a clean startup. No authentication is required.
  The first user must be admin (`auth === 1`).
* Add a new user. The client must authenticate as an admin user to perform this operation.

The provided password can be changed later using `/user/password`.

### change password
* method: `POST`
* path: `/user/password`
* param: `old_password` re-authenticate user with old password
* param: `new_password` new password to set
* ex: `curl -H 'sessionId:UUID' -d 'old_password=pwd&new_password=pwd2 http://localhost/wissl/user/password`
* does not return anything

Change the password for the user associated with the current session/
Clear-text password is sent as parameter. Configure the server to use SSL to prevent eavesdropping.

### remove user
* method: `POST`
* path: `/user/remove`
* param: `user_ids[]` array of unique user ids
* ex: `curl -H 'sessionId:UUID' -d 'user_ids=3&user_ids=5' http://locahost/wissl/user/remove`
* does not return anything

Remove one or more user accounts from the server.

You can not remove the user account that you are currently authenticated with.

You can not remove the last admin user account.

### create empty playlist
* method: `POST`
* path: `/playlist/create`
* param: `name` name of the new playlist
* ex: `curl -H 'sessionId:UUID' -d 'name=foo' http://localhost/wissl/playlist/create`
* returns:<pre>
    PLAYLIST
</pre>

Create a new empty playlist.

### create playlist with songs
* method: `POST`
* path: `/playlist/create-add`
* param: `name` name of the new playlist
* param: `clear` optional boolean. If true, clears a pre-existing playlist with the same name
* param: `song_ids[]` array of song ids to add to the playlist
* param: `album_ids[]` array of album ids to add to the playlist
* ex: `curl -H 'sessionId:UUID' -d 'name=foo&songs_ids=5&songs_ids=7' http://localhost/wissl/playlist/create-add`
* returns:<pre>
    {
      // number of songs added to the playlist
      "added": INT,
      // playlist that was just created
      "playlist": PLAYLIST
    }
</pre>

Create a new playlist and add songs to it.

If the optional boolean parameter `clear` is set to true and a playlist with the same `name` already
exists, it will be cleared before adding the songs specified by the parameters.

Both parameters `song_ids` and `album_ids` are optional and can be used simultaneously.

### random playlist
* method: `POST`
* path: `/playlist/random`
* param: `name` name of the random playlist
* param: `number` number of random songs to add
* ex: `curl -H 'sessionId:UUID' -d 'name=random&number=20' http://localhost/wissl/playlist/random`
* returns:<pre>
    {
      // number of songs added to the random playlist
      "added": INT,
      // unique song id of the first song in the playlist
      "first_song": INT,
      // playlist that was created
      "playlist": PLAYLIST
    }
</pre>

Creates a new playlist that contains randomly picked songs.

If a playlist with that name already exists, it will be cleared.

The maximum number of songs that can be requested is 50.

The number of songs added to the playlist may be less than requested
if the library contains less than 50 songs.

### add to playlist
* method: `POST`
* path: `/playlist/{playlist_id}/add`
* param: `playlist_id` unique playlist id
* param: `clear` optional boolean. If true, clears the playlist
* param: `song_ids[]` array of song ids to add to the playlist
* param: `album_ids[]` array of album ids to add to the playlist
* ex: `curl -H 'sessionId:UUID' -d 'song_ids=5&song_ids=7' http://localhost/wissl/playlist/3/add`
* returns:<pre>
    {
      // number of songs added to the playlist
      "added": INT,
      "playlist": PLAYLIST
    }
</pre>

Add songs to an existing playlist.

If the optional boolean parameter `clear` is set to true,
the playlist will be cleared before adding the songs specified by the parameters.

Both parameters `song_ids` and `album_ids` are optional and can be used simultaneously.

An user can only alter a playlist that it has created.

### remove from playlist
* method: `POST`
* path: `/playlist/{playlist_id}/remove`
* param: `playlist_id` unique playlist id
* param: `song_ids[]` array of song ids to remove from the playlist
* ex: `curl -H 'sessionId:UUID' -d 'song_ids=3&song_ids=7' http://localhost/wissl/playlist/3/remove`
* does not return anything

Remove songs from a playlist.

An user can only alter a playlist that it has created.

### playlist songs
* method: `GET`
* path: `/playlist/{playlist_id}/songs`
* param: `playlist_id` unique playlist id
* ex: `curl -H 'sessionId:UUID' http://localhost/wissl/playlist/3songs`
* returns:<pre>
    {
      // playlist name
      "name": STRING,
      // array of songs
      "playlist": [
        SONG, ...
      ]
    }
</pre>

List the content of a playlist.

The ordering of the `playlist` song array reflects the playlist play order.

### playlist song
* method: `GET`
* path: `/playlist/{playlist_id}/song/{song_pos}`
* param: `playlist_id` unique playlist id
* param: `song_pos` song position in playlist
* ex: `curl -H 'sessionId:UUID' http://localhost/wissl/{playlist_id}/song/{song_pos}`
* returns:<pre>
    SONG
</pre>

Get a single song at a given position in a playlist.

### remove playlists
* method: `POST`
* path: `/playlists/remove`
* param: `playlist_ids[]` array of playlist ids to remove
* does not return anything

Remove one or more playlists.

An user can only remove playlists that it has created.

### playlists
* method: `GET`
* path: `/playlists`
* ex: `curl -H 'sessionId:UUID' http://localhost/wissl/playlists`
* returns:<pre>
    {
      "playlists": [
        PLAYLIST, ...
      ]
    }
</pre>

Get all playlists created by the authenticated used.

### user playlists
* method: `GET`
* path: `/playlists/{user_id}`
* param: `user_id`
* ex: `curl -H 'sessionId:UUID' http://localhost/wissl/playlists/3`
* returns:<pre>
    {
      "playlists": [
        PLAYLIST, ...
      ]
    }
</pre>

Get all playlists created by a given user.

### artists
* method: `GET`
* path: `/artists`
* ex: `curl -H 'sessionId:UUID' http://localhost/wissl/artists`
* returns:<pre>
    {
      "artists": [
        {
          "artist": ARTIST,
          // list of album ids that have artworks
          "artworks": [
            INT, ...
          ]
        },
        ...
      ]
    }
</pre>

Get all artists.

The returned object contains both the artist information and the ids of all albums that
have an artwork ready to be served by the method `/art/{album_id}`.

### albums
* method: `GET`
* path: `/albums/{artist_id}`
* param: `artist_id` unique artist id
* ex: `curl -H 'sessionId:UUID' http://localhost/wissl/albums/{artist_id}`
* returns:<pre>
    {
      "artist": ARTIST,
      "albums": [
        ALBUM, ...
      ]
    }
</pre>

Get all albums for a given artist.

### songs
* method: `GET`
* path: `/songs/{album_id}`
* param: `album_id` unique album id
* ex: `curl -H 'sessionId:UUID' http://localhost/wissl/songs/5`
* returns:<pre>
    {
      "artist": ARTIST,
      "album": ALBUM,
      "songs": [
        SONG, ...
      ]
    }
</pre>

Get all songs for a given album.

### song
* method: `GET`
* path: `/song/{song_id}`
* param: `song_id` unique song id
* ex: `curl -H 'sessionId:UUID' http://localhost/wissl/song/10`
* returns:<pre>
    {
      "artist": ARTIST,
      "album": ALBUM,
      "song": SONG
    }
</pre>

Get a specific song.

### search
* method: `GET`
* path: `/search/{query}`
* param: `query` search query string
* ex: `curl -H 'sessionId:UUID' http://localhost/wissl/search/foo`
* returns:<pre>
    {
      "artists": [
        ARTIST, ...
      ],
      "albums": [
        ALBUM, ...
      ],
      "songs": [
        SONG, ...
      ]
    }
</pre>

Search for songs, albums and artists with a single string query.
The search query will be matched against song titles, artist names and album names.
For example, the query 'bea' will match artist 'The Beatles' and song 'Heartbeat'.

### stream
* method: `GET`
* path: `/song/{song_id}/stream`
* param: `song_id` unique song id
* ex: `mplayer http://localhost/wissl/song/13/stream?sessionId=UUID`
* ex: `curl -H 'sessionId:UUID' http://localhost/wissl/song/13/stream`
* returns: raw binary content of the song file

Read the music stream for a specific song.

The binary file is directly served, suitable to be read by any audio player that
understands the format.

This method supports seeking using HTTP range headers.
The reponse advertises remaiming byte length using the `Content-Length` header,
and will begin reading at the byte specified by the `range` request header.

### album art
* method: `GET`
* path: `/art/{album_id}`
* param: `album_id` unique album id
* does not require authentication
* ex: `curl http://localhost/wissl/art/6`
* returns: raw binary image content

Download the album art image.

If no artwork is available for this album, you will get a 404 Not Found error.

Authentication is not required because it would prevent caching for some browsers,
since they would try to download the image with a session id as query paremeter.

### music folders
* method: `GET`
* path: `/folders`
* no parameter
* requires admin privileges
* ex: `curl -H 'sessionId:UUID' http://localhost/wissl/folders`
* returns:<pre>
    {
      // array of folder paths relative to the server filesystem
      "folders": [
        STRING, ...
      ]
</pre>

Lists the folders that are indexed as part as the music library.

These folders are relative to the server's local filesystem.

### list folder
* method: `GET`
* path: `/folders/listing`
* param: `directory` optional directory to list, or `$ROOT` to view filesystem root
* requires admin privileges
* ex: `curl -H 'sessionId:UUID' http://localhost/wissl/folders/listing&directory=/tmp/`
* returns:<pre>
    {
      // directory listed
      "directory": STRING.
      // platform path separator
      "separator": STRING
      // parent directory
      "parent": STRING,
      // directories contained
      "listing": [
        STRING, ...
      ]
    }
</pre>

List directory on the server's filesystem.

Does not display single files, only directories.

This method allows an administrator to browse the server's filesystem to look for
a new folder to add to the library indexer path using `/folders/add`.

If the optional `directory` parameter is empty, the directory listed will
be the home of the user running the server process.

If the query parameter is `$ROOT`, this method will list the filesystem root.
This is equivalent to `/` on unix systems, but allows listing all drives on Windows.

### add folder
* method: `POST`
* path: `/folders/add`
* param: `directory` absolute path of the directory to add
* requires admin privileges
* ex: `curl -H 'sessionId:UUID' -d 'directory=/music/ http://localhost/wissl/folders/add`
* does not return anything

Add a folder to the music library indexation system.

This folder will be regularly scanned and the music files it contains will be indexed by the server.

The parameter `directory` is a directory relative to the server's local filesystem.
This directory can be discovered by using the `/folders/listing` method.

### remove folder
* method: `POST`
* path: `/folders/remove`
* param: `directory[]` array of absolute paths to remove from the library indexation system
* requires admin privileges
* ex: `curl -H 'sessionId:UUID' -d 'directory=/tmp&directory=/music' http://localhost/wissl/folders/remove`
* does not return anything

Remove one or more folders from the music library indexation system.

The folders currently indexed can be listed with the method `/folders`.

### indexer status
* method: `GET`
* path: `/indexer/status`
* no parameter
* requires admin privileges
* ex: `curl -H 'sessionId:UUID' http://localhost/wissl/indexer/status`
* returns:<pre>
    {
      // false when idle, true when indexing
      "running": BOOL,
      // when indexing, estimates percent done in [0.0;1.0]
      "percentDone": DOUBLE,
      // when indexing, estimates time left in seconds
      "secondsLeft": INT,
      // when indexing, total songs indexed
      "songsDone": INT,
      // when indexing, total songs to index
      "songsTodo": INT
    }
</pre>

Gets music library indexation system status.

Indexation is triggered when adding or removing a folder.
This method allows an administrator to know if the indexer is running,
and have an estimate of the time left before finishing.

### logs
* method: `POST`
* path: `/logs`
* no parameter
* requires admin privileges
* ex: `curl -H 'sessionId:UUID' http://localhost/wissl/logs`
* returns: log file content as plain text

List log file content.

Logs are rotated after a number of lines have been appended to it.
This method only returns the content of the last file, to investigate on recent events.

### runtime stats
* method: `GET`
* path: `/stats`
* no parameter
* ex: `curl -H 'sessionId:UUID' http://localhost/wissl/stats`
* returns:<pre>
    {
      "stats": {
        // total number of songs indexed
        "songs": INT,
        // total number of albums indexed
        "albums": INT,
        // total number of artists indexed
        "artists": INT,
        // total number of playlists
        "playlists": INT,
        // total number of users
        "users": INT,
        // cumulated duration of all songs in seconds
        "playtime": INT,
        // cumulated downloaded bytes since startup
        "downloaded": INT,
        // time since server startup in seconds
        "uptime": INT
      }
    }
<pre>

Return a short list of runtime statistics properties for the server.


### shutdown
* method: `POST`
* path: `/shutdown`
* no parameter
* requires admin privileges
* ex: `curl -H 'sessionId:UUID' -X POST http://localhost/wissl/shutdown`
* does not return anything

Shutdown the server.

/* This file is part of Wissl - Copyright (C) 2013 Mathieu Schnoor
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.msch.wissl.server;

import java.io.File;
import java.util.HashSet;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import fr.msch.wissl.server.Playlist;
import fr.msch.wissl.server.RuntimeStats;
import fr.msch.wissl.server.Song;

/**
 * Functional test for the following rest server endpoints:
 * <ul>
 * <li>/playlist/create
 * <li>/playlist/create-add
 * <li>/playlist/random
 * <li>/playlist/id/add
 * <li>/playlist/id/remove
 * <li>/playlist/id/songs
 * <li>/playlist/id/song/pos
 * <li>/playlists/remove
 * <li>/playlists
 * <li>/playlists/id
 * </ul>
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
public class TestPlaylist extends TServer {

	public void test() throws Exception {
		HttpClient client = new HttpClient();

		RuntimeStats rt = new RuntimeStats();
		rt.songCount.set(15);
		rt.albumCount.set(5);
		rt.artistCount.set(2);
		rt.playlistCount.set(0);
		rt.userCount.set(2);
		rt.playtime.set(15);
		rt.downloaded.set(0);
		this.addMusicFolder(new File("src/test/resources/data").getAbsolutePath(), rt);

		rt.songCount.set(24);
		rt.albumCount.set(6);
		rt.artistCount.set(3);
		rt.playtime.set(24);
		this.addMusicFolder(new File("src/test/resources/data2").getAbsolutePath(), rt);

		// create playlist with no name: 400
		PostMethod post = new PostMethod(URL + "playlist/create");
		post.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(post);
		assertEquals(400, post.getStatusCode());

		// create playlist 'foo'
		post = new PostMethod(URL + "playlist/create");
		post.addRequestHeader("sessionId", user_sessionId);
		post.addParameter("name", "foo");
		client.executeMethod(post);
		assertEquals(200, post.getStatusCode());
		JSONObject obj = new JSONObject(post.getResponseBodyAsString());
		Playlist foo = new Playlist(obj.getJSONObject("playlist").toString());
		assertEquals("foo", foo.name);
		assertEquals(0, foo.songs);
		assertEquals(0, foo.playtime);

		// create playlist 'foo' again: returns the same playlist
		post = new PostMethod(URL + "playlist/create");
		post.addRequestHeader("sessionId", user_sessionId);
		post.addParameter("name", "foo");
		client.executeMethod(post);
		assertEquals(200, post.getStatusCode());
		obj = new JSONObject(post.getResponseBodyAsString());
		assertEquals(foo,
				new Playlist(obj.getJSONObject("playlist").toString()));

		// create playlist 'foo' as admin: creates another playlist
		post = new PostMethod(URL + "playlist/create");
		post.addRequestHeader("sessionId", admin_sessionId);
		post.addParameter("name", "foo");
		client.executeMethod(post);
		assertEquals(200, post.getStatusCode());
		obj = new JSONObject(post.getResponseBodyAsString());
		Playlist pl = new Playlist(obj.getJSONObject("playlist").toString());
		assertNotSame(foo, pl);

		// playlists for user: 'foo'
		GetMethod get = new GetMethod(URL + "playlists");
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		assertEquals(1, obj.getJSONArray("playlists").length());
		assertEquals(foo, new Playlist(obj.getJSONArray("playlists").get(0)
				.toString()));

		// playlists/id for user: 'foo'
		get = new GetMethod(URL + "playlists/" + user_userId);
		get.addRequestHeader("sessionId", admin_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		assertEquals(1, obj.getJSONArray("playlists").length());
		assertEquals(foo, new Playlist(obj.getJSONArray("playlists").get(0)
				.toString()));

		// remove playlist without argument: 400
		post = new PostMethod(URL + "playlists/remove");
		post.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(post);
		assertEquals(400, post.getStatusCode());

		// remove admin 'foo' playlist as user: 403
		post = new PostMethod(URL + "playlists/remove");
		post.addRequestHeader("sessionId", user_sessionId);
		post.addParameter("playlist_ids[]", "" + pl.id);
		client.executeMethod(post);
		assertEquals(403, post.getStatusCode());

		// remove admin 'foo'
		post = new PostMethod(URL + "playlists/remove");
		post.addRequestHeader("sessionId", admin_sessionId);
		post.addParameter("playlist_ids[]", "" + pl.id);
		client.executeMethod(post);
		assertEquals(204, post.getStatusCode());

		// playlists for admin: 'none'
		get = new GetMethod(URL + "playlists");
		get.addRequestHeader("sessionId", admin_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		assertEquals(0, obj.getJSONArray("playlists").length());

		// playlists/id for admin: 'none'
		get = new GetMethod(URL + "playlists/" + admin_userId);
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		assertEquals(0, obj.getJSONArray("playlists").length());

		// playlist/create-add with no name : 400
		post = new PostMethod(URL + "playlist/create-add");
		post.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(post);
		assertEquals(400, post.getStatusCode());

		// playlist/create-add 'bar' with no songs
		post = new PostMethod(URL + "playlist/create-add");
		post.addRequestHeader("sessionId", user_sessionId);
		post.addParameter("name", "bar");
		client.executeMethod(post);
		assertEquals(200, post.getStatusCode());
		obj = new JSONObject(post.getResponseBodyAsString());
		assertEquals(0, obj.getInt("added"));
		Playlist bar = new Playlist(obj.getJSONObject("playlist").toString());
		assertEquals("bar", bar.name);
		assertEquals(0, bar.playtime);
		assertEquals(0, bar.songs);

		int[] song_ids = new int[4];
		int album_id;

		// search for 'o': 4 songs, 1 album with 1 song
		get = new GetMethod(URL + "search/o");
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		JSONArray songs = obj.getJSONArray("songs");
		for (int i = 0; i < 4; i++) {
			song_ids[i] = songs.getJSONObject(i).getInt("id");
		}
		JSONObject ok = obj.getJSONArray("albums").getJSONObject(0);
		album_id = ok.getInt("id");

		// playlist/create-add 'bar' with songs
		post = new PostMethod(URL + "playlist/create-add");
		post.addRequestHeader("sessionId", user_sessionId);
		post.addParameter("name", "bar");
		post.addParameter("song_ids[]", "" + song_ids[0]);
		post.addParameter("song_ids[]", "" + song_ids[1]);
		post.addParameter("song_ids[]", "" + song_ids[2]);
		post.addParameter("song_ids[]", "" + song_ids[3]);
		post.addParameter("album_ids[]", "" + album_id);
		client.executeMethod(post);
		assertEquals(200, post.getStatusCode());
		obj = new JSONObject(post.getResponseBodyAsString());
		assertEquals(5, obj.getInt("added"));
		bar = new Playlist(obj.getJSONObject("playlist").toString());
		assertEquals("bar", bar.name);
		assertEquals(5, bar.playtime);
		assertEquals(5, bar.songs);

		// check song list in 'bar'
		get = new GetMethod(URL + "playlist/" + bar.id + "/songs");
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		assertEquals("bar", obj.getString("name"));
		JSONArray arr = obj.getJSONArray("playlist");
		assertEquals(5, arr.length());
		for (int i = 0; i < 4; i++) {
			assertEquals(new Song(songs.getJSONObject(i).toString()), new Song(
					arr.getJSONObject(i).toString()));
		}
		Song s5 = new Song(arr.getJSONObject(4).toString());
		assertEquals("Ok", s5.album_name);

		// playlist/remove song as wrong user
		post = new PostMethod(URL + "playlist/" + bar.id + "/remove");
		post.addRequestHeader("sessionId", admin_sessionId);
		post.addParameter("song_ids[]", "" + song_ids[0]);
		client.executeMethod(post);
		assertEquals(403, post.getStatusCode());

		// playlist/remove 2 songs
		post = new PostMethod(URL + "playlist/" + bar.id + "/remove");
		post.addRequestHeader("sessionId", user_sessionId);
		post.addParameter("song_ids[]", "" + song_ids[1]);
		post.addParameter("song_ids[]", "" + song_ids[2]);
		client.executeMethod(post);
		assertEquals(204, post.getStatusCode());

		// check song list
		get = new GetMethod(URL + "playlist/" + bar.id + "/songs");
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		assertEquals("bar", obj.getString("name"));
		arr = obj.getJSONArray("playlist");
		assertEquals(3, arr.length());
		assertEquals(new Song(songs.getJSONObject(0).toString()), new Song(arr
				.getJSONObject(0).toString()));
		assertEquals(new Song(songs.getJSONObject(3).toString()), new Song(arr
				.getJSONObject(1).toString()));
		assertEquals(s5, new Song(arr.getJSONObject(2).toString()));

		// re-check with song/id/pos
		get = new GetMethod(URL + "playlist/" + bar.id + "/song/0");
		get.addRequestHeader("sessionId", admin_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString())
				.getJSONObject("song");
		assertEquals(new Song(songs.getJSONObject(0).toString()),
				new Song(obj.toString()));

		// 2nd song
		get = new GetMethod(URL + "playlist/" + bar.id + "/song/1");
		get.addRequestHeader("sessionId", admin_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString())
				.getJSONObject("song");
		assertEquals(new Song(songs.getJSONObject(3).toString()),
				new Song(obj.toString()));

		// 3rd song
		get = new GetMethod(URL + "playlist/" + bar.id + "/song/2");
		get.addRequestHeader("sessionId", admin_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString())
				.getJSONObject("song");
		assertEquals(s5, new Song(obj.toString()));

		// no more song in playlist
		get = new GetMethod(URL + "playlist/" + bar.id + "/song/3");
		get.addRequestHeader("sessionId", admin_sessionId);
		client.executeMethod(get);
		assertEquals(404, get.getStatusCode());

		// playlist/create-add 'bar' with other songs and clear
		post = new PostMethod(URL + "playlist/create-add");
		post.addRequestHeader("sessionId", user_sessionId);
		post.addParameter("name", "bar");
		post.addParameter("clear", "true");
		post.addParameter("album_ids[]", "" + album_id);
		client.executeMethod(post);
		assertEquals(200, post.getStatusCode());
		obj = new JSONObject(post.getResponseBodyAsString());
		assertEquals(1, obj.getInt("added"));
		bar = new Playlist(obj.getJSONObject("playlist").toString());
		assertEquals("bar", bar.name);
		assertEquals(1, bar.playtime);
		assertEquals(1, bar.songs);

		// playlist/add as admin: 403
		post = new PostMethod(URL + "playlist/" + bar.id + "/add");
		post.addRequestHeader("sessionId", admin_sessionId);
		post.addParameter("clear", "true");
		post.addParameter("song_ids", "" + s5.id);
		client.executeMethod(post);
		assertEquals(403, post.getStatusCode());

		// playlist/add with duplicate songs: 400
		post = new PostMethod(URL + "playlist/" + bar.id + "/add");
		post.addRequestHeader("sessionId", user_sessionId);
		post.addParameter("clear", "true");
		post.addParameter("song_ids[]", "" + song_ids[0]);
		post.addParameter("song_ids[]", "" + song_ids[0]);
		post.addParameter("album_ids[]", "" + album_id);
		client.executeMethod(post);
		assertEquals(500, post.getStatusCode());

		// playlist/add a couple songs w/ clear
		post = new PostMethod(URL + "playlist/" + bar.id + "/add");
		post.addRequestHeader("sessionId", user_sessionId);
		post.addParameter("clear", "true");
		post.addParameter("song_ids[]", "" + song_ids[0]);
		post.addParameter("song_ids[]", "" + song_ids[2]);
		client.executeMethod(post);
		assertEquals(200, post.getStatusCode());
		obj = new JSONObject(post.getResponseBodyAsString());
		assertEquals(2, obj.getInt("added"));
		pl = new Playlist(obj.getJSONObject("playlist").toString());
		assertEquals(2, pl.playtime);
		assertEquals(2, pl.songs);
		assertEquals("bar", pl.name);

		// check song list
		get = new GetMethod(URL + "playlist/" + pl.id + "/songs");
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		assertEquals("bar", obj.getString("name"));
		arr = obj.getJSONArray("playlist");
		assertEquals(2, arr.length());
		assertEquals(new Song(songs.getJSONObject(0).toString()), new Song(arr
				.getJSONObject(0).toString()));
		assertEquals(new Song(songs.getJSONObject(2).toString()), new Song(arr
				.getJSONObject(1).toString()));

		// re-check with song/id/pos
		get = new GetMethod(URL + "playlist/" + pl.id + "/song/0");
		get.addRequestHeader("sessionId", admin_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString())
				.getJSONObject("song");
		assertEquals(new Song(songs.getJSONObject(0).toString()),
				new Song(obj.toString()));

		// 2nd song
		get = new GetMethod(URL + "playlist/" + pl.id + "/song/1");
		get.addRequestHeader("sessionId", admin_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString())
				.getJSONObject("song");
		assertEquals(new Song(songs.getJSONObject(2).toString()),
				new Song(obj.toString()));

		// no more song in playlist
		get = new GetMethod(URL + "playlist/" + pl.id + "/song/2");
		get.addRequestHeader("sessionId", admin_sessionId);
		client.executeMethod(get);
		assertEquals(404, get.getStatusCode());

		// random playlist with no name : 400
		post = new PostMethod(URL + "playlist/random");
		post.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(post);
		assertEquals(400, post.getStatusCode());

		// random playlist with no song number : 400
		post = new PostMethod(URL + "playlist/random");
		post.addRequestHeader("sessionId", user_sessionId);
		post.addParameter("name", "toto");
		client.executeMethod(post);
		assertEquals(400, post.getStatusCode());

		// random playlist with too many songs: 400
		post = new PostMethod(URL + "playlist/random");
		post.addRequestHeader("sessionId", user_sessionId);
		post.addParameter("number", "60");
		post.addParameter("name", "toto");
		client.executeMethod(post);
		assertEquals(400, post.getStatusCode());

		// random playlist 'toto'
		post = new PostMethod(URL + "playlist/random");
		post.addRequestHeader("sessionId", user_sessionId);
		post.addParameter("number", "15");
		post.addParameter("name", "toto");
		client.executeMethod(post);
		assertEquals(200, post.getStatusCode());
		obj = new JSONObject(post.getResponseBodyAsString());
		assertEquals(15, obj.getInt("added"));
		Playlist rand = new Playlist(obj.getJSONObject("playlist").toString());
		assertEquals(15, rand.playtime);
		assertEquals(15, rand.songs);
		assertEquals("toto", rand.name);

		// get first song
		get = new GetMethod(URL + "playlist/" + rand.id + "/song/0");
		get.addRequestHeader("sessionId", admin_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		Song s = new Song(new JSONObject(get.getResponseBodyAsString())
				.getJSONObject("song").toString());
		assertEquals(s.id, obj.getInt("first_song"));

		// all random songs should fit in this hashset
		HashSet<Song> randSet = new HashSet<Song>(15);
		get = new GetMethod(URL + "playlist/" + rand.id + "/songs");
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		assertEquals("toto", obj.get("name"));
		arr = obj.getJSONArray("playlist");
		assertEquals(15, arr.length());
		for (int i = 0; i < arr.length(); i++) {
			Song ss = new Song(arr.getJSONObject(i).toString());
			assertFalse(randSet.contains(ss));
			randSet.add(ss);
		}

		// there are 24 songs total in library, try to create a 30 songs playlist
		post = new PostMethod(URL + "playlist/random");
		post.addRequestHeader("sessionId", user_sessionId);
		post.addParameter("number", "30");
		post.addParameter("name", "titi");
		client.executeMethod(post);
		assertEquals(200, post.getStatusCode());
		obj = new JSONObject(post.getResponseBodyAsString());
		assertEquals(24, obj.getInt("added"));
		rand = new Playlist(obj.getJSONObject("playlist").toString());
		assertEquals(24, rand.playtime);
		assertEquals(24, rand.songs);
		assertEquals("titi", rand.name);

		// all 24 random songs should fit in this hashset
		randSet = new HashSet<Song>(24);
		get = new GetMethod(URL + "playlist/" + rand.id + "/songs");
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		assertEquals("titi", obj.get("name"));
		arr = obj.getJSONArray("playlist");
		assertEquals(24, arr.length());
		for (int i = 0; i < arr.length(); i++) {
			Song ss = new Song(arr.getJSONObject(i).toString());
			assertFalse(randSet.contains(ss));
			randSet.add(ss);
		}

		// re-create 'titi' with 10 songs
		post = new PostMethod(URL + "playlist/random");
		post.addRequestHeader("sessionId", user_sessionId);
		post.addParameter("number", "10");
		post.addParameter("name", "titi");
		client.executeMethod(post);
		assertEquals(200, post.getStatusCode());
		obj = new JSONObject(post.getResponseBodyAsString());
		assertEquals(10, obj.getInt("added"));
		rand = new Playlist(obj.getJSONObject("playlist").toString());
		assertEquals(10, rand.playtime);
		assertEquals(10, rand.songs);
		assertEquals("titi", rand.name);

		// playlists for user: 'foo', 'bar', 'toto', 'titi'
		get = new GetMethod(URL + "playlists");
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		assertEquals(4, obj.getJSONArray("playlists").length());
		foo = new Playlist(obj.getJSONArray("playlists").get(0).toString());
		bar = new Playlist(obj.getJSONArray("playlists").get(1).toString());
		Playlist toto = new Playlist(obj.getJSONArray("playlists").get(2)
				.toString());
		Playlist titi = new Playlist(obj.getJSONArray("playlists").get(3)
				.toString());
		assertEquals("foo", foo.name);
		assertEquals(0, foo.songs);
		assertEquals("bar", bar.name);
		assertEquals(2, bar.songs);
		assertEquals("toto", toto.name);
		assertEquals(15, toto.songs);
		assertEquals("titi", titi.name);
		assertEquals(10, titi.songs);

		// remove all 4 user playlists
		post = new PostMethod(URL + "playlists/remove");
		post.addRequestHeader("sessionId", user_sessionId);
		post.addParameter("playlist_ids[]", "" + foo.id);
		post.addParameter("playlist_ids[]", "" + bar.id);
		post.addParameter("playlist_ids[]", "" + toto.id);
		post.addParameter("playlist_ids[]", "" + titi.id);
		client.executeMethod(post);
		assertEquals(204, post.getStatusCode());

		// check there are no more user playlists
		get = new GetMethod(URL + "playlists");
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		assertEquals(0, obj.getJSONArray("playlists").length());

	}
}

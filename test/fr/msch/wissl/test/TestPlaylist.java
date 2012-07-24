/* This file is part of Wissl - Copyright (C) 2012 Mathieu Schnoor
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
package fr.msch.wissl.test;

import java.io.File;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.codehaus.jettison.json.JSONObject;

import fr.msch.wissl.server.Playlist;
import fr.msch.wissl.server.RuntimeStats;

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
		this.addMusicFolder(new File("test/data").getAbsolutePath(), rt);

		rt.songCount.set(24);
		rt.albumCount.set(6);
		rt.artistCount.set(3);
		rt.playtime.set(24);
		this.addMusicFolder(new File("test/data2").getAbsolutePath(), rt);

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

	}
}

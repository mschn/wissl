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

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import fr.msch.wissl.server.RuntimeStats;

/**
 * Functional test for the following rest server endpoints:
 * <ul>
 * <li>edit/artist 
 * <li>edit/album
 * <li>edit/song
 * </ul>
 * 
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
public class TestEdition extends TServer {

	public void test() throws Exception {
		HttpClient client = new HttpClient();
		RuntimeStats rt = new RuntimeStats();
		rt.songCount.set(15);
		rt.albumCount.set(5);
		rt.artistCount.set(2);
		rt.userCount.set(2);
		rt.playlistCount.set(0);
		rt.playtime.set(15);
		rt.downloaded.set(0);
		this.addMusicFolder("src/test/resources/data", rt);

		// 401: requires admin
		PostMethod post = new PostMethod(URL + "edit/artist");
		post.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(post);
		assertEquals(401, post.getStatusCode());
		post = new PostMethod(URL + "edit/album");
		post.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(post);
		assertEquals(401, post.getStatusCode());
		post = new PostMethod(URL + "edit/song");
		post.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(post);
		assertEquals(401, post.getStatusCode());

		// 404: unknown param
		post = new PostMethod(URL + "edit/artist");
		post.addRequestHeader("sessionId", this.admin_sessionId);
		post.addParameter("artist_ids[]", "99999");
		client.executeMethod(post);
		assertEquals(404, post.getStatusCode());
		post = new PostMethod(URL + "edit/album");
		post.addRequestHeader("sessionId", this.admin_sessionId);
		post.addParameter("album_ids[]", "99999");
		client.executeMethod(post);
		assertEquals(404, post.getStatusCode());
		post = new PostMethod(URL + "edit/song");
		post.addRequestHeader("sessionId", this.admin_sessionId);
		post.addParameter("song_ids[]", "99999");
		client.executeMethod(post);
		assertEquals(404, post.getStatusCode());

		// get artist id for 'Foo'
		GetMethod get = new GetMethod(URL + "search/foo");
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		JSONObject obj = new JSONObject(get.getResponseBodyAsString());
		int foo_id = obj.getJSONArray("artists").getJSONObject(0).getInt("id");
		int bob_id = -1;

		// rename artist 'Foo' to 'Glouglou'
		post = new PostMethod(URL + "edit/artist");
		post.addRequestHeader("sessionId", this.admin_sessionId);
		post.addParameter("artist_ids[]", "" + foo_id);
		post.addParameter("artist_name", "Glouglou");
		client.executeMethod(post);
		assertEquals(204, post.getStatusCode());
		// wait for indexer
		checkStats(rt);

		// list artists: 'Foo' and 'Glouglou'
		get = new GetMethod(URL + "artists");
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		JSONArray arr = obj.getJSONArray("artists");
		assertEquals(2, arr.length());
		for (int i = 0; i < 2; i++) {
			obj = arr.getJSONObject(i).getJSONObject("artist");
			String name = obj.getString("name");
			assertTrue(name.equals("Glouglou") || name.equals("Bob"));
			if (name.equals("Bob")) {
				bob_id = obj.getInt("id");
			} else {
				foo_id = obj.getInt("id");
			}
		}

		// rename both artists to 'Abc- 12$'
		post = new PostMethod(URL + "edit/artist");
		post.addRequestHeader("sessionId", this.admin_sessionId);
		post.addParameter("artist_ids[]", "" + foo_id);
		post.addParameter("artist_ids[]", "" + bob_id);
		post.addParameter("artist_name", "Abc- 12$");
		client.executeMethod(post);
		assertEquals(204, post.getStatusCode());

		// wait for indexer
		rt.artistCount.set(1);
		checkStats(rt);

		// list artists: 'Abc- 12$'
		get = new GetMethod(URL + "artists");
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		arr = obj.getJSONArray("artists");
		assertEquals(1, arr.length());
		obj = arr.getJSONObject(0).getJSONObject("artist");
		String name = obj.getString("name");
		assertTrue(name.equals("Abc- 12$"));

		// list albums
		get = new GetMethod(URL + "albums/" + obj.getInt("id"));
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		arr = obj.getJSONArray("albums");

		// revert data as it was before using album edition
		post = new PostMethod(URL + "edit/album");
		post.addRequestHeader("sessionId", this.admin_sessionId);
		post.addParameter("artist_name", "Bob");
		for (int i = 0; i < arr.length(); i++) {
			obj = arr.getJSONObject(i);
			int id = obj.getInt("id");
			String n = obj.getString("name");
			if (n.equals("Gni") || n.equals("Ok") || n.equals("Qux")) {
				System.out.println(id);
				post.addParameter("album_ids[]", "" + id);
			}
		}
		client.executeMethod(post);
		assertEquals(204, post.getStatusCode());
		post = new PostMethod(URL + "edit/album");
		post.addRequestHeader("sessionId", this.admin_sessionId);
		post.addParameter("artist_name", "Foo");
		for (int i = 0; i < arr.length(); i++) {
			obj = arr.getJSONObject(i);
			String n = obj.getString("name");
			int id = obj.getInt("id");
			if (n.equals("Bar") || n.equals("Baz")) {
				post.addParameter("album_ids[]", "" + id);
			}
		}
		client.executeMethod(post);
		assertEquals(204, post.getStatusCode());

		rt.songCount.set(15);
		rt.albumCount.set(5);
		rt.artistCount.set(2);
		rt.userCount.set(2);
		rt.playlistCount.set(0);
		rt.playtime.set(15);
		rt.downloaded.set(0);
		checkStats(rt);

		// get album id of 'Gni'
		get = new GetMethod(URL + "search/Gni");
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		int gni_id = obj.getJSONArray("albums").getJSONObject(0).getInt("id");

		// rename album 'Gni' to 'Pwet', set date to '1664', genre to 'zouk'
		post = new PostMethod(URL + "edit/album");
		post.addRequestHeader("sessionId", admin_sessionId);
		post.addParameter("album_ids[]", "" + gni_id);
		post.addParameter("genre", "zouk");
		post.addParameter("date", "1664");
		post.addParameter("album_name", "Pwet");
		client.executeMethod(post);
		assertEquals(204, post.getStatusCode());

		// wait for indexer
		checkStats(rt);

		// check
		get = new GetMethod(URL + "search/Pwet");
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		obj = obj.getJSONArray("albums").getJSONObject(0);
		assertEquals("Pwet", obj.getString("name"));
		assertEquals("zouk", obj.getString("genre"));
		assertEquals("1664", obj.getString("date"));

	}
}

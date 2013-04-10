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

		// list artists: 'Bob' and 'Glouglou'
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

		// check album Pwet
		get = new GetMethod(URL + "search/Pwet");
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		obj = obj.getJSONArray("albums").getJSONObject(0);
		int pwet_id = obj.getInt("id");
		assertEquals("Pwet", obj.getString("name"));
		assertEquals("zouk", obj.getString("genre"));
		assertEquals("1664", obj.getString("date"));

		// get ids for 'Qux'
		get = new GetMethod(URL + "search/Qux");
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		obj = obj.getJSONArray("albums").getJSONObject(0);
		int qux_id = obj.getInt("id");

		// change genre of albums Pwet and Qux to 'hardcore testing'
		post = new PostMethod(URL + "edit/album");
		post.addRequestHeader("sessionId", admin_sessionId);
		post.addParameter("album_ids[]", "" + pwet_id);
		post.addParameter("album_ids[]", "" + qux_id);
		post.addParameter("genre", "hardcore testing");
		client.executeMethod(post);
		assertEquals(204, post.getStatusCode());

		// wait for indexer
		checkStats(rt);

		// checking
		get = new GetMethod(URL + "search/Qux");
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		obj = obj.getJSONArray("albums").getJSONObject(0);
		qux_id = obj.getInt("id");
		assertEquals("hardcore testing", obj.getString("genre"));
		get = new GetMethod(URL + "search/Pwet");
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		obj = obj.getJSONArray("albums").getJSONObject(0);
		pwet_id = obj.getInt("id");
		assertEquals("hardcore testing", obj.getString("genre"));

		// reset album 'Gni'
		post = new PostMethod(URL + "edit/album");
		post.addRequestHeader("sessionId", admin_sessionId);
		post.addParameter("album_ids[]", "" + pwet_id);
		post.addParameter("album_name", "Gni");
		post.addParameter("genre", "aggressive raggae");
		post.addParameter("date", "2009");
		client.executeMethod(post);
		assertEquals(204, post.getStatusCode());

		// reset album 'Qux'
		post = new PostMethod(URL + "edit/album");
		post.addRequestHeader("sessionId", admin_sessionId);
		post.addParameter("album_ids[]", "" + qux_id);
		post.addParameter("genre", "death jazz");
		client.executeMethod(post);
		assertEquals(204, post.getStatusCode());

		// find song 'Thirteen'
		get = new GetMethod(URL + "search/thirteen");
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		obj = obj.getJSONArray("songs").getJSONObject(0);
		int id_13 = obj.getInt("id");

		// song 'Thirteen' : set position to 14, disc number to 1, name to '13'
		post = new PostMethod(URL + "edit/song");
		post.addRequestHeader("sessionId", admin_sessionId);
		post.addParameter("song_ids[]", "" + id_13);
		post.addParameter("song_title", "13");
		post.addParameter("position", "14");
		post.addParameter("disc_no", "1");
		client.executeMethod(post);
		assertEquals(204, post.getStatusCode());

		// wait for indexer
		checkStats(rt);

		// check song 13
		get = new GetMethod(URL + "search/13");
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		obj = obj.getJSONArray("songs").getJSONObject(0);
		id_13 = obj.getInt("id");
		assertEquals("13", obj.getString("title"));
		assertEquals(14, obj.getInt("position"));
		assertEquals(1, obj.getInt("disc_no"));

		// restore song 13
		post = new PostMethod(URL + "edit/song");
		post.addRequestHeader("sessionId", admin_sessionId);
		post.addParameter("song_ids[]", "" + id_13);
		post.addParameter("song_title", "Thirteen");
		post.addParameter("position", "1");
		post.addParameter("disc_no", "2");
		client.executeMethod(post);
		assertEquals(204, post.getStatusCode());

		// find songs 'Four' and 'Fourteen'
		get = new GetMethod(URL + "search/four");
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		arr = obj.getJSONArray("songs");
		assertEquals(2, arr.length());
		int id_4 = -1, id_14 = -1;
		for (int i = 0; i < 2; i++) {
			obj = arr.getJSONObject(i);
			int id = obj.getInt("id");
			String title = obj.getString("title");
			if (title.equals("Four")) {
				id_4 = id;
			} else if (title.equals("Fourteen")) {
				id_14 = id;
			}
		}

		// move songs 'Four' to album 'chaleur tournante' by 'bosch'
		post = new PostMethod(URL + "edit/song");
		post.addRequestHeader("sessionId", admin_sessionId);
		post.addParameter("song_ids[]", "" + id_4);
		post.addParameter("album_name", "chaleur tournante");
		post.addParameter("artist_name", "bosch");
		client.executeMethod(post);
		assertEquals(204, post.getStatusCode());
		
		// move songs 'Fourteen' to album 'chaleur tournante' by 'bosch'
		post = new PostMethod(URL + "edit/song");
		post.addRequestHeader("sessionId", admin_sessionId);
		post.addParameter("song_ids[]", "" + id_14);
		post.addParameter("album_name", "chaleur tournante");
		post.addParameter("artist_name", "bosch");
		client.executeMethod(post);
		assertEquals(204, post.getStatusCode());

		// wait for indexer
		rt.artistCount.set(3);
		rt.albumCount.set(6);
		checkStats(rt);

		// check songs
		// find songs 'Four' and 'Fourteen'
		get = new GetMethod(URL + "search/four");
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		arr = obj.getJSONArray("songs");
		assertEquals(2, arr.length());
		for (int i = 0; i < 2; i++) {
			obj = arr.getJSONObject(i);
			int id = obj.getInt("id");
			String title = obj.getString("title");
			assertEquals("bosch", obj.getString("artist_name"));
			assertEquals("chaleur tournante", obj.getString("album_name"));
			assertTrue(title.equals("Four") || title.equals("Fourteen"));
			if (title.equals("Four")) {
				id_4 = id;
			} else if (title.equals("Fourteen")) {
				id_14 = id;
			}
		}

		// revert both songs
		post = new PostMethod(URL + "edit/song");
		post.addRequestHeader("sessionId", admin_sessionId);
		post.addParameter("song_ids[]", "" + id_4);
		post.addParameter("album_name", "Bar");
		post.addParameter("artist_name", "Foo");
		client.executeMethod(post);
		assertEquals(204, post.getStatusCode());

		post = new PostMethod(URL + "edit/song");
		post.addRequestHeader("sessionId", admin_sessionId);
		post.addParameter("song_ids[]", "" + id_14);
		post.addParameter("album_name", "Gni");
		post.addParameter("artist_name", "Bob");
		client.executeMethod(post);
		assertEquals(204, post.getStatusCode());

		get = new GetMethod(URL + "search/Bob");
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		bob_id = obj.getJSONArray("artists").getJSONObject(0).getInt("id");

		// find albums 'Ok' and 'Qux'
		get = new GetMethod(URL + "albums/" + bob_id);
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		arr = obj.getJSONArray("albums");
		assertEquals(3, arr.length());
		int ok_id = -1;
		qux_id = -1;
		for (int i = 0; i < 3; i++) {
			obj = arr.getJSONObject(i);
			int id = obj.getInt("id");
			name = obj.getString("name");
			if (name.equals("Ok")) {
				ok_id = id;
			} else if (name.equals("Qux")) {
				qux_id = id;
			}
		}

		// merge albums 'Ok' and 'Qux' to album 'okux'
		post = new PostMethod(URL + "edit/album");
		post.addRequestHeader("sessionId", admin_sessionId);
		post.addParameter("album_ids[]", "" + ok_id);
		post.addParameter("album_ids[]", "" + qux_id);
		post.addParameter("album_name", "okux");
		client.executeMethod(post);
		assertEquals(204, post.getStatusCode());

		// check new album 'okux'
		get = new GetMethod(URL + "albums/" + bob_id);
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		arr = obj.getJSONArray("albums");

		assertEquals(2, arr.length());
		int okux_id = -1;
		for (int i = 0; i < 2; i++) {
			obj = arr.getJSONObject(i);
			int id = obj.getInt("id");
			name = obj.getString("name");
			if (name.equals("okux")) {
				okux_id = id;
			}
		}

		get = new GetMethod(URL + "songs/" + okux_id);
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		arr = obj.getJSONArray("songs");
		obj = obj.getJSONObject("album");

		assertEquals(3, arr.length());
		assertEquals(3, obj.getInt("songs"));
		int id_8 = -1, id_9 = -1, id_15 = -1;
		for (int i = 0; i < 3; i++) {
			obj = arr.getJSONObject(i);
			int id = obj.getInt("id");
			String title = obj.getString("title");
			if (title.equals("Eight")) {
				id_8 = id;
			} else if (title.equals("Nine")) {
				id_9 = id;
			} else if (title.equals("Fifteen")) {
				id_15 = id;
			}
		}

		// revert data
		post = new PostMethod(URL + "edit/song");
		post.addRequestHeader("sessionId", admin_sessionId);
		post.addParameter("song_ids[]", "" + id_8);
		post.addParameter("song_ids[]", "" + id_9);
		post.addParameter("album_name", "Qux");
		client.executeMethod(post);
		assertEquals(204, post.getStatusCode());
		post = new PostMethod(URL + "edit/song");
		post.addRequestHeader("sessionId", admin_sessionId);
		post.addParameter("song_ids[]", "" + id_15);
		post.addParameter("album_name", "Ok");
		client.executeMethod(post);
		assertEquals(204, post.getStatusCode());
	}
}

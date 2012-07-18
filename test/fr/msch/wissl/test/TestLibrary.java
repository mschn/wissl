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
import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import fr.msch.wissl.server.Album;
import fr.msch.wissl.server.Artist;
import fr.msch.wissl.server.Song;

/**
 * Functional test for the following rest server endpoints:
 * <ul>
 * <li>/stats
 * <li>/artists
 * <li>/albums/id
 * <li>/songs/id
 * <li>/song/id
 * <li>/song/id/stream
 * <li>/art/id
 * <li>/search
 * </ul>
 * 
 * @author mathieu.schnoor@gmail.com
 * 
 */
public class TestLibrary extends TServer {

	public void test() throws Exception {
		HttpClient client = new HttpClient();

		// add mp3 folder to server indexer
		this.addMusicFolder();

		// wait for indexer to finish
		boolean done = false;
		do {
			GetMethod get = new GetMethod(URL + "stats");
			get.addRequestHeader("sessionId", this.user_sessionId);
			client.executeMethod(get);
			Assert.assertEquals(200, get.getStatusCode());
			JSONObject obj = new JSONObject(get.getResponseBodyAsString());
			JSONObject stats = obj.getJSONObject("stats");

			// song count is updated when indexer finishes
			if (stats.getInt("songs") > 0) {
				done = true;
				Assert.assertEquals(15, stats.getInt("songs"));
				Assert.assertEquals(5, stats.getInt("albums"));
				Assert.assertEquals(2, stats.getInt("artists"));
				Assert.assertEquals(0, stats.getInt("playlists"));
				Assert.assertEquals(2, stats.getInt("users"));
				Assert.assertEquals(15, stats.getInt("playtime"));
				Assert.assertEquals(0, stats.getInt("downloaded"));
			}
			Thread.sleep(100);
		} while (!done);

		// get artists list
		GetMethod get = new GetMethod(URL + "artists");
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		Assert.assertEquals(200, get.getStatusCode());
		JSONObject obj = new JSONObject(get.getResponseBodyAsString());
		JSONArray artists = obj.getJSONArray("artists");
		Assert.assertEquals(2, artists.length());
		for (int i = 0; i < artists.length(); i++) {
			Artist artist = new Artist(artists.getJSONObject(i)
					.getJSONObject("artist").toString());
			JSONArray artworks = artists.getJSONObject(i).getJSONArray(
					"artworks");
			Assert.assertEquals(getArtist(artist.name), artist);

			for (Album album : getAlbums(artist)) {
				assertEquals(getAlbum(album.name), album);

				boolean art = false;
				for (int j = 0; j < artworks.length(); j++) {
					if (artworks.getInt(j) == album.id) {
						art = true;
					}
				}
				assertEquals(album.has_art, art);

				// check artwork is present
				get = new GetMethod(URL + "art/" + album.id);
				client.executeMethod(get);
				if (art) {
					Assert.assertEquals(200, get.getStatusCode());
					Assert.assertEquals("image/jpeg",
							get.getResponseHeader("Content-Type").getValue());
				} else {
					Assert.assertEquals(404, get.getStatusCode());
				}

				for (Song song : getSongs(album, artist)) {
					Song ex_song = getSong(song.title);
					assertEquals(ex_song, song);

					get = new GetMethod(URL + "song/" + song.id);
					get.addRequestHeader("sessionId", user_sessionId);
					client.executeMethod(get);
					Assert.assertEquals(200, get.getStatusCode());

					obj = new JSONObject(get.getResponseBodyAsString());
					Song s = new Song(obj.getJSONObject("song").toString());
					Album al = new Album(obj.getJSONObject("album").toString());
					Artist ar = new Artist(obj.getJSONObject("artist")
							.toString());
					Assert.assertEquals(ex_song, s);
					Assert.assertEquals(getArtist(artist.name), ar);
					Assert.assertEquals(getAlbum(album.name), al);

					// check that the stream works
					get = new GetMethod(URL + "song/" + song.id
							+ "/stream?sessionId=" + user_sessionId);
					client.executeMethod(get);
					Assert.assertEquals(200, get.getStatusCode());
					int len = (int) new File(ex_song.filepath).length();
					int len2 = Integer.parseInt(get.getResponseHeader(
							"Content-Length").getValue());
					Assert.assertEquals(len, len2);
					Assert.assertEquals("audio/mpeg",
							get.getResponseHeader("Content-Type").getValue());
				}
			}
		}

		// search/ should be 404
		get = new GetMethod(URL + "search/");
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		assertEquals(404, get.getStatusCode());

		// search/foo should return only artist 'Foo'
		get = new GetMethod(URL + "search/foo?sessionId=" + user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		assertEquals(0, obj.getJSONArray("albums").length());
		assertEquals(0, obj.getJSONArray("songs").length());
		artists = obj.getJSONArray("artists");
		assertEquals(1, artists.length());
		assertEquals(getArtist("Foo"), new Artist(artists.get(0).toString()));

		// search/y should return nothing
		get = new GetMethod(URL + "search/y");
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		assertEquals(0, obj.getJSONArray("albums").length());
		assertEquals(0, obj.getJSONArray("artists").length());
		assertEquals(0, obj.getJSONArray("songs").length());

		// search/te should return 4 songs: ten,thirteen,fourteen,fifteen
		get = new GetMethod(URL + "search/te");
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		assertEquals(0, obj.getJSONArray("albums").length());
		assertEquals(0, obj.getJSONArray("artists").length());
		JSONArray songs = obj.getJSONArray("songs");
		assertEquals(4, songs.length());
		for (int i = 0; i < songs.length(); i++) {
			Song s = new Song(songs.get(i).toString());
			assertTrue(s.title.equals("Ten") || s.title.equals("Thirteen")
					|| s.title.equals("Fourteen") || s.title.equals("Fifteen"));
			assertEquals(getSong(s.title), s);
		}

		// search/o shoud return 1 album, 2 artists, 4 songs
		get = new GetMethod(URL + "search/o");
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		JSONArray albums = obj.getJSONArray("albums");
		artists = obj.getJSONArray("artists");
		songs = obj.getJSONArray("songs");
		assertEquals(1, albums.length());
		assertEquals(getAlbum("Ok"), new Album(albums.get(0).toString()));
		assertEquals(2, artists.length());
		for (int i = 0; i < artists.length(); i++) {
			Artist a = new Artist(artists.get(i).toString());
			assertTrue(a.name.equals("Bob") || a.name.equals("Foo"));
			assertEquals(getArtist(a.name), a);
		}
		assertEquals(4, songs.length());
		for (int i = 0; i < songs.length(); i++) {
			Song s = new Song(songs.get(i).toString());
			assertTrue(s.title.equals("Fourteen") || s.title.equals("Four")
					|| s.title.equals("Two") || s.title.equals("One"));
			assertEquals(getSong(s.title), s);
		}

	}

	private List<Album> getAlbums(Artist artist) throws Exception {
		HttpClient client = new HttpClient();
		GetMethod get = new GetMethod(URL + "albums/" + artist.id);
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		Assert.assertEquals(200, get.getStatusCode());

		JSONObject obj = new JSONObject(get.getResponseBodyAsString());
		Artist a2 = new Artist(obj.getString("artist"));
		assertEquals(artist, a2);

		JSONArray albums = obj.getJSONArray("albums");
		assertEquals(artist.albums, albums.length());
		ArrayList<Album> ret = new ArrayList<Album>();

		for (int j = 0; j < albums.length(); j++) {
			Album album = new Album(albums.getJSONObject(j).toString());
			ret.add(album);
		}
		return ret;
	}

	private List<Song> getSongs(Album album, Artist artist) throws Exception {
		HttpClient client = new HttpClient();
		GetMethod get = new GetMethod(URL + "songs/" + album.id);
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		Assert.assertEquals(200, get.getStatusCode());

		JSONObject obj = new JSONObject(get.getResponseBodyAsString());
		Artist a2 = new Artist(obj.getString("artist"));
		assertEquals(artist, a2);

		Album a3 = new Album(obj.getString("album"));
		assertEquals(album, a3);

		JSONArray songs = obj.getJSONArray("songs");
		assertEquals(album.songs, songs.length());
		ArrayList<Song> ret = new ArrayList<Song>();

		for (int j = 0; j < songs.length(); j++) {
			Song song = new Song(songs.getJSONObject(j).toString());
			ret.add(song);
		}
		return ret;
	}

	private static Artist getArtist(String name) {
		Artist a = new Artist();
		a.name = name;
		if (name.equals("Bob")) {
			a.playtime = 8;
			a.songs = 8;
			a.albums = 3;
		} else if (name.equals("Foo")) {
			a.playtime = 7;
			a.songs = 7;
			a.albums = 2;
		} else {
			throw new IllegalArgumentException("Unknown artist " + name);
		}
		return a;
	}

	private static Album getAlbum(String name) {
		Album a = new Album();
		a.name = name;
		if (name.equals("Ok")) {
			a.date = "1977";
			a.genre = "smooth punk";
			a.songs = 1;
			a.playtime = 1;
			a.artist_name = "Bob";
		} else if (name.equals("Qux")) {
			a.date = "1982";
			a.genre = "death jazz";
			a.songs = 2;
			a.playtime = 2;
			a.artist_name = "Bob";
		} else if (name.equals("Gni")) {
			a.date = "2009";
			a.genre = "aggressive raggae";
			a.songs = 5;
			a.playtime = 5;
			a.artist_name = "Bob";
		} else if (name.equals("Baz")) {
			a.date = "2000";
			a.genre = "testcore";
			a.songs = 3;
			a.playtime = 3;
			a.artist_name = "Foo";
		} else if (name.equals("Bar")) {
			a.date = "2002";
			a.genre = "testcore";
			a.songs = 4;
			a.playtime = 4;
			a.artist_name = "Foo";
		} else {
			throw new IllegalArgumentException("Unknown album " + name);
		}
		return a;
	}

	private static Song getSong(String title) {
		Song s = new Song();
		s.title = title;
		if (title.equals("One")) {
			s.position = 1;
			s.duration = 1;
			s.format = "audio/mp3";
			s.disc_no = 0;
			s.album_name = "Bar";
			s.artist_name = "Foo";
			s.filepath = "test/data/1.mp3";
		} else if (title.equals("Two")) {
			s.position = 2;
			s.duration = 1;
			s.format = "audio/mp3";
			s.disc_no = 0;
			s.album_name = "Bar";
			s.artist_name = "Foo";
			s.filepath = "test/data/2.mp3";
		} else if (title.equals("Three")) {
			s.position = 3;
			s.duration = 1;
			s.format = "audio/mp3";
			s.disc_no = 0;
			s.album_name = "Bar";
			s.artist_name = "Foo";
			s.filepath = "test/data/3.mp3";
		} else if (title.equals("Four")) {
			s.position = 4;
			s.duration = 1;
			s.format = "audio/mp3";
			s.disc_no = 0;
			s.album_name = "Bar";
			s.artist_name = "Foo";
			s.filepath = "test/data/4.mp3";
		} else if (title.equals("Five")) {
			s.position = 1;
			s.duration = 1;
			s.format = "audio/mp3";
			s.disc_no = 0;
			s.album_name = "Baz";
			s.artist_name = "Foo";
			s.filepath = "test/data/5.mp3";
		} else if (title.equals("Six")) {
			s.position = 2;
			s.duration = 1;
			s.format = "audio/mp3";
			s.disc_no = 0;
			s.album_name = "Baz";
			s.artist_name = "Foo";
			s.filepath = "test/data/6.mp3";
		} else if (title.equals("Seven")) {
			s.position = 3;
			s.duration = 1;
			s.format = "audio/mp3";
			s.disc_no = 0;
			s.album_name = "Baz";
			s.artist_name = "Foo";
			s.filepath = "test/data/7.mp3";
		} else if (title.equals("Eight")) {
			s.position = 1;
			s.duration = 1;
			s.format = "audio/mp3";
			s.disc_no = 0;
			s.album_name = "Qux";
			s.artist_name = "Bob";
			s.filepath = "test/data/8.mp3";
		} else if (title.equals("Nine")) {
			s.position = 2;
			s.duration = 1;
			s.format = "audio/mp3";
			s.disc_no = 0;
			s.album_name = "Qux";
			s.artist_name = "Bob";
			s.filepath = "test/data/9.mp3";
		} else if (title.equals("Ten")) {
			s.position = 1;
			s.duration = 1;
			s.format = "audio/mp3";
			s.disc_no = 1;
			s.album_name = "Gni";
			s.artist_name = "Bob";
			s.filepath = "test/data/10.mp3";
		} else if (title.equals("Eleven")) {
			s.position = 2;
			s.duration = 1;
			s.format = "audio/mp3";
			s.disc_no = 1;
			s.album_name = "Gni";
			s.artist_name = "Bob";
			s.filepath = "test/data/11.mp3";
		} else if (title.equals("Twelve")) {
			s.position = 3;
			s.duration = 1;
			s.format = "audio/mp3";
			s.disc_no = 1;
			s.album_name = "Gni";
			s.artist_name = "Bob";
			s.filepath = "test/data/12.mp3";
		} else if (title.equals("Thirteen")) {
			s.position = 1;
			s.duration = 1;
			s.format = "audio/mp3";
			s.disc_no = 2;
			s.album_name = "Gni";
			s.artist_name = "Bob";
			s.filepath = "test/data/13.mp3";
		} else if (title.equals("Fourteen")) {
			s.position = 2;
			s.duration = 1;
			s.format = "audio/mp3";
			s.disc_no = 2;
			s.album_name = "Gni";
			s.artist_name = "Bob";
			s.filepath = "test/data/14.mp3";
		} else if (title.equals("Fifteen")) {
			s.position = 1;
			s.duration = 1;
			s.format = "audio/mp3";
			s.disc_no = 0;
			s.album_name = "Ok";
			s.artist_name = "Bob";
			s.filepath = "test/data/15.mp3";
		} else {
			throw new IllegalArgumentException("Unknown song " + title);
		}
		return s;
	}
}

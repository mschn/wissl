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
package fr.msch.wissl.server;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.codehaus.jettison.json.JSONObject;
import org.jboss.resteasy.spi.NotFoundException;

import fr.msch.wissl.common.Config;
import fr.msch.wissl.server.exception.ForbiddenException;
import fr.msch.wissl.server.exception.SecurityError;

/**
 * RESTful HTTP frontend using JBoss RESTEASY
 * 
 * 
 * @author mathieu.schnoor@gmail.com
 * 
 */
@Path("/")
@Produces("application/json;charset=UTF-8")
public class REST {

	@Context
	private HttpServletRequest request;

	@Context
	private HttpServletResponse response;

	@HeaderParam("sessionId")
	private String sessionIdHeader;

	@QueryParam("sessionId")
	private String sessionIdGet;

	private void log(Session session, long nanoStartTime) {
		StringBuilder sb = new StringBuilder();
		sb.append(request.getMethod());
		sb.append(' ');
		sb.append(request.getRequestURI());
		sb.append(' ');
		sb.append(session.getUserName());
		sb.append('@');
		sb.append(session.getOrigin());
		sb.append(' ');
		int millis = (int) ((System.nanoTime() - nanoStartTime) / 1000000f);
		sb.append(millis);
		sb.append("ms");
		Logger.info(sb.toString());
	}

	private void nocache() {
		this.response.setHeader("Cache-Control", "no-cache");
	}

	@POST
	@Path("login")
	public String login(@FormParam("username") String username,
			@FormParam("password") String password) throws SQLException,
			SecurityError {
		long t = System.nanoTime();

		User user = DB.get().getUser(username);
		if (user == null) {
			throw new SecurityError("Invalid username or password");
		}

		user.password = password.getBytes();
		boolean passwordOk = user.checkPassword();
		user.password = null;

		if (!passwordOk) {
			throw new SecurityError("Invalid username or password");
		}

		Session old = Session.getSession(user.id);
		Session newSession = Session.create(request.getRemoteAddr(), user.id,
				username);

		if (old != null) {
			Session.replace(old, newSession);
		}

		StringBuilder ret = new StringBuilder();
		ret.append("{ \"sessionId\":\"");
		ret.append(newSession.getSessionId().toString());
		ret.append("\", \"userId\": ");
		ret.append(user.id);
		ret.append(",\"auth\":");
		ret.append(user.auth);
		ret.append("}");

		nocache();
		log(newSession, t);
		return ret.toString();
	}

	@POST
	@Path("logout")
	public void logout() throws SecurityError {
		long t = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr());
		Session.remove(sess);

		nocache();
		log(sess, t);
	}

	@GET
	@Path("users")
	public String getUsers() throws SQLException, SecurityError {
		long l1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr());
		int uid = sess.getUserId();

		List<User> users = DB.get().getUsers();
		Map<UUID, Session> sessions = Session.getSessions();
		boolean admin = false;

		StringBuilder ret = new StringBuilder();
		ret.append("{ \"users\": [");
		for (Iterator<User> it = users.iterator(); it.hasNext();) {
			User u = it.next();
			ret.append(u.toJSON());
			if (it.hasNext()) {
				ret.append(',');
			}
			if (u.id == uid) {
				admin = (u.auth == 1);
			}
		}
		ret.append("], \"sessions\": [");
		for (Iterator<Session> it = sessions.values().iterator(); it.hasNext();) {
			Session s = it.next();
			ret.append(s.toJSON(admin));
			if (it.hasNext()) {
				ret.append(',');
			}
		}
		ret.append("]}");

		nocache();
		log(sess, l1);
		return ret.toString();
	}

	@GET
	@Path("hasusers")
	public String hasUsers() throws SQLException {
		boolean hasUsers = DB.get().hasUsers();
		return "{\"hasusers\":" + hasUsers + "}";
	}

	@GET
	@Path("user/{user_id}")
	public String getUser(@PathParam("user_id") int userId)
			throws SQLException, SecurityError {
		long l1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr());

		StringBuilder ret = new StringBuilder();

		User u = DB.get().getUser(userId);
		if (u == null) {
			throw new NotFoundException("Unknown user: " + userId);
		}
		Session s = Session.getSession(userId);

		ret.append("{ \"user\":");
		ret.append(u.toJSON());
		ret.append(",\"session\":");
		if (s != null) {
			ret.append(s.toJSON(u.auth == 1));
		} else {
			ret.append("null");
		}

		List<Playlist> pl = DB.get().getPlaylists(userId);
		ret.append(",\"playlists\":[");
		for (Iterator<Playlist> it = pl.iterator(); it.hasNext();) {
			Playlist p = it.next();
			ret.append(p.toJSON());
			if (it.hasNext()) {
				ret.append(',');
			}
		}
		ret.append("]}");

		nocache();
		log(sess, l1);
		return ret.toString();
	}

	@POST
	@Path("user/add")
	public String addUser(@FormParam("username") String username,
			@FormParam("password") String password, @FormParam("auth") int auth)
			throws SQLException, SecurityError {
		long l = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);

		if (username == null || username.trim().length() == 0) {
			throw new IllegalArgumentException("Empty user name");
		}
		if (password == null || password.trim().length() < 4) {
			throw new IllegalArgumentException("Password too short");
		}

		StringBuilder ret = new StringBuilder();
		ret.append("{\"user\":");

		if (sid == null || sid.trim().length() == 0 && auth == 1) {
			// no user is present, accept the first admin creation
			// without any authentication !
			if (!DB.get().hasUsers()) {
				User u = new User();
				u.auth = auth;
				u.username = username;
				u.password = password.getBytes();
				u.hashPassword();

				u.id = DB.get().addUser(u);
				Logger.info("Added first user: " + username + " from "
						+ request.getRemoteAddr());
				ret.append(u.toJSON());
				ret.append("}");
				return ret.toString();
			}
		}

		Session s = Session.check(sid, request.getRemoteAddr(), true);

		User prev = DB.get().getUser(username);
		if (prev != null) {
			throw new IllegalArgumentException("User " + username
					+ " already exists");
		}
		if (auth != 1 && auth != 2) {
			throw new IllegalArgumentException("Invalid authorization level:"
					+ auth);
		}

		User u = new User();
		u.auth = auth;
		u.username = username;
		u.password = password.getBytes();
		u.hashPassword();

		u.id = DB.get().addUser(u);

		ret.append(u.toJSON());
		ret.append("}");

		RuntimeStats.get().userCount.addAndGet(1);

		nocache();
		log(s, l);
		return ret.toString();
	}

	@POST
	@Path("user/password")
	public void setUserPassword(@FormParam("old_password") String oldPassword,
			@FormParam("new_password") String newPassword) throws SQLException,
			SecurityError {
		long l = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr());

		User user = DB.get().getUser(sess.getUserId());
		user.password = oldPassword.getBytes();
		if (!user.checkPassword()) {
			throw new SecurityError("Invalid old password");
		}

		user.password = newPassword.getBytes();
		user.hashPassword();
		DB.get().setPassword(user);

		nocache();
		log(sess, l);
	}

	@POST
	@Path("user/remove")
	public void removeUsers(@FormParam("user_ids[]") int[] user_ids)
			throws SQLException, SecurityError {
		long l = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr(), true);
		int uid = sess.getUserId();

		if (user_ids.length == 0) {
			throw new IllegalArgumentException("No user specified");
		}

		for (int user_id : user_ids) {
			if (uid == user_id) {
				throw new IllegalArgumentException(
						"You cannot remove your own user.");
			}

			Session s = Session.getSession(user_id);
			if (s != null) {
				Session.remove(s);
			}
			DB.get().removeUser(user_id);
			RuntimeStats.get().userCount.addAndGet(-1);
		}

		nocache();
		log(sess, l);
	}

	@POST
	@Path("playlist/create")
	public String createPlaylist(@FormParam("name") String name)
			throws SQLException, SecurityError {
		long l = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session s = Session.check(sid, request.getRemoteAddr());
		int uid = s.getUserId();

		if (name == null || name.trim().length() == 0) {
			throw new IllegalArgumentException("Empty playlist name");
		}

		StringBuilder sb = new StringBuilder();
		Playlist pl = DB.get().addPlaylist(uid, name);
		RuntimeStats.get().playlistCount.addAndGet(1);
		sb.append("{\"playlist\":");
		sb.append(pl.toJSON());
		sb.append("}");

		nocache();
		log(s, l);
		return sb.toString();
	}

	@POST
	@Path("playlist/create-add")
	public String createAndAddToPlaylist(@FormParam("name") String name,
			@FormParam("clear") @DefaultValue("false") boolean clear,
			@FormParam("song_ids[]") int[] song_ids,
			@FormParam("album_ids[]") int[] album_ids) throws SQLException,
			SecurityError, javassist.NotFoundException, ForbiddenException {
		long l = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr());
		int uid = sess.getUserId();

		if (name == null || name.trim().length() == 0) {
			throw new IllegalArgumentException("Empty playlist name");
		}

		Playlist pl = DB.get().addPlaylist(uid, name);
		if (clear) {
			DB.get().clearPlaylist(pl.id, uid);
		}

		int count = 0;

		int[] arr = DB.get().getSongIds(album_ids);
		int[] s = new int[arr.length + song_ids.length];
		System.arraycopy(song_ids, 0, s, 0, song_ids.length);
		System.arraycopy(arr, 0, s, song_ids.length, arr.length);

		checkDuplicates(s);
		count = DB.get().addSongsToPlaylist(pl.id, s, uid);

		pl = DB.get().getPlaylist(pl.id);
		RuntimeStats.get().playlistCount.set(DB.get().getPlaylistCount());

		StringBuilder sb = new StringBuilder();
		sb.append("{ \"added\":" + count + ",");
		sb.append("\"playlist\":");
		sb.append(pl.toJSON());
		sb.append('}');

		nocache();
		log(sess, l);
		return sb.toString();
	}

	@POST
	@Path("playlist/random")
	public String randomPlaylist(@FormParam("name") String name,
			@FormParam("number") int number) throws SQLException,
			SecurityError, javassist.NotFoundException, ForbiddenException {
		long t1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr());
		int uid = sess.getUserId();

		if (name == null || name.trim().length() == 0) {
			throw new IllegalArgumentException("Empty playlist name");
		}

		if (number < 1)
			throw new IllegalArgumentException(
					"Cannot create random playlist with 0 songs");
		if (number > 50)
			throw new IllegalArgumentException(
					"Cannot create random playlist that big");

		Playlist pl = DB.get().addPlaylist(uid, name);
		if (pl.songs > 0) {
			DB.get().clearPlaylist(pl.id, uid);
		}

		List<Song> songs = DB.get().getRandomSongs(number);
		if (songs.size() == 0) {
			throw new IllegalStateException(
					"There are currently no songs in the library");
		}

		int[] ids = new int[songs.size()];
		for (int i = 0; i < songs.size(); i++) {
			ids[i] = songs.get(i).id;
		}
		DB.get().addSongsToPlaylist(pl.id, ids, uid);
		pl = DB.get().getPlaylist(pl.id);
		RuntimeStats.get().playlistCount.set(DB.get().getPlaylistCount());

		StringBuilder sb = new StringBuilder();
		sb.append("{ \"added\":" + songs.size() + ",");
		sb.append("\"first_song\":" + ids[0] + ",");
		sb.append("\"playlist\":");
		sb.append(pl.toJSON());
		sb.append('}');

		nocache();
		log(sess, t1);
		return sb.toString();
	}

	@POST
	@Path("playlist/{playlist_id}/add")
	public String addSongsToPlaylist(@PathParam("playlist_id") int playlist_id,
			@FormParam("clear") @DefaultValue("false") boolean clear,
			@FormParam("song_ids[]") int[] song_ids,
			@FormParam("album_ids[]") int[] album_ids) throws SQLException,
			SecurityError, ForbiddenException, NotFoundException {
		long t1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr());
		int uid = sess.getUserId();
		int count = 0;

		if (clear) {
			DB.get().clearPlaylist(playlist_id, uid);
		}

		int[] arr = DB.get().getSongIds(album_ids);
		int[] s = new int[arr.length + song_ids.length];
		System.arraycopy(song_ids, 0, s, 0, song_ids.length);
		System.arraycopy(arr, 0, s, song_ids.length, arr.length);

		if (s.length == 0) {
			throw new IllegalArgumentException("No song or album provided");
		}

		checkDuplicates(s);

		count = DB.get().addSongsToPlaylist(playlist_id, s, uid);
		Playlist pl = DB.get().getPlaylist(playlist_id);

		StringBuilder sb = new StringBuilder();
		sb.append("{ \"added\":" + count + ",");
		sb.append("\"playlist\":");
		sb.append(pl.toJSON());
		sb.append('}');

		nocache();
		log(sess, t1);
		return sb.toString();
	}

	@POST
	@Path("playlist/{playlist_id}/remove")
	public void removeFromPlaylist(@PathParam("playlist_id") int playlist_id,
			@FormParam("song_ids[]") int[] song_ids) throws SQLException,
			SecurityError, ForbiddenException {
		long l = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session s = Session.check(sid, request.getRemoteAddr());
		int uid = s.getUserId();

		if (song_ids == null || song_ids.length == 0)
			throw new IllegalArgumentException("No song ids provided");

		DB.get().removeSongsFromPlaylist(playlist_id, song_ids, uid);

		nocache();
		log(s, l);
	}

	@GET
	@Path("playlist/{playlist_id}/songs")
	public String getPlaylistSongs(@PathParam("playlist_id") int playlist_id)
			throws SQLException, SecurityError {
		long t1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr());

		Playlist pl = DB.get().getPlaylist(playlist_id);
		List<Song> songs = DB.get().getPlaylistSongs(playlist_id);

		if (pl == null) {
			throw new NotFoundException("No such playlist: " + playlist_id);
		}

		StringBuilder ret = new StringBuilder();
		ret.append("{\"name\":" + JSONObject.quote(pl.name) + ",");
		ret.append("\"playlist\":[");
		for (Iterator<Song> it = songs.iterator(); it.hasNext();) {
			Song s = it.next();
			ret.append(s.toJSON());
			if (it.hasNext()) {
				ret.append(',');
			}
		}
		ret.append("]}");

		nocache();
		log(sess, t1);
		return ret.toString();
	}

	@GET
	@Path("playlist/{playlist_id}/song/{song_pos}")
	public String getPlaylistSong(@PathParam("playlist_id") int playlist_id,
			@PathParam("song_pos") int song_pos) throws SQLException,
			SecurityError {
		long t1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr());

		StringBuilder ret = new StringBuilder();
		Song s = DB.get().getPlaylistSong(playlist_id, song_pos);
		if (s == null) {
			throw new NotFoundException("Could not find song " + song_pos
					+ " in playlist " + playlist_id);
		}
		ret.append("{\"song\":");
		ret.append(s.toJSON());
		ret.append("}");

		nocache();
		log(sess, t1);
		return ret.toString();
	}

	@POST
	@Path("playlists/remove")
	public void deletePlaylists(@FormParam("playlist_ids[]") int[] playlist_ids)
			throws SQLException, SecurityError, NotFoundException,
			ForbiddenException {
		long l = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr());
		int uid = sess.getUserId();

		if (playlist_ids == null || playlist_ids.length == 0)
			throw new IllegalArgumentException("No playlist ids provided");

		int ret = DB.get().removePlaylists(playlist_ids, uid);
		RuntimeStats.get().playlistCount.addAndGet(-ret);

		nocache();
		log(sess, l);
	}

	@GET
	@Path("playlists")
	public String getPlaylists() throws SQLException, SecurityError {
		long t1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session s = Session.check(sid, request.getRemoteAddr());

		List<Playlist> pl = DB.get().getPlaylists(s.getUserId());
		StringBuilder ret = new StringBuilder();
		ret.append("{\"playlists\":[");
		for (Iterator<Playlist> it = pl.iterator(); it.hasNext();) {
			Playlist p = it.next();
			ret.append(p.toJSON());
			if (it.hasNext()) {
				ret.append(',');
			}
		}
		ret.append("]}");

		nocache();
		log(s, t1);
		return ret.toString();
	}

	@GET
	@Path("playlists/{user_id}")
	public String getPlaylists(@PathParam("user_id") int userId)
			throws SQLException, SecurityError {
		long t1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr());

		List<Playlist> pl = DB.get().getPlaylists(userId);
		StringBuilder ret = new StringBuilder();
		ret.append("{\"playlists\":[");
		for (Iterator<Playlist> it = pl.iterator(); it.hasNext();) {
			Playlist p = it.next();
			ret.append(p.toJSON());
			if (it.hasNext()) {
				ret.append(',');
			}
		}
		ret.append("]}");

		nocache();
		log(sess, t1);
		return ret.toString();
	}

	@GET
	@Path("artists")
	public String getArtists() throws SQLException, SecurityError {
		long l = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session s = Session.check(sid, request.getRemoteAddr());

		List<Artist> artists = DB.get().getArtists();
		Map<Integer, List<Integer>> artworks = DB.get().getAlbumArtworks();

		StringBuilder ret = new StringBuilder();
		ret.append("{\"artists\":[");
		for (Iterator<Artist> it = artists.iterator(); it.hasNext();) {
			Artist ar = it.next();

			ret.append("{\"artist\":");
			ret.append(ar.toJSON());
			ret.append(",\"artworks\":[");
			List<Integer> al = artworks.get(ar.id);
			if (al != null) {
				for (Iterator<Integer> it2 = al.iterator(); it2.hasNext();) {
					int id = it2.next();
					ret.append(id);
					if (it2.hasNext()) {
						ret.append(',');
					}
				}
			}
			ret.append("]}");
			if (it.hasNext()) {
				ret.append(',');
			}
		}
		ret.append("]}");

		nocache();
		log(s, l);
		return ret.toString();
	}

	@GET
	@Path("albums/{artist_id}")
	public String getAlbums(@PathParam("artist_id") int artist_id)
			throws SQLException, SecurityError {
		long t1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr());

		Artist artist = DB.get().getArtist(artist_id);
		if (artist == null) {
			throw new NotFoundException("Cannot find artist " + artist_id);
		}
		List<Album> albums = DB.get().getAlbums(artist_id);

		StringBuilder ret = new StringBuilder();
		ret.append("{\"artist\":");
		ret.append(artist.toJSON());
		ret.append(",\"albums\":[");
		for (Iterator<Album> it = albums.iterator(); it.hasNext();) {
			Album al = it.next();
			ret.append(al.toJSON());
			if (it.hasNext()) {
				ret.append(',');
			}
		}
		ret.append("]}");

		nocache();
		log(sess, t1);
		return ret.toString();
	}

	@GET
	@Path("songs/{album_id}")
	public String getSongs(@PathParam("album_id") int album_id)
			throws SQLException, SecurityError {
		long t1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr());

		List<Song> songs = DB.get().getSongs(album_id);
		Album album = DB.get().getAlbum(album_id);
		if (album == null) {
			throw new NotFoundException("Cannot find album " + album_id);
		}
		Artist artist = DB.get().getArtist(album.artist_id);

		StringBuilder ret = new StringBuilder();
		ret.append("{\"artist\":" + artist.toJSON() + ",");
		ret.append("\"album\":" + album.toJSON() + ",");
		ret.append("\"songs\":[");
		for (Iterator<Song> it = songs.iterator(); it.hasNext();) {
			Song s = it.next();
			ret.append(s.toJSON());
			if (it.hasNext()) {
				ret.append(',');
			}
		}
		ret.append("]}");

		nocache();
		log(sess, t1);
		return ret.toString();
	}

	@GET
	@Path("song/{song_id}")
	public String getSong(@PathParam("song_id") final int song_id)
			throws SQLException, SecurityError {
		long t1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr());

		Song song = DB.get().getSong(song_id);
		if (song == null) {
			throw new NotFoundException("Cannot find song " + song_id);
		}
		Album album = DB.get().getAlbum(song.album_id);
		Artist artist = DB.get().getArtist(album.artist_id);

		StringBuilder ret = new StringBuilder();
		ret.append("{\"artist\":" + artist.toJSON() + ",");
		ret.append("\"album\":" + album.toJSON() + ",");
		ret.append("\"song\":" + song.toJSON() + "}");

		nocache();
		log(sess, t1);
		return ret.toString();
	}

	@GET
	@Path("search/{query}")
	public String search(@PathParam("query") final String query)
			throws SQLException, SecurityError {
		long t1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr());

		StringBuilder ret = new StringBuilder();

		List<Artist> artists = DB.get().searchArtist(query, 20);
		ret.append("{\"artists\":[");
		for (Iterator<Artist> it = artists.iterator(); it.hasNext();) {
			Artist ar = it.next();
			ret.append(ar.toJSON());
			if (it.hasNext())
				ret.append(',');
		}

		List<Album> albums = DB.get().searchAlbum(query, 20);
		ret.append("],\"albums\":[");
		for (Iterator<Album> it = albums.iterator(); it.hasNext();) {
			Album a = it.next();
			ret.append(a.toJSON());
			if (it.hasNext())
				ret.append(',');
		}

		List<Song> songs = DB.get().searchSong(query, 20);
		ret.append("],\"songs\":[");
		for (Iterator<Song> it = songs.iterator(); it.hasNext();) {
			Song s = it.next();
			ret.append(s.toJSON());
			if (it.hasNext())
				ret.append(',');
		}

		ret.append("]}");

		this.response.setHeader("Cache-Control", "max-age=60, must-revalidate");
		log(sess, t1);
		return ret.toString();
	}

	@GET
	@Path("song/{song_id}/stream")
	public Response getSong(@PathParam("song_id") final int song_id,
			@HeaderParam("range") final String range) throws SQLException,
			SecurityError {
		final long t1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		final Session s = Session.check(sid, request.getRemoteAddr());

		s.setLastPlayedSong(DB.get().getSong(song_id));

		final String filePath = DB.get().getSongFilePath(song_id);
		final File f = new File(filePath);

		// check the value for the 'range' http header, which
		// indicates when the client is seeking in the middle of a song
		int _startRange = 0;
		if (range != null) {
			Pattern pat = Pattern.compile("bytes=([0-9]+)-([0-9]*)");
			Matcher mat = pat.matcher(range);
			if (mat.matches()) {
				if (mat.group(1) != null) {
					_startRange = Integer.parseInt(mat.group(1));
				}
			}
		}
		final long startRange = _startRange;
		final long endRange = f.length() - 1;
		final String contentRange = "bytes " + startRange + "-" + endRange
				+ "/" + f.length();

		// this StreamingOutput object gives us a way to write the response to a
		// Stream,
		// which allows reading the file chunk by chunk to avoid having it all
		// in memory
		StreamingOutput stream = new StreamingOutput() {
			@Override
			public void write(OutputStream out) throws IOException,
					WebApplicationException {
				int totalBytes = 0;
				InputStream in = new FileInputStream(f);
				byte[] bytes = new byte[8192];
				int bytesRead;

				try {
					in.skip(startRange);
					while ((bytesRead = in.read(bytes)) != -1) {
						totalBytes += bytesRead;
						out.write(bytes, 0, bytesRead);
					}
				} catch (Throwable t) {
					return;
				} finally {
					RuntimeStats.get().downloaded.addAndGet(totalBytes);
					try {
						DB.get().updateDownloadedBytes(s.getUserId(),
								totalBytes);
					} catch (SQLException e) {
						Logger.error("Failed to update user stats", e);
					}
					in.close();
				}
			}
		};

		String contentType = "*/*";
		if (filePath.endsWith("mp3")) {
			contentType = "audio/mpeg";
		} else if (filePath.endsWith("mp4")) {
			contentType = "audio/aac";
		} else if (filePath.endsWith("aac")) {
			contentType = "audio/aac";
		} else if (filePath.endsWith("m4a")) {
			contentType = "audio/aac";
		} else if (filePath.endsWith("ogg")) {
			contentType = "audio/ogg";
		} else if (filePath.endsWith("wav")) {
			contentType = "audio/wav";
		}

		int status = 200;
		if (startRange > 0) {
			// seeking: HTTP 206 partial content
			status = 206;
		}

		log(s, t1);

		return Response.status(status) //
				.type(contentType) //
				.header("Content-Length", f.length() - startRange) //
				.header("Accept-Ranges", "bytes") //
				.header("Content-Range", contentRange) //
				.header("Cache-Control", "max-age=86400, must-revalidate") //
				.entity(stream) //
				.build();
	}

	@GET
	@Path("art/{album_id}")
	public Response getAlbumArtwork(@PathParam("album_id") int album_id)
			throws SQLException, SecurityError {

		final String art = DB.get().getAlbumArtwork(album_id);
		if (art == null || art.trim().length() == 0) {
			throw new NotFoundException("Not artwork for album " + album_id);
		}

		final File f = new File(art);

		if (!f.exists()) {
			throw new NotFoundException("Not artwork for album " + album_id);
		}

		StreamingOutput stream = new StreamingOutput() {
			@Override
			public void write(OutputStream out) throws IOException,
					WebApplicationException {
				InputStream in = new FileInputStream(f);
				byte[] bytes = new byte[8192];
				int bytesRead;
				int totalBytes = 0;

				try {
					while ((bytesRead = in.read(bytes)) != -1) {
						totalBytes += bytesRead;
						out.write(bytes, 0, bytesRead);
					}
				} catch (Throwable t) {
					return;
				} finally {
					RuntimeStats.get().downloaded.addAndGet(totalBytes);
					in.close();
				}
			}
		};

		Logger.debug("GET art/" + album_id + " " + request.getRemoteAddr());
		return Response.status(200) //
				.type("image/jpeg") //
				.header("Content-Length", f.length()) //
				.header("Accept-Ranges", "bytes") //
				.header("Cache-Control", "max-age=86400, public") //
				.entity(stream) //
				.build();
	}

	@GET
	@Path("folders")
	public String getMusicFolders() throws SecurityError, SQLException {
		long l = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr(), true);
		StringBuilder ret = new StringBuilder();

		ret.append("{\"folders\":[");
		for (Iterator<String> it = Config.getMusicPath().iterator(); it
				.hasNext();) {
			String path = it.next();
			ret.append(JSONObject.quote(path));
			if (it.hasNext()) {
				ret.append(',');
			}
		}
		ret.append("]}");

		nocache();
		log(sess, l);
		return ret.toString();
	}

	@GET
	@Path("folders/listing")
	public String getFolderListing(@QueryParam("directory") String directory)
			throws SecurityError, SQLException {
		long l1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr(), true);
		StringBuilder ret = new StringBuilder();

		File dir = null;
		File[] l = null;
		String dirName = null;

		if (directory == null || directory.trim().length() == 0) {
			dir = new File(System.getProperty("user.home"));
			l = dir.listFiles(new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					return pathname.isDirectory();
				}
			});
			dirName = dir.getAbsolutePath();
		} else if (directory.equals("$ROOT")) {
			dirName = "";
			l = File.listRoots();
		} else {
			dir = new File(directory);

			if (!dir.exists()) {
				throw new NotFoundException("Unknown directory: " + directory);
			} else if (!dir.isDirectory()) {
				throw new IllegalArgumentException("Not a directory: "
						+ directory);
			}

			l = dir.listFiles(new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					return pathname.isDirectory();
				}
			});
			dirName = dir.getAbsolutePath();
		}

		ret.append("{\"directory\":" + JSONObject.quote(dirName));
		ret.append(",\"separator\":" + JSONObject.quote(File.separator));
		if (dir != null && dir.getParent() != null) {
			ret.append(",\"parent\":" + JSONObject.quote(dir.getParent()));
		} else {
			ret.append(",\"parent\":" + JSONObject.quote("$ROOT"));
		}
		ret.append(",\"listing\":[");
		if (l != null) {
			Arrays.sort(l);
			boolean virgule = false;
			for (int i = 0; i < l.length; i++) {
				if (l[i].exists()) {
					if (virgule) {
						ret.append(',');
					}
					virgule = (i + 1 < l.length);
					ret.append(JSONObject.quote(l[i].getAbsolutePath()));
				}
			}
		}
		ret.append("]}");

		nocache();
		log(sess, l1);
		return ret.toString();
	}

	@POST
	@Path("folders/add")
	public void addMusicFolder(@FormParam("directory") String directory)
			throws SecurityError, SQLException {
		long l1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr(), true);

		File dir = new File(directory);
		if (!dir.exists()) {
			throw new NotFoundException("Unknown directory: " + directory);
		} else if (!dir.isDirectory()) {
			throw new IllegalArgumentException("Not a directory: " + directory);
		}

		Config.getMusicPath().add(directory);
		Library.interrupt();

		nocache();
		log(sess, l1);
	}

	@POST
	@Path("folders/remove")
	public void removeMusicFolders(@FormParam("directory[]") String[] directory)
			throws SecurityError, SQLException {
		long l1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr(), true);

		if (directory != null) {
			List<String> music = Config.getMusicPath();
			for (String dir : directory) {
				if (!music.contains(dir)) {
					throw new IllegalArgumentException("Directory '" + dir
							+ "' is not a music folder");
				}
			}

			for (String dir : directory) {
				File toDelete = new File(dir);
				for (Iterator<String> it = music.iterator(); it.hasNext();) {
					File f = new File(it.next());
					if (f.equals(toDelete)) {
						it.remove();
					}
				}
			}
		}
		Library.interrupt();

		nocache();
		log(sess, l1);
	}

	@GET
	@Path("indexer/status")
	public String getIndexerStatus() throws SecurityError {
		long l1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session s = Session.check(sid, request.getRemoteAddr(), true);

		String ret = Library.getIndexerStatusAsJSON();

		log(s, l1);
		return ret;
	}

	@POST
	@Path("indexer/rescan")
	public void rescan() throws SecurityError {
		long l1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session s = Session.check(sid, request.getRemoteAddr(), true);

		Library.interrupt();

		log(s, l1);
	}

	@GET
	@Path("logs")
	public Response getLogs() throws SecurityError {
		long l1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session s = Session.check(sid, request.getRemoteAddr(), true);

		final File log = new File(Config.getLogFilePath());
		StreamingOutput stream = new StreamingOutput() {
			@Override
			public void write(OutputStream out) throws IOException,
					WebApplicationException {

				int totalBytes = 0;
				InputStream in = new FileInputStream(log);
				byte[] bytes = new byte[8192];
				int bytesRead;

				try {
					while ((bytesRead = in.read(bytes)) != -1) {
						totalBytes += bytesRead;
						out.write(bytes, 0, bytesRead);
					}
				} catch (Throwable e) {
					Logger.warn(
							"GET log interrupted after " + totalBytes + "B", e);
				} finally {
					in.close();
				}
			}
		};

		nocache();
		log(s, l1);

		return Response.status(200) //
				.type("text/plain") //
				.header("Content-Length", log.length()) //
				.entity(stream) //
				.build();
	}

	@POST
	@Path("shutdown")
	public void shutdown() throws SecurityError {
		long l1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session s = Session.check(sid, request.getRemoteAddr(), true);

		Bootstrap.shutdown();
		log(s, l1);
		System.exit(0);
	}

	@GET
	@Path("stats")
	public String getStats() throws SecurityError {
		long l1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session s = Session.check(sid, request.getRemoteAddr(), false);
		StringBuilder sb = new StringBuilder();

		sb.append("{\"stats\":" + RuntimeStats.get().toJSON() + "}");

		nocache();
		log(s, l1);
		return sb.toString();
	}

	@GET
	@Path("info")
	public String getInfo() throws SecurityError {
		long l1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session s = Session.check(sid, request.getRemoteAddr(), false);

		StringBuilder sb = new StringBuilder();
		sb.append('{');
		sb.append("\"version\":" + JSONObject.quote(Config.getVersion()) + ",");
		sb.append("\"build\":" + JSONObject.quote(Config.getBuildInfo()) + ",");
		sb.append("\"server\":" + JSONObject.quote(Config.getServerInfo())
				+ ",");
		sb.append("\"os\":" + JSONObject.quote(Config.getOsInfo()) + ",");
		sb.append("\"java\":" + JSONObject.quote(Config.getJavaInfo()));
		sb.append('}');

		nocache();
		log(s, l1);

		return sb.toString();
	}

	private void checkDuplicates(int[] arr) {
		for (int i = 0; i < arr.length; i++) {
			for (int j = 0; j < arr.length; j++) {
				if (j != i && arr[i] == arr[j]) {
					throw new IllegalStateException("Duplicate id: " + arr[i]);
				}
			}

		}
	}
}

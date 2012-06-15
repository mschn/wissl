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

	/**
	 * @param username
	 *            a valid username
	 * @param password
	 *            cleartext password matching the username
	 * @return the id for a newly created session if login succeeds, ie:
	 * 
	 *         <pre>
	 * {
	 * 	"userId" : 1,
	 * 	"sessionId" : "af0ee222-6ed1-409d-9d99-5654c7802df1",
	 *  "auth" : 1
	 * }
	 * </pre>
	 * @throws SQLException
	 * @throws SecurityException
	 */
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

	/**
	 * Destroy session
	 * 
	 * @throws SecurityError
	 */
	@POST
	@Path("logout")
	public void logout() throws SecurityError {
		long t = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session removed = Session.remove(sid, request.getRemoteAddr());

		nocache();
		log(removed, t);
	}

	/**
	 * List global users info
	 * 
	 * @return a list of users containing various info as json, ie:
	 * 
	 *         <pre>
	 * { "users" : [
	 *     { "id": 0,
	 *       "username": "toto",
	 *       "auth": 0 },
	 *     { "id": 1,
	 *       "username": "titi",
	 *       "auth": 1 }
	 *   ], "sessions" : [
	 *     { "user_id": 0,
	 *       "last_activity": 123545 }  
	 *   ]
	 * }
	 * </pre>
	 * @throws SQLException
	 * @throws SecurityError
	 */
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

	/**
	 * Check whether the system already has users registered,
	 * or if the client should now create a new admin user
	 * @return a JSON object indicating whether the system has users:
	 * <pre>
	 * { "hasusers" : true }
	 * </pre>
	 * @throws SQLException
	 */
	@GET
	@Path("hasusers")
	public String hasUsers() throws SQLException {
		boolean hasUsers = DB.get().hasUsers();
		return "{\"hasusers\":" + hasUsers + "}";
	}

	/**
	 * Get info for one user: user info, session info, playlists, stats
	 * 
	 * @param userId
	 *            unique user id
	 * @return user info as json, ie:
	 * 
	 *         <pre>
	 * { "user":{
	 *     "id": 0,
	 *     "username": "toto",
	 *     "auth": 0
	 *   }, "session":{
	 *     "user_id":0,
	 *     "last_activity": 12345,
	 *   }, "playlists":[
	 * 	   {"id":1,"name":"foo"},
	 *     {"id":2,"name":"bar"}
	 *   ]}
	 * }
	 * </pre>
	 * @throws SQLException
	 * @throws SecurityError
	 */
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

	/**
	 * Create a new user
	 * 
	 * @param username
	 *            name of the user
	 * @param password
	 *            clear text password
	 * @param auth
	 *            1: admin, 2: user
	 * @throws SQLException
	 * @throws SecurityError
	 */
	@POST
	@Path("user/add")
	public void addUser(@FormParam("username") String username,
			@FormParam("password") String password, @FormParam("auth") int auth)
			throws SQLException, SecurityError {
		long l = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);

		if (username.trim().length() == 0) {
			throw new IllegalArgumentException("Empty user name");
		}
		if (password.trim().length() < 4) {
			throw new IllegalArgumentException("Password too short");
		}

		if (sid == null || sid.trim().length() == 0 && auth == 1) {
			// no user is present, accept the first admin creation
			// without any authentication !
			if (!DB.get().hasUsers()) {
				User u = new User();
				u.auth = auth;
				u.username = username;
				u.password = password.getBytes();
				u.hashPassword();

				DB.get().addUser(u);
				Logger.info("Added first user: " + username + " from "
						+ request.getRemoteAddr());
				return;
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

		DB.get().addUser(u);

		RuntimeStats.addUserCount(1);

		nocache();
		log(s, l);
	}

	@POST
	@Path("user/password")
	public void setUserPassword(@FormParam("old_password") String oldPassword,
			@FormParam("new_password") String newPassword) throws SQLException,
			SecurityError {
		long l = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr(), true);

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

		for (int user_id : user_ids) {
			if (uid == user_id) {
				throw new IllegalArgumentException(
						"You cannot remove your own user.");
			}
			DB.get().removeUser(user_id);
			RuntimeStats.addUserCount(-1);
		}

		nocache();
		log(sess, l);
	}

	/**
	 * Create new playlist for authenticated user
	 * 
	 * @param name
	 *            name of the new playlist
	 * @return the created playlist as json, ie:
	 * 
	 *         <pre>
	 * {"id":1,"name":"foo","user":1}
	 * </pre>
	 * @throws SQLException
	 * @throws SecurityError
	 */
	@POST
	@Path("playlist/create")
	public String createPlaylist(@FormParam("name") String name)
			throws SQLException, SecurityError {
		long l = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session s = Session.check(sid, request.getRemoteAddr());
		int uid = s.getUserId();

		Playlist pl = DB.get().addPlaylist(uid, name);
		RuntimeStats.addPlaylistCount(1);

		nocache();
		log(s, l);
		return pl.toJSON();
	}

	/**
	 * Create a new playlist and add songs to it If playlist already exists,
	 * reuse it
	 * 
	 * @param name
	 *            name of the new playlist
	 * @param song_ids
	 *            ids of the songs to add
	 * @param album_ids
	 *            id of the albums to add
	 * @return the created playlist as json along with number of added songs,
	 *         ie:
	 * 
	 *         <pre>
	 * {
	 *   "added": 4,
	 *   "playlist" : {
	 *     "id":1,
	 *     "name":"foo",
	 *     "user":1,
	 *     "songs":6
	 *   }
	 * }
	 * </pre>
	 * @throws SQLException
	 * @throws SecurityError
	 * @throws ForbiddenException
	 * @throws javassist.NotFoundException
	 */
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

		Playlist pl = DB.get().addPlaylist(uid, name);
		if (clear) {
			DB.get().clearPlaylist(pl.id, uid);
		}

		int count = 0;
		if (song_ids != null && song_ids.length > 0) {
			count += DB.get().addSongsToPlaylist(pl.id, song_ids, uid);
		}
		if (album_ids != null && album_ids.length > 0) {
			count += DB.get().addAlbumsToPlaylist(pl.id, album_ids, uid);
		}
		pl = DB.get().getPlaylist(pl.id);

		RuntimeStats.setPlaylistCount(DB.get().getPlaylistCount());

		StringBuilder sb = new StringBuilder();
		sb.append("{ \"added\":" + count + ",");
		sb.append("\"playlist\":");
		sb.append(pl.toJSON());
		sb.append('}');

		nocache();
		log(sess, l);
		return sb.toString();
	}

	/**
	 * Create a new playlist, fills it with random songs. If the playlist
	 * already exists, it will be cleared
	 * 
	 * @param name
	 *            name of the new playlist
	 * @param number
	 *            number of random songs to add
	 * @return the created playlist as json along with number of added songs, id
	 *         of first song
	 * 
	 *         <pre>
	 * {
	 *   "added": 4,
	 *   "first_song": 1234,
	 *   "playlist" : {
	 *     "id":1,
	 *     "name":"foo",
	 *     "user":1,
	 *     "songs":6
	 *   }
	 * }
	 * </pre>
	 * @throws SQLException
	 * @throws SecurityError
	 * @throws ForbiddenException
	 * @throws javassist.NotFoundException
	 */
	@POST
	@Path("playlist/random")
	public String randomPlaylist(@FormParam("name") String name,
			@FormParam("number") int number) throws SQLException,
			SecurityError, javassist.NotFoundException, ForbiddenException {
		long t1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr());
		int uid = sess.getUserId();

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

		RuntimeStats.setPlaylistCount(DB.get().getPlaylistCount());

		StringBuilder sb = new StringBuilder();
		sb.append("{ \"added\":" + number + ",");
		sb.append("\"first_song\":" + ids[0] + ",");
		sb.append("\"playlist\":");
		sb.append(pl.toJSON());
		sb.append('}');

		nocache();
		log(sess, t1);
		return sb.toString();
	}

	/**
	 * Add songs at the end of the given playlist
	 * 
	 * @param playlist_id
	 *            id of the playlist
	 * @param clear
	 *            clear playlist before adding if true, defaults to false
	 * @param song_ids
	 *            id of the songs to add
	 * @param album_ids
	 *            id of the albums to add
	 * @return the created playlist as json along with number of added songs,
	 *         ie:
	 * 
	 *         <pre>
	 * {
	 *   "added": 4,
	 *   "playlist" : {
	 *     "id":1,
	 *     "name":"foo",
	 *     "user":1,
	 *     "songs":6
	 *   }
	 * }
	 * </pre>
	 * @throws SQLException
	 * @throws SecurityError
	 * @throws ForbiddenException
	 * @throws javassist.NotFoundException
	 */
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

		if (song_ids != null && song_ids.length > 0) {
			count += DB.get().addSongsToPlaylist(playlist_id, song_ids, uid);
		}
		if (album_ids != null && album_ids.length > 0) {
			count += DB.get().addAlbumsToPlaylist(playlist_id, album_ids, uid);
		}
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

	/**
	 * Remove songs from a playlist
	 * <p>
	 * impl note: should use @DELETE, but @DELETE resources cannot have proper
	 * array params and should be identified uniquely using only path and query
	 * parameters. Therefore I'm using POST which is a lot more convenient
	 * 
	 * @param playlist_id
	 *            id of the playlist from which songs are deleted
	 * @param song_ids
	 *            ids of the songs to remove from playlist
	 * @throws SQLException
	 * @throws SecurityError
	 * @throws ForbiddenException
	 */
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

	/**
	 * Get the content of a given playlist
	 * 
	 * @param playlist_id
	 *            id of the playlist
	 * @return the playlist content as json, ie:
	 * 
	 *         <pre>
	 * {
	 *  "name": "foo",
	 *  "playlist":[
	 *   {"id":1,"name":"Song1"},
	 *   {"id":30,"name":"Song30"}
	 *  ]
	 * }
	 * </pre>
	 * 
	 *         The ordering of the song array reflects the playlist order.
	 * @throws SQLException
	 * @throws SecurityError
	 */
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

	/**
	 * Get a single Song at a given position in a playlist
	 * 
	 * @param playlist_id
	 *            unique playlist id
	 * @param song_pos
	 *            position of the song in the playlist
	 * @return the song as JSON, ie:
	 * 
	 *         <pre>
	 * {
	 * 	"id": 10,
	 *  "name": "Foo",
	 *  "position": "1/7",
	 *  "duration": "342"
	 * }
	 * </pre>
	 * @throws SQLException
	 * @throws SecurityError
	 */
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
		if (s != null) {
			ret.append(s.toJSON());
		}

		nocache();
		log(sess, t1);
		return ret.toString();
	}

	/**
	 * Remove playlists
	 * <p>
	 * impl note: should use @DELETE, but @DELETE resources cannot have proper
	 * array params and should be identified uniquely using only path and query
	 * parameters. Therefore I'm using POST which is a lot more convenient
	 * 
	 * @param playlist_ids
	 *            ids of the playlists to delete
	 * @throws SQLException
	 * @throws SecurityError
	 * @throws ForbiddenException
	 * @throws NotFoundException
	 */
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
		RuntimeStats.addPlaylistCount(-ret);

		nocache();
		log(sess, l);
	}

	/**
	 * Return all playlists for the authenticated user
	 * 
	 * @return all playlists (excluding actual songs) as JSON, ie:
	 * 
	 *         <pre>
	 * {
	 * 	"playlists":[
	 * 	  {"id":1,"name":"foo"},
	 *    {"id":2,"name":"bar"}
	 *  ]
	 * }
	 * </pre>
	 * @throws SQLException
	 * @throws SecurityError
	 */
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

	/**
	 * Return all playlists for the given user
	 * 
	 * @return all playlists (excluding actual songs) as JSON, ie:
	 * 
	 *         <pre>
	 * {
	 * 	"playlists":[
	 * 	  {"id":1,"name":"foo"},
	 *    {"id":2,"name":"bar"}
	 *  ]
	 * }
	 * </pre>
	 * @throws SQLException
	 * @throws SecurityError
	 */
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

	/**
	 * @return all artists as json, ie:
	 * 
	 *         <pre>
	 * {
	 *  "artists": [
	 *    {"id":1,"name":"Yes"},
	 *    {"id":2,"name":"Rush"}
	 *  ]
	 * }
	 * </pre>
	 * @throws SQLException
	 * @throws SecurityError
	 */
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

	/**
	 * @param artist_id
	 *            a valid unique artist id
	 * @return the artist and all its albums as json,ie:
	 * 
	 *         <pre>
	 * {
	 *  "artist": {"id":1,"name":"Yes"},
	 * 	"albums":[
	 *    {"id":1,"name":"Fragile","date":"1971"},
	 *    {"id":2,"name":"Relayer","date":"1974"},
	 *  ]
	 * }
	 * </pre>
	 * @throws SQLException
	 * @throws SecurityError
	 */
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

	/**
	 * @param album_id
	 *            a valid unique album id
	 * @return the artist, the album and all the songs as json, ie:
	 * 
	 *         <pre>
	 * {
	 *  "artist":{"id":1,"name":"Yes"},
	 *  "album":{"id":1,"name":"Fragile","date":"1971"},
	 * 	"songs":[
	 *    {"id":1,"title":"Roundabout","position":"1/11","duration":285}
	 *  ]
	 * }
	 * </pre>
	 * @throws SQLException
	 * @throws SecurityError
	 */
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

	/**
	 * @param song_id
	 *            valid unique song id
	 * @return the artist, album and song as json, ie:
	 * 
	 *         <pre>
	 * {
	 *  "artist":{"id":1,"name":"Yes"},
	 *  "album":{"id":1,"name":"Fragile","date":"1971"},
	 *  "song":{"id":1,"title":"Roundabout","position":"1/11","duration":285}
	 * }
	 * </pre>
	 * @throws SQLException
	 * @throws SecurityError
	 */
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

	/**
	 * Get song file as a stream<br>
	 * Content type is dynamically set depending the actual file extension<br>
	 * The response body can be directly decoded by any media player<br>
	 * Supports seeking anywhere in the file using the http range header<br>
	 * 
	 * @param song_id
	 *            a valid unique song id
	 * @param sessionId
	 *            session id as GET parameter, convenience audio players that do
	 *            not allow setting http headers
	 * @return binary file content of the song, ie the raw mp3/ogg/audio data,
	 *         that a client will be able to decode and play.
	 * @throws SecurityError
	 */
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
					RuntimeStats.addDownloaded(totalBytes);
					try {
						DB.get().updateDownloadedBytes(s.getUserId(),
								totalBytes);
					} catch (SQLException e) {
						Logger.error("Failed to update user stats", e);
					}
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
					RuntimeStats.addDownloaded(totalBytes);
				}
			}
		};

		Logger.info("GET art/" + album_id + " " + request.getRemoteAddr());
		return Response.status(200) //
				.type("image/jpeg") //
				.header("Content-Length", f.length()) //
				.header("Accept-Ranges", "bytes") //
				.header("Cache-Control", "max-age=86400, public") //
				.entity(stream) //
				.build();
	}

	/**
	 * Requires admin privileges
	 * 
	 * @return file paths on the server's filesystem that are searched for music
	 *         files to be served, ie:
	 * 
	 *         <pre>
	 * { "folders": [
	 *   "C:\Users\bob\Downloads",
	 *   "E:\music"
	 *   ]
	 * }
	 * </pre>
	 * @throws SecurityError
	 * @throws SQLException
	 */
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

	/**
	 * List directory content on the server's filesystem requires admin
	 * privileges
	 * 
	 * @param directory
	 *            directory to list, or null, or $ROOT to list FS root
	 * @return directories contained in the parameter directory, or directories
	 *         contained in home directory if none specified
	 * @throws SecurityError
	 * @throws SQLException
	 */
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

	/**
	 * Add a folder to the library search path
	 * <p>
	 * library will be notified and songs will be added asynchronously
	 * 
	 * @param directory
	 *            a valid directory on the server's filesystem
	 * @throws SecurityError
	 * @throws SQLException
	 */
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
		try {
			Config.write();
		} catch (IOException e) {
			Logger.error("Failed to write configuration", e);
		}
		Library.interrupt();

		nocache();
		log(sess, l1);
	}

	/**
	 * Remove folders from the library search path
	 * <p>
	 * library will be notified and will remove songs asynchronously
	 * 
	 * @param directory
	 *            a valid directory on the server's filesystem
	 * @throws SecurityError
	 * @throws SQLException
	 */
	@POST
	@Path("folders/remove")
	public void removeMusicFolders(@FormParam("directory[]") String[] directory)
			throws SecurityError, SQLException {
		long l1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session sess = Session.check(sid, request.getRemoteAddr());

		if (directory != null) {
			for (String dir : directory) {
				File toDelete = new File(dir);

				for (Iterator<String> it = Config.getMusicPath().iterator(); it
						.hasNext();) {
					File f = new File(it.next());
					if (f.equals(toDelete)) {
						it.remove();
					}
				}
			}
		}

		try {
			Config.write();
		} catch (IOException e) {
			Logger.error("Failed to write configuration", e);
		}
		Library.interrupt();

		nocache();
		log(sess, l1);
	}

	/**
	 * @return server logs as text/plain
	 * @throws SecurityError
	 */
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

	/**
	 * Shutdown the server and the JVM
	 * @throws SecurityError
	 */
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

	/**
	 * @return indexer status as a json string, ie:
	 * If not running :
	 * <pre>
	 * { "running": false }
	 * </pre>
	 * If running :
	 * <pre>
	 * {
	 *   "running": true,
	 *   "percentDone": 0.5,
	 *   "secondsLeft": 431,
	 *   "songsDone": 600,
	 *   "songsTodo": 1200
	 * }
	 * </pre>
	 * @throws SecurityError
	 */
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

	/**
	 * @return simple runtime stats containing general server info, ie:
	 * <pre>
	 * {
	 *   "songs": 123,
	 *   "albums": 26,
	 *   "artists": 5
	 * }
	 * </pre>
	 */
	@GET
	@Path("stats")
	public String getStats() throws SecurityError {
		long l1 = System.nanoTime();
		String sid = (sessionIdHeader == null ? sessionIdGet : sessionIdHeader);
		Session s = Session.check(sid, request.getRemoteAddr(), false);
		StringBuilder sb = new StringBuilder();

		sb.append("{\"stats\":" + RuntimeStats.toJSON() + "}");

		nocache();
		log(s, l1);
		return sb.toString();
	}

	/**
	 * @return Various server info packed in a JSON object,ie:
	 * <pre>
	 * { 
	 *   "version": "1.0",
	 *   "build": "b32",
	 *   "server": "Tomcat 7.0",
	 *   "os": "Linux 3.3"
	 *   "java": "HotSpot 1.6 Sun"
	 * }
	 * </pre>
	 * @throws SecurityError
	 */
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
}

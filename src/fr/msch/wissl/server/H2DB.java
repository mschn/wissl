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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.h2.tools.Server;
import org.jboss.resteasy.spi.NotFoundException;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.DataSources;

import fr.msch.wissl.common.Config;
import fr.msch.wissl.server.exception.ForbiddenException;

/**
 * DB Implementation using H2 server through JDBC and c3p0 connection pooling
 * 
 * 
 * @author mschnoor
 *
 */
public class H2DB extends DB {

	private static final long SCHEMA_VERSION = 1L;

	private static final String driver = "org.h2.Driver";
	private static final String protocol = "jdbc:h2:";

	private Server server = null;

	private ComboPooledDataSource pool;

	private boolean closed = false;

	protected H2DB(String db) throws SQLException {
		this.server = Server.createTcpServer("-tcp").start();
		// printing this URL is useful to connect with the H2 console
		String publicUrl = server.getURL() + "/" + db;

		this.closed = false;

		try {
			this.pool = new ComboPooledDataSource();
			this.pool.setDriverClass(driver);
			this.pool.setJdbcUrl(protocol + publicUrl);
			this.pool.setUser(Config.getDbUser());
			this.pool.setPassword(Config.getDbPassword());
		} catch (Exception e) {
			throw new SQLException("Failed to create Connection Pool ", e);
		}

		long version = -1L;
		try {
			version = getSchemaVersion();
		} catch (SQLException e) {
			Logger.warn("Failed to get Schema version", e);
		}

		boolean createNewDb = false;

		if (Config.isDbClean()) {
			Logger.debug("Forcing new DB creation");
			createNewDb = true;
		} else if (version == -1L) {
			Logger.debug("Previous DB not found, will create new one");
			createNewDb = true;
		} else if (version != SCHEMA_VERSION) {
			Logger.debug("DB schema version changed: creating new DB");
			createNewDb = true;
		} else {
			Logger.info("Attempting to recover previous DB");
		}

		if (createNewDb) {
			try {
				this.dropDB();
			} catch (SQLException e) {
				Logger.warn("Failed to clear DB", e);
			}
			this.createDB();
			Logger.info("Created H2 DB: " + publicUrl);
		}
	}

	@Override
	public void close() throws SQLException {
		Logger.info("closing DB");
		try {
			DataSources.destroy(this.pool);
		} catch (Exception e) {
			Logger.warn("Failed to close Connection Pool", e);
		}

		this.closed = true;
		this.server.stop();
		this.server.shutdown();
	}

	@Override
	protected Connection getConnection() throws SQLException {
		if (this.closed)
			throw new SQLException("Connection closed");

		return this.pool.getConnection();
	}

	/**
	 * Remove all tables from the DB
	 * 
	 * @throws SQLException
	 */
	private void dropDB() throws SQLException {
		Connection conn = getConnection();
		Statement st = null;

		try {
			st = conn.createStatement();
			st.addBatch("DROP TABLE IF EXISTS info");
			st.addBatch("DROP TABLE IF EXISTS song");
			st.addBatch("DROP TABLE IF EXISTS album");
			st.addBatch("DROP TABLE IF EXISTS artist");
			st.addBatch("DROP TABLE IF EXISTS user");
			st.addBatch("DROP TABLE IF EXISTS playlist");
			st.addBatch("DROP TABLE IF EXISTS playlist_song");
			st.executeBatch();
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	/**
	 * Add all the required tables to the DB
	 * 
	 * @throws SQLException
	 * @return false if tables were already present
	 */
	private boolean createDB() throws SQLException {
		Connection conn = getConnection();
		Statement st = conn.createStatement();

		/*
		DatabaseMetaData md = conn.getMetaData();
		ResultSet rs = md.getTables(null, null, "%", null);
		HashSet<String> hs = new HashSet<String>();
		hs.add("artist");
		hs.add("song");
		hs.add("album");
		hs.add("user");
		hs.add("playlist");
		hs.add("playlist_song");

		while (rs.next()) {
			String n = rs.getString(3).toLowerCase();
			hs.remove(n);
		}

		if (hs.size() == 0) {
			return false;
		}
		*/

		try {
			st.addBatch("CREATE TABLE info (" + //
					"schema_version LONG NOT NULL," + //
					"song_count LONG," + //
					"album_count LONG," + //
					"song_duration LONG" + //
					")");

			st.addBatch("CREATE TABLE artist (" + //
					"artist_id IDENTITY," + //
					"artist_name VARCHAR(64) NOT NULL," + //
					"albums INTEGER NOT NULL," + //
					"songs INTEGER NOT NULL," + //
					"playtime INTEGER NOT NULL," + //
					"CONSTRAINT pk_artist PRIMARY KEY (artist_id)" + //
					")");

			st.addBatch("CREATE TABLE album (" + //
					"album_id IDENTITY," + //
					"album_name VARCHAR(254) NOT NULL," + //
					"date VARCHAR(8)," + //
					"songs INTEGER NOT NULL," + //
					"playtime INTEGER NOT NULL," + //
					"artwork_path VARCHAR(254)," + //
					"CONSTRAINT pk_album PRIMARY KEY (album_id)," + //
					"artist_id INTEGER," + //
					"CONSTRAINT fk_band FOREIGN KEY (artist_id)" + //
					" REFERENCES artist(artist_id)" + //
					")");

			st.addBatch("CREATE TABLE song (" + //
					"song_id IDENTITY," + //
					"title VARCHAR(254) NOT NULL," + //
					"album_name VARCHAR(254) NOT NULL," + //
					"artist_name VARCHAR(254) NOT NULL," + //
					"filepath VARCHAR(254) NOT NULL," + //
					"hash VARCHAR(32) NOT NULL, " + //
					"position INTEGER," + //
					"disc_no INTEGER," + //
					"duration INTEGER," + //
					"format VARCHAR(254) NOT NULL," + //
					"CONSTRAINT pk_song PRIMARY KEY (song_id)," + //
					"album_id INTEGER NOT NULL," + //
					"artist_id INTEGER NOT NULL," + //
					"CONSTRAINT fk_album FOREIGN KEY (album_id)" + //
					" REFERENCES album(album_id)" + //
					")");

			st.addBatch("CREATE TABLE user (" + //
					"user_id IDENTITY," + //
					"user_name VARCHAR(64) NOT NULL," + //
					"hash BINARY(64) NOT NULL," + //
					"salt BINARY(64) NOT NULL," + //
					"auth INTEGER NOT NULL," + //
					"downloaded LONG," + //
					"CONSTRAINT pk_user PRIMARY KEY (user_id)" + //
					")");

			st.addBatch("CREATE TABLE playlist (" + //
					"playlist_id IDENTITY (1,1)," + //
					"user_id INTEGER NOT NULL," + //
					"name VARCHAR(64) NOT NULL," + //
					"playtime INTEGER NOT NULL," + //
					"songs INTEGER NOT NULL," + //
					"CONSTRAINT fk_user FOREIGN KEY (user_id)" + //
					" REFERENCES user(user_id)," + //
					"CONSTRAINT pk_playlist PRIMARY KEY (playlist_id)" + //
					")");

			st.addBatch("CREATE TABLE playlist_song (" + //
					"playlist_id INTEGER NOT NULL," + //
					"song_id INTEGER NOT NULL," + //
					"position INTEGER NOT NULL," + //
					"CONSTRAINT fk_playlist FOREIGN KEY (playlist_id)" + //
					" REFERENCES playlist(playlist_id) ON DELETE CASCADE," + //
					"CONSTRAINT fk_song FOREIGN KEY (song_id)" + //
					" REFERENCES song(song_id) ON DELETE CASCADE" + //
					")");

			// song(hash) is used very frequently by the indexer to check if a file is already present in DB
			// adding this index increases performances tenfold
			st.addBatch("CREATE INDEX idx_song_hash ON song(hash)");

			st.addBatch("CREATE INDEX idx_song_pos ON playlist_song(position)");

			st.addBatch("INSERT INTO info (schema_version) VALUES ("
					+ SCHEMA_VERSION + ")");

			st.executeBatch();
			return true;
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	/**
	 * @return Schema Version as stored in DB, or -1 if no record found
	 * @throws SQLException
	 */
	private long getSchemaVersion() throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		long ver = -1l;

		try {
			st = conn.prepareStatement("SELECT schema_version FROM info");
			ResultSet rs = st.executeQuery();

			if (rs.next()) {
				ver = rs.getLong(1);
			}
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return ver;
	}

	@Override
	public void addSong(Song s) throws SQLException {
		// get artist_id or insert
		Integer artist_id = getArtistId(s.artist);
		if (artist_id == null) {
			artist_id = insertArtist(s.artist);
		}

		// get album_id or insert
		Integer album_id = getAlbumId(s.album, artist_id);
		if (album_id == null) {
			album_id = insertAlbum(s.album, artist_id);
		}

		// get songId or insert
		Integer song_id = getSongId(s, album_id);
		if (song_id == null) {
			insertSong(s, album_id, artist_id);
		} else {
			Logger.debug("Song already present: " + s.filepath);
		}
	}

	@Override
	public void addUser(User user) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;

		try {
			st = conn.prepareStatement("INSERT INTO user " + //
					"(user_name,hash,salt,auth,downloaded) VALUES(?,?,?,?,?);");
			st.setString(1, user.username);
			st.setBytes(2, user.sha1);
			st.setBytes(3, user.salt);
			st.setInt(4, user.auth);
			st.setInt(5, 0);

			int rs = st.executeUpdate();
			if (rs != 1) {
				throw new SQLException("Could not insert user '"
						+ user.username + "'");
			}
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	@Override
	public User getUser(String username) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		User user = null;
		try {
			st = conn.prepareStatement("SELECT user_id,hash,salt,auth " + //
					"FROM user WHERE user_name=?");
			st.setString(1, username);
			ResultSet rs = st.executeQuery();

			if (rs.next()) {
				user = new User();
				user.username = username;
				user.id = rs.getInt(1);
				user.sha1 = rs.getBytes(2);
				user.salt = rs.getBytes(3);
				user.auth = rs.getInt(4);
			}

		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return user;
	}

	@Override
	public User getUser(int uid) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		User ret = null;
		try {
			st = conn.prepareStatement("SELECT * FROM user WHERE user_id=?");
			st.setInt(1, uid);
			ResultSet rs = st.executeQuery();

			if (rs.next()) {
				ret = new User();
				ret.id = uid;
				ret.username = rs.getString("user_name");
				ret.auth = rs.getInt("auth");
				ret.sha1 = rs.getBytes("hash");
				ret.salt = rs.getBytes("salt");
				ret.downloaded = rs.getLong("downloaded");
			}

		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return ret;
	}

	@Override
	public List<User> getUsers() throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		List<User> ret = new ArrayList<User>();
		try {
			st = conn.prepareStatement("SELECT * FROM user");
			ResultSet rs = st.executeQuery();

			while (rs.next()) {
				User u = new User();
				u.id = rs.getInt("user_id");
				u.username = rs.getString("user_name");
				u.auth = rs.getInt("auth");
				u.downloaded = rs.getLong("downloaded");
				ret.add(u);
			}

		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}

		return ret;
	}

	@Override
	public void removeUser(int uid) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		try {
			st = conn.prepareStatement("DELETE FROM user WHERE user_id=?");
			st.setInt(1, uid);
			st.executeUpdate();
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	@Override
	public void updateSongCount() throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;

		try {
			// get all album ids
			st = conn.prepareStatement("SELECT album_id FROM album");
			ResultSet rs = st.executeQuery();
			List<Integer> albumIds = new ArrayList<Integer>();
			while (rs.next()) {
				albumIds.add(rs.getInt(1));
			}
			// for each album...
			for (int albumId : albumIds) {
				List<Song> songs = getSongs(albumId);
				int playtime = 0;
				int songCount = songs.size();
				for (Song s : songs) {
					playtime += s.duration;
				}

				if (songCount == 0) {
					// remove album
					st = conn.prepareStatement("DELETE FROM album "
							+ "WHERE album_id=?");
					st.setInt(1, albumId);
					st.executeUpdate();
				} else {
					// update album
					st = conn.prepareStatement("UPDATE album " //
							+ "SET songs=?,playtime=? " //
							+ "WHERE album_id=?");
					st.setInt(1, songCount);
					st.setInt(2, playtime);
					st.setInt(3, albumId);
					st.executeUpdate();
				}
			}

			// get all artist ids
			st = conn.prepareStatement("SELECT artist_id FROM artist");
			rs = st.executeQuery();
			List<Integer> artistIds = new ArrayList<Integer>();
			while (rs.next()) {
				artistIds.add(rs.getInt(1));
			}
			// for each artist...
			for (int artistId : artistIds) {
				List<Album> albums = getAlbums(artistId);
				int playtime = 0;
				int albumCount = albums.size();
				int songCount = 0;
				for (Album a : albums) {
					playtime += a.playtime;
					songCount += a.songs;
				}

				if (songCount == 0) {
					// remove artist
					st = conn.prepareStatement("DELETE FROM artist "
							+ "WHERE artist_id=?");
					st.setInt(1, artistId);
					st.executeUpdate();
				} else {
					// update album
					st = conn.prepareStatement("UPDATE artist " //
							+ "SET songs=?,playtime=?,albums=?" //
							+ "WHERE artist_id=?");
					st.setInt(1, songCount);
					st.setInt(2, playtime);
					st.setInt(3, albumCount);
					st.setInt(4, artistId);
					st.executeUpdate();
				}
			}
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	@Override
	public Playlist addPlaylist(int user_id, String name) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;

		try {
			st = conn.prepareStatement("SELECT playlist_id,songs,playtime "
					+ "FROM playlist WHERE user_id=? AND name=?");
			st.setInt(1, user_id);
			st.setString(2, name);
			ResultSet rs = st.executeQuery();
			if (rs.next()) {
				Playlist pl = new Playlist();
				pl.id = rs.getInt(1);
				pl.name = name;
				pl.user_id = user_id;
				pl.songs = rs.getInt(2);
				pl.playtime = rs.getInt(3);
				return pl;
			}

			st = conn.prepareStatement(
					"INSERT INTO playlist(user_id,name,songs,playtime)"
							+ "VALUES (?,?,0,0)",
					Statement.RETURN_GENERATED_KEYS);
			st.setInt(1, user_id);
			st.setString(2, name);

			st.executeUpdate();

			ResultSet keys = st.getGeneratedKeys();
			if (keys != null && keys.next()) {
				Playlist pl = new Playlist();
				pl.id = keys.getInt(1);
				pl.name = name;
				pl.user_id = user_id;
				return pl;
			} else {
				throw new SQLException("Failed to create new playlist");
			}
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	@Override
	public int addSongsToPlaylist(int playlist_id, int[] song_ids, int user_id)
			throws SQLException, NotFoundException, ForbiddenException {
		this.checkPlaylistUser(playlist_id, user_id);

		Connection conn = getConnection();
		PreparedStatement st = null;
		conn.setAutoCommit(false);
		int inserted = 0;

		try {
			// existing songs: find out number and filter duplicates
			st = conn.prepareStatement("SELECT song_id FROM playlist_song "
					+ "WHERE playlist_id=?");
			st.setInt(1, playlist_id);
			ResultSet rs = st.executeQuery();

			int count = 0;
			HashSet<Integer> hs = new HashSet<Integer>();
			while (rs.next()) {
				hs.add(rs.getInt(1));
				count++;
			}

			// insert songs at the end of the playlist
			st = conn.prepareStatement("INSERT INTO playlist_song"
					+ "(song_id,playlist_id,position) VALUES (?,?,?)");
			for (int id : song_ids) {
				if (hs.contains(id))
					continue;

				st.setInt(1, id);
				st.setInt(2, playlist_id);
				st.setInt(3, count);
				count++;
				inserted++;
				st.addBatch();
			}
			st.executeBatch();

			// find out playtime
			int playtime = 0;
			st = conn.prepareStatement("SELECT sum(song.duration) "
					+ "FROM song JOIN playlist_song "
					+ "ON song.song_id=playlist_song.song_id "
					+ "WHERE playlist_id=?");
			st.setInt(1, playlist_id);
			rs = st.executeQuery();
			if (rs.next()) {
				playtime = rs.getInt(1);
			}

			// update playtime and songcount
			st = conn.prepareStatement("UPDATE playlist "
					+ "SET songs=songs+?, playtime=? "
					+ "WHERE playlist.playlist_id=?");
			st.setInt(1, song_ids.length);
			st.setInt(2, playtime);
			st.setInt(3, playlist_id);
			st.executeUpdate();

			conn.commit();
		} catch (SQLException t) {
			conn.rollback();
			throw t;
		} finally {
			conn.setAutoCommit(true);
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return inserted;
	}

	@Override
	public int addAlbumsToPlaylist(int playlist_id, int[] album_ids, int user_id)
			throws SQLException, NotFoundException, ForbiddenException {

		Connection conn = getConnection();
		PreparedStatement st = null;
		int songCount = 0;
		try {
			String ids = "";
			for (int i = 0; i < album_ids.length; i++) {
				ids += '?';
				if (i < album_ids.length - 1)
					ids += ',';
			}
			st = conn.prepareStatement("SELECT song_id FROM song "
					+ "JOIN album ON song.album_id=album.album_id "
					+ " WHERE song.album_id IN (" + ids
					+ ") ORDER BY date,album.album_id,disc_no,position");
			for (int i = 0; i < album_ids.length; i++) {
				st.setInt(i + 1, album_ids[i]);
			}
			ResultSet rs = st.executeQuery();

			ArrayList<Integer> song_ids = new ArrayList<Integer>();
			while (rs.next()) {
				song_ids.add(rs.getInt(1));
			}
			int[] arr = new int[song_ids.size()];
			for (int i = 0; i < arr.length; i++) {
				arr[i] = song_ids.get(i);
			}
			songCount = this.addSongsToPlaylist(playlist_id, arr, user_id);

		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return songCount;
	}

	@Override
	public int removeSongsFromPlaylist(int playlist_id, int[] song_ids,
			int user_id) throws SQLException, NotFoundException,
			ForbiddenException {
		this.checkPlaylistUser(playlist_id, user_id);

		Connection conn = getConnection();
		PreparedStatement st = null;
		conn.setAutoCommit(false);
		int ret = 0;
		try {
			String ids = "";
			for (int i = 0; i < song_ids.length; i++) {
				ids += '?';
				if (i < song_ids.length - 1)
					ids += ',';
			}
			// remove songs
			st = conn.prepareStatement("DELETE FROM playlist_song "
					+ "WHERE song_id IN (" + ids + ")");
			for (int i = 0; i < song_ids.length; i++) {
				st.setInt(i + 1, song_ids[i]);
			}
			ret = st.executeUpdate();

			// count new playtime
			int playtime = 0;
			st = conn.prepareStatement("SELECT sum(song.duration) "
					+ "FROM song JOIN playlist_song "
					+ "ON song.song_id=playlist_song.song_id "
					+ "WHERE playlist_id=?");
			st.setInt(1, playlist_id);
			ResultSet rs = st.executeQuery();
			if (rs.next()) {
				playtime = rs.getInt(1);
			}

			// update new playtime and songcount
			st = conn.prepareStatement("UPDATE playlist "
					+ "SET songs=songs-?, playtime=? "
					+ "WHERE playlist.playlist_id=?");
			st.setInt(1, song_ids.length);
			st.setInt(2, playtime);
			st.setInt(3, playlist_id);
			st.executeUpdate();
			conn.commit();
		} catch (SQLException t) {
			conn.rollback();
			throw t;
		} finally {
			conn.setAutoCommit(true);
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		reorganizePlaylistOrder(playlist_id);
		return ret;
	}

	@Override
	public int removeSongs(Set<String> hashesToKeep) throws SQLException {
		// this method will take a _long_ time 
		// it should not be called frequently

		Connection conn = getConnection();
		PreparedStatement st = null;
		conn.setAutoCommit(false);
		int ret = 0;

		try {
			String ids = "";
			if (hashesToKeep.size() > 0) {
				for (int i = 0; i < hashesToKeep.size(); i++) {
					ids += "?,";
				}
				ids = ids.substring(0, ids.length() - 1);

				// find which songs will be removed
				st = conn.prepareStatement("SELECT song_id FROM song "
						+ "WHERE hash NOT IN (" + ids + ")");
			} else {
				st = conn.prepareStatement("SELECT song_id FROM song");
			}

			int i = 0;
			for (String hash : hashesToKeep) {
				i++;
				st.setString(i, hash);
			}
			ResultSet rs = st.executeQuery();
			List<Integer> toRemove = new ArrayList<Integer>();
			ids = "";
			while (rs.next()) {
				ids += "?,";
				toRemove.add(rs.getInt(1));
			}
			if (toRemove.size() == 0) {
				return 0;
			}
			ret = toRemove.size();

			ids = ids.substring(0, ids.length() - 1);

			// find which playlists are affected
			st = conn.prepareStatement("SELECT playlist_id FROM playlist_song "
					+ "WHERE song_id IN (" + ids + ")");
			for (i = 0; i < toRemove.size(); i++) {
				st.setInt(i + 1, toRemove.get(i));
			}
			rs = st.executeQuery();
			List<Integer> playlists = new ArrayList<Integer>();
			while (rs.next()) {
				playlists.add(rs.getInt(1));
			}

			// delete the songs
			st = conn.prepareStatement("DELETE FROM song WHERE song_id IN ("
					+ ids + ")");
			for (i = 0; i < toRemove.size(); i++) {
				st.setInt(i + 1, toRemove.get(i));
			}
			st.executeUpdate();
			conn.commit();

			// update playlists playtime & songcount
			for (int playlist_id : playlists) {
				int songNum = 0;
				st = conn
						.prepareStatement("SELECT count(*) FROM playlist_song "
								+ "WHERE playlist_id=?");
				st.setInt(1, playlist_id);
				rs = st.executeQuery();
				if (rs.next()) {
					songNum = rs.getInt(1);
				}

				// find out playtime
				int playtime = 0;
				st = conn.prepareStatement("SELECT sum(song.duration) "
						+ "FROM song JOIN playlist_song "
						+ "ON song.song_id=playlist_song.song_id "
						+ "WHERE playlist_id=?");
				st.setInt(1, playlist_id);
				rs = st.executeQuery();
				if (rs.next()) {
					playtime = rs.getInt(1);
				}

				// update playtime and songcount
				st = conn.prepareStatement("UPDATE playlist "
						+ "SET songs=?, playtime=? "
						+ "WHERE playlist.playlist_id=?");
				st.setInt(1, songNum);
				st.setInt(2, playtime);
				st.setInt(3, playlist_id);
				st.executeUpdate();
				conn.commit();

				// update playlists order
				reorganizePlaylistOrder(playlist_id);
			}
		} catch (SQLException t) {
			conn.rollback();
			throw t;
		} finally {
			conn.setAutoCommit(true);
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return ret;
	}

	private void reorganizePlaylistOrder(int playlist_id) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		conn.setAutoCommit(false);

		try {
			st = conn.prepareStatement("SELECT * FROM playlist_song "
					+ "WHERE playlist_id=? ORDER BY position");
			st.setInt(1, playlist_id);
			ResultSet rs = st.executeQuery();

			st = conn.prepareStatement("UPDATE playlist_song "
					+ "SET position=? WHERE playlist_id=? AND song_id=?");

			int pos = 0;
			while (rs.next()) {
				st.setInt(1, pos);
				st.setInt(2, playlist_id);
				st.setInt(3, rs.getInt("song_id"));
				st.addBatch();
				pos++;
			}
			st.executeBatch();
			conn.commit();
		} catch (SQLException t) {
			conn.rollback();
			throw t;
		} finally {
			conn.setAutoCommit(true);
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	@Override
	public void removePlaylists(int[] playlist_ids, int user_id)
			throws SQLException, NotFoundException, ForbiddenException {
		for (int playlist_id : playlist_ids) {
			this.checkPlaylistUser(playlist_id, user_id);
		}

		Connection conn = getConnection();
		PreparedStatement st = null;
		try {
			String ids = "";
			for (int i = 0; i < playlist_ids.length; i++) {
				ids += '?';
				if (i < playlist_ids.length - 1)
					ids += ',';
			}

			st = conn.prepareStatement("DELETE FROM playlist WHERE "
					+ "playlist_id IN (" + ids + ")");
			for (int i = 0; i < playlist_ids.length; i++) {
				st.setInt(i + 1, playlist_ids[i]);
			}

			st.executeUpdate();
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	@Override
	public void clearPlaylist(int playlist_id, int user_id)
			throws SQLException, NotFoundException, ForbiddenException {
		this.checkPlaylistUser(playlist_id, user_id);

		Connection conn = getConnection();
		PreparedStatement st = null;
		try {
			st = conn
					.prepareStatement("DELETE FROM playlist_song WHERE playlist_id=?");
			st.setInt(1, playlist_id);
			st.executeUpdate();

			st = conn
					.prepareStatement("UPDATE playlist SET songs=0, playtime=0 "
							+ "WHERE playlist_id=?");
			st.setInt(1, playlist_id);
			st.executeUpdate();

		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	@Override
	public List<Playlist> getPlaylists(int user_id) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		List<Playlist> ret = new ArrayList<Playlist>();

		try {
			st = conn
					.prepareStatement("SELECT playlist_id,name,songs,playtime FROM playlist "
							+ "WHERE user_id=?");
			st.setInt(1, user_id);
			ResultSet rs = st.executeQuery();

			while (rs.next()) {
				Playlist pl = new Playlist();
				pl.user_id = user_id;
				pl.id = rs.getInt(1);
				pl.name = rs.getString(2);
				pl.songs = rs.getInt(3);
				pl.playtime = rs.getInt(4);
				ret.add(pl);
			}
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return ret;
	}

	@Override
	public Playlist getPlaylist(int playlist_id) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		Playlist ret = null;
		try {
			st = conn
					.prepareStatement("SELECT name,user_id,songs,playtime FROM playlist "
							+ "WHERE playlist_id=?");
			st.setInt(1, playlist_id);

			ResultSet rs = st.executeQuery();
			if (rs.next()) {
				ret = new Playlist();
				ret.id = playlist_id;
				ret.name = rs.getString(1);
				ret.user_id = rs.getInt(2);
				ret.songs = rs.getInt(3);
				ret.playtime = rs.getInt(4);
			}
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return ret;
	}

	@Override
	public List<Song> getPlaylistSongs(int playlist_id) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		List<Song> ret = new ArrayList<Song>();

		try {
			st = conn
					.prepareStatement("SELECT "
							+ "song.song_id,song.title,song.position,song.duration,song.disc_no,"
							+ "song.album_id,song.artist_id,song.album_name,song.artist_name "
							+ "FROM song JOIN playlist_song "
							+ "ON song.song_id=playlist_song.song_id "
							+ "WHERE playlist_id=?"
							+ "ORDER BY playlist_song.position");
			st.setInt(1, playlist_id);
			ResultSet rs = st.executeQuery();

			while (rs.next()) {
				Song s = new Song();
				s.id = rs.getInt(1);
				s.title = rs.getString(2);
				s.position = rs.getInt(3);
				s.duration = rs.getInt(4);
				s.disc_no = rs.getInt(5);
				s.album_id = rs.getInt(6);
				s.artist_id = rs.getInt(7);
				s.album_name = rs.getString(8);
				s.artist_name = rs.getString(9);
				ret.add(s);
			}
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return ret;
	}

	public Song getPlaylistSong(int playlist_id, int position)
			throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		Song ret = null;

		try {
			st = conn
					.prepareStatement("SELECT song.* FROM song JOIN playlist_song "
							+ "ON song.song_id=playlist_song.song_id "
							+ "WHERE playlist_id=? AND playlist_song.position=?");
			st.setInt(1, playlist_id);
			st.setInt(2, position);
			ResultSet rs = st.executeQuery();

			if (rs.next()) {
				ret = new Song();
				ret.id = rs.getInt("song_id");
				ret.title = rs.getString("title");
				ret.position = rs.getInt("position");
				ret.disc_no = rs.getInt("disc_no");
				ret.duration = rs.getInt("duration");
				ret.album_id = rs.getInt("album_id");
				ret.artist_id = rs.getInt("artist_id");
				ret.album_name = rs.getString("album_name");
				ret.artist_name = rs.getString("artist_name");
			}

		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return ret;
	}

	@Override
	public List<Artist> getArtists() throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		List<Artist> ret = new ArrayList<Artist>();

		try {
			st = conn
					.prepareStatement("SELECT artist_id,artist_name,albums,songs,playtime "
							+ "FROM artist ORDER BY lower(artist_name)");
			ResultSet rs = st.executeQuery();

			while (rs.next()) {
				Artist a = new Artist();
				a.id = rs.getInt(1);
				a.name = rs.getString(2);
				a.albums = rs.getInt(3);
				a.songs = rs.getInt(4);
				a.playtime = rs.getInt(5);
				ret.add(a);
			}

		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return ret;
	}

	@Override
	public Artist getArtist(int id) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		Artist ret = new Artist();

		try {
			st = conn
					.prepareStatement("SELECT artist_id,artist_name,albums,songs,playtime FROM artist WHERE artist_id=?");
			st.setInt(1, id);
			ResultSet rs = st.executeQuery();

			if (rs.next()) {
				ret.id = rs.getInt(1);
				ret.name = rs.getString(2);
				ret.albums = rs.getInt(3);
				ret.songs = rs.getInt(4);
				ret.playtime = rs.getInt(5);
			} else {
				return null;
			}
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return ret;
	}

	@Override
	public List<Album> getAlbums(int artist_id) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		List<Album> ret = new ArrayList<Album>();

		try {
			st = conn
					.prepareStatement("SELECT album_id,album_name,date,songs,playtime,artwork_path "
							+ "FROM album WHERE artist_id=? ORDER BY date,album_name");
			st.setInt(1, artist_id);
			ResultSet rs = st.executeQuery();

			while (rs.next()) {
				Album a = new Album();
				a.id = rs.getInt(1);
				a.artist_id = artist_id;
				a.name = rs.getString(2);
				a.date = rs.getString(3);
				a.songs = rs.getInt(4);
				a.playtime = rs.getInt(5);
				a.artwork_path = rs.getString(6);
				ret.add(a);
			}

		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return ret;
	}

	@Override
	public Album getAlbum(int album_id) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		Album ret = new Album();

		try {
			st = conn
					.prepareStatement("SELECT album_id,artist_id,album_name,date,songs,playtime,artwork_path "
							+ "FROM album WHERE album_id=?");
			st.setInt(1, album_id);
			ResultSet rs = st.executeQuery();

			if (rs.next()) {
				ret.id = rs.getInt(1);
				ret.artist_id = rs.getInt(2);
				ret.name = rs.getString(3);
				ret.date = rs.getString(4);
				ret.songs = rs.getInt(5);
				ret.playtime = rs.getInt(6);
				ret.artwork_path = rs.getString(7);
			} else {
				return null;
			}

		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return ret;
	}

	@Override
	public String getAlbumArtwork(int album_id) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		try {
			st = conn.prepareStatement("SELECT artwork_path "
					+ "FROM album WHERE album_id=?");
			st.setInt(1, album_id);
			ResultSet rs = st.executeQuery();
			if (rs.next()) {
				return rs.getString(1);
			}

		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return null;
	}

	public Map<Integer, List<Integer>> getAlbumArtworks() throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		Map<Integer, List<Integer>> ret = new HashMap<Integer, List<Integer>>();

		try {
			st = conn.prepareStatement("SELECT album_id,artist_id "
					+ "FROM album WHERE artwork_path != 'null'");
			ResultSet rs = st.executeQuery();
			while (rs.next()) {
				int album = rs.getInt(1);
				int artist = rs.getInt(2);
				List<Integer> li = ret.get(artist);
				if (li == null) {
					li = new ArrayList<Integer>();
					ret.put(artist, li);
				}
				li.add(album);
			}
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return ret;
	}

	@Override
	public List<Song> getSongs(int album_id) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		List<Song> ret = new ArrayList<Song>();

		try {
			st = conn
					.prepareStatement("SELECT song_id,title,position,disc_no,duration,format "
							+ "FROM song WHERE album_id=? ORDER BY disc_no,position");
			st.setInt(1, album_id);
			ResultSet rs = st.executeQuery();

			while (rs.next()) {
				Song s = new Song();
				s.id = rs.getInt(1);
				s.album_id = album_id;
				s.title = rs.getString(2);
				s.position = rs.getInt(3);
				s.disc_no = rs.getInt(4);
				s.duration = rs.getInt(5);
				s.format = rs.getString(6);
				ret.add(s);
			}
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return ret;
	}

	@Override
	public Song getSong(int song_id) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		Song ret = new Song();

		try {
			st = conn.prepareStatement("SELECT * "
					+ "FROM song WHERE song_id=?");
			st.setInt(1, song_id);
			ResultSet rs = st.executeQuery();

			if (rs.next()) {
				ret.id = rs.getInt("song_id");
				ret.album_id = rs.getInt("album_id");
				ret.album_name = rs.getString("album_name");
				ret.artist_id = rs.getInt("artist_id");
				ret.artist_name = rs.getString("artist_name");
				ret.title = rs.getString("title");
				ret.position = rs.getInt("position");
				ret.disc_no = rs.getInt("disc_no");
				ret.duration = rs.getInt("duration");
				ret.format = rs.getString("format");
			} else {
				return null;
			}
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return ret;
	}

	@Override
	public List<Song> getRandomSongs(int number) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		List<Song> ret = new ArrayList<Song>();

		try {
			st = conn
					.prepareStatement("SELECT song_id,album_id,title,position,disc_no,duration,format "
							+ "FROM song " + "ORDER BY RAND() LIMIT ?");
			st.setInt(1, number);
			ResultSet rs = st.executeQuery();

			while (rs.next()) {
				Song s = new Song();
				s.id = rs.getInt(1);
				s.album_id = rs.getInt(2);
				s.title = rs.getString(3);
				s.position = rs.getInt(4);
				s.disc_no = rs.getInt(5);
				s.duration = rs.getInt(6);
				s.format = rs.getString(7);
				ret.add(s);
			}
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return ret;
	}

	@Override
	public void updateDownloadedBytes(int uid, long bytes) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		try {
			st = conn
					.prepareStatement("UPDATE user SET downloaded=downloaded+? "
							+ "WHERE user_id=?");
			st.setLong(1, bytes);
			st.setInt(2, uid);
			st.executeUpdate();
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	@Override
	public String getSongFilePath(int song_id) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		String path = null;

		try {
			st = conn
					.prepareStatement("SELECT filepath FROM song WHERE song_id=?");
			st.setInt(1, song_id);
			ResultSet rs = st.executeQuery();

			if (rs.next()) {
				path = rs.getString(1);
			}

		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return path;
	}

	/**
	 * @param art non null Artist
	 * @return id of the Artist in DB, or null
	 * @throws SQLException
	 */
	private Integer getArtistId(Artist art) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;

		try {
			st = conn
					.prepareStatement("SELECT artist_id FROM artist where artist_name=?");
			st.setString(1, art.name);
			ResultSet rs = st.executeQuery();

			if (rs.next()) {
				int id = rs.getInt(1);
				return id;
			} else {
				return null;
			}

		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	/**
	 * @param art non null Artist to insert into DB
	 * @return Id of the inserted row
	 * @throws SQLException
	 */
	private int insertArtist(Artist art) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;

		try {
			st = conn
					.prepareStatement(
							"INSERT INTO artist(artist_name,songs,playtime,albums) VALUES (?,0,0,0)",
							Statement.RETURN_GENERATED_KEYS);
			st.setString(1, art.name);
			st.executeUpdate();

			ResultSet keys = st.getGeneratedKeys();
			if (keys != null && keys.next()) {
				return keys.getInt(1);
			} else {
				throw new SQLException("Failed to insert artist " + art.name);
			}
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	/**
	 * @param alb non null Album
	 * @param artist_id id of the artist related artist
	 * @return id of the album that matches both parameters, or null
	 * @throws SQLException
	 */
	private Integer getAlbumId(Album alb, int artist_id) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;

		try {
			st = conn
					.prepareStatement("SELECT album_id FROM album where album_name=? AND artist_id=?");
			st.setString(1, alb.name);
			st.setInt(2, artist_id);
			ResultSet rs = st.executeQuery();
			if (rs.next()) {
				int id = rs.getInt(1);
				return id;
			} else {
				return null;
			}

		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	/**
	 * @param alb non null Album to insert
	 * @param artistId id of the related Artist
	 * @return id of the inserted row
	 * @throws SQLException
	 */
	private int insertAlbum(Album alb, int artistId) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;

		try {
			st = conn.prepareStatement(
					"INSERT INTO album(album_name,date,songs,playtime,"
							+ "artist_id,artwork_path) "
							+ "VALUES (?,?,?,?,?,?)",
					Statement.RETURN_GENERATED_KEYS);
			st.setString(1, alb.name);
			st.setString(2, alb.date);
			st.setInt(3, 0);
			st.setInt(4, 0);
			st.setInt(5, artistId);
			st.setString(6, alb.artwork_path);
			st.executeUpdate();

			ResultSet keys = st.getGeneratedKeys();
			if (keys != null && keys.next()) {
				return keys.getInt(1);
			} else {
				throw new SQLException("Failed to insert album " + alb.name);
			}
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	/**
	 * 
	 * @param song a non null Song
	 * @param album_id id of the Album containing this song
	 * @return id of the Song matching both parameters, or null
	 * @throws SQLException
	 */
	private Integer getSongId(Song song, int album_id) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;

		try {
			st = conn.prepareStatement("SELECT song_id FROM song where hash=?");
			st.setString(1, song.hash);
			ResultSet rs = st.executeQuery();
			if (rs.next()) {
				int id = rs.getInt(1);
				return id;
			} else {
				return null;
			}

		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	@Override
	public boolean hasSong(String hash) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;

		try {
			st = conn.prepareStatement("SELECT song_id FROM song WHERE hash=?");
			st.setString(1, hash);
			st.executeQuery();

			return st.getResultSet().next();
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	/**
	 * @param son a non null Song
	 * @param albumId id of the Album containing this Song
	 * @param artistId id of the Artist who authored this Song
	 * @return id of the inserted row
	 * @throws SQLException
	 */
	private int insertSong(Song son, int albumId, int artistId)
			throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;

		try {
			st = conn
					.prepareStatement(
							"INSERT INTO song(title,filepath,position,duration,hash,"
									+ "album_id,artist_id,album_name,artist_name,format,"
									+ "disc_no) "
									+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
							Statement.RETURN_GENERATED_KEYS);
			st.setString(1, son.title);
			st.setString(2, son.filepath);
			st.setInt(3, son.position);
			st.setInt(4, son.duration);
			st.setString(5, son.hash);
			st.setInt(6, albumId);
			st.setInt(7, artistId);
			st.setString(8, son.album_name);
			st.setString(9, son.artist_name);
			st.setString(10, son.format);
			st.setInt(11, son.disc_no);

			st.executeUpdate();

			ResultSet keys = st.getGeneratedKeys();
			if (keys != null && keys.next()) {
				return keys.getInt(1);
			} else {
				throw new SQLException("Failed to insert song " + son.title);
			}
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	/**
	 * @param playlist_id playlist id in db
	 * @param user_id user id in db
	 * @throws SQLException
	 * @throws NotFoundException playlist_id is not in db
	 * @throws ForbiddenException playlist does not belong to user_id
	 */
	private void checkPlaylistUser(int playlist_id, int user_id)
			throws SQLException, NotFoundException, ForbiddenException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		try {
			st = conn
					.prepareStatement("SELECT user_id FROM playlist WHERE playlist_id=?");
			st.setInt(1, playlist_id);
			ResultSet rs = st.executeQuery();

			if (rs.next()) {
				int uid = rs.getInt(1);
				if (uid != user_id) {
					throw new ForbiddenException(
							"You do not have the permissions to edit this playlist");
				}
			} else {
				throw new NotFoundException("No such playlist: " + playlist_id);
			}

		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}
}

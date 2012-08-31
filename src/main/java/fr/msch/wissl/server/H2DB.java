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
 * @author mathieu.schnoor@gmail.com
 *
 */
public class H2DB extends DB {

	/* This number should be incremented each time the DB Schema changes.
	 * It is written in the DB so that we decide on startup whether 
	 * the DB can be recovered or needs to be erased */
	private static final long SCHEMA_VERSION = 7L;

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

		try {
			st.addBatch("CREATE TABLE info (" + //
					"schema_version LONG NOT NULL" + //
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
					"artist_name VARCHAR(254) NOT NULL," + //
					"date VARCHAR(8)," + //
					"genre VARCHAR(64) NOT NULL," + //
					"songs INTEGER NOT NULL," + //
					"playtime INTEGER NOT NULL," + //
					"artwork_path VARCHAR(254)," + //
					"CONSTRAINT pk_album PRIMARY KEY (album_id)," + //
					"artist_id INTEGER," + //
					"CONSTRAINT fk_band FOREIGN KEY (artist_id)" + //
					" REFERENCES artist(artist_id) ON DELETE CASCADE" + //
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
					" REFERENCES album(album_id) ON DELETE CASCADE" + //
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
					" REFERENCES user(user_id) ON DELETE CASCADE," + //
					"CONSTRAINT pk_playlist PRIMARY KEY (playlist_id)" + //
					")");

			st.addBatch("CREATE TABLE playlist_song (" + //
					"playlist_id INTEGER NOT NULL," + //
					"song_id INTEGER NOT NULL," + //
					"position INTEGER NOT NULL," + //
					"CONSTRAINT fk_playlist FOREIGN KEY (playlist_id)" + //
					" REFERENCES playlist(playlist_id) ON DELETE CASCADE," + //
					"CONSTRAINT fk_song FOREIGN KEY (song_id)" + //
					" REFERENCES song(song_id) ON DELETE CASCADE," + //
					"CONSTRAINT u_song UNIQUE (song_id, playlist_id)" + //
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
	public int addUser(User user) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;

		try {
			st = conn.prepareStatement("INSERT INTO user " + //
					"(user_name,hash,salt,auth,downloaded) VALUES(?,?,?,?,?);",
					Statement.RETURN_GENERATED_KEYS);
			st.setString(1, user.username);
			st.setBytes(2, user.sha1);
			st.setBytes(3, user.salt);
			st.setInt(4, user.auth);
			st.setInt(5, 0);

			st.executeUpdate();

			ResultSet keys = st.getGeneratedKeys();
			if (keys != null && keys.next()) {
				return keys.getInt(1);
			} else {
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
	public void setPassword(User user) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;

		try {
			st = conn.prepareStatement("UPDATE user " + //
					"SET hash=?,salt=? WHERE user_id=?;");
			st.setBytes(1, user.sha1);
			st.setBytes(2, user.salt);
			st.setInt(3, user.id);
			st.executeUpdate();
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
			st = conn.prepareStatement("SELECT * " + //
					"FROM user WHERE user_name=?");
			st.setString(1, username);
			ResultSet rs = st.executeQuery();

			if (rs.next()) {
				user = new User();
				user.username = username;
				user.id = rs.getInt("user_id");
				user.sha1 = rs.getBytes("hash");
				user.salt = rs.getBytes("salt");
				user.auth = rs.getInt("auth");
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
	public boolean hasUsers() throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		try {
			st = conn.prepareStatement("SELECT * FROM user");
			ResultSet rs = st.executeQuery();

			if (rs.next()) {
				return true;
			} else {
				return false;
			}

		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
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
				albumIds.add(rs.getInt("album_id"));
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
				artistIds.add(rs.getInt("artist_id"));
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
			st = conn.prepareStatement("SELECT * "
					+ "FROM playlist WHERE user_id=? AND name=?");
			st.setInt(1, user_id);
			st.setString(2, name);
			ResultSet rs = st.executeQuery();
			if (rs.next()) {
				Playlist pl = new Playlist();
				pl.id = rs.getInt("playlist_id");
				pl.name = name;
				pl.user_id = user_id;
				pl.songs = rs.getInt("songs");
				pl.playtime = rs.getInt("playtime");
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
				hs.add(rs.getInt("song_id"));
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
	public int[] getSongIds(int[] album_ids) throws SQLException,
			NotFoundException {

		Connection conn = getConnection();
		PreparedStatement st = null;
		int[] ret = new int[0];

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
				song_ids.add(rs.getInt("song_id"));
			}
			int[] arr = new int[song_ids.size()];
			for (int i = 0; i < arr.length; i++) {
				arr[i] = song_ids.get(i);
			}
			ret = arr;

		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return ret;
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
				toRemove.add(rs.getInt("song_id"));
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
				playlists.add(rs.getInt("playlist_id"));
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
	public int removePlaylists(int[] playlist_ids, int user_id)
			throws SQLException, NotFoundException, ForbiddenException {
		for (int playlist_id : playlist_ids) {
			this.checkPlaylistUser(playlist_id, user_id);
		}

		Connection conn = getConnection();
		PreparedStatement st = null;
		int ret;
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

			ret = st.executeUpdate();
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
		return ret;
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
			st = conn.prepareStatement("SELECT * FROM playlist "
					+ "WHERE user_id=?");
			st.setInt(1, user_id);
			ResultSet rs = st.executeQuery();

			while (rs.next()) {
				Playlist pl = new Playlist();
				pl.user_id = user_id;
				pl.id = rs.getInt("playlist_id");
				pl.name = rs.getString("name");
				pl.songs = rs.getInt("songs");
				pl.playtime = rs.getInt("playtime");
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
			st = conn.prepareStatement("SELECT * FROM playlist "
					+ "WHERE playlist_id=?");
			st.setInt(1, playlist_id);

			ResultSet rs = st.executeQuery();
			if (rs.next()) {
				ret = new Playlist();
				ret.id = playlist_id;
				ret.name = rs.getString("name");
				ret.user_id = rs.getInt("user_id");
				ret.songs = rs.getInt("songs");
				ret.playtime = rs.getInt("playtime");
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
							//+ "song.song_id,song.title,song.position,song.duration,song.disc_no,"
							//+ "song.album_id,song.artist_id,song.album_name,song.artist_name "
							+ "song.* " + "FROM song JOIN playlist_song "
							+ "ON song.song_id=playlist_song.song_id "
							+ "WHERE playlist_id=?"
							+ "ORDER BY playlist_song.position");
			st.setInt(1, playlist_id);
			ResultSet rs = st.executeQuery();

			while (rs.next()) {
				Song s = new Song();
				s.id = rs.getInt("song_id");
				s.title = rs.getString("title");
				s.position = rs.getInt("position");
				s.duration = rs.getInt("duration");
				s.disc_no = rs.getInt("disc_no");
				s.album_id = rs.getInt("album_id");
				s.artist_id = rs.getInt("artist_id");
				s.album_name = rs.getString("album_name");
				s.artist_name = rs.getString("artist_name");
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
			st = conn.prepareStatement("SELECT * " //
					+ "FROM artist ORDER BY lower(artist_name)");
			ResultSet rs = st.executeQuery();

			while (rs.next()) {
				Artist a = new Artist();
				a.id = rs.getInt("artist_id");
				a.name = rs.getString("artist_name");
				a.albums = rs.getInt("albums");
				a.songs = rs.getInt("songs");
				a.playtime = rs.getInt("playtime");
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
			st = conn.prepareStatement("SELECT * " + //
					"FROM artist WHERE artist_id=?");
			st.setInt(1, id);
			ResultSet rs = st.executeQuery();

			if (rs.next()) {
				ret.id = rs.getInt("artist_id");
				ret.name = rs.getString("artist_name");
				ret.albums = rs.getInt("albums");
				ret.songs = rs.getInt("songs");
				ret.playtime = rs.getInt("playtime");
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
	public List<String> getArtistSongPaths(int[] artist_id) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		List<String> ret = new ArrayList<String>();

		try {
			String ids = "";
			for (int i = 0; i < artist_id.length; i++) {
				ids += '?';
				if (i < artist_id.length - 1)
					ids += ',';
			}
			st = conn
					.prepareStatement("SELECT filepath FROM song WHERE artist_id "
							+ "IN (" + ids + ")");
			for (int i = 0; i < artist_id.length; i++) {
				st.setInt(i + 1, artist_id[i]);
			}
			ResultSet rs = st.executeQuery();
			while (rs.next()) {
				ret.add(rs.getString("filepath"));
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
	public List<String> getAlbumSongPaths(int[] album_id) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		List<String> ret = new ArrayList<String>();

		try {
			String ids = "";
			for (int i = 0; i < album_id.length; i++) {
				ids += '?';
				if (i < album_id.length - 1)
					ids += ',';
			}
			st = conn
					.prepareStatement("SELECT filepath FROM song WHERE album_id "
							+ "IN (" + ids + ")");
			for (int i = 0; i < album_id.length; i++) {
				st.setInt(i + 1, album_id[i]);
			}
			ResultSet rs = st.executeQuery();
			while (rs.next()) {
				ret.add(rs.getString("filepath"));
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
	public List<String> getSongPaths(int[] song_ids) throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		List<String> ret = new ArrayList<String>();

		try {
			String ids = "";
			for (int i = 0; i < song_ids.length; i++) {
				ids += '?';
				if (i < song_ids.length - 1)
					ids += ',';
			}
			st = conn
					.prepareStatement("SELECT filepath FROM song WHERE song_id "
							+ "IN (" + ids + ")");
			for (int i = 0; i < song_ids.length; i++) {
				st.setInt(i + 1, song_ids[i]);
			}
			ResultSet rs = st.executeQuery();
			while (rs.next()) {
				ret.add(rs.getString("filepath"));
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
			st = conn.prepareStatement("SELECT * " //
					+ "FROM album WHERE artist_id=? ORDER BY date,album_name");
			st.setInt(1, artist_id);
			ResultSet rs = st.executeQuery();

			while (rs.next()) {
				Album a = new Album();
				a.id = rs.getInt("album_id");
				a.artist_id = artist_id;
				a.name = rs.getString("album_name");
				a.date = rs.getString("date");
				a.songs = rs.getInt("songs");
				a.playtime = rs.getInt("playtime");
				a.artwork_path = rs.getString("artwork_path");
				a.genre = rs.getString("genre");
				a.artist_name = rs.getString("artist_name");
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
			st = conn.prepareStatement("SELECT * " //
					+ "FROM album WHERE album_id=?");
			st.setInt(1, album_id);
			ResultSet rs = st.executeQuery();

			if (rs.next()) {
				ret.id = rs.getInt("album_id");
				ret.artist_id = rs.getInt("artist_id");
				ret.name = rs.getString("album_name");
				ret.date = rs.getString("date");
				ret.songs = rs.getInt("songs");
				ret.playtime = rs.getInt("playtime");
				ret.artwork_path = rs.getString("artwork_path");
				ret.artist_name = rs.getString("artist_name");
				ret.genre = rs.getString("genre");
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
				return rs.getString("artwork_path");
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
				int album = rs.getInt("album_id");
				int artist = rs.getInt("artist_id");
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
			st = conn.prepareStatement("SELECT * " //
					+ "FROM song WHERE album_id=? ORDER BY disc_no,position");
			st.setInt(1, album_id);
			ResultSet rs = st.executeQuery();

			while (rs.next()) {
				Song s = new Song();
				s.id = rs.getInt("song_id");
				s.album_id = album_id;
				s.title = rs.getString("title");
				s.position = rs.getInt("position");
				s.disc_no = rs.getInt("disc_no");
				s.duration = rs.getInt("duration");
				s.format = rs.getString("format");
				s.album_name = rs.getString("album_name");
				s.artist_name = rs.getString("artist_name");
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
			st = conn.prepareStatement("SELECT * " //
					+ "FROM song " + "ORDER BY RAND() LIMIT ?");
			st.setInt(1, number);
			ResultSet rs = st.executeQuery();

			while (rs.next()) {
				Song s = new Song();
				s.id = rs.getInt("song_id");
				s.album_id = rs.getInt("album_id");
				s.title = rs.getString("title");
				s.position = rs.getInt("position");
				s.disc_no = rs.getInt("disc_no");
				s.duration = rs.getInt("duration");
				s.format = rs.getString("format");
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
				path = rs.getString("filepath");
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
				int id = rs.getInt("artist_id");
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
				int id = rs.getInt("album_id");
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
							+ "artist_id,artwork_path,artist_name,genre) "
							+ "VALUES (?,?,?,?,?,?,?,?)",
					Statement.RETURN_GENERATED_KEYS);
			st.setString(1, alb.name);
			st.setString(2, alb.date);
			st.setInt(3, 0);
			st.setInt(4, 0);
			st.setInt(5, artistId);
			st.setString(6, alb.artwork_path);
			st.setString(7, alb.artist_name);
			st.setString(8, alb.genre);
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
				int id = rs.getInt("song_id");
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
				int uid = rs.getInt("user_id");
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

	@Override
	public int getSongCount() throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		try {
			st = conn.prepareStatement("SELECT count(*) FROM song");
			ResultSet rs = st.executeQuery();
			if (rs.next()) {
				return rs.getInt(1);
			}
			return 0;
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	@Override
	public int getAlbumCount() throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		try {
			st = conn.prepareStatement("SELECT count(*) FROM album");
			ResultSet rs = st.executeQuery();
			if (rs.next()) {
				return rs.getInt(1);
			}
			return 0;
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	@Override
	public int getArtistCount() throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		try {
			st = conn.prepareStatement("SELECT count(*) FROM artist");
			ResultSet rs = st.executeQuery();
			if (rs.next()) {
				return rs.getInt(1);
			}
			return 0;
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	@Override
	public int getPlaylistCount() throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		try {
			st = conn.prepareStatement("SELECT count(*) FROM playlist");
			ResultSet rs = st.executeQuery();
			if (rs.next()) {
				return rs.getInt(1);
			}
			return 0;
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	@Override
	public int getUserCount() throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		try {
			st = conn.prepareStatement("SELECT count(*) FROM user");
			ResultSet rs = st.executeQuery();
			if (rs.next()) {
				return rs.getInt(1);
			}
			return 0;
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	@Override
	public long getTotalSongDuration() throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		try {
			st = conn.prepareStatement("SELECT sum(duration) FROM song");
			ResultSet rs = st.executeQuery();
			if (rs.next()) {
				return rs.getLong(1);
			}
			return 0;
		} finally {
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	@Override
	public List<Artist> searchArtist(String name, int maxResults)
			throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		List<Artist> ret = new ArrayList<Artist>();
		try {
			st = conn
					.prepareStatement("SELECT * FROM artist WHERE lower(artist_name) "
							+ "LIKE lower(?) LIMIT ?");
			st.setString(1, "%" + name + "%");
			st.setInt(2, maxResults);
			ResultSet rs = st.executeQuery();

			while (rs.next()) {
				Artist ar = new Artist();
				ar.id = rs.getInt("artist_id");
				ar.name = rs.getString("artist_name");
				ar.albums = rs.getInt("albums");
				ar.songs = rs.getInt("songs");
				ar.playtime = rs.getInt("playtime");
				ret.add(ar);
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
	public List<Album> searchAlbum(String title, int maxResults)
			throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		List<Album> ret = new ArrayList<Album>();
		try {
			st = conn
					.prepareStatement("SELECT * FROM album WHERE lower(album_name) "
							+ "LIKE lower(?) LIMIT ?");
			st.setString(1, "%" + title + "%");
			st.setInt(2, maxResults);
			ResultSet rs = st.executeQuery();

			while (rs.next()) {
				Album al = new Album();
				al.id = rs.getInt("album_id");
				al.name = rs.getString("album_name");
				al.date = rs.getString("date");
				al.songs = rs.getInt("songs");
				al.playtime = rs.getInt("playtime");
				al.artist_id = rs.getInt("artist_id");
				al.artist_name = rs.getString("artist_name");
				al.genre = rs.getString("genre");
				ret.add(al);
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
	public List<Song> searchSong(String title, int maxResults)
			throws SQLException {
		Connection conn = getConnection();
		PreparedStatement st = null;
		List<Song> ret = new ArrayList<Song>();
		try {
			st = conn.prepareStatement("SELECT * FROM song WHERE lower(title) "
					+ "LIKE lower(?) LIMIT ?");
			st.setString(1, "%" + title + "%");
			st.setInt(2, maxResults);
			ResultSet rs = st.executeQuery();

			while (rs.next()) {
				Song s = new Song();
				s.id = rs.getInt("song_id");
				s.title = rs.getString("title");
				s.album_name = rs.getString("album_name");
				s.artist_name = rs.getString("artist_name");
				s.position = rs.getInt("position");
				s.disc_no = rs.getInt("disc_no");
				s.duration = rs.getInt("duration");
				s.format = rs.getString("format");
				s.album_id = rs.getInt("album_id");
				s.artist_id = rs.getInt("artist_id");
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
	public void editArtist(int[] artist_ids, String artist_name)
			throws SQLException {
		Connection conn = getConnection();
		conn.setAutoCommit(false);
		PreparedStatement st = null;

		try {
			// find existing artist ?
			int id = -1;
			st = conn
					.prepareStatement("SELECT artist_id FROM artist WHERE artist_name=?");
			st.setString(1, artist_name);
			ResultSet rs = st.executeQuery();
			if (rs.next()) {
				id = rs.getInt("artist_id");
			}

			if (id == -1) {
				id = artist_ids[0];
			}

			for (int artist_id : artist_ids) {

				st = conn.prepareStatement("UPDATE album " + //
						"SET artist_name=?, artist_id=? " + // 
						"WHERE artist_id=?");
				st.setString(1, artist_name);
				st.setInt(2, id);
				st.setInt(3, artist_id);
				st.executeUpdate();

				st = conn.prepareStatement("UPDATE song " + //
						"SET artist_name=?, artist_id=? " + //
						"WHERE artist_id=?");
				st.setString(1, artist_name);
				st.setInt(2, id);
				st.setInt(3, artist_id);
				st.executeUpdate();

				if (id == artist_id) {
					st = conn.prepareStatement("UPDATE artist " + // 
							"SET artist_name=?  " + //
							"WHERE artist_id=?");
					st.setString(1, artist_name);
					st.setInt(2, artist_id);
					st.executeUpdate();
				} else {
					// delete artist at the end to avoid cascade delete to album/song
					st = conn.prepareStatement("DELETE FROM artist "
							+ "WHERE artist_id=?");
					st.setInt(1, artist_id);
					st.executeUpdate();
				}
			}
			conn.commit();
			conn.setAutoCommit(true);

			this.updateSongCount();

		} catch (SQLException e) {
			conn.rollback();
		} finally {
			conn.setAutoCommit(true);
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	@Override
	public void editAlbum(int[] album_ids, String album_name,
			String artist_name, int date, String genre, byte[] artwork)
			throws SQLException {
		Connection conn = getConnection();
		conn.setAutoCommit(false);
		PreparedStatement st = null;

		try {
			String ids = "";
			for (int i = 0; i < album_ids.length; i++) {
				ids += '?';
				if (i < album_ids.length - 1)
					ids += ',';
			}
			// move artist if necessary
			if (artist_name != null && artist_name.length() > 0) {
				int artist_id = -1;
				st = conn.prepareStatement("SELECT artist_id FROM artist "
						+ "WHERE artist_name=?");
				st.setString(1, artist_name);
				ResultSet rs = st.executeQuery();
				if (rs.next()) {
					artist_id = rs.getInt("artist_id");
				}
				if (artist_id == -1) {
					Artist ar = new Artist();
					ar.name = artist_name;
					artist_id = insertArtist(ar);
				}

				st = conn.prepareStatement("UPDATE album SET " + //
						"artist_id=?, artist_name=? " + //
						"WHERE album_id IN (" + ids + ")");
				st.setInt(1, artist_id);
				st.setString(2, artist_name);
				for (int i = 0; i < album_ids.length; i++) {
					st.setInt(i + 3, album_ids[i]);
				}
				st.executeUpdate();

				st = conn.prepareStatement("UPDATE song SET " + //
						"artist_id=?, artist_name=? " + //
						"WHERE album_id IN (" + ids + ")");
				st.setInt(1, artist_id);
				st.setString(2, artist_name);
				for (int i = 0; i < album_ids.length; i++) {
					st.setInt(i + 3, album_ids[i]);
				}
				st.executeUpdate();
			}

			// move album if necessary
			if (album_name != null && album_name.length() > 0) {
				int album_id = -1;
				st = conn.prepareStatement("SELECT album_id FROM album "
						+ "WHERE album_name=?");
				st.setString(1, album_name);
				ResultSet rs = st.executeQuery();
				if (rs.next()) {
					album_id = rs.getInt("artist_id");
				}
				if (album_id == -1) {
					album_id = album_ids[0];
				}

				for (int i = 0; i < album_ids.length; i++) {
					st = conn.prepareStatement("UPDATE song " + //
							"SET album_name=?, album_id=? " + //
							"WHERE artist_id=?");
					st.setString(1, album_name);
					st.setInt(2, album_id);
					st.setInt(3, album_ids[i]);
					st.executeUpdate();

					if (album_id == album_ids[i]) {
						st = conn.prepareStatement("UPDATE album " + //
								"SET album_name=?" + //
								"WHERE album_id=?");
						st.setString(1, album_name);
						st.setInt(2, album_id);
						st.executeUpdate();
					} else {
						st = conn.prepareStatement("DELETE FROM album "
								+ "WHERE album_id=?");
						st.setInt(1, album_ids[i]);
						st.executeUpdate();
					}
				}

				if (date > 0) {
					st = conn.prepareStatement("UPDATE album SET " + //
							"date=? WHERE album_id=?");
					st.setInt(1, date);
					st.setInt(2, album_id);
					st.executeUpdate();
				}
				if (genre != null && genre.length() > 0) {
					st = conn.prepareStatement("UPDATE album SET " + //
							"genre=? WHERE album_id=?");
					st.setString(1, genre);
					st.setInt(2, album_id);
					st.executeUpdate();
				}

			} else {

				ids = "";
				for (int i = 0; i < album_ids.length; i++) {
					ids += '?';
					if (i < album_ids.length - 1)
						ids += ',';
				}
				if (date > 0) {
					st = conn.prepareStatement("UPDATE album SET " + //
							"date=? WHERE album_id IN (" + ids + ")");
					st.setInt(1, date);
					for (int i = 0; i < album_ids.length; i++) {
						st.setInt(i + 2, album_ids[i]);
					}
					st.executeUpdate();
				}

				if (genre != null && genre.length() > 0) {
					st = conn.prepareStatement("UPDATE album SET " + //
							"genre=? WHERE album_id IN (" + ids + ")");
					st.setString(1, genre);
					for (int i = 0; i < album_ids.length; i++) {
						st.setInt(i + 2, album_ids[i]);
					}
					st.executeUpdate();
				}
			}
			conn.commit();
			conn.setAutoCommit(true);

			this.updateSongCount();

		} catch (SQLException e) {
			conn.rollback();
		} finally {
			conn.setAutoCommit(true);
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

	@Override
	public void editSong(int[] song_ids, String song_title, int position,
			int disc_no, String album_name, String artist_name, byte[] artwork)
			throws SQLException {
		Connection conn = getConnection();
		conn.setAutoCommit(false);
		PreparedStatement st = null;

		try {

			String ids = "";
			for (int i = 0; i < song_ids.length; i++) {
				ids += '?';
				if (i < song_ids.length - 1)
					ids += ',';
			}

			int artist_id = -1;
			if (artist_name != null && artist_name.length() > 0) {
				st = conn.prepareStatement("SELECT artist_id FROM artist "
						+ "WHERE artist_name=?");
				st.setString(1, artist_name);
				ResultSet rs = st.executeQuery();
				if (rs.next()) {
					artist_id = rs.getInt("artist_id");
				}
				if (artist_id == -1) {
					Artist ar = new Artist();
					ar.name = artist_name;
					artist_id = insertArtist(ar);
				}

				st = conn.prepareStatement("UPDATE song SET " + //
						"artist_id=?, artist_name=? " + //
						"WHERE song_id IN (" + ids + ")");
				st.setInt(1, artist_id);
				st.setString(2, artist_name);
				for (int i = 0; i < song_ids.length; i++) {
					st.setInt(i + 3, song_ids[i]);
				}
				st.executeUpdate();
			}

			if (album_name != null && album_name.length() > 0) {
				int album_id = -1;
				st = conn
						.prepareStatement("SELECT album_id,artist_id,artist_name"
								+ " FROM album WHERE album_name=?");
				st.setString(1, album_name);
				ResultSet rs = st.executeQuery();
				if (rs.next()) {
					album_id = rs.getInt("album_id");
					if (artist_id == -1) {
						artist_id = rs.getInt("artist_id");
						artist_name = rs.getString("artist_name");
					}
				}
				if (album_id == -1) {
					Album ar = new Album();
					ar.name = album_name;
					ar.artist_name = artist_name;
					album_id = insertAlbum(ar, artist_id);
				}

				st = conn.prepareStatement("UPDATE song SET " + //
						"album_id=?, album_name=? " + //
						"WHERE song_id IN (" + ids + ")");
				st.setInt(1, album_id);
				st.setString(2, album_name);
				for (int i = 0; i < song_ids.length; i++) {
					st.setInt(i + 3, song_ids[i]);
				}
				st.executeUpdate();
			}

			ids = "";
			for (int i = 0; i < song_ids.length; i++) {
				ids += '?';
				if (i < song_ids.length - 1)
					ids += ',';
			}
			if (position > 0) {
				st = conn.prepareStatement("UPDATE song SET " + //
						"position=? WHERE song_id IN (" + ids + ")");
				st.setInt(1, position);
				for (int i = 0; i < song_ids.length; i++) {
					st.setInt(i + 2, song_ids[i]);
				}
				st.executeUpdate();
			}

			if (disc_no > 0) {
				st = conn.prepareStatement("UPDATE song SET " + //
						"disc_no=? WHERE song_id IN (" + ids + ")");
				st.setInt(1, disc_no);
				for (int i = 0; i < song_ids.length; i++) {
					st.setInt(i + 2, song_ids[i]);
				}
				st.executeUpdate();
			}

			if (song_title != null) {
				st = conn.prepareStatement("UPDATE song SET " + //
						"title=? WHERE song_id IN (" + ids + ")");
				st.setString(1, song_title);
				for (int i = 0; i < song_ids.length; i++) {
					st.setInt(i + 2, song_ids[i]);
				}
				st.executeUpdate();
			}

			conn.commit();
			conn.setAutoCommit(true);

			this.updateSongCount();

		} catch (SQLException e) {
			conn.rollback();
		} finally {
			conn.setAutoCommit(true);
			if (st != null)
				st.close();
			if (conn != null)
				conn.close();
		}
	}

}

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
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.resteasy.spi.NotFoundException;

import fr.msch.wissl.server.exception.ForbiddenException;

/**
 * 
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
public abstract class DB {

	/** singleton instance */
	private static DB instance = null;

	/**
	 * create and initialize DB
	 * @param db generic DB parameter; file or URL depending the connector used
	 * @throws SQLException something you probably can't recover from
	 */
	public static void create(String db) throws SQLException {
		instance = new H2DB(db);
	}

	/**
	 * @return static singleton DB instance
	 */
	public static DB get() {
		return instance;
	}

	/**
	 * close open connection, shutdown DB
	 */
	public abstract void close() throws SQLException;

	/**
	 * @return an open connection to the DB
	 * @throws SQLException
	 */
	protected abstract Connection getConnection() throws SQLException;

	/**
	 * Insert a Song into DB, inserting as well Artist & Album if they are not already known
	 * @param song a song with a non null Artist and Album linked
	 * @throws SQLException
	 */
	public abstract void addSong(Song song) throws SQLException;

	/**
	 * Insert a new User record into DB
	 * @param user user to insert
	 * @return unique id of the inserted user
	 * @throws SQLException
	 */
	public abstract int addUser(User user) throws SQLException;

	/**
	 * Change password for an existing user
	 * @param user user changing its password
	 * @throws SQLException
	 */
	public abstract void setPassword(User user) throws SQLException;

	/**
	 * @param username valid username of user in db
	 * @return corresponding user, or null
	 * @throws SQLException
	 */
	public abstract User getUser(String username) throws SQLException;

	/**
	 * @return true if there is at least one user in DB
	 * @throws SQLException
	 */
	public abstract boolean hasUsers() throws SQLException;

	/**
	 * @param user_id unique user id in db
	 * @return corresponding user, or null
	 * @throws SQLException
	 */
	public abstract User getUser(int user_id) throws SQLException;

	/**
	 * @return list of all users
	 * @throws SQLException
	 */
	public abstract List<User> getUsers() throws SQLException;

	/**
	 * @param uid unique id of the user to remove
	 * @throws SQLException
	 */
	public abstract void removeUser(int uid) throws SQLException;

	/**
	 * Goes through all songs/albums/artists to update album count, song count,
	 * playtime and delete empty albums/artists
	 * @throws SQLException
	 */
	public abstract void updateSongCount() throws SQLException;

	/**
	 * Create new playlist, return existing playlist if it exists
	 * @param user_id unique if of an user in DB
	 * @param name name of the new playlist
	 * @throws SQLException
	 */
	public abstract Playlist addPlaylist(int user_id, String name)
			throws SQLException;

	/**
	 * Add a new song into the playlist
	 * @param playlist_id unique id of the playlist
	 * @param song_id unique id of the song in DB
	 * @param user_id id of the user requesting songs to be added
	 * @return number of inserted songs
	 * @throws SQLException
	 * @throws ForbiddenException 
	 * @throws NotFoundException 
	 */
	public abstract int addSongsToPlaylist(int playlist_id, int[] song_ids,
			int user_id) throws SQLException, NotFoundException,
			ForbiddenException;

	/**
	 * Returns the ids of all songs contained in the given albums
	 * @param album_ids unique ids of the albums in DB
	 * @param userId id of the user requesting songs to be added
	 * @return array of unique song ids
	 * @throws SQLException
	 * @throws NotFoundException 
	 */
	public abstract int[] getSongIds(int[] album_ids) throws SQLException,
			NotFoundException;

	/**
	 * Remove songs from the given playlist
	 * @param playlist_id unique id of the playlist
	 * @param song_ids unique id of the songs to remove from the playlist
	 * @paran user_id id of the user requesting songs to be removed from playlist
	 * @return number of deleted records
	 * @throws SQLException
	 * @throws ForbiddenException 
	 * @throws NotFoundException 
	 */
	public abstract int removeSongsFromPlaylist(int playlist_id,
			int[] song_ids, int user_id) throws SQLException,
			NotFoundException, ForbiddenException;

	/**
	 * Remove playlists
	 * @param playlist_ids unique ids of the playlist
	 * @param user_id id of the user requesting playlist removal
	 * @return number of deleted playlists
	 * @throws SQLException
	 * @throws ForbiddenException 
	 * @throws NotFoundException 
	 */
	public abstract int removePlaylists(int[] playlist_ids, int user_id)
			throws SQLException, NotFoundException, ForbiddenException;

	/**
	 * Remove all songs from a playlist 
	 * @param playlist_id unique playlist id
	 * @param user_id id of the user requesting clearing the playlist
	 * @throws SQLException
	 * @throws ForbiddenException 
	 * @throws NotFoundException 
	 */
	public abstract void clearPlaylist(int playlist_id, int user_id)
			throws SQLException, NotFoundException, ForbiddenException;

	/**
	 * Get all playlists for a given user
	 * @param user_id unique id of an user in DB
	 * @return all playlists owned by the matching user, or an empy list
	 * @throws SQLException
	 */
	public abstract List<Playlist> getPlaylists(int user_id)
			throws SQLException;

	/**
	 * Get a single playlist
	 * @param playlist_id unique id of the playlist in DB
	 * @return the selected playlist or null
	 * @throws SQLException
	 */
	public abstract Playlist getPlaylist(int playlist_id) throws SQLException;

	/**
	 * Get all songs for a given playlist
	 * @param playlist_id unique id of a playlist in DB
	 * @return list of all songs in this playlist
	 * @throws SQLException
	 */
	public abstract List<Song> getPlaylistSongs(int playlist_id)
			throws SQLException;

	/**
	 * Return the song at the given position in the given playlist
	 * @param playlist_id unique playlist ID in DB
	 * @param position song position in the playlist
	 * @return the corresponding song, or null
	 * @throws SQLException
	 */
	public abstract Song getPlaylistSong(int playlist_id, int position)
			throws SQLException;

	/**
	 * @param hash MD5 hash of a song filepath
	 * @return true if the DB already contains a song with the same hashed filepath value
	 * @throws SQLException
	 */
	public abstract boolean hasSong(String hash) throws SQLException;

	/**
	 * @return all artists in DB, or an empty list
	 * @throws SQLException
	 */
	public abstract List<Artist> getArtists() throws SQLException;

	/**
	 * @param id id of an artist in DB
	 * @return one specific artist, or null
	 * @throws SQLException
	 */
	public abstract Artist getArtist(int id) throws SQLException;

	/**
	 * @param artist_id valid value for artist.artist_id in DB 
	 * @return all albums from the artist corresponding the id, or an empty list
	 * @throws SQLException
	 */
	public abstract List<Album> getAlbums(int artist_id) throws SQLException;

	/**
	 * @param id id of an album in DB
	 * @return the album matching the id, or null
	 * @throws SQLException
	 */
	public abstract Album getAlbum(int id) throws SQLException;

	/**
	 * @param album_id valid value for album.album_id in DB
	 * @return all songs from the album corresponding the id, or an empty list
	 * @throws SQLException
	 */
	public abstract List<Song> getSongs(int album_id) throws SQLException;

	/**
	 * Remove all songs that do not match the given hashes
	 * @param hashesToKeep hashes of the songs to keep in DB
	 * @return number of removed entries
	 * @throws SQLException
	 */
	public abstract int removeSongs(Set<String> hashesToKeep)
			throws SQLException;

	/**
	 * @param num number of random songs
	 * @return random songs
	 * @throws SQLException
	 */
	public abstract List<Song> getRandomSongs(int num) throws SQLException;

	/**
	 * @param song_id valud value for song.song_id in DB
	 * @return the matching song, or null
	 * @throws SQLException
	 */
	public abstract Song getSong(int song_id) throws SQLException;

	/**
	 * @param album_id unique album id in DB
	 * @return album artwork path on the local FS
	 * @throws SQLException
	 */
	public abstract String getAlbumArtwork(int album_id) throws SQLException;

	/**
	 * @return maps artist_id with a par of album_id:artwork_id for each album that has an artwork
	 * @throws SQLException
	 */
	public abstract Map<Integer, Map<Integer, String>> getAlbumArtworks()
			throws SQLException;

	/**
	 * @param uid unique user uid to update
	 * @param bytes bytes to add to the downloaded total
	 * @throws SQLException
	 */
	public abstract void updateDownloadedBytes(int uid, long bytes)
			throws SQLException;

	/**
	 * @param song_id valid value for song.song_id in DB
	 * @return file path on the local filesystem for the song matching the id, or null
	 * @throws SQLException
	 */
	public abstract String getSongFilePath(int song_id) throws SQLException;

	/**
	 * @param artist_id unique DB artist ids
	 * @return list of file paths for all individual songs for these artists
	 * @throws SQLException
	 */
	public abstract List<String> getArtistSongPaths(int[] artist_id)
			throws SQLException;

	/**
	 * @param album_id unique DB album ids
	 * @return list of file paths for all individual songs in this album
	 * @throws SQLException
	 */
	public abstract List<String> getAlbumSongPaths(int[] album_id)
			throws SQLException;

	/**
	 * @param song_id unique DB song ids
	 * @return list of all file paths for these songs
	 * @throws SQLException
	 */
	public abstract List<String> getSongPaths(int[] song_id)
			throws SQLException;

	/**
	 * @return total number of songs
	 * @throws SQLException
	 */
	public abstract int getSongCount() throws SQLException;

	/**
	 * @return total number of albums
	 * @throws SQLException
	 */
	public abstract int getAlbumCount() throws SQLException;

	/**
	 * @return total number of artists
	 * @throws SQLException
	 */
	public abstract int getArtistCount() throws SQLException;

	/**
	 * @return total number of playlists
	 * @throws SQLException
	 */
	public abstract int getPlaylistCount() throws SQLException;

	/**
	 * @return total number of users
	 * @throws SQLException
	 */
	public abstract int getUserCount() throws SQLException;

	/**
	 * @return cumulated duration of all known songs
	 * @throws SQLException
	 */
	public abstract long getTotalSongDuration() throws SQLException;

	/**
	 * @param title partial artist name
	 * @param maxResults maximum number of results
	 * @return artists matching the provided artist name, ignoring case.
	 *  ie parameter 'roll' will match artist 'The Rolling Stones'
	 * @throws SQLException
	 */
	public abstract List<Artist> searchArtist(String name, int maxResults)
			throws SQLException;

	/**
	 * @param title partial album title
	 * @param maxResults maximum number of results
	 * @return albums matching the provided album title, ignoring case.
	 *  ie parameter 'sou' will match album 'Rubber Soul'
	 * @throws SQLException
	 */
	public abstract List<Album> searchAlbum(String title, int maxResults)
			throws SQLException;

	/**
	 * @param title partial song title
	 * @param maxResults maximum number of results
	 * @return songs matching the provided song title, ignoring case.
	 *  ie parameter 'hun' will match song 'The Hunter'
	 * @throws SQLException
	 */
	public abstract List<Song> searchSong(String title, int maxResults)
			throws SQLException;

	/**
	 * Edit artist related info in DB
	 * Will merge artists when necessary
	 * @param artist_ids ids of the artists to apply new info to
	 * @param artist_name new artist name
	 * @throws SQLException
	 */
	public abstract void editArtist(int[] artist_ids, String artist_name)
			throws SQLException;

	/**
	 * Edit album related info in DB
	 * Will merge artists and albums when necessary
	 * @param album_ids ids of the albums to apply new info
	 * @param album_name new album name
	 * @param artist_name new artist name
	 * @param date new date
	 * @param genre new genre
	 * @throws SQLException
	 */
	public abstract void editAlbum(int[] album_ids, String album_name,
			String artist_name, int date, String genre) throws SQLException;

	/**
	 * Edit song related info in DB
	 * Will merge artists and albums when necessary
	 * @param song_ids ids of the songs to apply new info
	 * @param song_title new song name
	 * @param position new position in album
	 * @param disc_no new disc number
	 * @param album_name new album name
	 * @param artist_name new artist name
	 * @throws SQLException
	 */
	public abstract void editSong(int[] song_ids, String song_title,
			int position, int disc_no, String album_name, String artist_name)
			throws SQLException;

	/**
	 * Edit artwork location for the given albums
	 * @param album_ids unique album ids
	 * @param filePath path to the new artwork
	 * @throws SQLException
	 */
	public abstract void editAlbumArtwork(int[] album_ids, String filePath)
			throws SQLException;

}

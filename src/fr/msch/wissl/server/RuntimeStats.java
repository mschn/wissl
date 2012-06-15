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

import java.util.Calendar;
import java.util.Date;

/**
 * Holds and exposes very simple runtime statistics
 * <p>
 * Most of this information is held in DB,
 * but it makes sense to cache it here 
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
public class RuntimeStats {

	private static RuntimeStats instance = null;

	/** total number of songs known */
	private long songCount;
	private long albumCount;
	private long artistCount;
	private long playlistCount;
	private long userCount;
	private long playtime;
	private long downloaded;

	private Date uptime;

	private RuntimeStats() {
		this.uptime = Calendar.getInstance().getTime();
	}

	static void create() {
		instance = new RuntimeStats();
	}

	/**
	 * @return a JSON representation of this object
	 */
	public static String toJSON() {
		StringBuilder sb = new StringBuilder();

		long dt = Calendar.getInstance().getTime().getTime()
				- instance.uptime.getTime();

		sb.append("{\"songs\":" + instance.songCount);
		sb.append(",\"albums\":" + instance.albumCount);
		sb.append(",\"artists\":" + instance.artistCount);
		sb.append(",\"playlists\":" + instance.playlistCount);
		sb.append(",\"users\":" + instance.userCount);
		sb.append(",\"playtime\":" + instance.playtime);
		sb.append(",\"downloaded\":" + instance.downloaded);
		sb.append(",\"uptime\":" + dt + "}");

		return sb.toString();
	}

	public static long getSongCount() {
		return instance.songCount;
	}

	public static void setSongCount(long songCount) {
		synchronized (instance) {
			instance.songCount = songCount;
		}
	}

	public static void addSongCount(long add) {
		synchronized (instance) {
			instance.songCount += add;
		}
	}

	public static long getAlbumCount() {
		return instance.albumCount;
	}

	public static void setAlbumCount(long albumCount) {
		synchronized (instance) {
			instance.albumCount = albumCount;
		}
	}

	public static void addAlbumCount(long add) {
		synchronized (instance) {
			instance.albumCount += add;
		}
	}

	public static long getArtistCount() {
		return instance.artistCount;
	}

	public static void setArtistCount(long artistCount) {
		synchronized (instance) {
			instance.artistCount = artistCount;
		}
	}

	public static void addArtistCount(long add) {
		synchronized (instance) {
			instance.artistCount += add;
		}
	}

	public static long getPlaylistCount() {
		return instance.playlistCount;
	}

	public static void setPlaylistCount(long playlists) {
		synchronized (instance) {
			instance.playlistCount = playlists;
		}
	}

	public static void addPlaylistCount(int add) {
		synchronized (instance) {
			instance.playlistCount += add;
		}
	}

	public static long getPlaytime() {
		return instance.playtime;
	}

	public static void setPlaytime(long playtime) {
		synchronized (instance) {
			instance.playtime = playtime;
		}
	}

	public static long getUserCount() {
		return instance.userCount;
	}

	public static void setUserCount(long users) {
		synchronized (instance) {
			instance.userCount = users;
		}
	}

	public static void addUserCount(int add) {
		synchronized (instance) {
			instance.userCount += add;
		}
	}

	public static long getDownloaded() {
		return instance.downloaded;
	}

	public static void setDownloaded(long downloaded) {
		synchronized (instance) {
			instance.downloaded = downloaded;
		}
	}

	public static void addDownloaded(int add) {
		synchronized (instance) {
			instance.downloaded += add;
		}
	}

	public static long getUptime() {
		return instance.uptime.getTime();
	}

}

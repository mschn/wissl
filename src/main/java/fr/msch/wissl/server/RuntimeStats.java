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

import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

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

	public AtomicLong songCount;
	public AtomicLong albumCount;
	public AtomicLong artistCount;
	public AtomicLong playlistCount;
	public AtomicLong userCount;
	public AtomicLong playtime;
	public AtomicLong downloaded;

	private Date uptime;

	/**
	 * @return global singleton instance
	 */
	static RuntimeStats get() {
		if (instance == null) {
			instance = new RuntimeStats();
		}
		return instance;
	}

	public RuntimeStats() {
		this.uptime = Calendar.getInstance().getTime();
		songCount = new AtomicLong();
		albumCount = new AtomicLong();
		artistCount = new AtomicLong();
		playlistCount = new AtomicLong();
		userCount = new AtomicLong();
		playtime = new AtomicLong();
		downloaded = new AtomicLong();
	}

	public RuntimeStats(String json) {
		this();
		try {
			JSONObject o = new JSONObject(json);
			songCount.set(o.getInt("songs"));
			albumCount.set(o.getInt("albums"));
			artistCount.set(o.getInt("artists"));
			playlistCount.set(o.getInt("playlists"));
			userCount.set(o.getInt("users"));
			playtime.set(o.getInt("playtime"));
			downloaded.set(o.getInt("downloaded"));
		} catch (JSONException e) {
			throw new IllegalArgumentException("Invalid JSON", e);
		}
	}

	/**
	 * @return a JSON representation of this object
	 */
	public String toJSON() {
		StringBuilder sb = new StringBuilder();

		long dt = Calendar.getInstance().getTime().getTime()
				- this.uptime.getTime();

		sb.append("{\"songs\":" + this.songCount);
		sb.append(",\"albums\":" + this.albumCount);
		sb.append(",\"artists\":" + this.artistCount);
		sb.append(",\"playlists\":" + this.playlistCount);
		sb.append(",\"users\":" + this.userCount);
		sb.append(",\"playtime\":" + this.playtime);
		sb.append(",\"downloaded\":" + this.downloaded);
		sb.append(",\"uptime\":" + dt + "}");

		return sb.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof RuntimeStats) {
			RuntimeStats r = (RuntimeStats) o;

			return (this.songCount.intValue() == r.songCount.intValue()
					&& this.albumCount.intValue() == r.albumCount.intValue()
					&& this.artistCount.intValue() == r.artistCount.intValue()
					&& this.playlistCount.intValue() == r.playlistCount
							.intValue()
					&& this.userCount.intValue() == r.userCount.intValue() && this.playtime
						.intValue() == r.playtime.intValue());
		} else {
			return false;
		}
	}

	@Override
	public String toString() {
		return toJSON();
	}

	public void updateFromDB() throws SQLException {
		RuntimeStats.get().songCount.set(DB.get().getSongCount());
		RuntimeStats.get().albumCount.set(DB.get().getAlbumCount());
		RuntimeStats.get().artistCount.set(DB.get().getArtistCount());
		RuntimeStats.get().playlistCount.set(DB.get().getPlaylistCount());
		RuntimeStats.get().userCount.set(DB.get().getUserCount());
		RuntimeStats.get().playtime.set(DB.get().getTotalSongDuration());
	}

}

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

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * 
 * 
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
final class Playlist implements JSON {

	/** DB unique id */
	public int id = 0;
	/** playlist name */
	public String name = "";
	/** user id of owner */
	public int user_id = 0;

	/** song count */
	public int songs = 0;
	/** total songs play duration */
	public int playtime = 0;

	public Playlist() {
	}

	public Playlist(String json) {
		try {
			JSONObject o = new JSONObject(json);
			id = o.getInt("id");
			name = o.getString("name");
			songs = o.getInt("songs");
			user_id = o.getInt("user");
			playtime = o.getInt("playtime");
		} catch (JSONException e) {
			throw new IllegalArgumentException("Invalid JSON", e);
		}
	}

	@Override
	public String toJSON() {
		StringBuilder str = new StringBuilder();
		str.append('{');
		str.append("\"id\":" + id + ",");
		str.append("\"name\":" + JSONObject.quote(name) + ",");
		str.append("\"user\":" + user_id + ",");
		str.append("\"songs\":" + songs + ",");
		str.append("\"playtime\":" + playtime);
		str.append('}');
		return str.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Playlist) {
			Playlist p = (Playlist) o;
			return (name.equals(p.name) && songs == p.songs
					&& playtime == p.playtime && user_id == p.user_id);
		} else {
			return false;
		}
	}

	public String toString() {
		return toJSON();
	}

}

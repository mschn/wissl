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

import org.codehaus.jettison.json.JSONObject;

/**
 * 
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
public class Album {

	/** DB unique id */
	public int id = 0;
	/** name of the album */
	public String name = "";
	/** year of release, ie "2012" */
	public String date = "";
	/** album genre, ie 'Pop Rock' */
	public String genre = "";
	/** number of songs */
	public int songs = 0;
	/** DB unique artist id */
	public int artist_id = 0;
	/** playtime in seconds */
	public long playtime = 0;
	/** path to the artwork image on the local FS */
	public String artwork_path = null;
	/** artist name */
	public String artist_name = null;

	public String toJSON() {
		StringBuilder str = new StringBuilder();
		str.append('{');
		str.append("\"id\":" + id + ",");
		str.append("\"artist\":" + artist_id + ",");
		str.append("\"artist_name\":" + JSONObject.quote(artist_name) + ",");
		str.append("\"name\":" + JSONObject.quote(name) + ",");
		str.append("\"date\":" + JSONObject.quote(date) + ",");
		str.append("\"genre\":" + JSONObject.quote(genre) + ",");
		str.append("\"songs\":" + songs + ",");
		str.append("\"playtime\":" + playtime);
		if (artwork_path != null && artwork_path.trim().length() > 0) {
			str.append(",\"artwork\":true");
		}
		str.append('}');
		return str.toString();
	}
}

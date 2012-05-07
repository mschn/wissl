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
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
public class Song {
	/** DB unique id */
	public int id = 0;
	/** DB album unique id */
	public int album_id = 0;
	/** DB artist unique id */
	public int artist_id = 0;

	/** absolute file path on the local filesystem */
	public String filepath = "";
	/** MD5 hash of filePath */
	public String hash = "";
	/** song title */
	public String title = "";
	/** album name */
	public String album_name = "";
	/**artist name */
	public String artist_name = "";
	/** song position in album */
	public int position = 0;
	/** song duration in seconds */
	public int duration = 0;
	/** mimetype, ie 'audio/mp3' */
	public String format = null;
	/** multiple CDs that mess with song position in album */
	public int disc_no = 0;

	/** album containing this song */
	public Album album = null;
	/** artist who authored this song */
	public Artist artist = null;

	public String toJSON() {
		StringBuilder str = new StringBuilder();
		str.append('{');
		str.append("\"id\":" + id + ",");
		str.append("\"album_id\":" + album_id + ",");
		str.append("\"artist_id\":" + artist_id + ",");
		str.append("\"title\": " + JSONObject.quote(title) + ",");
		str.append("\"album_name\": " + JSONObject.quote(album_name) + ",");
		str.append("\"artist_name\": " + JSONObject.quote(artist_name) + ",");
		str.append("\"position\":" + position + ",");
		str.append("\"disc_no\":" + disc_no + ",");
		str.append("\"duration\":" + duration + ",");
		str.append("\"format\":" + JSONObject.quote(format));
		str.append('}');
		return str.toString();
	}

}

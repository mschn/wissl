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
 * @author mathieu.schnoor@gmail.com
 *
 */
public class Artist {

	/** DB unique id */
	public int id = 0;
	/** artist or band name */
	public String name = "";
	/** number of albums */
	public int albums = 0;
	/** number of songs */
	public int songs = 0;
	/** playtime in seconds */
	public long playtime = 0;

	public String toJSON() {
		StringBuilder str = new StringBuilder();
		str.append('{');
		str.append("\"id\":" + id + ",");
		str.append("\"name\":" + JSONObject.quote(name) + ",");
		str.append("\"albums\":" + albums + ",");
		str.append("\"songs\":" + songs + ",");
		str.append("\"playtime\":" + playtime);
		str.append('}');
		return str.toString();
	}

}

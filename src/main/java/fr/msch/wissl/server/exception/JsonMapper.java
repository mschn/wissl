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
package fr.msch.wissl.server.exception;

import javax.ws.rs.core.Response;

import org.codehaus.jettison.json.JSONObject;

import fr.msch.wissl.server.Logger;

/**
 *
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
public class JsonMapper {

	public static Response getResponse(Throwable t, int status) {
		Logger.warn("HTTP " + status, t);
		return Response.status(status). //
				type("application/json"). //
				entity(JsonMapper.getStackTrace(t, status)). //
				build();

	}

	private static String getStackTrace(Throwable t, int status) {
		StringBuilder str = new StringBuilder();

		str.append('{');
		if (t != null) {
			str.append("\"status\":" + status + ",");
			str.append("\"class\":" + //
					JSONObject.quote(t.getClass().getSimpleName()) + //
					",");
			str.append("\"message\":"
					+ JSONObject.quote(t.getLocalizedMessage()));
		}
		str.append('}');

		return str.toString();
	}
}

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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import fr.msch.wissl.common.Config;

/**
 * Utility to help manipulate dates
 * <p>
 * 
 * @author mathieu.schnoor@gmail.com
 * 
 */
final class DateHelper {

	private final static String defaultFormat = "yy-MM-dd hh:mm:ss.SSS";
	private final static String compactFormat = "yyMMddhhmmss";

	/**
	 * Timestamps formatter
	 */
	private static DateFormat timeStamps = null;
	private static DateFormat compactStamps = null;

	/**
	 * Singleton instance
	 */
	private static DateHelper instance = null;

	/**
	 * Constructor
	 * <p>
	 * Access to non-static class members is restricted
	 */
	private DateHelper() {
		String format = Config.getLogDateFormat();
		compactStamps = new SimpleDateFormat(compactFormat);
		try {
			timeStamps = new SimpleDateFormat(format);
		} catch (Exception e) {
			timeStamps = new SimpleDateFormat(defaultFormat);
		}
	}

	/**
	 * Internal init
	 */
	private static void init() {
		if (instance == null) {
			instance = new DateHelper();
		}
	}

	/**
	 * @return a String representation of the current date, suited for log
	 *         timestamps
	 */
	public static String getTimeStamps() {
		init();

		Date now = new Date();
		return timeStamps.format(now);
	}

	/**
	 * @return current time formatted as a compact String
	 */
	public static String getCompactFormat() {
		init();
		return compactStamps.format(new Date());
	}

	/**
	 * @return the number of minutes since epoch
	 */
	public static long getMinutesSinceEpoch() {
		return (System.currentTimeMillis() / 1000);
	}
}

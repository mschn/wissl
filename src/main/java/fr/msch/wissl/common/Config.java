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
package fr.msch.wissl.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * 
 * 
 * @author mathieu.schnoor@gmail.com
 * 
 */
public final class Config {

	/** singleton instance */
	private static Config instance = null;

	/** will expand as tmpdir for file paths */
	private static String tmpMacro = "$TMP";
	private static String tmpdir = System.getProperty("java.io.tmpdir");

	private String version = null;
	private String buildInfo = null;
	private String serverInfo = null;
	private String osInfo = null;
	private String javaInfo = null;

	private int httpPort = 0;

	private String logFilePath = null;
	private boolean logFileEnabled = false;
	private boolean logFileOverwrite = false;
	private boolean logStdoutEnabled = false;
	private boolean logDebugEnabled = false;
	private boolean logStdoutTrace = false;
	private int logTraceLength = 0;
	private String logDateFormat = null;
	private int logMaxlines = 0;

	private String dbPath = null;
	private boolean dbClean = false;
	private String dbUser = null;
	private String dbPassword = null;

	private List<String> musicPaths = null;
	private int musicRefreshRate = 0;
	private List<String> musicFormats = null;

	private String artworkPath = null;
	private String artworkMatcher = null;

	private int sessionExpirationDelay = 0;

	/**
	 * Create and initialize
	 */
	public static void create(String servletPath) throws IOException {
		instance = new Config(servletPath);
	}

	private Config(String servletPath) throws IOException {
		String configPath = System.getProperty("wsl.config");
		Properties props = new Properties();

		// read build info from version file
		try {
			// if we are running from jar it will be here
			InputStream is = Config.class
					.getResourceAsStream("/WEB-INF/classes/version");
			// running from extracted war, /WEB-INF/classes/ will be the top folder
			if (is == null) {
				is = Config.class.getResourceAsStream("/version");
			}
			// actually we don't really care about this :)
			if (is == null) {
				System.setProperty("wsl.version", "0");
				System.setProperty("wsl.buildinfo", "0");
			} else {
				Properties p = new Properties();
				p.load(is);
				System.setProperty("wsl.version", p.getProperty("version"));
				System.setProperty("wsl.buildinfo", p.getProperty("buildinfo"));
			}
		} catch (Exception e) {
			throw new IOException("failed to read version file", e);
		}

		// user specified config using -Dwsl.config=path
		if (configPath != null && configPath.trim().length() > 0) {
			props.load(new FileInputStream(new File(configPath)));
		}
		// default config file should be provided within application
		else {
			// running from jar: file is at the root of the jar
			InputStream is = Config.class.getResourceAsStream("/config.ini");
			if (is == null) {
				// running from extracted war: file is in servlet root path
				is = new FileInputStream(new File(servletPath + "/config.ini"));
			}
			props.load(is);
		}

		this.version = getString("wsl.version", props);
		this.buildInfo = getString("wsl.buildinfo", props);

		this.httpPort = getInt("wsl.http.port", props);

		this.logFilePath = getString("wsl.log.file.path", props).replace(
				tmpMacro, tmpdir);
		this.logFileEnabled = getBoolean("wsl.log.file.enabled", props);
		this.logFileOverwrite = getBoolean("wsl.log.file.overwrite", props);
		this.logStdoutEnabled = getBoolean("wsl.log.stdout.enabled", props);
		this.logDebugEnabled = getBoolean("wsl.log.debug.enabled", props);
		this.logStdoutTrace = getBoolean("wsl.log.stdout.trace", props);
		this.logTraceLength = getInt("wsl.log.trace.length", props);
		this.logDateFormat = getString("wsl.log.date.format", props);
		this.logMaxlines = getInt("wsl.log.maxlines", props);

		this.musicPaths = getList("wsl.music.path", props, true);
		this.musicRefreshRate = getInt("wsl.music.refresh.rate", props);
		this.musicFormats = getList("wsl.music.formats", props);

		this.dbPath = getString("wsl.db.path", props).replace(tmpMacro, tmpdir);
		this.dbClean = getBoolean("wsl.db.clean", props);
		this.dbUser = getString("wsl.db.user", props);
		this.dbPassword = getString("wsl.db.password", props);

		this.artworkPath = getString("wsl.artwork.path", props).replace(
				tmpMacro, tmpdir);
		this.artworkMatcher = getString("wsl.artwork.regex", props);

		this.sessionExpirationDelay = getInt("wsl.session.expiration.delay",
				props);

		if (dbClean) {
			deleteRecursive(new File(this.artworkPath));
		}

		new File(this.logFilePath).getParentFile().mkdirs();
		new File(this.dbPath).getParentFile().mkdirs();
		new File(this.artworkPath).mkdirs();
	}

	private String getString(String property, Properties props) {
		String v = props.getProperty(property);
		String value = System.getProperty(property, v);
		if (value != null) {
			return value;
		} else {
			throw new IllegalStateException(
					"Missing value for configuration property " + property);
		}
	}

	private int getInt(String property, Properties props) {
		String v = props.getProperty(property);
		String value = System.getProperty(property, v);
		if (value != null) {
			return Integer.parseInt(value);
		} else {
			throw new IllegalStateException(
					"Missing value for configuration property " + property);
		}
	}

	private boolean getBoolean(String property, Properties props) {
		String v = props.getProperty(property);
		String value = System.getProperty(property, v);
		if (value != null) {
			return Boolean.parseBoolean(value);
		} else {
			throw new IllegalStateException(
					"Missing value for configuration property " + property);
		}
	}

	private List<String> getList(String property, Properties props) {
		return getList(property, props, false);
	}

	private List<String> getList(String property, Properties props,
			boolean canBeEmpty) {
		String v = props.getProperty(property);
		List<String> list = Collections
				.synchronizedList(new ArrayList<String>());
		String value = System.getProperty(property, v);
		if (value == null) {
			throw new IllegalStateException(
					"Missing value for configuration property " + property);
		}

		StringTokenizer strt = new StringTokenizer(value, ";");
		while (strt.hasMoreTokens()) {
			String str = strt.nextToken();
			list.add(str);
		}
		if (!canBeEmpty && list.isEmpty()) {
			throw new IllegalStateException("List " + property
					+ " must contain at least one element");
		}
		return list;
	}

	public static String getBuildInfo() {
		return instance.buildInfo;
	}

	public static void setBuildInfo(String buildInfo) {
		instance.buildInfo = buildInfo;
	}

	public static String getServerInfo() {
		return instance.serverInfo;
	}

	public static void setServerInfo(String serverInfo) {
		instance.serverInfo = serverInfo;
	}

	public static String getOsInfo() {
		return instance.osInfo;
	}

	public static void setOsInfo(String osInfo) {
		instance.osInfo = osInfo;
	}

	public static String getJavaInfo() {
		return instance.javaInfo;
	}

	public static void setJavaInfo(String javaInfo) {
		instance.javaInfo = javaInfo;
	}

	public static String getVersion() {
		return instance.version;
	}

	public static void setVersion(String version) {
		instance.version = version;
	}

	public static int getHttpPort() {
		return instance.httpPort;
	}

	public static void setHttpPort(int port) {
		instance.httpPort = port;
	}

	public static String getLogFilePath() {
		return instance.logFilePath;
	}

	public static boolean isLogFileEnabled() {
		return instance.logFileEnabled;
	}

	public static boolean isLogFileOverwrite() {
		return instance.logFileOverwrite;
	}

	public static boolean isLogStdoutEnabled() {
		return instance.logStdoutEnabled;
	}

	public static boolean isLogStdoutDebug() {
		return instance.logDebugEnabled;
	}

	public static boolean isLogStdoutTrace() {
		return instance.logStdoutTrace;
	}

	public static int getLogTraceLength() {
		return instance.logTraceLength;
	}

	public static String getLogDateFormat() {
		return instance.logDateFormat;
	}

	public static int getLogMaxlines() {
		return instance.logMaxlines;
	}

	public static String getDbPath() {
		return instance.dbPath;
	}

	public static boolean isDbClean() {
		return instance.dbClean;
	}

	public static void setIsDbClean(boolean clean) {
		instance.dbClean = clean;
	}

	public static String getDbUser() {
		return instance.dbUser;
	}

	public static String getDbPassword() {
		return instance.dbPassword;
	}

	public static List<String> getMusicPath() {
		return instance.musicPaths;
	}

	public static void setMusicPath(List<String> musicPaths) {
		instance.musicPaths.clear();
		instance.musicPaths.addAll(musicPaths);
	}

	public static int getMusicRefreshRate() {
		return instance.musicRefreshRate;
	}

	public static List<String> getMusicFormats() {
		return instance.musicFormats;
	}

	public static int getSessionExpirationDelay() {
		return instance.sessionExpirationDelay;
	}

	public static String getArtworkPath() {
		return instance.artworkPath;
	}

	public static String getArtworkRegex() {
		return instance.artworkMatcher;
	}

	private static void deleteRecursive(File f) {
		if (f.isDirectory()) {
			for (File c : f.listFiles())
				deleteRecursive(c);
		}
		f.delete();
	}
}

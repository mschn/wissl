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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * 
 * 
 * @author mathieu.schnoor@gmail.com
 * 
 */
public class Config {

	public static final String configPathProperty = "wsl.config";

	/** singleton instance */
	private static Config instance = null;

	/** will expand as tmpdir for file paths */
	private static String tmpMacro = "$TMP";
	private static String tmpdir = System.getProperty("java.io.tmpdir");

	/** default admin pw */
	public static final byte[] defaultAdminPw = { 'a', 'd', 'm', 'i', 'n' };

	/** property file backing this config instance */
	private File configFile = null;

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

	/** File organizer module activation */
	private boolean fileOrganizerEnabled = true;
	/** File organizer module default library */
	private String fileOrganizerLibrary = null;
	/** File organizer is applied even if tags are missing */
	private boolean allowFileOrganizeMissingTag = false;

	/**
	 * Create and initialize
	 */
	public static void create() throws IOException {
		instance = new Config();
	}

	private Config() throws IOException {
		String configPath = System.getProperty("wsl.config");
		Properties props = null;
		if (configPath == null) {
			throw new IOException("Property " + configPathProperty
					+ " is not defined, cannot load configuration");
		} else {
			this.configFile = new File(configPath);
			props = new Properties();
			props.load(new FileInputStream(configFile));
		}

		this.version = getString("wsl.version", props);

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

		this.fileOrganizerEnabled = getBoolean("wsl.fileoragnizer.enabled",
				props);
		this.fileOrganizerLibrary = getString("wsl.fileoragnizer.library",
				props);
		this.allowFileOrganizeMissingTag = getBoolean(
				"wsl.fileoragnizer.missing.tags.allowed", props);

		if (dbClean) {
			deleteRecursive(new File(this.artworkPath));
		}

		new File(this.logFilePath).getParentFile().mkdirs();
		new File(this.dbPath).getParentFile().mkdirs();
		new File(this.artworkPath).mkdirs();
	}

	public static synchronized void write() throws IOException {
		PrintWriter pw = new PrintWriter(new FileOutputStream(
				instance.configFile, false));
		// not using Properties#store(),
		// it writes properties in a random order

		pw.println("wsl.version=" + getVersion());
		pw.println();
		pw.println("wsl.http.port=" + getHttpPort());
		pw.println();
		pw.println("wsl.log.file.path="
				+ getLogFilePath().replace(tmpdir, tmpMacro).replace("\\",
						"\\\\"));
		pw.println("wsl.log.file.enabled=" + isLogFileEnabled());
		pw.println("wsl.log.file.overwrite=" + isLogFileOverwrite());
		pw.println("wsl.log.stdout.enabled=" + isLogStdoutEnabled());
		pw.println("wsl.log.stdout.trace=" + isLogStdoutTrace());
		pw.println("wsl.log.debug.enabled=" + isLogStdoutDebug());
		pw.println("wsl.log.trace.length=" + getLogTraceLength());
		pw.println("wsl.log.date.format=" + getLogDateFormat());
		pw.println("wsl.log.maxlines=" + getLogMaxlines());
		pw.println();
		pw.println("wsl.music.path="
				+ listAsString(getMusicPath()).replace("\\", "\\\\"));
		pw.println("wsl.music.refresh.rate=" + getMusicRefreshRate());
		pw.println("wsl.music.formats=" + listAsString(getMusicFormats()));
		pw.println();
		pw.println("wsl.db.path="
				+ getDbPath().replace(tmpdir, tmpMacro).replace("\\", "\\\\"));
		pw.println("wsl.db.clean=" + isDbClean());
		pw.println("wsl.db.user=" + getDbUser());
		pw.println("wsl.db.password=" + getDbPassword());
		pw.println();
		pw.println("wsl.session.expiration.delay="
				+ getSessionExpirationDelay());
		pw.println();
		pw.println("wsl.artwork.regex=" + getArtworkRegex());
		pw.println("wsl.artwork.path="
				+ getArtworkPath().replace(tmpdir, tmpMacro).replace("\\",
						"\\\\"));
		pw.println();
		pw.println("wsl.fileoragnizer.enabled=" + isFileOrganizerEnabled());
		pw.println("wsl.fileoragnizer.library="
				+ getFileOrganizerLibrary().replace("\\", "\\\\"));
		pw.println("wsl.fileoragnizer.missing.tags.allowed="
				+ allowFileOrganizeMissingTag());

		pw.println();

		pw.close();
	}

	private static String listAsString(List<String> list) {
		StringBuilder sb = new StringBuilder();
		for (Iterator<String> it = list.iterator(); it.hasNext();) {
			sb.append(it.next());
			if (it.hasNext())
				sb.append(';');
		}
		return sb.toString();
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

	/**
	 * Is the FileOrganizer module enabled
	 * 
	 * @return TRUE if FileOragnizer is enabled
	 */
	public static boolean isFileOrganizerEnabled() {
		return instance.fileOrganizerEnabled;
	}

	/**
	 * Enable/disable the FileOrganizer module
	 * 
	 * @param enabled
	 *            Set TRUE to enable the FileOragnizer module, else FALSE
	 */
	public static void enableFileOrganizer(boolean enabled) {
		instance.fileOrganizerEnabled = enabled;
	}

	public static String getFileOrganizerLibrary() {
		return instance.fileOrganizerLibrary;
	}

	public static void setFileOrganizerLibrary(String library) {
		instance.fileOrganizerLibrary = library;
	}

	public static void setAllowFileOrganizeMissingTag(boolean allow) {
		instance.allowFileOrganizeMissingTag = allow;
	}

	public static boolean allowFileOrganizeMissingTag() {
		return instance.allowFileOrganizeMissingTag;
	}

	public static boolean deleteRecursive(File f) {
		boolean result = true;

		if (f.isDirectory()) {
			for (File c : f.listFiles())
				result &= deleteRecursive(c);
		}

		return result && f.delete();
	}

}

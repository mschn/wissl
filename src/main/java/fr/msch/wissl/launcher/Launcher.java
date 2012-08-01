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
package fr.msch.wissl.launcher;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

//import net.winstone.Server;
//import net.winstone.boot.BootStrap;
import fr.msch.wissl.common.Config;
import fr.msch.wissl.server.Logger;

/**
 * 
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
public class Launcher {

	public static void main(String[] args) {
		String jarPath = null;
		try {
			// that's obviously ugly. if you know better email me :)
			jarPath = Launcher.class.getProtectionDomain().getCodeSource()
					.getLocation().toURI().getPath();
		} catch (URISyntaxException e1) {
			throw new Error(e1);
		}
		String baseDir = jarPath.substring(0,
				jarPath.lastIndexOf(File.separator) + 1);

		checkLock(baseDir);

		File warFile = new File(baseDir + "dist" + File.separator + "wissl.war");
		File configFile = new File(baseDir + "config.ini");
		int port = 8080;
		boolean verbose = false;

		for (int i = 0; i < args.length; i++) {
			String str = args[i];

			if ("-w".equals(str)) {
				i++;
				if (args.length == i) {
					error("Option -w requires an argument");
				} else {
					warFile = new File(args[i]);
				}
			} else if ("-c".equals(str)) {
				i++;
				if (args.length == i) {
					error("Option -c requires an argument");
				} else {
					configFile = new File(args[i]);
				}
			} else if ("-p".equals(str)) {
				i++;
				if (args.length == i) {
					error("Option -p requires an argument");
				} else {
					try {
						port = Integer.parseInt(args[i]);
					} catch (NumberFormatException e) {
						port = -1;
					}
				}
			} else if ("-v".equals(str)) {
				verbose = true;
			} else {
				if (str.startsWith("-D")) {
					Pattern pat = Pattern.compile("^-D([^=]+)(?:=(.+))?");
					Matcher mat = pat.matcher(str);
					if (mat.matches()) {
						if (mat.groupCount() > 0) {
							String key = mat.group(1);
							String val = mat.group(2);
							if (val == null) {
								System.setProperty(key, "");
							} else {
								System.setProperty(key, val);
							}
						} else {
							error("Invalid argument: " + str);
						}
					}

				} else {
					if (!"-h".equals(str)) {
						System.out.println("Unknown option: " + str);
					}
					System.out.println("Usage: java "
							+ Launcher.class.getCanonicalName() + " [opts]");
					System.out.println("Options:");
					System.out.println("-w WAR   WAR file path [="
							+ warFile.getAbsolutePath() + "]");
					System.out.println("-c CONF  Configuration file path [="
							+ configFile.getAbsolutePath() + "]");
					System.out.println("-p PORT  HTTP listening port [=" + port
							+ "]");
					System.out.println("-v       Verbose stdout");
					System.out.println("-Dx=y    JVM system property");

					System.exit(0);
				}
			}
		}
		System.setProperty("wsl.config", configFile.getAbsolutePath());

		if (!warFile.exists()) {
			error("WAR file does not exist at: " + warFile);
		} else if (!configFile.exists()) {
			error("Configuration file does not exist at: " + configFile);
		} else if (port == -1) {
			error("Invalid port number");
		}

		PrintStream sysout = System.out;
		if (!verbose) {
			try {
				PrintStream ps = new PrintStream(File.createTempFile("wissl",
						".stdout"));
				System.setOut(ps);
				//System.setErr(ps);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		setLF();
	//	startServer(warFile, configFile, port);

		// read build info if running from jar
		try {
			InputStream is = Launcher.class
					.getResourceAsStream("/META-INF/MANIFEST.MF");
			if (is != null) {
				Properties props = new Properties();
				props.load(is);
				String info = props.getProperty("Implementation-Version");
				Config.setBuildInfo(info);
			}
		} catch (Exception e) {
			Logger.warn("Failed to read build info. Not running from jar ?", e);
		}

		URI uri = null;
		try {
			uri = new URI("http://localhost:" + Config.getHttpPort());
		} catch (URISyntaxException e) {
			e.printStackTrace();
			System.exit(1);
		}

		if (GraphicsEnvironment.isHeadless()) {
			sysout.println("Server started: " + uri.toString());
		} else {
			try {
				Desktop.getDesktop().browse(uri);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}
/*
	static Server startServer(File warFile, File configFile, int port) {
		Map<String, String> srvArgs = new HashMap<String, String>();
		srvArgs.put("httpPort", "" + port);
		srvArgs.put("ajp13Port", "-1");
		srvArgs.put("warfile", warFile.getAbsolutePath());

		Server srv = new BootStrap(srvArgs).boot();
		srv.start();
		return srv;
	}
*/
	private static void setLF() {
		if (GraphicsEnvironment.isHeadless())
			return;

		try {
			if (System.getProperty("os.name").toLowerCase().contains("linux")) {
				UIManager
						.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
			} else {
				UIManager.setLookAndFeel(UIManager
						.getSystemLookAndFeelClassName());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void checkLock(String dir) {
		boolean locked = false;
		RandomAccessFile raf = null;
		try {
			File lock = new File(dir + "lock");
			if (!lock.exists()) {
				lock.createNewFile();
				lock.deleteOnExit();
			}
			raf = new RandomAccessFile(lock, "rw");
			FileChannel chan = raf.getChannel();
			FileLock fl = chan.tryLock();
			if (fl == null) {
				locked = true;
			}
		} catch (Exception e) {
			e.printStackTrace();
			locked = true;
		} finally {
		}

		if (locked) {
			error("Another process is already running");
		}
	}

	static void error(String msg) {
		if (GraphicsEnvironment.isHeadless()) {
			System.out.println(msg);
			System.exit(1);
		} else {
			setLF();
			JOptionPane.showMessageDialog(null, msg, "wissl",
					JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}
	}

}

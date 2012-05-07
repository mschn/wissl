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

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import net.winstone.Server;
import net.winstone.boot.BootStrap;

/**
 * 
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
public class Launcher {

	private static boolean headless = GraphicsEnvironment.isHeadless();

	public static void main(String[] args) {
		setLF();

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

				System.exit(0);
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

		startServer(warFile, configFile, port);
	}

	static Server startServer(File warFile, File configFile, int port) {
		Map<String, String> srvArgs = new HashMap<String, String>();
		srvArgs.put("httpPort", "" + port);
		srvArgs.put("warfile", warFile.getAbsolutePath());
		loadConfig(configFile);

		Server srv = new BootStrap(srvArgs).boot();
		srv.start();
		return srv;
	}

	private static void setLF() {
		if (headless)
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

	private static void loadConfig(File confPath) {
		Properties props = new Properties();

		try {
			props.load(new FileInputStream(confPath));
		} catch (IOException e) {
			error("Failed to load config: " + e.getMessage());
		}

		for (Entry<Object, Object> entry : props.entrySet()) {
			System.setProperty(entry.getKey().toString(), entry.getValue()
					.toString());
		}
	}

	private static void checkLock(String dir) {
		boolean locked = false;
		try {
			File lock = new File(dir + "lock");
			if (!lock.exists()) {
				lock.createNewFile();
				lock.deleteOnExit();
			}
			FileChannel chan = new RandomAccessFile(lock, "rw").getChannel();
			FileLock fl = chan.tryLock();
			if (fl == null) {
				locked = true;
			}
		} catch (Exception e) {
			e.printStackTrace();
			locked = true;
		}

		if (locked) {
			error("Another process is already running");
		}
	}

	static void error(String msg) {
		if (headless) {
			System.out.println(msg);
			System.exit(1);
		} else {
			JOptionPane.showMessageDialog(null, msg, "wissl",
					JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}
	}

}

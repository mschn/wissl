/* This file is part of Wissl - Copyright (C) 2013 Mathieu Schnoor
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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 * 
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
final class Launcher {

	public static void main(String[] args) {
		File configFile = null;
		int port = 8080;
		boolean verbose = false;

		for (int i = 0; i < args.length; i++) {
			String str = args[i];

			if ("-c".equals(str)) {
				i++;
				if (args.length == i) {
					error("Option -c requires an argument");
				} else {
					configFile = new File(args[i]);
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
					System.out.println("-c CONF  Configuration file path [="
							+ configFile.getAbsolutePath() + "]");
					System.out.println("-v       Verbose stdout");
					System.out.println("-Dx=y    JVM system property");

					System.exit(0);
				}
			}
		}

		String pp = System.getProperty("wsl.http.port");

		if (configFile != null) {
			if (configFile.exists()) {
				System.setProperty("wsl.config", configFile.getAbsolutePath());
				Properties props = new Properties();
				try {
					props.load(new FileInputStream(configFile));
					port = Integer.parseInt(props.getProperty("wsl.http.port"));
				} catch (IOException e) {
					e.printStackTrace();
					error("Failed to read port from config");
				}
			} else {
				error("Config file does not exist:"
						+ configFile.getAbsolutePath());
			}
		}

		if (pp != null && pp.trim().length() > 0) {
			port = Integer.parseInt(pp);
		}

		PrintStream sysout = System.out;
		if (!verbose) {
			try {
				PrintStream ps = new PrintStream(File.createTempFile("wissl",
						".stdout"));
				System.setOut(ps);
				System.setErr(ps);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		setLF();

		if (isRunning(port)) {
			error("Wissl is already running on http://localhost:" + port);
		}

		final int fport = port;
		new Thread(new Runnable() {
			public void run() {
				startServer(fport);
			}
		}).start();

		URI uri = null;
		try {
			uri = new URI("http://localhost:" + port);
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

	static void startServer(int port) {
		Server server = new Server();
		SocketConnector connector = new SocketConnector();

		connector.setPort(port);
		server.setConnectors(new Connector[] { connector });

		WebAppContext context = new WebAppContext();
		context.setServer(server);
		context.setContextPath("/");

		ProtectionDomain protectionDomain = Launcher.class
				.getProtectionDomain();
		URL location = protectionDomain.getCodeSource().getLocation();
		context.setWar(location.toExternalForm());

		server.setHandler(context);
		try {
			server.start();
		} catch (Exception e) {
			e.printStackTrace();
			error("Failed to start server");
		}

	}

	static boolean isRunning(int port) {
		String endpoint = "http://localhost:" + port + "/wissl/hasusers";
		try {
			// can't use commons httpclient, don't have the jar inside the launcher
			HttpURLConnection get = (HttpURLConnection) new URL(endpoint)
					.openConnection();

			if (get.getResponseCode() == 200) {
				return true;
			}
		} catch (IOException e) {
		}
		return false;
	}

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

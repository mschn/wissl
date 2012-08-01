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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.Map.Entry;
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
public class Launcher {

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
		} else {
			InputStream is = Launcher.class.getResourceAsStream("/config.ini");
			Properties properties = new Properties();
			try {
				properties.load(is);
			} catch (IOException e) {
				System.out.println("Failed to load default config");
				e.printStackTrace();
			}
			for (Entry<Object, Object> prop : properties.entrySet()) {
				System.setProperty(prop.getKey().toString(), prop.getValue()
						.toString());
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

		// read build info from war
		try {
			InputStream is = Launcher.class
					.getResourceAsStream("/WEB-INF/classes/version");
			if (is != null) {
				Properties props = new Properties();
				props.load(is);
				System.setProperty("wsl.version", props.getProperty("version"));
				System.setProperty("wsl.buildinfo",
						props.getProperty("buildinfo"));
			}
		} catch (Exception e) {
			e.printStackTrace(sysout);
		}

		startServer(port);

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

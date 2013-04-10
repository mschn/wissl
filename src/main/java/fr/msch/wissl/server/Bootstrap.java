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
package fr.msch.wissl.server;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.ServletContextEvent;

import org.jboss.resteasy.plugins.server.servlet.ResteasyBootstrap;

import fr.msch.wissl.common.Config;

/**
 * 
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
public final class Bootstrap extends ResteasyBootstrap {

	@Override
	public void contextInitialized(ServletContextEvent event) {
		super.contextInitialized(event);

		try {
			Config.create(event.getServletContext().getRealPath("/"));
		} catch (IOException e) {
			throw new Error("Failed to load configuration", e);
		}
		Logger.create();

		Config.setOsInfo(System.getProperty("os.name") + " "
				+ System.getProperty("os.arch") + " "
				+ System.getProperty("os.version"));
		Config.setServerInfo(event.getServletContext().getServerInfo());
		Config.setJavaInfo(System.getProperty("java.vm.name") + ", "
				+ System.getProperty("java.version") + ", "
				+ System.getProperty("java.vendor"));

		Logger.debug("Server:     " + Config.getServerInfo());
		Logger.debug("wissl:      " + Config.getVersion());
		Logger.debug("OS:         " + Config.getOsInfo());

		Logger.debug("Java:       " + Config.getJavaInfo());
		Logger.debug("JAVA_HOME:  " + System.getProperty("java.home"));
		Logger.debug("PWD:        " + System.getProperty("user.dir"));

		try {
			DB.create(Config.getDbPath());
		} catch (Throwable t) {
			Logger.error("Failed to create DB", t);
			throw new Error("Failed to create DB", t);
		}

		List<String> folders = null;
		try {
			folders = DB.get().getFolders();
		} catch (SQLException e) {
			Logger.error("Failed to recover library folders", e);
			throw new Error("Failed to recover library folders", e);
		}
		for (String path : folders) {
			Config.getMusicPath().add(path);
			Logger.debug("Recovered music folder path: " + path);
		}

		Session.start();
		Library.create();
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		super.contextDestroyed(event);
		shutdown();
		Logger.info("Server Context destroyed");
	}

	public static void shutdown() {
		try {
			throw new Exception("Stack dump");
		} catch (Exception e) {
			Logger.warn("Shutting down", e);
		}

		Library.stop();
		Session.stop();

		try {
			DB db = DB.get();
			if (db != null) {
				db.close();
			}
		} catch (SQLException e) {
			Logger.error("Failed to close DB", e);
		}
	}
}

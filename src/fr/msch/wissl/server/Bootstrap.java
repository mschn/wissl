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

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletContextEvent;

import org.jboss.resteasy.plugins.server.servlet.ResteasyBootstrap;

import fr.msch.wissl.common.Config;
import fr.msch.wissl.server.exception.SecurityError;

/**
 * 
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
public class Bootstrap extends ResteasyBootstrap {

	@Override
	public void contextInitialized(ServletContextEvent event) {
		super.contextInitialized(event);

		try {
			Config.create();
		} catch (IOException e) {
			throw new Error("Failed to load configuration", e);
		}
		Logger.create();

		Logger.debug("Server:     " + event.getServletContext().getServerInfo());
		Logger.debug("wissl:      " + Config.getVersion());
		Logger.debug("OS:         " + System.getProperty("os.name") + " "
				+ System.getProperty("os.arch") + " "
				+ System.getProperty("os.version"));

		Logger.debug("Java:       " + System.getProperty("java.vm.name") + ", "
				+ System.getProperty("java.version") + ", "
				+ System.getProperty("java.vendor"));
		Logger.debug("JAVA_HOME:  " + System.getProperty("java.home"));
		Logger.debug("PWD:        " + System.getProperty("user.dir"));

		try {
			DB.create(Config.getDbPath());
		} catch (Throwable t) {
			Logger.error("Failed to create DB", t);
			throw new Error("Failed to create DB", t);
		}
		addAdminUser();
		Session.start();
		Library.create();
	}

	private void addAdminUser() {

		User admin = null;
		try {
			admin = DB.get().getUser("admin");
		} catch (SQLException e1) {
			Logger.error("Failed to get admin user", e1);
		}
		if (admin == null) {
			admin = new User();
			admin.auth = 1;
			admin.username = "admin";
			admin.password = Config.defaultAdminPw;
			try {
				admin.hashPassword();
			} catch (SecurityError e) {
				Logger.error("Could not create admin user", e);
				throw new Error("Could not create admin user", e);
			}
			try {
				DB.get().addUser(admin);
			} catch (SQLException e) {
				Logger.error("Failed to insert admin into DB", e);
				throw new Error("Failed to insert admin into DB", e);
			}
		}

		User alex = null;
		try {
			alex = DB.get().getUser("alex");
		} catch (SQLException e1) {
			Logger.error("Failed to get alex user", e1);
		}
		if (alex == null) {
			// TODO remove this
			alex = new User();
			alex.auth = 2;
			alex.username = "alex";
			alex.password = "alex".getBytes();
			try {
				alex.hashPassword();
			} catch (SecurityError e) {
			}
			try {
				DB.get().addUser(alex);
			} catch (SQLException e) {
			}
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		super.contextDestroyed(event);

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

		Logger.info("Server Context destroyed");
	}
}

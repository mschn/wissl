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
package fr.msch.wissl.test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;
import junit.framework.TestCase;
import net.winstone.Server;
import net.winstone.boot.BootStrap;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.log4j.LogManager;
import org.apache.log4j.varia.NullAppender;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;

/**
 * Helper class for rest server testing
 * 
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
public class TServer extends TestCase {

	public static final String URL = "http://localhost:8080/wissl/";

	protected Server srv = null;

	protected int userId_admin = -1;
	protected String sessionId_admin = null;
	protected String username_admin = "tadmin";
	protected String password_admin = "pw-tadmin";

	protected int userId_user = -1;
	protected String sessionId_user = null;
	protected String username_user = "tuser";
	protected String password_user = "pw-tuser";

	@Before
	public void setUp() throws Exception {
		startServer();

		// create admin-level user
		String ret = this.addUser(username_admin, password_admin, "1", null);
		JSONObject o = new JSONObject(ret);
		JSONObject user = o.getJSONObject("user");
		this.userId_admin = user.getInt("id");
		Assert.assertEquals(this.username_admin, user.getString("username"));

		// login admin
		ret = this.login(username_admin, password_admin);
		o = new JSONObject(ret);
		Assert.assertEquals(this.userId_admin, o.getInt("userId"));
		this.sessionId_admin = o.getString("sessionId");

		// create user-level user
		ret = this.addUser(username_user, password_user, "2", sessionId_admin);
		o = new JSONObject(ret);
		user = o.getJSONObject("user");
		this.userId_user = user.getInt("id");
		Assert.assertEquals(this.username_user, user.getString("username"));

		// login user
		ret = this.login(username_user, password_user);
		o = new JSONObject(ret);
		Assert.assertEquals(this.userId_user, o.getInt("userId"));
		this.sessionId_user = o.getString("sessionId");
	}

	@After
	public void tearDown() {
		this.srv.shutdown();
	}

	public TServer() {
	}

	private void startServer() {
		final File warFile = new File("dist/wissl.war");
		final File conf = new File("config.ini");

		System.setProperty("java.awt.headless", "true");
		System.setProperty("wsl.db.clean", "true");
		System.setProperty("wsl.music.path", "");
		System.setProperty("wsl.log.file.enabled", "false");
		System.setProperty("wsl.log.stdout.trace", "true");
		System.setProperty("wsl.log.debug.enabled", "false");
		System.setProperty("wsl.log.trace.length", "30");
		System.setProperty("wsl.config", conf.getAbsolutePath());

		Map<String, String> srvArgs = new HashMap<String, String>();
		srvArgs.put("httpPort", "8080");
		srvArgs.put("warfile", warFile.getAbsolutePath());

		this.srv = new BootStrap(srvArgs).boot();
		this.srv.start();

		LogManager.resetConfiguration();
		LogManager.getRootLogger().removeAllAppenders();
		LogManager.getRootLogger().addAppender(new NullAppender());

		LogManager.getLogger(BootStrap.class).removeAllAppenders();
		LogManager.getLogger(BootStrap.class).addAppender(new NullAppender());
	}

	private String addUser(String username, String password, String auth,
			String sessionId) throws IOException {
		HttpClient c = new HttpClient();
		PostMethod m = new PostMethod(URL + "user/add");
		m.addParameter("username", username);
		m.addParameter("password", password);
		m.addParameter("auth", auth);
		if (sessionId != null) {
			m.addRequestHeader("sessionId", sessionId);
		}
		c.executeMethod(m);
		String ret = m.getResponseBodyAsString();
		return ret;
	}

	private String login(String username, String password) throws IOException {
		HttpClient c = new HttpClient();
		PostMethod m = new PostMethod(URL + "login");
		m.addParameter("username", username);
		m.addParameter("password", password);
		c.executeMethod(m);
		String ret = m.getResponseBodyAsString();
		return ret;
	}
}

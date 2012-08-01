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

import junit.framework.Assert;
import junit.framework.TestCase;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.After;
import org.junit.Before;

import fr.msch.wissl.server.RuntimeStats;

/**
 * Helper class for rest server testing
 * <p>
 * Will start a clean server,
 * create 2 users (one admin, one regular user),
 * and login using those 2 user accounts.
 * Both accounts can then be used by test cases.
 * <p>
 * Also tests the following rest server endpoints:
 * <ul>
 * <li>/hasusers
 * </ul>
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
public class TServer extends TestCase {

	public static final String URL = "http://localhost:8888/wissl/";

	protected Server srv = null;

	protected int admin_userId = -1;
	protected String admin_sessionId = null;
	protected String admin_username = "tadmin";
	protected String admin_password = "pw-tadmin";

	protected int user_userId = -1;
	protected String user_sessionId = null;
	protected String user_username = "tuser";
	protected String user_password = "pw-tuser";

	@Before
	public void setUp() throws Exception {
		startServer();

		Assert.assertFalse(hasUsers());

		// create admin-level user
		String ret = this.addUser(admin_username, admin_password, "1", null);
		JSONObject o = new JSONObject(ret);
		JSONObject user = o.getJSONObject("user");
		this.admin_userId = user.getInt("id");
		Assert.assertEquals(this.admin_username, user.getString("username"));

		// login admin
		ret = this.login(admin_username, admin_password);
		o = new JSONObject(ret);
		Assert.assertEquals(this.admin_userId, o.getInt("userId"));
		this.admin_sessionId = o.getString("sessionId");

		// create user-level user
		ret = this.addUser(user_username, user_password, "2", admin_sessionId);
		o = new JSONObject(ret);
		user = o.getJSONObject("user");
		this.user_userId = user.getInt("id");
		Assert.assertEquals(this.user_username, user.getString("username"));

		// login user
		ret = this.login(user_username, user_password);
		o = new JSONObject(ret);
		Assert.assertEquals(this.user_userId, o.getInt("userId"));
		this.user_sessionId = o.getString("sessionId");

		Assert.assertTrue(hasUsers());
	}

	@After
	public void tearDown() {
		try {
			this.srv.stop();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public TServer() {
	}

	private void startServer() {
		final File conf = new File("src/main/webapp/config.ini");

		System.setProperty("wsl.buildinfo", "testing");
		System.setProperty("wsl.version", "testing");

		System.setProperty("java.awt.headless", "true");
		System.setProperty("wsl.db.clean", "true");
		System.setProperty("wsl.music.path", "");
		System.setProperty("wsl.log.file.enabled", "false");
		System.setProperty("wsl.log.stdout.trace", "true");
		//System.setProperty("wsl.log.debug.enabled", "false");
		System.setProperty("wsl.log.trace.length", "30");
		System.setProperty("wsl.log.file.path", "$TMP/wsl-test/wsl.log.txt");
		System.setProperty("wsl.db.path", "$TMP/wsl-test/H2");
		System.setProperty("wsl.config", conf.getAbsolutePath());

		this.srv = new Server(8888);
		WebAppContext context = new WebAppContext();
		context.setDescriptor("src/main/webapp/WEB-INF/web.xml");
		context.setResourceBase("src/main/webapp");
		context.setContextPath("/");
		context.setParentLoaderPriority(true);

		srv.setHandler(context);
		try {
			srv.start();
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("failed to start jetty", e);
		}
	}

	protected boolean hasUsers() throws IOException, JSONException {
		HttpClient c = new HttpClient();
		GetMethod get = new GetMethod(TServer.URL + "hasusers");
		c.executeMethod(get);
		Assert.assertEquals(200, get.getStatusCode());
		JSONObject obj = new JSONObject(get.getResponseBodyAsString());
		return obj.getBoolean("hasusers");
	}

	protected String addUser(String username, String password, String auth,
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

	protected String login(String username, String password) throws IOException {
		HttpClient c = new HttpClient();
		PostMethod m = new PostMethod(URL + "login");
		m.addParameter("username", username);
		m.addParameter("password", password);
		c.executeMethod(m);
		String ret = m.getResponseBodyAsString();
		return ret;
	}

	protected void addMusicFolder(String path, RuntimeStats rtStats)
			throws Exception {
		HttpClient c = new HttpClient();
		PostMethod post = new PostMethod(URL + "folders/add");
		post.addRequestHeader("sessionId", this.admin_sessionId);
		File folder = new File(path);
		post.addParameter("directory", folder.getAbsolutePath());
		c.executeMethod(post);
		Assert.assertEquals(204, post.getStatusCode());
		checkStats(rtStats);
	}

	protected void checkStats(RuntimeStats rtStats) throws Exception {
		HttpClient c = new HttpClient();
		boolean done = false;
		do {
			GetMethod get = new GetMethod(URL + "indexer/status");
			get.addRequestHeader("sessionId", this.admin_sessionId);
			c.executeMethod(get);
			Assert.assertEquals(200, get.getStatusCode());
			JSONObject obj = new JSONObject(get.getResponseBodyAsString());

			if (!obj.getBoolean("running")) {
				done = true;

				get = new GetMethod(URL + "stats");
				get.addRequestHeader("sessionId", this.user_sessionId);
				c.executeMethod(get);
				Assert.assertEquals(200, get.getStatusCode());
				obj = new JSONObject(get.getResponseBodyAsString());
				RuntimeStats ret = new RuntimeStats(obj.getJSONObject("stats")
						.toString());
				assertEquals(rtStats, ret);
			}
			Thread.sleep(100);
		} while (!done);
	}
}

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

import java.io.IOException;
import java.util.UUID;

import junit.framework.Assert;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

/**
 * Functional test for the following rest server endpoints:
 * <ul>
 * <li>/login
 * <li>/logout
 * </ul>
 * 
 * @author mathieu.schnoor@gmail.com
 * 
 */
public class TestLogin extends TServer {

	@Test
	public void test() throws IOException, JSONException {
		HttpClient client = new HttpClient();

		// good username, bad password: error 401
		PostMethod post = new PostMethod(TServer.URL + "login");
		post.addParameter("username", admin_username);
		post.addParameter("password", "bad_password");
		client.executeMethod(post);
		post.getResponseBodyAsString();
		Assert.assertEquals(401, post.getStatusCode());

		// empty password and username: error 401
		post = new PostMethod(TServer.URL + "login");
		post.addParameter("username", "");
		post.addParameter("password", "");
		client.executeMethod(post);
		post.getResponseBodyAsString();
		Assert.assertEquals(401, post.getStatusCode());

		// log in as admin
		post = new PostMethod(TServer.URL + "login");
		post.addParameter("username", admin_username);
		post.addParameter("password", admin_password);
		client.executeMethod(post);

		String ret = post.getResponseBodyAsString();
		JSONObject obj = new JSONObject(ret);
		int uid_admin = obj.getInt("userId");
		String sid_admin = obj.getString("sessionId");
		int auth = obj.getInt("auth");

		Assert.assertEquals(200, post.getStatusCode());
		Assert.assertEquals(uid_admin, this.admin_userId);
		Assert.assertNotNull(UUID.fromString(sid_admin));
		Assert.assertEquals(auth, 1);

		// call 'stats' with this session, should succeed
		GetMethod get = new GetMethod(TServer.URL + "stats");
		get.addRequestHeader("sessionId", sid_admin);
		client.executeMethod(get);
		Assert.assertEquals(200, get.getStatusCode());

		// the previous session that was setup by the test case 
		// for this user should have been destroyed
		get = new GetMethod(TServer.URL + "stats");
		get.addRequestHeader("sessionId", this.admin_sessionId);
		client.executeMethod(get);
		Assert.assertEquals(401, get.getStatusCode());

		// the other user set up by the test case should still be logged in
		get = new GetMethod(TServer.URL + "stats");
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		Assert.assertEquals(200, get.getStatusCode());

		// logout both users
		post = new PostMethod(TServer.URL + "logout");
		post.addRequestHeader("sessionId", sid_admin);
		client.executeMethod(post);
		Assert.assertEquals(204, post.getStatusCode());

		post = new PostMethod(TServer.URL + "logout");
		post.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(post);
		Assert.assertEquals(204, post.getStatusCode());

		// check that neither client can call 'stats'
		get = new GetMethod(TServer.URL + "stats");
		get.addRequestHeader("sessionId", sid_admin);
		client.executeMethod(get);
		Assert.assertEquals(401, get.getStatusCode());

		get = new GetMethod(TServer.URL + "stats");
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		Assert.assertEquals(401, get.getStatusCode());
	}
}

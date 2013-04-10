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

import junit.framework.Assert;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

/**
 * Functional test for the following rest server endpoints:
 * <ul>
 * <li>/users
 * <li>/user/{user_id}
 * <li>/user/add
 * <li>/user/password
 * <li>/user/remove
 * </ul>
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
public class TestUsers extends TServer {

	@Test
	public void test() throws IOException, JSONException {
		HttpClient client = new HttpClient();

		// check the users and sessions created by TServer
		GetMethod get = new GetMethod(TServer.URL + "users");
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		Assert.assertEquals(200, get.getStatusCode());
		JSONObject obj = new JSONObject(get.getResponseBodyAsString());
		JSONArray users = obj.getJSONArray("users");
		Assert.assertEquals(2, users.length());
		for (int i = 0; i < 2; i++) {
			JSONObject u = users.getJSONObject(i);
			String username = u.getString("username");
			if (username.equals(this.user_username)) {
				Assert.assertEquals(this.user_userId, u.getInt("id"));
				Assert.assertEquals(2, u.getInt("auth"));
				Assert.assertEquals(0, u.getInt("downloaded"));
			} else if (username.equals(this.admin_username)) {
				Assert.assertEquals(this.admin_userId, u.getInt("id"));
				Assert.assertEquals(1, u.getInt("auth"));
				Assert.assertEquals(0, u.getInt("downloaded"));
			} else {
				Assert.fail("Unexpected user:" + username);
			}
		}

		JSONArray sessions = obj.getJSONArray("sessions");
		Assert.assertEquals(2, sessions.length());
		for (int i = 0; i < 2; i++) {
			JSONObject s = sessions.getJSONObject(i);
			String username = s.getString("username");
			if (username.equals(this.user_username)) {
				Assert.assertEquals(this.user_userId, s.getInt("user_id"));
				Assert.assertTrue(s.getInt("last_activity") >= 0); // might be 0
				Assert.assertTrue(s.getInt("created_at") >= 0);
				Assert.assertFalse(s.has("origins")); // admin only
				Assert.assertFalse(s.has("last_played_song"));
			}
		}

		// unknown user: 404
		get = new GetMethod(TServer.URL + "user/100");
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		Assert.assertEquals(404, get.getStatusCode());

		// check admin user
		get = new GetMethod(TServer.URL + "user/" + this.admin_userId);
		get.addRequestHeader("sessionId", this.admin_sessionId);
		client.executeMethod(get);
		Assert.assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		JSONObject u = obj.getJSONObject("user");
		Assert.assertEquals(this.admin_userId, u.getInt("id"));
		Assert.assertEquals(this.admin_username, u.getString("username"));
		Assert.assertEquals(1, u.getInt("auth"));
		Assert.assertEquals(0, u.getInt("downloaded"));
		sessions = obj.getJSONArray("sessions");
		Assert.assertEquals(1, sessions.length());
		JSONObject s = sessions.getJSONObject(0);

		Assert.assertEquals(this.admin_userId, s.getInt("user_id"));
		Assert.assertTrue(s.getInt("last_activity") >= 0); // might be 0
		Assert.assertTrue(s.getInt("created_at") > 0);
		Assert.assertTrue(s.has("origins")); // admin only
		Assert.assertFalse(s.has("last_played_song"));
		Assert.assertTrue(obj.getJSONArray("playlists").length() == 0);

		// create a new user with user account: error 401
		PostMethod post = new PostMethod(TServer.URL + "user/add");
		post.addRequestHeader("sessionId", this.user_sessionId);
		post.addParameter("username", "new-user");
		post.addParameter("password", "new-pw");
		client.executeMethod(post);
		Assert.assertEquals(401, post.getStatusCode());

		// create new user with empty username: err 400
		post = new PostMethod(TServer.URL + "user/add");
		post.addRequestHeader("sessionId", this.admin_sessionId);
		post.addParameter("username", "");
		post.addParameter("password", "new-pw");
		post.addParameter("auth", "1");
		client.executeMethod(post);
		Assert.assertEquals(400, post.getStatusCode());

		// create new user with short password: err 400
		post = new PostMethod(TServer.URL + "user/add");
		post.addRequestHeader("sessionId", this.admin_sessionId);
		post.addParameter("username", "new-user");
		post.addParameter("password", "pw");
		post.addParameter("auth", "1");
		client.executeMethod(post);
		Assert.assertEquals(400, post.getStatusCode());

		// create new user with invalid auth: err 400
		post = new PostMethod(TServer.URL + "user/add");
		post.addRequestHeader("sessionId", this.admin_sessionId);
		post.addParameter("username", "new-user");
		post.addParameter("password", "pw");
		post.addParameter("auth", "3");
		client.executeMethod(post);
		Assert.assertEquals(400, post.getStatusCode());

		int new_userId = -1;
		String new_sessionId = null;
		String new_username = "new-user";
		String new_password = "new-pw";

		// create new user
		post = new PostMethod(TServer.URL + "user/add");
		post.addRequestHeader("sessionId", this.admin_sessionId);
		post.addParameter("username", new_username);
		post.addParameter("password", new_password);
		post.addParameter("auth", "2");
		client.executeMethod(post);
		Assert.assertEquals(200, post.getStatusCode());
		u = new JSONObject(post.getResponseBodyAsString())
				.getJSONObject("user");
		new_userId = u.getInt("id");
		Assert.assertEquals(new_username, u.getString("username"));
		Assert.assertEquals(2, u.getInt("auth"));

		// check new user 
		get = new GetMethod(TServer.URL + "user/" + new_userId);
		get.addRequestHeader("sessionId", this.user_sessionId);
		client.executeMethod(get);
		Assert.assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		Assert.assertTrue(obj.getJSONArray("playlists").length() == 0);
		Assert.assertEquals(0, obj.getJSONArray("sessions").length());

		// login new user
		obj = new JSONObject(this.login(new_username, new_password));
		Assert.assertEquals(new_userId, obj.getInt("userId"));
		new_sessionId = obj.getString("sessionId");

		// check if logged in with /users
		get = new GetMethod(URL + "users");
		get.addRequestHeader("sessionId", new_sessionId);
		client.executeMethod(get);
		Assert.assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		users = obj.getJSONArray("users");
		Assert.assertEquals(3, users.length());
		for (int i = 0; i < 3; i++) {
			u = users.getJSONObject(i);
			String username = u.getString("username");
			if (username.equals(new_username)) {
				Assert.assertEquals(new_userId, u.getInt("id"));
			} else {
				Assert.assertTrue(username.equals(user_username)
						|| username.equals(admin_username));
			}
		}
		sessions = obj.getJSONArray("sessions");
		Assert.assertEquals(3, sessions.length());
		for (int i = 0; i < 3; i++) {
			s = sessions.getJSONObject(i);
			String username = s.getString("username");
			if (username.equals(new_username)) {
				Assert.assertEquals(new_userId, s.getInt("user_id"));
				Assert.assertTrue(s.getInt("last_activity") >= 0); // might be 0
				Assert.assertTrue(s.getInt("created_at") > 0);
				Assert.assertFalse(s.has("origins")); // admin only
				Assert.assertFalse(s.has("last_played_song"));
			} else {
				Assert.assertTrue(username.equals(user_username)
						|| username.equals(admin_username));
			}
		}

		// try to change password without the old password: 401
		post = new PostMethod(URL + "user/password");
		post.addRequestHeader("sessionId", new_sessionId);
		post.addParameter("old_password", "password");
		post.addParameter("new_password", "password2");
		client.executeMethod(post);
		Assert.assertEquals(401, post.getStatusCode());

		// change password for new user
		post = new PostMethod(URL + "user/password");
		post.addRequestHeader("sessionId", new_sessionId);
		post.addParameter("old_password", new_password);
		new_password = "something else";
		post.addParameter("new_password", new_password);
		client.executeMethod(post);
		Assert.assertEquals(204, post.getStatusCode());

		// logout and relogin with new password
		post = new PostMethod(URL + "logout");
		post.addRequestHeader("sessionId", new_sessionId);
		client.executeMethod(post);
		Assert.assertEquals(204, post.getStatusCode());

		obj = new JSONObject(this.login(new_username, new_password));
		Assert.assertEquals(new_userId, obj.getInt("userId"));
		new_sessionId = obj.getString("sessionId");

		// try to delete an user as user: 401
		post = new PostMethod(URL + "user/remove");
		post.addRequestHeader("sessionId", user_sessionId);
		post.addParameter("user_ids[]", "" + new_userId);
		client.executeMethod(post);
		Assert.assertEquals(401, post.getStatusCode());

		// try to delete no user: 400
		post = new PostMethod(URL + "user/remove");
		post.addRequestHeader("sessionId", admin_sessionId);
		client.executeMethod(post);
		Assert.assertEquals(400, post.getStatusCode());

		// try to delete admin with admin: 400
		post = new PostMethod(URL + "user/remove");
		post.addRequestHeader("sessionId", admin_sessionId);
		post.addParameter("user_ids[]", "" + admin_userId);
		client.executeMethod(post);
		Assert.assertEquals(400, post.getStatusCode());

		// delete both regular users
		post = new PostMethod(URL + "user/remove");
		post.addRequestHeader("sessionId", admin_sessionId);
		post.addParameter("user_ids[]", "" + user_userId);
		post.addParameter("user_ids[]", "" + new_userId);
		client.executeMethod(post);
		Assert.assertEquals(204, post.getStatusCode());

		// check that old sessions do not work anymore
		get = new GetMethod(URL + "users");
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		Assert.assertEquals(401, get.getStatusCode());

		// check that there is only 1 user
		get = new GetMethod(URL + "users");
		get.addRequestHeader("sessionId", admin_sessionId);
		client.executeMethod(get);
		Assert.assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		Assert.assertEquals(obj.getJSONArray("users").length(), 1);
		Assert.assertEquals(obj.getJSONArray("sessions").length(), 1);

	}
}

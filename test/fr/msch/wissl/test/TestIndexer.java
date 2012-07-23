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

import junit.framework.Assert;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

/**
 * Functional test for the following rest server endpoints:
 * <ul>
 * <li>/indexer/status
 * <li>/indexer/rescan
 * <li>/folders
 * <li>/folders/listing
 * <li>/folders/add
 * <li>/folders/remove
 * </ul>
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
public class TestIndexer extends TServer {

	public void test() throws Exception {
		HttpClient client = new HttpClient();

		// /indexer/status as user: 401
		GetMethod get = new GetMethod(URL + "indexer/status");
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		Assert.assertEquals(401, get.getStatusCode());

		// /indexer/status as admin: 200
		get = new GetMethod(URL + "indexer/status");
		get.addRequestHeader("sessionId", admin_sessionId);
		client.executeMethod(get);
		Assert.assertEquals(200, get.getStatusCode());
		JSONObject obj = new JSONObject(get.getResponseBodyAsString());
		// won't try to check the actual content of this object,
		// since I can't predict easily and accurately if 
		// the Indexer will be in Running or Sleeping state at a given time.
		assertTrue(obj.has("running"));
		assertTrue(obj.has("percentDone"));
		assertTrue(obj.has("secondsLeft"));
		assertTrue(obj.has("songsDone"));
		assertTrue(obj.has("songsTodo"));

		// /indexer/rescan as user: 401
		PostMethod post = new PostMethod(URL + "indexer/rescan");
		post.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(post);
		Assert.assertEquals(401, post.getStatusCode());

		// /folders as user: 401
		get = new GetMethod(URL + "folders");
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		Assert.assertEquals(401, get.getStatusCode());

		// /folders/listing as user: 401
		get = new GetMethod(URL + "folders/listing");
		get.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(get);
		Assert.assertEquals(401, get.getStatusCode());

		// /folders/listing on some file that does not exist: 404
		get = new GetMethod(URL + "folders/listing?directory=/does/not/exist");
		get.addRequestHeader("sessionId", admin_sessionId);
		client.executeMethod(get);
		assertEquals(404, get.getStatusCode());

		File exp_home = new File(System.getProperty("user.home"));

		// /folders/listing with no arg: homedir
		get = new GetMethod(URL + "folders/listing");
		get.addRequestHeader("sessionId", admin_sessionId);
		client.executeMethod(get);
		assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		assertEquals(File.separator, obj.getString("separator"));
		File dir = new File(obj.getString("directory"));
		assertEquals(exp_home.getAbsolutePath(), dir.getAbsolutePath());
		assertEquals(exp_home.getParentFile().getAbsolutePath(), dir
				.getParentFile().getAbsolutePath());
		assertTrue(obj.getJSONArray("listing").length() > 0);

		// /folders/listing with arg '$ROOT'
		get = new GetMethod(URL + "folders/listing?directory=$ROOT");
		get.addRequestHeader("sessionId", admin_sessionId);
		client.executeMethod(get);
		Assert.assertEquals(200, get.getStatusCode());
		obj = new JSONObject(get.getResponseBodyAsString());
		assertEquals(File.separator, obj.getString("separator"));
		File[] dirs = File.listRoots();
		assertEquals("", obj.getString("directory"));
		assertEquals("$ROOT", obj.getString("parent"));
		JSONArray arr = obj.getJSONArray("listing");
		assertEquals(dirs.length, arr.length());
		for (int i = 0; i < dirs.length; i++) {
			// assumes the order is the same... is this always true?
			File dd = new File(arr.getString(i));
			assertEquals(dirs[i].getAbsolutePath(), dd.getAbsolutePath());
		}

		// lists test resources folder
		File f = new File("test/data2");
		get = new GetMethod(URL + "folders/listing?directory="
				+ f.getAbsolutePath());
		get.addRequestHeader("sessionId", admin_sessionId);
		client.executeMethod(get);
		obj = new JSONObject(get.getResponseBodyAsString());
		assertEquals(File.separator, obj.getString("separator"));
		dir = new File(obj.getString("directory"));
		assertEquals(f.getAbsolutePath(), dir.getAbsolutePath());
		assertEquals(f.getParentFile().getAbsolutePath(), dir.getParentFile()
				.getAbsolutePath());
		dirs = dir.listFiles();
		arr = obj.getJSONArray("listing");
		assertEquals(2, arr.length());
		assertEquals(new File("test/data2/sub1").getAbsolutePath(), arr.get(0));
		assertEquals(new File("test/data2/sub2").getAbsolutePath(), arr.get(1));

		// /folders/add as user: 401
		post = new PostMethod(URL + "folders/add");
		post.addParameter("directory", "/");
		post.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(post);
		Assert.assertEquals(401, post.getStatusCode());

		// /folders/remove as user: 401
		post = new PostMethod(URL + "folders/remove");
		post.addParameter("directory[]", "/");
		post.addRequestHeader("sessionId", user_sessionId);
		client.executeMethod(post);
		Assert.assertEquals(401, post.getStatusCode());

	}
}

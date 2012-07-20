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

import junit.framework.Assert;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;

/**
 * Functional test for the following rest server endpoints:
 * <ul>
 * <li>/indexer/status
 * <li>/folders
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

		// /folders/add as user: 401
		PostMethod post = new PostMethod(URL + "folders/add");
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

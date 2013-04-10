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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.codehaus.jettison.json.JSONObject;

import fr.msch.wissl.common.Config;
import fr.msch.wissl.server.exception.SecurityError;

/**
 * 
 * 
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
final class Session implements JSON {

	/** active sessions */
	private static Map<UUID, Session> sessions = null;
	/** removes expired sessions */
	private static Thread cleaner;

	private static class Access {
		long time;
		String userAgent;
		String ip;

		Access(long time, String userAgent, String ip) {
			this.time = time;
			this.userAgent = userAgent;
			this.ip = ip;
		}
	}

	/** unique session id*/
	private UUID id;
	/** timestamp, last time used */
	private long lastActivity;
	/** timestamp, creation time */
	private long createdAt;
	/** origin (ip) address, last access time */
	private Map<String, Access> origins;
	/** user id */
	private int userId;
	/** user name */
	private String username;
	/** last played song */
	private Song last_played_song = null;

	private Session(String origin, int uid, String username, String userAgent) {
		long t = System.currentTimeMillis();

		this.origins = new HashMap<String, Access>();
		this.id = UUID.randomUUID();
		this.createdAt = t;
		this.lastActivity = this.createdAt;
		this.userId = uid;
		this.username = username;

		this.origins.put(origin, new Access(t, userAgent, origin));
	}

	@Override
	public String toJSON() {
		return this.toJSON(false);
	}

	public String toJSON(boolean privileged) {
		long now = System.currentTimeMillis();
		StringBuilder ret = new StringBuilder();
		ret.append('{');
		ret.append("\"last_activity\":" + (now - lastActivity) + ",");
		ret.append("\"created_at\":" + (now - createdAt) + ",");
		ret.append("\"username\":" + JSONObject.quote(username) + ",");
		ret.append("\"user_id\":" + userId);
		if (privileged) {
			ret.append(",\"origins\": [");
			Iterator<Entry<String, Access>> it = origins.entrySet().iterator();
			while (it.hasNext()) {
				Access access = it.next().getValue();
				ret.append("{\"ip\": " + JSONObject.quote(access.ip));
				ret.append(",\"user_agent\": "
						+ JSONObject.quote(access.userAgent));
				ret.append(",\"time\":" + (now - access.time) + "}");
				if (it.hasNext())
					ret.append(",");
			}
			ret.append("]");
		}
		if (last_played_song != null) {
			ret.append(",\"last_played_song\":" + last_played_song.toJSON());
		}
		ret.append('}');
		return ret.toString();
	}

	/**
	 * Initialize session handler
	 * starts the thread that will check for expired sessions
	 */
	public static void start() {
		sessions = Collections.synchronizedMap(new HashMap<UUID, Session>());

		cleaner = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					Iterator<Entry<UUID, Session>> it = sessions.entrySet()
							.iterator();
					while (it.hasNext()) {
						Session sess = it.next().getValue();
						long t = System.currentTimeMillis();
						if (t - sess.lastActivity > 1000 * Config
								.getSessionExpirationDelay()) {
							it.remove();
							Logger.info("Session expired: "
									+ sess.getSessionId().toString() + " "
									+ sess.getUserName());
						}
					}
					try {
						Thread.sleep(30 * 1000);
					} catch (InterruptedException e) {
						Logger.warn("SessionCleaner interrupted", e);
					}
				}
			}
		});
		cleaner.setName("SessionCleaner");
		cleaner.start();
	}

	public static void stop() {
		if (cleaner != null)
			cleaner.interrupt();
	}

	public int getUserId() {
		return this.userId;
	}

	public String getUserName() {
		return this.username;
	}

	public UUID getSessionId() {
		return this.id;
	}

	public void setLastPlayedSong(Song last_played) {
		this.last_played_song = last_played;
	}

	/**
	 * Add a new session
	 * @param origin client request address to limit session spoofing
	 * @param uid user id
	 * @param username user name
	 * @param userAgent user agent
	 * @return the created session
	 */
	public static Session create(String origin, int uid, String username,
			String userAgent) {
		Session s = new Session(origin, uid, username, userAgent);
		sessions.put(s.id, s);
		return s;
	}

	/**
	 * Destroy given session
	 * @param sess session to destroy
	 * @throws SecurityError
	 */
	public static void remove(Session sess) throws SecurityError {
		sessions.remove(sess.id);
	}

	/**
	 * @param uid unique user id
	 * @return open list of Sessions for this user, or empty list if not logged
	 */
	public static List<Session> getSessions(int uid) {
		List<Session> ret = new ArrayList<Session>();
		for (Session s : sessions.values()) {
			if (s.userId == uid) {
				ret.add(s);
			}
		}
		return ret;
	}

	public static Map<UUID, Session> getSessions() {
		return sessions;
	}

	/**
	 * Check if a session with the given ID exists
	 * @param id session id to check
	 * @param origin client IP address
	 * @param userAgent client user agent
	 * @throws SecurityError session id is rejected
	 */
	public static Session check(String id, String origin, String userAgent)
			throws SecurityError {
		return check(id, origin, userAgent, false);
	}

	/**
	 * Check if a session with the given ID exists
	 * @param id session id to check
	 * @param origin client IP address
	 * @param requiredAdmin only authorize administrator users
	 * @param userAgent client user agent
	 * @throws SecurityError session id is rejected
	 */
	public static Session check(String id, String origin, String userAgent,
			boolean requireAdmin) throws SecurityError {
		if (id == null) {
			throw new SecurityError("No session id provided");
		}

		UUID uid = null;
		try {
			uid = UUID.fromString(id);
		} catch (Throwable e) {
			throw new SecurityError("Invalid session id: " + id, e);
		}
		Session s = sessions.get(uid);
		if (s == null) {
			Logger.debug("Session check failed for sessionId " + id + " from "
					+ origin);
			throw new SecurityError("Not logged in");
		}
		s.lastActivity = System.currentTimeMillis();
		Access a = new Access(s.lastActivity, userAgent, origin);
		s.origins.put(origin, a);

		if (requireAdmin) {
			User u = null;
			try {
				u = DB.get().getUser(s.getUserId());
			} catch (SQLException e) {
				Logger.error("Failed to retrieve user from DB", e);
				throw new SecurityError("Failed to check user privileges");
			}
			if (u.auth != 1) {
				throw new SecurityError("Administrator privileges required");
			}
		}

		return s;
	}
}

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

import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
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
	/** sessions replaced by a re-login */
	private static Map<UUID, Session> replaced = null;
	/** removes expired sessions */
	private static Thread cleaner;

	private static DateFormat format;

	/** unique session id*/
	private UUID id;
	/** timestamp, last time used */
	private long lastActivity;
	/** timestamp, creation time */
	private long createdAt;
	/** origin (ip) address */
	private String origin;
	/** user id */
	private int userId;
	/** user name */
	private String username;
	/** last played song */
	private Song last_played_song = null;

	/** if disconnected by another session */
	private Session replacedBy = null;

	private Session(String origin, int uid, String username) {
		this.id = UUID.randomUUID();
		this.createdAt = System.currentTimeMillis();
		this.lastActivity = this.createdAt;
		this.origin = origin;
		this.userId = uid;
		this.username = username;
	}

	@Override
	public String toJSON () {
		return this.toJSON(false);
	}
	
	public String toJSON(boolean privileged) {
		StringBuilder ret = new StringBuilder();
		ret.append('{');
		ret.append("\"last_activity\":"
				+ (System.currentTimeMillis() - lastActivity) + ",");
		ret.append("\"created_at\":" + (System.currentTimeMillis() - createdAt)
				+ ",");
		ret.append("\"username\":" + JSONObject.quote(username) + ",");
		ret.append("\"user_id\":" + userId);
		if (privileged) {
			ret.append(",\"origin\":" + JSONObject.quote(origin));
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
		replaced = Collections.synchronizedMap(new HashMap<UUID, Session>());
		format = new SimpleDateFormat("hh:mm");

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
							Logger.info("Session expired");
						}
					}

					it = replaced.entrySet().iterator();
					while (it.hasNext()) {
						Session sess = it.next().getValue();
						long t = System.currentTimeMillis();
						if (t - sess.lastActivity > 1000 * Config
								.getSessionExpirationDelay()) {
							it.remove();
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

	public String getOrigin() {
		return this.origin;
	}

	public void setLastPlayedSong(Song last_played) {
		this.last_played_song = last_played;
	}

	/**
	 * Add a new session
	 * @param origin client request address to limit session spoofing
	 * @param uid user id
	 * @param username user name
	 * @return the created session
	 */
	public static Session create(String origin, int uid, String username) {
		Session s = new Session(origin, uid, username);
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
	 * User was still logged, but used login again.
	 * Previous Session is removed and stored to notify
	 * the first client of what happened
	 * @param id
	 */
	public static void replace(Session oldSession, Session newSession) {
		Session s = sessions.remove(oldSession.id);
		s.replacedBy = newSession;
		replaced.put(oldSession.id, s);
	}

	/**
	 * @param uid unique user id
	 * @return open Session for this user, or null if not logged
	 */
	public static Session getSession(int uid) {
		for (Session s : sessions.values()) {
			if (s.userId == uid) {
				return s;
			}
		}
		return null;
	}

	public static Map<UUID, Session> getSessions() {
		return sessions;
	}

	/**
	 * @param id session id to check
	 * @throws SecurityError session id is rejected
	 */
	public static Session check(String id, String origin) throws SecurityError {
		return check(id, origin, false);
	}

	/**
	 * @param id session id to check
	 * @param requiredAdmin only authorize administrator users
	 * @throws SecurityError session id is rejected
	 */
	public static Session check(String id, String origin, boolean requireAdmin)
			throws SecurityError {
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

			Session r = replaced.get(uid);
			if (r != null) {
				Date d = new Date(r.replacedBy.createdAt);
				throw new SecurityError("You have been disconnected by "
						+ r.replacedBy.origin + " at " + format.format(d));
			}

			throw new SecurityError("Not logged in");
		}
		if (!s.origin.equals(origin)) {
			sessions.remove(uid);
			throw new SecurityError("Origin changed, session closed");
		}
		s.lastActivity = System.currentTimeMillis();

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

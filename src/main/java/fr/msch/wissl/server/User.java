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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;

import org.codehaus.jettison.json.JSONObject;

import fr.msch.wissl.server.exception.SecurityError;

/**
 * 
 * 
 * 
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
final class User implements JSON {

	/** unique id in DB */
	public int id = 0;
	/** user name */
	public String username = null;
	/** SHA-1 password */
	public byte[] sha1 = null;
	/** clear text password */
	public byte[] password = null;
	/** salt for hashing */
	public byte[] salt = null;
	/** 1: admin, 2: user */
	public int auth = 2;

	/** bytes downloaded (music stream only) */
	public long downloaded = 0;

	@Override
	public String toJSON() {
		StringBuilder str = new StringBuilder();
		str.append('{');
		str.append("\"id\":" + id + ",");
		str.append("\"username\":" + JSONObject.quote(username) + ",");
		str.append("\"auth\":" + auth);
		str.append(",\"downloaded\":" + downloaded);
		str.append('}');
		return str.toString();
	}

	/**
	 * Generate salt in {@link User#salt},
	 * use it along with {@link User#password} 
	 * to create {@link User#sha1}.
	 * Nulls password once it is no longer needed
	 * @throws NoSuchAlgorithmException
	 */
	public void hashPassword() throws SecurityError {
		if (password == null || password.length < 4) {
			throw new SecurityError(
					"Password must be at least 4 characters long");
		}
		Random rand = new SecureRandom();
		salt = new byte[20];
		rand.nextBytes(salt);

		byte[] b = concatenate(password, salt);
		password = null;

		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			Logger.error("Cannot use SHA-1", e);
			throw new Error(e);
		}
		sha1 = md.digest(b);
	}

	/**
	 * Check that this user's {@link User#sha1} is equal to the
	 * SHA-1 digest of {@link User#password} and {@link User#salt}
	 * concatenated
	 * @return true if both digest match
	 * @throws NoSuchAlgorithmException
	 */
	public boolean checkPassword() {
		byte[] b = concatenate(password, salt);
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			Logger.error("Cannot use SHA-1", e);
			throw new Error(e);
		}

		byte[] digest = md.digest(b);
		return Arrays.equals(sha1, digest);
	}

	private static byte[] concatenate(byte[] a, byte[] b) {
		byte[] c = new byte[a.length + b.length];
		for (int i = 0; i < a.length; i++) {
			c[i] = a[i];
		}
		for (int i = 0; i < b.length; i++) {
			c[i + a.length] = b[i];
		}
		return c;
	}
}

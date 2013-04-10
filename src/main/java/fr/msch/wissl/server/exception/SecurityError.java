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
package fr.msch.wissl.server.exception;

/**
 * Indicates a security problem from which the user should not be able to recover
 * Client should get back to the login page and display a message
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
public final class SecurityError extends Exception {

	private static final long serialVersionUID = 1L;

	public SecurityError(String message) {
		super(message);
	}

	public SecurityError(String message, Throwable cause) {
		super(message, cause);
	}

}

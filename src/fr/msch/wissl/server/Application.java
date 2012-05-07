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

import java.util.HashSet;
import java.util.Set;

import fr.msch.wissl.server.exception.BadRequestMapper;
import fr.msch.wissl.server.exception.MethodNotAllowedMapper;
import fr.msch.wissl.server.exception.NotAcceptableMapper;
import fr.msch.wissl.server.exception.NotFoundMapper;
import fr.msch.wissl.server.exception.SecurityErrorMapper;
import fr.msch.wissl.server.exception.ThrowableMapper;

/**
 * 
 * 
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
public class Application extends javax.ws.rs.core.Application {

	@Override
	public Set<Class<?>> getClasses() {
		HashSet<Class<?>> set = new HashSet<Class<?>>();
		set.add(NotFoundMapper.class);
		set.add(BadRequestMapper.class);
		set.add(ThrowableMapper.class);
		set.add(MethodNotAllowedMapper.class);
		set.add(NotAcceptableMapper.class);
		set.add(SecurityErrorMapper.class);
		return set;
	}

}

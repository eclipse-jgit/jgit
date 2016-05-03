/*
 * Copyright (C) 2016, Matthias Sohn <matthias.sohn@sap.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.attributes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jgit.lib.Repository;

/**
 * Registry for built-in filters
 *
 * @since 4.6
 */
public class FilterCommandRegistry {
	private static ConcurrentHashMap<String, FilterCommandFactory> filterCommandRegistry = new ConcurrentHashMap<>();

	/**
	 * Registers a {@link FilterCommandFactory} responsible for creating
	 * {@link FilterCommand}s for a certain command name. If the factory f1 is
	 * registered for the name "jgit://builtin/x" then a call to
	 * <code>getCommand("jgit://builtin/x", ...)</code> will call
	 * <code>f1(...)</code> to create a new instance of {@link FilterCommand}
	 *
	 * @param filterCommandName
	 *            the command name for which this factory is registered
	 * @param factory
	 *            the factory responsible for creating {@link FilterCommand}s
	 *            for the specified name
	 * @return the previous factory associated with <tt>commandName</tt>, or
	 *         <tt>null</tt> if there was no mapping for <tt>commandName</tt>
	 */
	public static FilterCommandFactory register(String filterCommandName,
			FilterCommandFactory factory) {
		return filterCommandRegistry.put(filterCommandName, factory);
	}

	/**
	 * Unregisters the {@link FilterCommandFactory} registered for the given
	 * command name
	 *
	 * @param filterCommandName
	 *            the FilterCommandFactory's filter command name
	 * @return the previous factory associated with <tt>filterCommandName</tt>,
	 *         or <tt>null</tt> if there was no mapping for <tt>commandName</tt>
	 */
	public static FilterCommandFactory unregister(String filterCommandName) {
		return filterCommandRegistry.remove(filterCommandName);
	}

	/**
	 * Checks whether any {@link FilterCommandFactory} is registered for a given
	 * command name
	 *
	 * @param filterCommandName
	 *            the name for which the registry should be checked
	 * @return <code>true</code> if any factory was registered for the name
	 */
	public static boolean isRegistered(String filterCommandName) {
		return filterCommandRegistry.containsKey(filterCommandName);
	}

	/**
	 * @return Set of commandNames for which a {@link FilterCommandFactory} is
	 *         registered
	 */
	public static Set<String> getRegisteredFilterCommands() {
		return filterCommandRegistry.keySet();
	}

	/**
	 * Creates a new {@link FilterCommand} for the given name. A factory must be
	 * registered for the name in advance.
	 *
	 * @param filterCommandName
	 *            The name for which a new {@link FilterCommand} should be
	 *            created
	 * @param db
	 *            the repository this command should work on
	 * @param in
	 *            the {@link InputStream} this {@link FilterCommand} should read
	 *            from
	 * @param out
	 *            the {@link OutputStream} this {@link FilterCommand} should
	 *            write to
	 * @return the command if a command could be created or <code>null</code> if
	 *         there was no factory registered for that name
	 * @throws IOException
	 */
	public static FilterCommand createFilterCommand(String filterCommandName,
			Repository db, InputStream in, OutputStream out)
			throws IOException {
		FilterCommandFactory cf = filterCommandRegistry.get(filterCommandName);
		return (cf == null) ? null : cf.create(db, in, out);
	}

}

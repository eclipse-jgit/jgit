/*
 * Copyright (C) 2016, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
	 * Register a {@link org.eclipse.jgit.attributes.FilterCommandFactory}
	 * responsible for creating
	 * {@link org.eclipse.jgit.attributes.FilterCommand}s for a certain command
	 * name. If the factory f1 is registered for the name "jgit://builtin/x"
	 * then a call to <code>getCommand("jgit://builtin/x", ...)</code> will call
	 * <code>f1(...)</code> to create a new instance of
	 * {@link org.eclipse.jgit.attributes.FilterCommand}
	 *
	 * @param filterCommandName
	 *            the command name for which this factory is registered
	 * @param factory
	 *            the factory responsible for creating
	 *            {@link org.eclipse.jgit.attributes.FilterCommand}s for the
	 *            specified name
	 * @return the previous factory associated with <tt>commandName</tt>, or
	 *         <tt>null</tt> if there was no mapping for <tt>commandName</tt>
	 */
	public static FilterCommandFactory register(String filterCommandName,
			FilterCommandFactory factory) {
		return filterCommandRegistry.put(filterCommandName, factory);
	}

	/**
	 * Unregister the {@link org.eclipse.jgit.attributes.FilterCommandFactory}
	 * registered for the given command name
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
	 * Check whether any
	 * {@link org.eclipse.jgit.attributes.FilterCommandFactory} is registered
	 * for a given command name
	 *
	 * @param filterCommandName
	 *            the name for which the registry should be checked
	 * @return <code>true</code> if any factory was registered for the name
	 */
	public static boolean isRegistered(String filterCommandName) {
		return filterCommandRegistry.containsKey(filterCommandName);
	}

	/**
	 * Get registered filter commands
	 *
	 * @return Set of commandNames for which a
	 *         {@link org.eclipse.jgit.attributes.FilterCommandFactory} is
	 *         registered
	 */
	public static Set<String> getRegisteredFilterCommands() {
		return filterCommandRegistry.keySet();
	}

	/**
	 * Create a new {@link org.eclipse.jgit.attributes.FilterCommand} for the
	 * given name. A factory must be registered for the name in advance.
	 *
	 * @param filterCommandName
	 *            The name for which a new
	 *            {@link org.eclipse.jgit.attributes.FilterCommand} should be
	 *            created
	 * @param db
	 *            the repository this command should work on
	 * @param in
	 *            the {@link java.io.InputStream} this
	 *            {@link org.eclipse.jgit.attributes.FilterCommand} should read
	 *            from
	 * @param out
	 *            the {@link java.io.OutputStream} this
	 *            {@link org.eclipse.jgit.attributes.FilterCommand} should write
	 *            to
	 * @return the command if a command could be created or <code>null</code> if
	 *         there was no factory registered for that name
	 * @throws java.io.IOException
	 */
	public static FilterCommand createFilterCommand(String filterCommandName,
			Repository db, InputStream in, OutputStream out)
			throws IOException {
		FilterCommandFactory cf = filterCommandRegistry.get(filterCommandName);
		return (cf == null) ? null : cf.create(db, in, out);
	}

}

/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * List of all commands known by jgit's command line tools.
 * <p>
 * Commands are implementations of {@link org.eclipse.jgit.pgm.TextBuiltin},
 * with an optional {@link org.eclipse.jgit.pgm.Command} class annotation to
 * insert additional documentation or override the default command name (which
 * is guessed from the class name).
 * <p>
 * Commands may be registered by adding them to a services file in the same JAR
 * (or classes directory) as the command implementation. The service file name
 * is <code>META-INF/services/org.eclipse.jgit.pgm.TextBuiltin</code> and it
 * contains one concrete implementation class name per line.
 * <p>
 * Command registration is identical to Java 6's services, however the catalog
 * uses a lightweight wrapper to delay creating a command instance as much as
 * possible. This avoids initializing the AWT or SWT GUI toolkits even if the
 * command's constructor might require them.
 */
public class CommandCatalog {
	private static final CommandCatalog INSTANCE = new CommandCatalog();

	/**
	 * Locate a single command by its user friendly name.
	 *
	 * @param name
	 *            name of the command. Typically in dash-lower-case-form, which
	 *            was derived from the DashLowerCaseForm class name.
	 * @return the command instance; null if no command exists by that name.
	 */
	public static CommandRef get(String name) {
		return INSTANCE.commands.get(name);
	}

	/**
	 * Get all commands sorted by their name
	 *
	 * @return all known commands, sorted by command name.
	 */
	public static CommandRef[] all() {
		return toSortedArray(INSTANCE.commands.values());
	}

	/**
	 * Get all common commands sorted by their name
	 *
	 * @return all common commands, sorted by command name.
	 */
	public static CommandRef[] common() {
		final ArrayList<CommandRef> common = new ArrayList<>();
		for (CommandRef c : INSTANCE.commands.values())
			if (c.isCommon())
				common.add(c);
		return toSortedArray(common);
	}

	private static CommandRef[] toSortedArray(Collection<CommandRef> c) {
		final CommandRef[] r = c.toArray(new CommandRef[0]);
		Arrays.sort(r, (CommandRef o1, CommandRef o2) -> o1.getName()
				.compareTo(o2.getName()));
		return r;
	}

	private final ClassLoader ldr;

	private final Map<String, CommandRef> commands;

	private CommandCatalog() {
		ldr = Thread.currentThread().getContextClassLoader();
		commands = new HashMap<>();

		final Enumeration<URL> catalogs = catalogs();
		while (catalogs.hasMoreElements())
			scan(catalogs.nextElement());
	}

	private Enumeration<URL> catalogs() {
		try {
			final String pfx = "META-INF/services/"; //$NON-NLS-1$
			return ldr.getResources(pfx + TextBuiltin.class.getName());
		} catch (IOException err) {
			return new Vector<URL>().elements();
		}
	}

	private void scan(URL cUrl) {
		try (BufferedReader cIn = new BufferedReader(
				new InputStreamReader(cUrl.openStream(), UTF_8))) {
			String line;
			while ((line = cIn.readLine()) != null) {
				if (line.length() > 0 && !line.startsWith("#")) //$NON-NLS-1$
					load(line);
			}
		} catch (IOException e) {
			// Ignore errors
		}
	}

	private void load(String cn) {
		final Class<? extends TextBuiltin> clazz;
		try {
			clazz = Class.forName(cn, false, ldr).asSubclass(TextBuiltin.class);
		} catch (ClassNotFoundException | ClassCastException notBuiltin) {
			// Doesn't exist, even though the service entry is present.
			// Isn't really a builtin, even though its listed as such.
			return;
		}

		final CommandRef cr;
		final Command a = clazz.getAnnotation(Command.class);
		if (a != null)
			cr = new CommandRef(clazz, a);
		else
			cr = new CommandRef(clazz);

		commands.put(cr.getName(), cr);
	}
}

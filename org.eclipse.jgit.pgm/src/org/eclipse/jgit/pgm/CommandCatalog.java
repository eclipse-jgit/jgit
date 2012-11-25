/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.pgm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * List of all commands known by jgit's command line tools.
 * <p>
 * Commands are implementations of {@link TextBuiltin}, with an optional
 * {@link Command} class annotation to insert additional documentation or
 * override the default command name (which is guessed from the class name).
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
	public static CommandRef get(final String name) {
		return INSTANCE.commands.get(name);
	}

	/**
	 * @return all known commands, sorted by command name.
	 */
	public static CommandRef[] all() {
		return toSortedArray(INSTANCE.commands.values());
	}

	/**
	 * @return all common commands, sorted by command name.
	 */
	public static CommandRef[] common() {
		final ArrayList<CommandRef> common = new ArrayList<CommandRef>();
		for (final CommandRef c : INSTANCE.commands.values())
			if (c.isCommon())
				common.add(c);
		return toSortedArray(common);
	}

	private static CommandRef[] toSortedArray(final Collection<CommandRef> c) {
		final CommandRef[] r = c.toArray(new CommandRef[c.size()]);
		Arrays.sort(r, new Comparator<CommandRef>() {
			public int compare(final CommandRef o1, final CommandRef o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});
		return r;
	}

	private final ClassLoader ldr;

	private final Map<String, CommandRef> commands;

	private CommandCatalog() {
		ldr = Thread.currentThread().getContextClassLoader();
		commands = new HashMap<String, CommandRef>();

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

	private void scan(final URL cUrl) {
		final BufferedReader cIn;
		try {
			final InputStream in = cUrl.openStream();
			cIn = new BufferedReader(new InputStreamReader(in, "UTF-8")); //$NON-NLS-1$
		} catch (IOException err) {
			// If we cannot read from the service list, go to the next.
			//
			return;
		}

		try {
			String line;
			while ((line = cIn.readLine()) != null) {
				if (line.length() > 0 && !line.startsWith("#")) //$NON-NLS-1$
					load(line);
			}
		} catch (IOException err) {
			// If we failed during a read, ignore the error.
			//
		} finally {
			try {
				cIn.close();
			} catch (IOException e) {
				// Ignore the close error; we are only reading.
			}
		}
	}

	private void load(final String cn) {
		final Class<? extends TextBuiltin> clazz;
		try {
			clazz = Class.forName(cn, false, ldr).asSubclass(TextBuiltin.class);
		} catch (ClassNotFoundException notBuiltin) {
			// Doesn't exist, even though the service entry is present.
			//
			return;
		} catch (ClassCastException notBuiltin) {
			// Isn't really a builtin, even though its listed as such.
			//
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

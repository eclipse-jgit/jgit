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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;

import org.eclipse.jgit.pgm.internal.CLIText;

/**
 * Description of a command (a {@link org.eclipse.jgit.pgm.TextBuiltin}
 * subclass).
 * <p>
 * These descriptions are lightweight compared to creating a command instance
 * and are therefore suitable for catalogs of "known" commands without linking
 * the command's implementation and creating a dummy instance of the command.
 */
public class CommandRef {
	private final Class<? extends TextBuiltin> impl;

	private final String name;

	private String usage;

	boolean common;

	CommandRef(Class<? extends TextBuiltin> clazz) {
		this(clazz, guessName(clazz));
	}

	CommandRef(Class<? extends TextBuiltin> clazz, Command cmd) {
		this(clazz, cmd.name().length() > 0 ? cmd.name() : guessName(clazz));
		usage = cmd.usage();
		common = cmd.common();
	}

	private CommandRef(Class<? extends TextBuiltin> clazz, String cn) {
		impl = clazz;
		name = cn;
		usage = ""; //$NON-NLS-1$
	}

	private static String guessName(Class<? extends TextBuiltin> clazz) {
		final StringBuilder s = new StringBuilder();
		if (clazz.getName().startsWith("org.eclipse.jgit.pgm.debug.")) //$NON-NLS-1$
			s.append("debug-"); //$NON-NLS-1$

		boolean lastWasDash = true;
		for (char c : clazz.getSimpleName().toCharArray()) {
			if (Character.isUpperCase(c)) {
				if (!lastWasDash)
					s.append('-');
				lastWasDash = !lastWasDash;
				s.append(Character.toLowerCase(c));
			} else {
				s.append(c);
				lastWasDash = false;
			}
		}
		return s.toString();
	}

	/**
	 * Get the <code>name</code>.
	 *
	 * @return name the command is invoked as from the command line.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get <code>usage</code>.
	 *
	 * @return one line description of the command's feature set.
	 */
	public String getUsage() {
		return usage;
	}

	/**
	 * Is this command commonly used
	 *
	 * @return true if this command is considered to be commonly used.
	 */
	public boolean isCommon() {
		return common;
	}

	/**
	 * Get implementation class name
	 *
	 * @return name of the Java class which implements this command.
	 */
	public String getImplementationClassName() {
		return impl.getName();
	}

	/**
	 * Get implementation class loader
	 *
	 * @return loader for {@link #getImplementationClassName()}.
	 */
	public ClassLoader getImplementationClassLoader() {
		return impl.getClassLoader();
	}

	/**
	 * Create an instance of the command implementation
	 *
	 * @return a new instance of the command implementation.
	 */
	public TextBuiltin create() {
		final Constructor<? extends TextBuiltin> c;
		try {
			c = impl.getDeclaredConstructor();
		} catch (SecurityException | NoSuchMethodException e) {
			throw new RuntimeException(MessageFormat
					.format(CLIText.get().cannotCreateCommand, getName(), e));
		}
		c.setAccessible(true);

		final TextBuiltin r;
		try {
			r = c.newInstance();
		} catch (InstantiationException | IllegalAccessException
				| IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(MessageFormat
					.format(CLIText.get().cannotCreateCommand, getName(), e));
		}
		r.setCommandName(getName());
		return r;
	}

	/** {@inheritDoc} */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return "CommandRef [impl=" + impl + ", name=" + name + ", usage="
				+ CLIText.get().resourceBundle().getString(usage) + ", common="
				+ common + "]";
	}
}

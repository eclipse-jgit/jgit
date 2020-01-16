/*
 * Copyright (C) 2017, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.transport.RefSpec;

/**
 * Something that knows how to convert plain strings from a git
 * {@link org.eclipse.jgit.lib.Config} to typed values.
 *
 * @since 4.9
 */
public interface TypedConfigGetter {

	/**
	 * Get a boolean value from a git {@link org.eclipse.jgit.lib.Config}.
	 *
	 * @param config
	 *            to get the value from
	 * @param section
	 *            section the key is grouped within.
	 * @param subsection
	 *            subsection name, such a remote or branch name.
	 * @param name
	 *            name of the key to get.
	 * @param defaultValue
	 *            default value to return if no value was present.
	 * @return true if any value or defaultValue is true, false for missing or
	 *         explicit false
	 */
	boolean getBoolean(Config config, String section, String subsection,
			String name, boolean defaultValue);

	/**
	 * Parse an enumeration from a git {@link org.eclipse.jgit.lib.Config}.
	 *
	 * @param config
	 *            to get the value from
	 * @param all
	 *            all possible values in the enumeration which should be
	 *            recognized. Typically {@code EnumType.values()}.
	 * @param section
	 *            section the key is grouped within.
	 * @param subsection
	 *            subsection name, such a remote or branch name.
	 * @param name
	 *            name of the key to get.
	 * @param defaultValue
	 *            default value to return if no value was present.
	 * @return the selected enumeration value, or {@code defaultValue}.
	 */
	<T extends Enum<?>> T getEnum(Config config, T[] all, String section,
			String subsection, String name, T defaultValue);

	/**
	 * Obtain an integer value from a git {@link org.eclipse.jgit.lib.Config}.
	 *
	 * @param config
	 *            to get the value from
	 * @param section
	 *            section the key is grouped within.
	 * @param subsection
	 *            subsection name, such a remote or branch name.
	 * @param name
	 *            name of the key to get.
	 * @param defaultValue
	 *            default value to return if no value was present.
	 * @return an integer value from the configuration, or defaultValue.
	 */
	int getInt(Config config, String section, String subsection, String name,
			int defaultValue);

	/**
	 * Obtain a long value from a git {@link org.eclipse.jgit.lib.Config}.
	 *
	 * @param config
	 *            to get the value from
	 * @param section
	 *            section the key is grouped within.
	 * @param subsection
	 *            subsection name, such a remote or branch name.
	 * @param name
	 *            name of the key to get.
	 * @param defaultValue
	 *            default value to return if no value was present.
	 * @return a long value from the configuration, or defaultValue.
	 */
	long getLong(Config config, String section, String subsection, String name,
			long defaultValue);

	/**
	 * Parse a numerical time unit, such as "1 minute", from a git
	 * {@link org.eclipse.jgit.lib.Config}.
	 *
	 * @param config
	 *            to get the value from
	 * @param section
	 *            section the key is in.
	 * @param subsection
	 *            subsection the key is in, or null if not in a subsection.
	 * @param name
	 *            the key name.
	 * @param defaultValue
	 *            default value to return if no value was present.
	 * @param wantUnit
	 *            the units of {@code defaultValue} and the return value, as
	 *            well as the units to assume if the value does not contain an
	 *            indication of the units.
	 * @return the value, or {@code defaultValue} if not set, expressed in
	 *         {@code units}.
	 */
	long getTimeUnit(Config config, String section, String subsection,
			String name, long defaultValue, TimeUnit wantUnit);


	/**
	 * Parse a list of {@link org.eclipse.jgit.transport.RefSpec}s from a git
	 * {@link org.eclipse.jgit.lib.Config}.
	 *
	 * @param config
	 *            to get the list from
	 * @param section
	 *            section the key is in.
	 * @param subsection
	 *            subsection the key is in, or null if not in a subsection.
	 * @param name
	 *            the key name.
	 * @return a possibly empty list of
	 *         {@link org.eclipse.jgit.transport.RefSpec}s
	 */
	@NonNull
	List<RefSpec> getRefSpecs(Config config, String section, String subsection,
			String name);
}

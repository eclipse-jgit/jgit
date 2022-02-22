/*
 * Copyright (C) 2017, 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.util.FS;

/**
 * Something that knows how to convert plain strings from a git {@link Config}
 * to typed values.
 *
 * @since 4.9
 */
public interface TypedConfigGetter {

	/**
	 * Use {@code Integer#MIN_VALUE} as unset int value
	 *
	 * @since 6.1
	 */
	public static final int UNSET_INT = Integer.MIN_VALUE;

	/**
	 * Get a boolean value from a git {@link Config}.
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
	 * Parse an enumeration from a git {@link Config}.
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
	 * Obtain an integer value from a git {@link Config}.
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
	 * Obtain an integer value from a git {@link Config} which must be in given
	 * range.
	 *
	 * @param config
	 *            to get the value from
	 * @param section
	 *            section the key is grouped within.
	 * @param subsection
	 *            subsection name, such a remote or branch name.
	 * @param name
	 *            name of the key to get.
	 * @param minValue
	 *            minimal value
	 * @param maxValue
	 *            maximum value
	 * @param defaultValue
	 *            default value to return if no value was present. Use
	 *            {@code #UNSET_INT} to set the default to unset.
	 * @return an integer value from the configuration, or defaultValue.
	 *         {@code #UNSET_INT} if unset.
	 * @since 6.1
	 */
	int getIntInRange(Config config, String section, String subsection,
			String name, int minValue, int maxValue, int defaultValue);

	/**
	 * Obtain a long value from a git {@link Config}.
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
	 * {@link Config}.
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
	 * Parse a string value from a git {@link Config} and treat it as a file
	 * path, replacing a ~/ prefix by the user's home directory.
	 * <p>
	 * <b>Note:</b> this may throw {@link InvalidPathException} if the string is
	 * not a valid path.
	 * </p>
	 *
	 * @param config
	 *            to get the path from.
	 * @param section
	 *            section the key is in.
	 * @param subsection
	 *            subsection the key is in, or null if not in a subsection.
	 * @param name
	 *            the key name.
	 * @param fs
	 *            to use to convert the string into a path.
	 * @param resolveAgainst
	 *            directory to resolve the path against if it is a relative
	 *            path.
	 * @param defaultValue
	 *            to return if no value was present
	 * @return the {@link Path}, or {@code defaultValue} if not set
	 * @since 5.10
	 */
	default Path getPath(Config config, String section, String subsection,
			String name, @NonNull FS fs, File resolveAgainst,
			Path defaultValue) {
		String value = config.getString(section, subsection, name);
		if (value == null) {
			return defaultValue;
		}
		File file;
		if (value.startsWith("~/")) { //$NON-NLS-1$
			file = fs.resolve(fs.userHome(), value.substring(2));
		} else {
			file = fs.resolve(resolveAgainst, value);
		}
		return file.toPath();
	}

	/**
	 * Parse a list of {@link RefSpec}s from a git {@link Config}.
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

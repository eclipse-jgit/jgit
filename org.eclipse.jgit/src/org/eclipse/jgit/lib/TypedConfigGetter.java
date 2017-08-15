/*
 * Copyright (C) 2017, Thomas Wolf <thomas.wolf@paranor.ch>
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

package org.eclipse.jgit.lib;

import java.util.concurrent.TimeUnit;

/**
 * Something that knows how to convert plain strings from a git {@link Config}
 * to typed values.
 *
 * @since 4.9
 */
public interface TypedConfigGetter {

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
	 * @param <T>
	 *            type of the enumeration object.
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

}

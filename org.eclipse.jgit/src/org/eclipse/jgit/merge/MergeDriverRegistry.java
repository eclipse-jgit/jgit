/*******************************************************************************
 * Copyright (C) 2013, Obeo
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
 *******************************************************************************/
package org.eclipse.jgit.merge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This will hold all registered merger drivers.
 */
public class MergeDriverRegistry {
	/**
	 * Holds the merge drivers for this registry. Keys of this map are the merge
	 * drivers' name.
	 */
	private static final Map<String, IMergeDriver> REGISTERED_DRIVERS = new ConcurrentHashMap<String, IMergeDriver>();

	/*
	 * Note : once support for gitattributes is added, this map will have to
	 * react to the changes made in these files. For now we do not take the path
	 * into account at all, we only consider the file name pattern for the
	 * association.
	 */
	/**
	 * This will be used to register the associations from path to merge driver.
	 */
	private static final Map<Pattern, String> DRIVER_ASSOCIATION = new ConcurrentHashMap<Pattern, String>();

	private MergeDriverRegistry() {
		// prevents instantiation
	}

	/**
	 * Registers a new merge driver within this registry.
	 *
	 * @param driver
	 *            The merge driver.
	 * @return The previously registered driver with the same name, if any.
	 */
	public static IMergeDriver registerDriver(IMergeDriver driver) {
		checkNotNull(driver);
		return REGISTERED_DRIVERS.put(driver.getName(), driver);
	}

	/**
	 * Removes any driver with the given name from this registry.
	 *
	 * @param name
	 *            The driver name.
	 * @return The removed driver, if any.
	 */
	public static IMergeDriver removeDriver(String name) {
		return REGISTERED_DRIVERS.remove(name);
	}

	/**
	 * Associates the given file name pattern with the given driver. Note that
	 * this association won't have any effect if the given driver name doesn't
	 * map to an existing driver.
	 *
	 * @param fileNamePattern
	 *            Pattern for the file names to associate with this driver.
	 * @param driverName
	 *            Name of the target driver.
	 * @return the old driver associated with this pattern, if any.
	 */
	public static String associate(Pattern fileNamePattern, String driverName) {
		return DRIVER_ASSOCIATION.put(checkNotNull(fileNamePattern),
				checkNotNull(driverName));
	}

	/**
	 * This will be called by the merge strategy in order to determine which
	 * merge driver needs to be called for a given path.
	 *
	 * @param path
	 *            Path of the file we need a merger for.
	 * @return The merge driver for this path.
	 */
	public static IMergeDriver findMergeDriver(String path) {
		String fileName = path;
		int separatorIndex = path.indexOf('/');
		if (separatorIndex >= 0)
			fileName = path.substring(separatorIndex + 1);

		for (Map.Entry<Pattern, String> association : DRIVER_ASSOCIATION
				.entrySet()) {
			final Matcher matcher = association.getKey().matcher(fileName);
			if (matcher.matches()) {
				final IMergeDriver driver = REGISTERED_DRIVERS.get(association
						.getValue());
				if (driver != null)
					return driver;
			}
		}
		return null;
	}

	/**
	 * Checks whether a given reference is <code>null</code>, throwing a
	 * {@link NullPointerException} if it is.
	 *
	 * @param reference
	 *            The reference.
	 * @return The passed in reference if it is not <code>null</code>.
	 * @throws NullPointerException
	 *             If the given reference was <code>null</code>.
	 */
	private static <T> T checkNotNull(T reference) {
		if (reference == null)
			throw new NullPointerException();
		return reference;
	}
}

/*******************************************************************************
 * Copyright (C) 2014, Obeo
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.PathMatcher;

/**
 * Registry of all merge drivers.
 * 
 * @since 3.4
 */
public class MergeDriverRegistry {
	/**
	 * Holds the merge drivers for this registry. Keys of this map are the merge
	 * drivers' name.
	 */
	private static final Map<String, MergeDriver> REGISTERED_DRIVERS = new ConcurrentHashMap<String, MergeDriver>();

	/*
	 * Note : once support for gitattributes is added, this map will have to
	 * react to the changes made in these files. For now we do not take the path
	 * into account at all, we only consider the file name pattern for the
	 * association.
	 */
	/**
	 * This will be used to register the associations from path to merge driver.
	 * Note that the keys of this map will be treated as globs supporting at
	 * least the "**", "*" and "?" wildcards.
	 *
	 * @see #associate(String, String)
	 */
	private static final Map<String, String> DRIVER_ASSOCIATION = new LinkedHashMap<String, String>();

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
	public static MergeDriver registerDriver(MergeDriver driver) {
		checkNotNull(driver);
		return REGISTERED_DRIVERS.put(driver.getName(), driver);
	}

	/**
	 * Associates the given glob pattern with the given driver. Note that even
	 * though it would be legal, an association wouldn't have any effect if the
	 * given driver name doesn't map to an existing driver.
	 * <p>
	 * The pattern supported by this registry will accept the following
	 * wildcards :
	 * <ul>
	 * <li><code>*</code> : matches zero or more characters.</li>
	 * <li><code>**</code> : matches zero or more characters and across one or
	 * more sub-folders.</li>
	 * <li><code>?</code> : matches exactly one character.</li>
	 * </ul>
	 * </p>
	 *
	 * @param globPattern
	 *            Pattern for the file names to associate with this driver.
	 * @param driverName
	 *            Name of the target driver.
	 * @return The old driver associated with this pattern, if any.
	 */
	public static String associate(String globPattern, String driverName) {
		return DRIVER_ASSOCIATION.put(checkNotNull(globPattern),
				checkNotNull(driverName));
	}

	/**
	 * This will return the merge driver associated with a glob matching the
	 * given path.
	 * <p>
	 * This will return the <b>last</b> corresponding merge driver. For example,
	 * if <code>path</code> is "abc" and we have associated, in this order :
	 * <ol>
	 * <li>a* => merge driver A</li>
	 * <li>abc => merge driver B</li>
	 * <li>*c => merge driver C</li>
	 * <li>abc? => merge driver D</li>
	 * </ol>
	 * Then the returned driver will be C, since D does not match "abc" and C is
	 * the last that matches.
	 * </p>
	 *
	 * @param path
	 *            Path of the file we need a merger for.
	 * @return The merge driver for this path.
	 */
	public static MergeDriver findMergeDriver(String path) {
		String fileName = path;
		int separatorIndex = path.lastIndexOf('/');
		if (separatorIndex >= 0)
			fileName = path.substring(separatorIndex + 1);

		MergeDriver lastMatch = null;
		for (Map.Entry<String, String> association : DRIVER_ASSOCIATION
				.entrySet()) {
			// Should we keep the matcher around?
			final PathMatcher matcher = FS.DETECTED.getPathMatcher(association
					.getKey());
			if (matcher.matches(fileName)) {
				final MergeDriver driver = REGISTERED_DRIVERS.get(association
						.getValue());
				if (driver != null)
					lastMatch = driver;
			}
		}
		return lastMatch;
	}

	/**
	 * Clears out this registry of all drivers it holds.
	 */
	public static void clear() {
		REGISTERED_DRIVERS.clear();
		DRIVER_ASSOCIATION.clear();
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

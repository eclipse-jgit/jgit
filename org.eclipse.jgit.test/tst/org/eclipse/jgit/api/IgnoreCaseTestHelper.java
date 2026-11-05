/*
 * Copyright (C) 2026 Federico Jeanne <federico.jeanne@vector.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import java.io.File;
import java.util.Arrays;
import java.util.function.Predicate;

import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS_POSIX;
import org.eclipse.jgit.util.FS_Win32;

/**
 * Helpers for tests of {@code core.ignorecase} handling.
 */
public final class IgnoreCaseTestHelper {

	private IgnoreCaseTestHelper() {
		// utility class
	}

	/**
	 * Reads {@code core.ignorecase} from the repository's config file.
	 *
	 * @param repo
	 *            the repository
	 * @return the value as written, or {@code null} if not set
	 * @throws Exception
	 *             if the config file cannot be read
	 */
	public static String readIgnoreCase(Repository repo) throws Exception {
		FileBasedConfig cfg = new FileBasedConfig(
				new File(repo.getDirectory(), Constants.CONFIG), FS.DETECTED);
		cfg.load();
		return cfg.getString(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_IGNORECASE);
	}

	/**
	 * Creates a file system that finds files regardless of the case of their
	 * names, like a case-insensitive file system, on any platform.
	 *
	 * @return the file system
	 */
	public static FS caseInsensitiveFs() {
		return testFs(
				path -> containsName(path, path.getName()::equalsIgnoreCase));
	}

	/**
	 * Creates a file system that finds files only by their exact name, like a
	 * case-sensitive file system, on any platform.
	 *
	 * @return the file system
	 */
	public static FS caseSensitiveFs() {
		return testFs(path -> containsName(path, path.getName()::equals));
	}

	private static boolean containsName(File path, Predicate<String> matches) {
		File parent = path.getParentFile();
		String[] names = parent == null ? null : parent.list();
		if (names == null) {
			return path.exists();
		}
		return Arrays.stream(names).anyMatch(matches);
	}

	/**
	 * Creates the platform's file system with a different notion of which files
	 * exist; everything else behaves like the platform's default file system.
	 *
	 * @param exists
	 *            decides whether a file exists
	 * @return the file system
	 */
	private static FS testFs(Predicate<File> exists) {
		if (FS.DETECTED instanceof FS_Win32) {
			return new FS_Win32() {

				@Override
				public boolean exists(File path) {
					return exists.test(path);
				}
			};
		}
		return new FS_POSIX() {

			@Override
			public boolean exists(File path) {
				return exists.test(path);
			}
		};
	}
}

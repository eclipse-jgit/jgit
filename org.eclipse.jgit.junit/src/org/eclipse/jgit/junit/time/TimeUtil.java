/*
 * Copyright (C) 2019, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.junit.time;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import org.eclipse.jgit.util.FS;

/**
 * Utility methods for handling timestamps
 */
public class TimeUtil {
	/**
	 * Set the lastModified time of a given file by adding a given offset to the
	 * current lastModified time
	 *
	 * @param path
	 *            path of a file to set last modified
	 * @param offsetMillis
	 *            offset in milliseconds, if negative the new lastModified time
	 *            is offset before the original lastModified time, otherwise
	 *            after the original time
	 * @return the new lastModified time
	 */
	public static Instant setLastModifiedWithOffset(Path path,
			long offsetMillis) {
		Instant mTime = FS.DETECTED.lastModifiedInstant(path)
				.plusMillis(offsetMillis);
		try {
			Files.setLastModifiedTime(path, FileTime.from(mTime));
			return mTime;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Set the lastModified time of file a to the one from file b
	 *
	 * @param a
	 *            file to set lastModified time
	 * @param b
	 *            file to read lastModified time from
	 */
	public static void setLastModifiedOf(Path a, Path b) {
		Instant mTime = FS.DETECTED.lastModifiedInstant(b);
		try {
			Files.setLastModifiedTime(a, FileTime.from(mTime));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}

/*
 * Copyright (C) 2019, Matthias Sohn <matthias.sohn@sap.com>
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

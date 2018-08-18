/*
 * Copyright (C) 2017 Two Sigma Open Source
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

package org.eclipse.jgit.internal.storage.file;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.FileTime;
import java.text.ParseException;
import java.time.Instant;

import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.util.GitDateParser;
import org.eclipse.jgit.util.SystemReader;

/**
 * This class manages the gc.log file for a {@link FileRepository}.
 */
class GcLog {
	private final FileRepository repo;

	private final File logFile;

	private final LockFile lock;

	private Instant gcLogExpire;

	private static final String LOG_EXPIRY_DEFAULT = "1.day.ago"; //$NON-NLS-1$

	private boolean nonEmpty = false;

	/**
	 * Construct a GcLog object for a {@link FileRepository}
	 *
	 * @param repo
	 *            the repository
	 */
	GcLog(FileRepository repo) {
		this.repo = repo;
		logFile = new File(repo.getDirectory(), "gc.log"); //$NON-NLS-1$
		lock = new LockFile(logFile);
	}

	private Instant getLogExpiry() throws ParseException {
		if (gcLogExpire == null) {
			String logExpiryStr = repo.getConfig().getString(
					ConfigConstants.CONFIG_GC_SECTION, null,
					ConfigConstants.CONFIG_KEY_LOGEXPIRY);
			if (logExpiryStr == null) {
				logExpiryStr = LOG_EXPIRY_DEFAULT;
			}
			gcLogExpire = GitDateParser.parse(logExpiryStr, null,
					SystemReader.getInstance().getLocale()).toInstant();
		}
		return gcLogExpire;
	}

	private boolean autoGcBlockedByOldLockFile() {
		try {
			FileTime lastModified = Files.getLastModifiedTime(logFile.toPath());
			if (lastModified.toInstant().compareTo(getLogExpiry()) > 0) {
				// There is an existing log file, which is too recent to ignore
				return true;
			}
		} catch (NoSuchFileException e) {
			// No existing log file, OK.
		} catch (IOException | ParseException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
		return false;
	}

	/**
	 * Lock the GC log file for updates
	 *
	 * @return {@code true} if we hold the lock
	 */
	boolean lock() {
		try {
			if (!lock.lock()) {
				return false;
			}
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
		if (autoGcBlockedByOldLockFile()) {
			lock.unlock();
			return false;
		}
		return true;
	}

	/**
	 * Unlock (roll back) the GC log lock
	 */
	void unlock() {
		lock.unlock();
	}

	/**
	 * Commit changes to the gc log, if there have been any writes. Otherwise,
	 * just unlock and delete the existing file (if any)
	 *
	 * @return true if committing (or unlocking/deleting) succeeds.
	 */
	boolean commit() {
		if (nonEmpty) {
			return lock.commit();
		} else {
			logFile.delete();
			lock.unlock();
			return true;
		}
	}

	/**
	 * Write to the pending gc log. Content will be committed upon a call to
	 * commit()
	 *
	 * @param content
	 *            The content to write
	 * @throws IOException
	 */
	void write(String content) throws IOException {
		if (content.length() > 0) {
			nonEmpty = true;
		}
		lock.write(content.getBytes(UTF_8));
	}
}

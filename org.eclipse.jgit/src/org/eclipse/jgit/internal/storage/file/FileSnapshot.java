/*
 * Copyright (C) 2010, Google Inc.
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

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caches when a file was last read, making it possible to detect future edits.
 * <p>
 * This object tracks the last modified time of a file. Later during an
 * invocation of {@link #isModified(File)} the object will return true if the
 * file may have been modified and should be re-read from disk.
 * <p>
 * A snapshot does not "live update" when the underlying filesystem changes.
 * Callers must poll for updates by periodically invoking
 * {@link #isModified(File)}.
 * <p>
 * To work around the "racy git" problem (where a file may be modified multiple
 * times within the granularity of the filesystem modification clock) this class
 * may return true from isModified(File) if the last modification time of the
 * file is less than 3 seconds ago.
 */
public class FileSnapshot {
	/**
	 * A FileSnapshot that is considered to always be modified.
	 * <p>
	 * This instance is useful for application code that wants to lazily read a
	 * file, but only after {@link #isModified(File)} gets invoked. The returned
	 * snapshot contains only invalid status information.
	 */
	public static final FileSnapshot DIRTY = new FileSnapshot(-1, -1);

	private static final Logger LOG = LoggerFactory.getLogger(FileSnapshot.class);

	/**
	 * A FileSnapshot that is clean if the file does not exist.
	 * <p>
	 * This instance is useful if the application wants to consider a missing
	 * file to be clean. {@link #isModified(File)} will return false if the file
	 * path does not exist.
	 */
	public static final FileSnapshot MISSING_FILE = new FileSnapshot(0, 0) {
		@Override
		public boolean isModified(File path) {
			return FS.DETECTED.exists(path);
		}
	};

	/**
	 * Record a snapshot for a specific file path.
	 * <p>
	 * This method should be invoked before the file is accessed.
	 *
	 * @param path
	 *            the path to later remember. The path's current status
	 *            information is saved.
	 * @return the snapshot.
	 */
	public static FileSnapshot save(File path) {
		long read = System.currentTimeMillis();
		long modified;
		try {
			modified = FS.DETECTED.lastModified(path);
		} catch (IOException e) {
			modified = path.lastModified();
		}
		return new FileSnapshot(read, modified);
	}

	/**
	 * Record a snapshot for a file for which the last modification time is
	 * already known.
	 * <p>
	 * This method should be invoked before the file is accessed.
	 *
	 * @param modified
	 *            the last modification time of the file
	 * @return the snapshot.
	 */
	public static FileSnapshot save(long modified) {
		final long read = System.currentTimeMillis();
		return new FileSnapshot(read, modified);
	}

	/** Last observed modification time of the path. */
	private final long lastModified;

	/** Last wall-clock time the path was read. */
	private volatile long lastRead;

	/** True once {@link #lastRead} is far later than {@link #lastModified}. */
	private boolean cannotBeRacilyClean;

	private FileSnapshot(long read, long modified) {
		this.lastRead = read;
		this.lastModified = modified;
		this.cannotBeRacilyClean = notRacyClean(read);
	}

	/**
	 * Get time of last snapshot update
	 *
	 * @return time of last snapshot update
	 */
	public long lastModified() {
		return lastModified;
	}

	/**
	 * Check if the path may have been modified since the snapshot was saved.
	 *
	 * @param path
	 *            the path the snapshot describes.
	 * @return true if the path needs to be read again.
	 */
	public boolean isModified(File path) {
		long currLastModified;
		try {
			currLastModified = FS.DETECTED.lastModified(path);
		} catch (IOException e) {
			currLastModified = path.lastModified();
		}
		return isModified(currLastModified);
	}

	/**
	 * Update this snapshot when the content hasn't changed.
	 * <p>
	 * If the caller gets true from {@link #isModified(File)}, re-reads the
	 * content, discovers the content is identical, and
	 * {@link #equals(FileSnapshot)} is true, it can use
	 * {@link #setClean(FileSnapshot)} to make a future
	 * {@link #isModified(File)} return false. The logic goes something like
	 * this:
	 *
	 * <pre>
	 * if (snapshot.isModified(path)) {
	 *  FileSnapshot other = FileSnapshot.save(path);
	 *  Content newContent = ...;
	 *  if (oldContent.equals(newContent) &amp;&amp; snapshot.equals(other))
	 *      snapshot.setClean(other);
	 * }
	 * </pre>
	 *
	 * @param other
	 *            the other snapshot.
	 */
	public void setClean(FileSnapshot other) {
		final long now = other.lastRead;
		if (notRacyClean(now))
			cannotBeRacilyClean = true;
		lastRead = now;
	}

	/**
	 * Compare two snapshots to see if they cache the same information.
	 *
	 * @param other
	 *            the other snapshot.
	 * @return true if the two snapshots share the same information.
	 */
	public boolean equals(FileSnapshot other) {
		return lastModified == other.lastModified;
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(Object other) {
		if (other instanceof FileSnapshot)
			return equals((FileSnapshot) other);
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		// This is pretty pointless, but override hashCode to ensure that
		// x.hashCode() == y.hashCode() when x.equals(y) is true.
		//
		return (int) lastModified;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		if (this == DIRTY)
			return "DIRTY"; //$NON-NLS-1$
		if (this == MISSING_FILE)
			return "MISSING_FILE"; //$NON-NLS-1$
		DateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", //$NON-NLS-1$
				Locale.US);
		return "FileSnapshot[modified: " + f.format(new Date(lastModified)) //$NON-NLS-1$
				+ ", read: " + f.format(new Date(lastRead)) + "]"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private boolean notRacyClean(long read) {
		// The last modified time granularity of FAT filesystems is 2 seconds.
		// Using 2.5 seconds here provides a reasonably high assurance that
		// a modification was not missed.
		//
		return read - lastModified > 2500;
	}

	private boolean isModified(long currLastModified) {
		// Any difference indicates the path was modified.
		//
		if (lastModified != currLastModified) {
			LOG.info("Modified: lastModified={} currModified={}", lastModified, currLastModified);
			return true;
		}

		// We have already determined the last read was far enough
		// after the last modification that any new modifications
		// are certain to change the last modified time.
		//
		if (cannotBeRacilyClean)
			return false;

		if (notRacyClean(lastRead)) {
			// Our last read should have marked cannotBeRacilyClean,
			// but this thread may not have seen the change. The read
			// of the volatile field lastRead should have fixed that.
			//
			return false;
		}

		// We last read this path too close to its last observed
		// modification time. We may have missed a modification.
		// Scan again, to ensure we still see the same state.
		//
		return true;
	}
}

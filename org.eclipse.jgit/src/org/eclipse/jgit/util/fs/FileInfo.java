/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2010, Matthias Sohn <matthias.sohn@sap.com>
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

package org.eclipse.jgit.util.fs;

import java.util.Date;

/** Result of {@link FileAccess#lstat(java.io.File)}, describing a file path. */
public final class FileInfo {
	// TODO In 2038 ctime/mtime overflow unless changed to long.

	// *** WARNING ***
	// The fields of this class are accessed directly from JNI code.
	// Do not rename, add or delete fields without making the same
	// changes in the native C code.

	private int atime;

	private int ctime;

	private int mtime;

	private int atime_nsec;

	private int ctime_nsec;

	private int mtime_nsec;

	private int dev;

	private int ino;

	private int mode;

	private int uid;

	private int gid;

	private long size;

	/**
	 * Construct from data available from {@link java.io.File}.
	 *
	 * @param lastModified
	 *            the last time a file's data changed, in milliseconds since the
	 *            UNIX epoch.
	 * @param mode
	 *            the file's mode, in UNIX-style mode bits. These can be
	 *            obtained from {@link org.eclipse.jgit.lib.FileMode} constants.
	 * @param size
	 *            the file's size in bytes.
	 */
	FileInfo(long lastModified, int mode, long size) {
		this.mtime = (int) (lastModified / 1000);
		this.mtime_nsec = ((int) (lastModified % 1000)) * 1000000;
		this.mode = mode;
		this.size = size;
	}

	/** @return file size, in bytes. */
	public long length() {
		return size;
	}

	/** @return the mode bits. */
	public int mode() {
		return mode;
	}

	/** @return last modification time, in milliseconds since epoch. */
	public long lastModified() {
		return ts(mtime, mtime_nsec);
	}

	/** @return last modification time, truncated to seconds. */
	public int lastModifiedSeconds() {
		return mtime;
	}

	/** @return nanoseconds component of last modification time. */
	public int lastModifiedNanos() {
		return mtime_nsec;
	}

	/** @return creation time, in milliseconds since epoch. */
	public long created() {
		return ts(ctime, ctime_nsec);
	}

	/** @return creation time, truncated to seconds. */
	public int createdSeconds() {
		return ctime;
	}

	/** @return nanoseconds component of creation time. */
	public int createdNanos() {
		return ctime_nsec;
	}

	/** @return last access time, in milliseconds since epoch. */
	public long lastAccessed() {
		return ts(atime, atime_nsec);
	}

	/** @return last accessed time, truncated to seconds. */
	public int lastAccessedSeconds() {
		return atime;
	}

	/** @return nanoseconds component of last accessed time. */
	public int lastAccessedNanos() {
		return atime_nsec;
	}

	/** @return device the file resides on. */
	public int device() {
		return dev;
	}

	/** @return inode number within the device. */
	public int inode() {
		return ino;
	}

	/** @return user-id of owner. */
	public int uid() {
		return uid;
	}

	/** @return group-id of owner. */
	public int gid() {
		return gid;
	}

	private static long ts(long sec, int nsec) {
		return (sec * 1000L) + (nsec / 1000000);
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append("length: ").append(length()).append("\n");
		s.append("mode: ").append(Integer.toOctalString(mode())).append("\n");
		s.append("lastModified: ").append(new Date(lastModified())).append("\n");
		s.append("created: ").append(new Date(created())).append("\n");
		s.append("lastAccessed: ").append(new Date(lastAccessed())).append("\n");
		s.append("device: ").append(device()).append("\n");
		s.append("inode: ").append(inode()).append("\n");
		s.append("uid/gid: ").append(uid()).append("/").append(gid());
		return s.toString();
	}
}

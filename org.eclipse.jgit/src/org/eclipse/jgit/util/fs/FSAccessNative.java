/*
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

import java.io.File;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.util.FS;

/**
 * Expose some file system functions not available from Java
 */
public class FSAccessNative extends FSAccess {

	static {
		System.loadLibrary("jgitnative");
	}

	/**
	 * empty default constructor
	 */
	FSAccessNative() {
		// empty
	}

	/**
	 * Retrieves lstat() data via native lstat() call
	 *
	 * @param path
	 *            Filesystem path the lstat data is requested for
	 * @return the returned array contains the following lstat data:
	 *         <ul>
	 *         <li>[0] ctime seconds, the last time a file's metadata changed</li>
	 *         <li>[1] ctime nanoseconds</li>
	 *         <li>[2] mtime seconds, the last time a file's data changed</li>
	 *         <li>[3] mtime nanoseconds</li>
	 *         <li>[4] dev, device inode resides on</li>
	 *         <li>[5] ino, inode's number</li>
	 *         <li>[6] mode, inode protection mode</li>
	 *         <li>[7] uid, user-id of owner</li>
	 *         <li>[8] gid, group-id of owner</li>
	 *         <li>[9] file size, in bytes, higher bits</li>
	 *         <li>[10] file size, in bytes, lower bits</li>
	 *         </ul>
	 */
	private static final native int[] lstatImpl(String path);

	/**
	 * Retrieves lstat data for file at given path
	 *
	 * @param fs
	 *            file system abstraction
	 * @param file
	 *            file the lstat data is requested for
	 * @return the lstat data
	 */
	public LStat lstat(FS fs, File file) throws NoSuchFileException,
			NotDirectoryException {
		// fs is not needed here, since native implementation can directly
		// determine execute bit
		String path = file.getPath();
		int[] rawlstat = lstatImpl(path);
		if (rawlstat.length != 11)
			throw new IllegalArgumentException(
					JGitText.get().lstatImplIllegalResult);

		return new LStat(rawlstat);
	}

	@Override
	public boolean isNativeImplementation() {
		return true;
	}
}

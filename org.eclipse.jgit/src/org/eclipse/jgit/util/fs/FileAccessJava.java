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

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.util.FS;

/** Pure Java approximation of {@link FileAccess} routines. */
public class FileAccessJava extends FileAccess {
	private final FS fs;

	/**
	 * Construct a FileAccess relying on a particular {@link FS} implementation.
	 *
	 * @param fs
	 *            the type of FS this access instance supports.
	 */
	public FileAccessJava(FS fs) {
		this.fs = fs;
	}

	@Override
	public FileInfo lstat(File file) throws NoSuchFileException,
			NotDirectoryException {
		// Check modification time first. Non-existent paths will return 0.
		// Files last modified on the epoch aren't common (but could exist)
		// so also check if the path doesn't exist.

		final long mtime = file.lastModified();
		if (mtime == 0 && !file.exists())
			throw new NoSuchFileException(file.getPath());

		final int mode;
		final long sz;
		if (file.isDirectory()) {
			mode = FileMode.TREE.getBits();
			sz = 0;

		} else if (file.isFile()) {
			if (fs.canExecute(file))
				mode = FileMode.EXECUTABLE_FILE.getBits();
			else
				mode = FileMode.REGULAR_FILE.getBits();
			sz = file.length();

		} else {
			// This might be a device or some other non-portable type.
			mode = 0;
			sz = 0;
		}

		return new FileInfo(mtime, mode, sz);
	}
}

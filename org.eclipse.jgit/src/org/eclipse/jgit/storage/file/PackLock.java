/*
 * Copyright (C) 2009, Google Inc.
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

package org.eclipse.jgit.storage.file;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;

/** Keeps track of a {@link PackFile}'s associated <code>.keep</code> file. */
public class PackLock {
	private final File keepFile;
	private final FS fs;

	/**
	 * Create a new lock for a pack file.
	 *
	 * @param packFile
	 *            location of the <code>pack-*.pack</code> file.
	 * @param fs
	 *            the filesystem abstraction used by the repository.
	 */
	public PackLock(final File packFile, final FS fs) {
		final File p = packFile.getParentFile();
		final String n = packFile.getName();
		keepFile = new File(p, n.substring(0, n.length() - 5) + ".keep");
		this.fs = fs;
	}

	/**
	 * Create the <code>pack-*.keep</code> file, with the given message.
	 *
	 * @param msg
	 *            message to store in the file.
	 * @return true if the keep file was successfully written; false otherwise.
	 * @throws IOException
	 *             the keep file could not be written.
	 */
	public boolean lock(String msg) throws IOException {
		if (msg == null)
			return false;
		if (!msg.endsWith("\n"))
			msg += "\n";
		final LockFile lf = new LockFile(keepFile, fs);
		if (!lf.lock())
			return false;
		lf.write(Constants.encode(msg));
		return lf.commit();
	}

	/**
	 * Remove the <code>.keep</code> file that holds this pack in place.
	 *
	 * @throws IOException
	 *             if deletion of .keep file failed
	 */
	public void unlock() throws IOException {
		FileUtils.delete(keepFile);
	}
}

/*
 * Copyright (C) 2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;

/**
 * Keeps track of a {@link org.eclipse.jgit.internal.storage.file.PackFile}'s
 * associated <code>.keep</code> file.
 */
public class PackLock {
	private final File keepFile;

	/**
	 * Create a new lock for a pack file.
	 *
	 * @param packFile
	 *            location of the <code>pack-*.pack</code> file.
	 * @param fs
	 *            the filesystem abstraction used by the repository.
	 */
	public PackLock(File packFile, FS fs) {
		final File p = packFile.getParentFile();
		final String n = packFile.getName();
		keepFile = new File(p, n.substring(0, n.length() - 5) + ".keep"); //$NON-NLS-1$
	}

	/**
	 * Create the <code>pack-*.keep</code> file, with the given message.
	 *
	 * @param msg
	 *            message to store in the file.
	 * @return true if the keep file was successfully written; false otherwise.
	 * @throws java.io.IOException
	 *             the keep file could not be written.
	 */
	public boolean lock(String msg) throws IOException {
		if (msg == null)
			return false;
		if (!msg.endsWith("\n")) //$NON-NLS-1$
			msg += "\n"; //$NON-NLS-1$
		final LockFile lf = new LockFile(keepFile);
		if (!lf.lock())
			return false;
		lf.write(Constants.encode(msg));
		return lf.commit();
	}

	/**
	 * Remove the <code>.keep</code> file that holds this pack in place.
	 *
	 * @throws java.io.IOException
	 *             if deletion of .keep file failed
	 */
	public void unlock() throws IOException {
		FileUtils.delete(keepFile);
	}
}

/*
 * Copyright (C) 2016, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs;

import static org.eclipse.jgit.lib.Constants.OBJECTS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lib.Repository;

/**
 * Class which represents the lfs folder hierarchy inside a {@code .git} folder
 *
 * @since 4.6
 */
public class Lfs {
	private Path root;

	private Path objDir;

	private Path tmpDir;

	/**
	 * Constructor for Lfs.
	 *
	 * @param db
	 *            the associated repo
	 *
	 * @since 4.11
	 */
	public Lfs(Repository db) {
		this.root = db.getDirectory().toPath().resolve(Constants.LFS);
	}

	/**
	 * Get the LFS root directory
	 *
	 * @return the path to the LFS directory
	 */
	public Path getLfsRoot() {
		return root;
	}

	/**
	 * Get the path to the temporary directory used by LFS.
	 *
	 * @return the path to the temporary directory used by LFS. Will be
	 *         {@code <repo>/.git/lfs/tmp}
	 */
	public Path getLfsTmpDir() {
		if (tmpDir == null) {
			tmpDir = root.resolve("tmp"); //$NON-NLS-1$
		}
		return tmpDir;
	}

	/**
	 * Get the object directory used by LFS
	 *
	 * @return the path to the object directory used by LFS. Will be
	 *         {@code <repo>/.git/lfs/objects}
	 */
	public Path getLfsObjDir() {
		if (objDir == null) {
			objDir = root.resolve(OBJECTS);
		}
		return objDir;
	}

	/**
	 * Get the media file which stores the original content
	 *
	 * @param id
	 *            the id of the mediafile
	 * @return the file which stores the original content. Its path will look
	 *         like
	 *         {@code "<repo>/.git/lfs/objects/<firstTwoLettersOfID>/<remainingLettersOfID>"}
	 */
	public Path getMediaFile(AnyLongObjectId id) {
		String idStr = id.name();
		return getLfsObjDir().resolve(idStr.substring(0, 2))
				.resolve(idStr.substring(2, 4)).resolve(idStr);
	}

	/**
	 * Create a new temp file in the LFS directory
	 *
	 * @return a new temporary file in the LFS directory
	 * @throws java.io.IOException
	 *             when the temp file could not be created
	 */
	public Path createTmpFile() throws IOException {
		return Files.createTempFile(getLfsTmpDir(), null, null);
	}

}

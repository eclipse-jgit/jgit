/*
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2009, JetBrains s.r.o.
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

package org.eclipse.jgit.lib;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * The configuration file based on the blobs stored in the repository
 */
public class BlobBasedConfig extends Config {
	/**
	 * The constructor from a byte array
	 *
	 * @param base
	 *            the base configuration file
	 * @param blob
	 *            the byte array, should be UTF-8 encoded text.
	 * @throws ConfigInvalidException
	 *             the byte array is not a valid configuration format.
	 */
	public BlobBasedConfig(Config base, final byte[] blob)
			throws ConfigInvalidException {
		super(base);
		fromText(RawParseUtils.decode(blob));
	}

	/**
	 * The constructor from object identifier
	 *
	 * @param base
	 *            the base configuration file
	 * @param r
	 *            the repository
	 * @param objectId
	 *            the object identifier
	 * @throws IOException
	 *             the blob cannot be read from the repository.
	 * @throws ConfigInvalidException
	 *             the blob is not a valid configuration format.
	 */
	public BlobBasedConfig(Config base, final Repository r,
			final ObjectId objectId) throws IOException, ConfigInvalidException {
		super(base);
		final ObjectLoader loader = r.openBlob(objectId);
		if (loader == null)
			throw new IOException("Blob not found: " + objectId);
		fromText(RawParseUtils.decode(loader.getBytes()));
	}

	/**
	 * The constructor from commit and path
	 *
	 * @param base
	 *            the base configuration file
	 * @param commit
	 *            the commit that contains the object
	 * @param path
	 *            the path within the tree of the commit
	 * @throws FileNotFoundException
	 *             the path does not exist in the commit's tree.
	 * @throws IOException
	 *             the tree and/or blob cannot be accessed.
	 * @throws ConfigInvalidException
	 *             the blob is not a valid configuration format.
	 */
	public BlobBasedConfig(Config base, final Commit commit, final String path)
			throws FileNotFoundException, IOException, ConfigInvalidException {
		super(base);
		final ObjectId treeId = commit.getTreeId();
		final Repository r = commit.getRepository();
		final TreeWalk tree = TreeWalk.forPath(r, path, treeId);
		if (tree == null)
			throw new FileNotFoundException("Entry not found by path: " + path);
		final ObjectId blobId = tree.getObjectId(0);
		final ObjectLoader loader = tree.getRepository().openBlob(blobId);
		if (loader == null)
			throw new IOException("Blob not found: " + blobId + " for path: "
					+ path);
		fromText(RawParseUtils.decode(loader.getBytes()));
	}
}

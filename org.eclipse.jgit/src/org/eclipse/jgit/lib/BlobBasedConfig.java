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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Configuration file based on the blobs stored in the repository.
 *
 * This implementation currently only provides reading support, and is primarily
 * useful for supporting the {@code .gitmodules} file.
 */
public class BlobBasedConfig extends Config {
	/**
	 * Parse a configuration from a byte array.
	 *
	 * @param base
	 *            the base configuration file
	 * @param blob
	 *            the byte array, should be UTF-8 encoded text.
	 * @throws org.eclipse.jgit.errors.ConfigInvalidException
	 *             the byte array is not a valid configuration format.
	 */
	public BlobBasedConfig(Config base, byte[] blob)
			throws ConfigInvalidException {
		super(base);
		final String decoded;
		if (isUtf8(blob)) {
			decoded = RawParseUtils.decode(UTF_8, blob, 3, blob.length);
		} else {
			decoded = RawParseUtils.decode(blob);
		}
		fromText(decoded);
	}

	/**
	 * Load a configuration file from a blob.
	 *
	 * @param base
	 *            the base configuration file
	 * @param db
	 *            the repository
	 * @param objectId
	 *            the object identifier
	 * @throws java.io.IOException
	 *             the blob cannot be read from the repository.
	 * @throws org.eclipse.jgit.errors.ConfigInvalidException
	 *             the blob is not a valid configuration format.
	 */
	public BlobBasedConfig(Config base, Repository db, AnyObjectId objectId)
			throws IOException, ConfigInvalidException {
		this(base, read(db, objectId));
	}

	private static byte[] read(Repository db, AnyObjectId blobId)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		try (ObjectReader or = db.newObjectReader()) {
			return read(or, blobId);
		}
	}

	private static byte[] read(ObjectReader or, AnyObjectId blobId)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		ObjectLoader loader = or.open(blobId, Constants.OBJ_BLOB);
		return loader.getCachedBytes(Integer.MAX_VALUE);
	}

	/**
	 * Load a configuration file from a blob stored in a specific commit.
	 *
	 * @param base
	 *            the base configuration file
	 * @param db
	 *            the repository containing the objects.
	 * @param treeish
	 *            the tree (or commit) that contains the object
	 * @param path
	 *            the path within the tree
	 * @throws java.io.FileNotFoundException
	 *             the path does not exist in the commit's tree.
	 * @throws java.io.IOException
	 *             the tree and/or blob cannot be accessed.
	 * @throws org.eclipse.jgit.errors.ConfigInvalidException
	 *             the blob is not a valid configuration format.
	 */
	public BlobBasedConfig(Config base, Repository db, AnyObjectId treeish,
			String path) throws FileNotFoundException, IOException,
			ConfigInvalidException {
		this(base, read(db, treeish, path));
	}

	private static byte[] read(Repository db, AnyObjectId treeish, String path)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		try (ObjectReader or = db.newObjectReader()) {
			TreeWalk tree = TreeWalk.forPath(or, path, asTree(or, treeish));
			if (tree == null)
				throw new FileNotFoundException(MessageFormat.format(JGitText
						.get().entryNotFoundByPath, path));
			return read(or, tree.getObjectId(0));
		}
	}

	private static AnyObjectId asTree(ObjectReader or, AnyObjectId treeish)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		if (treeish instanceof RevTree)
			return treeish;

		if (treeish instanceof RevCommit
				&& ((RevCommit) treeish).getTree() != null)
			return ((RevCommit) treeish).getTree();

		try (RevWalk rw = new RevWalk(or)) {
			return rw.parseTree(treeish).getId();
		}
	}
}

/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.lib;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A class for writing loose objects.
 *
 * @deprecated Use {@link Repository#newObjectInserter()}.
 */
public class ObjectWriter {
	private final ObjectInserter inserter;

	/**
	 * Construct an Object writer for the specified repository
	 *
	 * @param d
	 */
	public ObjectWriter(final Repository d) {
		inserter = d.newObjectInserter();
	}

	/**
	 * Write a blob with the specified data
	 *
	 * @param b
	 *            bytes of the blob
	 * @return SHA-1 of the blob
	 * @throws IOException
	 */
	public ObjectId writeBlob(final byte[] b) throws IOException {
		try {
			ObjectId id = inserter.insert(OBJ_BLOB, b);
			inserter.flush();
			return id;
		} finally {
			inserter.release();
		}
	}

	/**
	 * Write a blob with the data in the specified file
	 *
	 * @param f
	 *            a file containing blob data
	 * @return SHA-1 of the blob
	 * @throws IOException
	 */
	public ObjectId writeBlob(final File f) throws IOException {
		final FileInputStream is = new FileInputStream(f);
		try {
			return writeBlob(f.length(), is);
		} finally {
			is.close();
		}
	}

	/**
	 * Write a blob with data from a stream
	 *
	 * @param len
	 *            number of bytes to consume from the stream
	 * @param is
	 *            stream with blob data
	 * @return SHA-1 of the blob
	 * @throws IOException
	 */
	public ObjectId writeBlob(final long len, final InputStream is)
			throws IOException {
		try {
			ObjectId id = inserter.insert(OBJ_BLOB, len, is);
			inserter.flush();
			return id;
		} finally {
			inserter.release();
		}
	}

	/**
	 * Write a Tree to the object database.
	 *
	 * @param tree
	 *            Tree
	 * @return SHA-1 of the tree
	 * @throws IOException
	 */
	public ObjectId writeTree(Tree tree) throws IOException {
		try {
			ObjectId id = inserter.insert(OBJ_TREE, inserter.format(tree));
			inserter.flush();
			return id;
		} finally {
			inserter.release();
		}
	}

	/**
	 * Write a canonical tree to the object database.
	 *
	 * @param treeData
	 *            the canonical encoding of the tree object.
	 * @return SHA-1 of the tree
	 * @throws IOException
	 */
	public ObjectId writeCanonicalTree(byte[] treeData) throws IOException {
		try {
			ObjectId id = inserter.insert(OBJ_TREE, treeData);
			inserter.flush();
			return id;
		} finally {
			inserter.release();
		}
	}

	/**
	 * Write a Commit to the object database
	 *
	 * @param commit
	 *            Commit to store
	 * @return SHA-1 of the commit
	 * @throws IOException
	 */
	public ObjectId writeCommit(CommitBuilder commit) throws IOException {
		try {
			ObjectId id = inserter.insert(commit);
			inserter.flush();
			return id;
		} finally {
			inserter.release();
		}
	}

	/**
	 * Write an annotated Tag to the object database
	 *
	 * @param tag
	 *            Tag
	 * @return SHA-1 of the tag
	 * @throws IOException
	 */
	public ObjectId writeTag(TagBuilder tag) throws IOException {
		try {
			ObjectId id = inserter.insert(tag);
			inserter.flush();
			return id;
		} finally {
			inserter.release();
		}
	}

	/**
	 * Compute the SHA-1 of a blob without creating an object. This is for
	 * figuring out if we already have a blob or not.
	 *
	 * @param len
	 *            number of bytes to consume
	 * @param is
	 *            stream for read blob data from
	 * @return SHA-1 of a looked for blob
	 * @throws IOException
	 */
	public ObjectId computeBlobSha1(long len, InputStream is)
			throws IOException {
		return computeObjectSha1(OBJ_BLOB, len, is);
	}

	/**
	 * Compute the SHA-1 of an object without actually creating an object in the
	 * database
	 *
	 * @param type
	 *            kind of object
	 * @param len
	 *            number of bytes to consume
	 * @param is
	 *            stream for read data from
	 * @return SHA-1 of data combined with type information
	 * @throws IOException
	 */
	public ObjectId computeObjectSha1(int type, long len, InputStream is)
			throws IOException {
		try {
			return inserter.idFor(type, len, is);
		} finally {
			inserter.release();
		}
	}
}

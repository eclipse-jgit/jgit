/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.eclipse.jgit.errors.ObjectWritingException;

/**
 * A class for writing loose objects.
 */
public class ObjectWriter {
	private static final byte[] htree = Constants.encodeASCII("tree");

	private static final byte[] hparent = Constants.encodeASCII("parent");

	private static final byte[] hauthor = Constants.encodeASCII("author");

	private static final byte[] hcommitter = Constants.encodeASCII("committer");

	private static final byte[] hencoding = Constants.encodeASCII("encoding");

	private final Repository r;

	private final byte[] buf;

	private final MessageDigest md;

	/**
	 * Construct an Object writer for the specified repository
	 * @param d
	 */
	public ObjectWriter(final Repository d) {
		r = d;
		buf = new byte[8192];
		md = Constants.newMessageDigest();
	}

	/**
	 * Write a blob with the specified data
	 *
	 * @param b bytes of the blob
	 * @return SHA-1 of the blob
	 * @throws IOException
	 */
	public ObjectId writeBlob(final byte[] b) throws IOException {
		return writeBlob(b.length, new ByteArrayInputStream(b));
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
		return writeObject(Constants.OBJ_BLOB, len, is, true);
	}

	/**
	 * Write a Tree to the object database.
	 *
	 * @param t
	 *            Tree
	 * @return SHA-1 of the tree
	 * @throws IOException
	 */
	public ObjectId writeTree(final Tree t) throws IOException {
		final ByteArrayOutputStream o = new ByteArrayOutputStream();
		final TreeEntry[] items = t.members();
		for (int k = 0; k < items.length; k++) {
			final TreeEntry e = items[k];
			final ObjectId id = e.getId();

			if (id == null)
				throw new ObjectWritingException("Object at path \""
						+ e.getFullName() + "\" does not have an id assigned."
						+ "  All object ids must be assigned prior"
						+ " to writing a tree.");

			e.getMode().copyTo(o);
			o.write(' ');
			o.write(e.getNameUTF8());
			o.write(0);
			id.copyRawTo(o);
		}
		return writeCanonicalTree(o.toByteArray());
	}

	/**
	 * Write a canonical tree to the object database.
	 *
	 * @param b
	 *            the canonical encoding of the tree object.
	 * @return SHA-1 of the tree
	 * @throws IOException
	 */
	public ObjectId writeCanonicalTree(final byte[] b) throws IOException {
		return writeTree(b.length, new ByteArrayInputStream(b));
	}

	private ObjectId writeTree(final long len, final InputStream is)
			throws IOException {
		return writeObject(Constants.OBJ_TREE, len, is, true);
	}

	/**
	 * Write a Commit to the object database
	 *
	 * @param c
	 *            Commit to store
	 * @return SHA-1 of the commit
	 * @throws IOException
	 */
	public ObjectId writeCommit(final Commit c) throws IOException {
		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		String encoding = c.getEncoding();
		if (encoding == null)
			encoding = Constants.CHARACTER_ENCODING;
		final OutputStreamWriter w = new OutputStreamWriter(os, encoding);

		os.write(htree);
		os.write(' ');
		c.getTreeId().copyTo(os);
		os.write('\n');

		ObjectId[] ps = c.getParentIds();
		for (int i=0; i<ps.length; ++i) {
			os.write(hparent);
			os.write(' ');
			ps[i].copyTo(os);
			os.write('\n');
		}

		os.write(hauthor);
		os.write(' ');
		w.write(c.getAuthor().toExternalString());
		w.flush();
		os.write('\n');

		os.write(hcommitter);
		os.write(' ');
		w.write(c.getCommitter().toExternalString());
		w.flush();
		os.write('\n');

		if (!encoding.equals(Constants.CHARACTER_ENCODING)) {
			os.write(hencoding);
			os.write(' ');
			os.write(Constants.encodeASCII(encoding));
			os.write('\n');
		}

		os.write('\n');
		w.write(c.getMessage());
		w.flush();

		return writeCommit(os.toByteArray());
	}

	private ObjectId writeTag(final byte[] b) throws IOException {
		return writeTag(b.length, new ByteArrayInputStream(b));
	}

	/**
	 * Write an annotated Tag to the object database
	 *
	 * @param c
	 *            Tag
	 * @return SHA-1 of the tag
	 * @throws IOException
	 */
	public ObjectId writeTag(final Tag c) throws IOException {
		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		final OutputStreamWriter w = new OutputStreamWriter(os,
				Constants.CHARSET);

		w.write("object ");
		c.getObjId().copyTo(w);
		w.write('\n');

		w.write("type ");
		w.write(c.getType());
		w.write("\n");

		w.write("tag ");
		w.write(c.getTag());
		w.write("\n");

		w.write("tagger ");
		w.write(c.getAuthor().toExternalString());
		w.write('\n');

		w.write('\n');
		w.write(c.getMessage());
		w.close();

		return writeTag(os.toByteArray());
	}

	private ObjectId writeCommit(final byte[] b) throws IOException {
		return writeCommit(b.length, new ByteArrayInputStream(b));
	}

	private ObjectId writeCommit(final long len, final InputStream is)
			throws IOException {
		return writeObject(Constants.OBJ_COMMIT, len, is, true);
	}

	private ObjectId writeTag(final long len, final InputStream is)
		throws IOException {
		return writeObject(Constants.OBJ_TAG, len, is, true);
	}

	/**
	 * Compute the SHA-1 of a blob without creating an object. This is for
	 * figuring out if we already have a blob or not.
	 *
	 * @param len number of bytes to consume
	 * @param is stream for read blob data from
	 * @return SHA-1 of a looked for blob
	 * @throws IOException
	 */
	public ObjectId computeBlobSha1(final long len, final InputStream is)
			throws IOException {
		return writeObject(Constants.OBJ_BLOB, len, is, false);
	}

	ObjectId writeObject(final int type, long len, final InputStream is,
			boolean store) throws IOException {
		final File t;
		final DeflaterOutputStream deflateStream;
		final FileOutputStream fileStream;
		ObjectId id = null;
		Deflater def = null;

		if (store) {
			t = File.createTempFile("noz", null, r.getObjectsDirectory());
			fileStream = new FileOutputStream(t);
		} else {
			t = null;
			fileStream = null;
		}

		md.reset();
		if (store) {
			def = new Deflater(r.getConfig().getCore().getCompression());
			deflateStream = new DeflaterOutputStream(fileStream, def);
		} else
			deflateStream = null;

		try {
			byte[] header;
			int n;

			header = Constants.encodedTypeString(type);
			md.update(header);
			if (deflateStream != null)
				deflateStream.write(header);

			md.update((byte) ' ');
			if (deflateStream != null)
				deflateStream.write((byte) ' ');

			header = Constants.encodeASCII(len);
			md.update(header);
			if (deflateStream != null)
				deflateStream.write(header);

			md.update((byte) 0);
			if (deflateStream != null)
				deflateStream.write((byte) 0);

			while (len > 0
					&& (n = is.read(buf, 0, (int) Math.min(len, buf.length))) > 0) {
				md.update(buf, 0, n);
				if (deflateStream != null)
					deflateStream.write(buf, 0, n);
				len -= n;
			}

			if (len != 0)
				throw new IOException("Input did not match supplied length. "
						+ len + " bytes are missing.");

			if (deflateStream != null ) {
				deflateStream.close();
				if (t != null)
					t.setReadOnly();
			}

			id = ObjectId.fromRaw(md.digest());
		} finally {
			if (id == null && deflateStream != null) {
				try {
					deflateStream.close();
				} finally {
					t.delete();
				}
			}
			if (def != null) {
				def.end();
			}
		}

		if (t == null)
			return id;

		if (r.hasObject(id)) {
			// Object is already in the repository so remove
			// the temporary file.
			//
			t.delete();
		} else {
			final File o = r.toFile(id);
			if (!t.renameTo(o)) {
				// Maybe the directory doesn't exist yet as the object
				// directories are always lazily created. Note that we
				// try the rename first as the directory likely does exist.
				//
				o.getParentFile().mkdir();
				if (!t.renameTo(o)) {
					if (!r.hasObject(id)) {
						// The object failed to be renamed into its proper
						// location and it doesn't exist in the repository
						// either. We really don't know what went wrong, so
						// fail.
						//
						t.delete();
						throw new ObjectWritingException("Unable to"
								+ " create new object: " + o);
					}
				}
			}
		}

		return id;
	}
}

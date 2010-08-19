/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
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
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.text.MessageFormat;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.errors.ObjectWritingException;

/**
 * Inserts objects into an existing {@code ObjectDatabase}.
 * <p>
 * An inserter is not thread-safe. Individual threads should each obtain their
 * own unique inserter instance, or must arrange for locking at a higher level
 * to ensure the inserter is in use by no more than one thread at a time.
 * <p>
 * Objects written by an inserter may not be immediately visible for reading
 * after the insert method completes. Callers must invoke either
 * {@link #release()} or {@link #flush()} prior to updating references or
 * otherwise making the returned ObjectIds visible to other code.
 */
public abstract class ObjectInserter {
	private static final byte[] htree = Constants.encodeASCII("tree");

	private static final byte[] hparent = Constants.encodeASCII("parent");

	private static final byte[] hauthor = Constants.encodeASCII("author");

	private static final byte[] hcommitter = Constants.encodeASCII("committer");

	private static final byte[] hencoding = Constants.encodeASCII("encoding");

	/** An inserter that can be used for formatting and id generation only. */
	public static class Formatter extends ObjectInserter {
		@Override
		public ObjectId insert(int objectType, long length, InputStream in)
				throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void flush() throws IOException {
			// Do nothing.
		}

		@Override
		public void release() {
			// Do nothing.
		}
	}

	/** Digest to compute the name of an object. */
	private final MessageDigest digest;

	/** Temporary working buffer for streaming data through. */
	private byte[] tempBuffer;

	/** Create a new inserter for a database. */
	protected ObjectInserter() {
		digest = Constants.newMessageDigest();
	}

	/** @return a temporary byte array for use by the caller. */
	protected byte[] buffer() {
		if (tempBuffer == null)
			tempBuffer = new byte[8192];
		return tempBuffer;
	}

	/** @return digest to help compute an ObjectId */
	protected MessageDigest digest() {
		digest.reset();
		return digest;
	}

	/**
	 * Compute the name of an object, without inserting it.
	 *
	 * @param type
	 *            type code of the object to store.
	 * @param data
	 *            complete content of the object.
	 * @return the name of the object.
	 */
	public ObjectId idFor(int type, byte[] data) {
		return idFor(type, data, 0, data.length);
	}

	/**
	 * Compute the name of an object, without inserting it.
	 *
	 * @param type
	 *            type code of the object to store.
	 * @param data
	 *            complete content of the object.
	 * @param off
	 *            first position within {@code data}.
	 * @param len
	 *            number of bytes to copy from {@code data}.
	 * @return the name of the object.
	 */
	public ObjectId idFor(int type, byte[] data, int off, int len) {
		MessageDigest md = digest();
		md.update(Constants.encodedTypeString(type));
		md.update((byte) ' ');
		md.update(Constants.encodeASCII(len));
		md.update((byte) 0);
		md.update(data, off, len);
		return ObjectId.fromRaw(md.digest());
	}

	/**
	 * Compute the name of an object, without inserting it.
	 *
	 * @param objectType
	 *            type code of the object to store.
	 * @param length
	 *            number of bytes to scan from {@code in}.
	 * @param in
	 *            stream providing the object content. The caller is responsible
	 *            for closing the stream.
	 * @return the name of the object.
	 * @throws IOException
	 *             the source stream could not be read.
	 */
	public ObjectId idFor(int objectType, long length, InputStream in)
			throws IOException {
		MessageDigest md = digest();
		md.update(Constants.encodedTypeString(objectType));
		md.update((byte) ' ');
		md.update(Constants.encodeASCII(length));
		md.update((byte) 0);
		byte[] buf = buffer();
		while (length > 0) {
			int n = in.read(buf, 0, (int) Math.min(length, buf.length));
			if (n < 0)
				throw new EOFException("Unexpected end of input");
			md.update(buf, 0, n);
			length -= n;
		}
		return ObjectId.fromRaw(md.digest());
	}

	/**
	 * Insert a single object into the store, returning its unique name.
	 *
	 * @param type
	 *            type code of the object to store.
	 * @param data
	 *            complete content of the object.
	 * @return the name of the object.
	 * @throws IOException
	 *             the object could not be stored.
	 */
	public ObjectId insert(final int type, final byte[] data)
			throws IOException {
		return insert(type, data, 0, data.length);
	}

	/**
	 * Insert a single object into the store, returning its unique name.
	 *
	 * @param type
	 *            type code of the object to store.
	 * @param data
	 *            complete content of the object.
	 * @param off
	 *            first position within {@code data}.
	 * @param len
	 *            number of bytes to copy from {@code data}.
	 * @return the name of the object.
	 * @throws IOException
	 *             the object could not be stored.
	 */
	public ObjectId insert(int type, byte[] data, int off, int len)
			throws IOException {
		return insert(type, len, new ByteArrayInputStream(data, off, len));
	}

	/**
	 * Insert a single object into the store, returning its unique name.
	 *
	 * @param objectType
	 *            type code of the object to store.
	 * @param length
	 *            number of bytes to copy from {@code in}.
	 * @param in
	 *            stream providing the object content. The caller is responsible
	 *            for closing the stream.
	 * @return the name of the object.
	 * @throws IOException
	 *             the object could not be stored, or the source stream could
	 *             not be read.
	 */
	public abstract ObjectId insert(int objectType, long length, InputStream in)
			throws IOException;

	/**
	 * Make all inserted objects visible.
	 * <p>
	 * The flush may take some period of time to make the objects available to
	 * other threads.
	 *
	 * @throws IOException
	 *             the flush could not be completed; objects inserted thus far
	 *             are in an indeterminate state.
	 */
	public abstract void flush() throws IOException;

	/**
	 * Release any resources used by this inserter.
	 * <p>
	 * An inserter that has been released can be used again, but may need to be
	 * released after the subsequent usage.
	 */
	public abstract void release();

	/**
	 * Format a Tree in canonical format.
	 *
	 * @param tree
	 *            the tree object to format
	 * @return canonical encoding of the tree object.
	 * @throws IOException
	 *             the tree cannot be loaded, or its not in a writable state.
	 */
	public final byte[] format(Tree tree) throws IOException {
		ByteArrayOutputStream o = new ByteArrayOutputStream();
		for (TreeEntry e : tree.members()) {
			ObjectId id = e.getId();
			if (id == null)
				throw new ObjectWritingException(MessageFormat.format(JGitText
						.get().objectAtPathDoesNotHaveId, e.getFullName()));

			e.getMode().copyTo(o);
			o.write(' ');
			o.write(e.getNameUTF8());
			o.write(0);
			id.copyRawTo(o);
		}
		return o.toByteArray();
	}

	/**
	 * Format a Commit in canonical format.
	 *
	 * @param commit
	 *            the commit object to format
	 * @return canonical encoding of the commit object.
	 * @throws UnsupportedEncodingException
	 *             the commit's chosen encoding isn't supported on this JVM.
	 */
	public final byte[] format(Commit commit)
			throws UnsupportedEncodingException {
		String encoding = commit.getEncoding();
		if (encoding == null)
			encoding = Constants.CHARACTER_ENCODING;

		ByteArrayOutputStream os = new ByteArrayOutputStream();
		OutputStreamWriter w = new OutputStreamWriter(os, encoding);
		try {
			os.write(htree);
			os.write(' ');
			commit.getTreeId().copyTo(os);
			os.write('\n');

			ObjectId[] ps = commit.getParentIds();
			for (int i = 0; i < ps.length; ++i) {
				os.write(hparent);
				os.write(' ');
				ps[i].copyTo(os);
				os.write('\n');
			}

			os.write(hauthor);
			os.write(' ');
			w.write(commit.getAuthor().toExternalString());
			w.flush();
			os.write('\n');

			os.write(hcommitter);
			os.write(' ');
			w.write(commit.getCommitter().toExternalString());
			w.flush();
			os.write('\n');

			if (!encoding.equals(Constants.CHARACTER_ENCODING)) {
				os.write(hencoding);
				os.write(' ');
				os.write(Constants.encodeASCII(encoding));
				os.write('\n');
			}

			os.write('\n');
			w.write(commit.getMessage());
			w.flush();
		} catch (IOException err) {
			// This should never occur, the only way to get it above is
			// for the ByteArrayOutputStream to throw, but it doesn't.
			//
			throw new RuntimeException(err);
		}
		return os.toByteArray();
	}

	/**
	 * Format a Tag in canonical format.
	 *
	 * @param tag
	 *            the tag object to format
	 * @return canonical encoding of the tag object.
	 */
	public final byte[] format(Tag tag) {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		OutputStreamWriter w = new OutputStreamWriter(os, Constants.CHARSET);
		try {
			w.write("object ");
			tag.getObjId().copyTo(w);
			w.write('\n');

			w.write("type ");
			w.write(tag.getType());
			w.write("\n");

			w.write("tag ");
			w.write(tag.getTag());
			w.write("\n");

			w.write("tagger ");
			if(tag.getTagger() != null)
				w.write(tag.getTagger().toExternalString());
			w.write('\n');

			w.write('\n');
			if(tag.getMessage() != null)
				w.write(tag.getMessage());
			w.close();
		} catch (IOException err) {
			// This should never occur, the only way to get it above is
			// for the ByteArrayOutputStream to throw, but it doesn't.
			//
			throw new RuntimeException(err);
		}
		return os.toByteArray();
	}
}

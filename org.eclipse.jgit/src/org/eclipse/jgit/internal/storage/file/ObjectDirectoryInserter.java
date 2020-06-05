/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.text.MessageFormat;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.eclipse.jgit.errors.ObjectWritingException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.sha1.SHA1;

/** Creates loose objects in a {@link ObjectDirectory}. */
class ObjectDirectoryInserter extends ObjectInserter {
	private final FileObjectDatabase db;

	private final WriteConfig config;

	private Deflater deflate;

	ObjectDirectoryInserter(FileObjectDatabase dest, Config cfg) {
		db = dest;
		config = cfg.get(WriteConfig.KEY);
	}

	/** {@inheritDoc} */
	@Override
	public ObjectId insert(int type, byte[] data, int off, int len)
			throws IOException {
		return insert(type, data, off, len, false);
	}

	/**
	 * Insert a loose object into the database. If createDuplicate is true,
	 * write the loose object even if we already have it in the loose or packed
	 * ODB.
	 *
	 * @param type
	 * @param data
	 * @param off
	 * @param len
	 * @param createDuplicate
	 * @return ObjectId
	 * @throws IOException
	 */
	private ObjectId insert(
			int type, byte[] data, int off, int len, boolean createDuplicate)
			throws IOException {
		ObjectId id = idFor(type, data, off, len);
		if (!createDuplicate && db.has(id)) {
			return id;
		}
		File tmp = toTemp(type, data, off, len);
		return insertOneObject(tmp, id, createDuplicate);
	}

	/** {@inheritDoc} */
	@Override
	public ObjectId insert(int type, long len, InputStream is)
			throws IOException {
		return insert(type, len, is, false);
	}

	/**
	 * Insert a loose object into the database. If createDuplicate is true,
	 * write the loose object even if we already have it in the loose or packed
	 * ODB.
	 *
	 * @param type
	 * @param len
	 * @param is
	 * @param createDuplicate
	 * @return ObjectId
	 * @throws IOException
	 */
	ObjectId insert(int type, long len, InputStream is, boolean createDuplicate)
			throws IOException {
		if (len <= buffer().length) {
			byte[] buf = buffer();
			int actLen = IO.readFully(is, buf, 0);
			return insert(type, buf, 0, actLen, createDuplicate);

		}
		SHA1 md = digest();
		File tmp = toTemp(md, type, len, is);
		ObjectId id = md.toObjectId();
		return insertOneObject(tmp, id, createDuplicate);
	}

	private ObjectId insertOneObject(
			File tmp, ObjectId id, boolean createDuplicate)
			throws IOException {
		switch (db.insertUnpackedObject(tmp, id, createDuplicate)) {
		case INSERTED:
		case EXISTS_PACKED:
		case EXISTS_LOOSE:
			return id;

		case FAILURE:
		default:
			break;
		}

		final File dst = db.fileFor(id);
		throw new ObjectWritingException(MessageFormat
				.format(JGitText.get().unableToCreateNewObject, dst));
	}

	/** {@inheritDoc} */
	@Override
	public PackParser newPackParser(InputStream in) throws IOException {
		return new ObjectDirectoryPackParser(db, in);
	}

	/** {@inheritDoc} */
	@Override
	public ObjectReader newReader() {
		return new WindowCursor(db, this);
	}

	/** {@inheritDoc} */
	@Override
	public void flush() throws IOException {
		// Do nothing. Loose objects are immediately visible.
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		if (deflate != null) {
			try {
				deflate.end();
			} finally {
				deflate = null;
			}
		}
	}

	@SuppressWarnings("resource" /* java 7 */)
	private File toTemp(final SHA1 md, final int type, long len,
			final InputStream is) throws IOException {
		boolean delete = true;
		File tmp = newTempFile();
		try {
			FileOutputStream fOut = new FileOutputStream(tmp);
			try {
				OutputStream out = fOut;
				if (config.getFSyncObjectFiles())
					out = Channels.newOutputStream(fOut.getChannel());
				DeflaterOutputStream cOut = compress(out);
				SHA1OutputStream dOut = new SHA1OutputStream(cOut, md);
				writeHeader(dOut, type, len);

				final byte[] buf = buffer();
				while (len > 0) {
					int n = is.read(buf, 0, (int) Math.min(len, buf.length));
					if (n <= 0)
						throw shortInput(len);
					dOut.write(buf, 0, n);
					len -= n;
				}
				dOut.flush();
				cOut.finish();
			} finally {
				if (config.getFSyncObjectFiles())
					fOut.getChannel().force(true);
				fOut.close();
			}

			delete = false;
			return tmp;
		} finally {
			if (delete)
				FileUtils.delete(tmp, FileUtils.RETRY);
		}
	}

	@SuppressWarnings("resource" /* java 7 */)
	private File toTemp(final int type, final byte[] buf, final int pos,
			final int len) throws IOException {
		boolean delete = true;
		File tmp = newTempFile();
		try {
			FileOutputStream fOut = new FileOutputStream(tmp);
			try {
				OutputStream out = fOut;
				if (config.getFSyncObjectFiles())
					out = Channels.newOutputStream(fOut.getChannel());
				DeflaterOutputStream cOut = compress(out);
				writeHeader(cOut, type, len);
				cOut.write(buf, pos, len);
				cOut.finish();
			} finally {
				if (config.getFSyncObjectFiles())
					fOut.getChannel().force(true);
				fOut.close();
			}

			delete = false;
			return tmp;
		} finally {
			if (delete)
				FileUtils.delete(tmp, FileUtils.RETRY);
		}
	}

	void writeHeader(OutputStream out, int type, long len)
			throws IOException {
		out.write(Constants.encodedTypeString(type));
		out.write((byte) ' ');
		out.write(Constants.encodeASCII(len));
		out.write((byte) 0);
	}

	File newTempFile() throws IOException {
		return File.createTempFile("noz", null, db.getDirectory()); //$NON-NLS-1$
	}

	DeflaterOutputStream compress(OutputStream out) {
		if (deflate == null)
			deflate = new Deflater(config.getCompression());
		else
			deflate.reset();
		return new DeflaterOutputStream(out, deflate, 8192);
	}

	private static EOFException shortInput(long missing) {
		return new EOFException(MessageFormat.format(
				JGitText.get().inputDidntMatchLength, Long.valueOf(missing)));
	}

	private static class SHA1OutputStream extends FilterOutputStream {
		private final SHA1 md;

		SHA1OutputStream(OutputStream out, SHA1 md) {
			super(out);
			this.md = md;
		}

		@Override
		public void write(int b) throws IOException {
			md.update((byte) b);
			out.write(b);
		}

		@Override
		public void write(byte[] in, int p, int n) throws IOException {
			md.update(in, p, n);
			out.write(in, p, n);
		}
	}
}

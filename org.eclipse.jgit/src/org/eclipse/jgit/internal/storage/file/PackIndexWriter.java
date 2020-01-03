/*
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.text.MessageFormat;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.NB;

/**
 * Creates a table of contents to support random access by
 * {@link org.eclipse.jgit.internal.storage.file.PackFile}.
 * <p>
 * Pack index files (the <code>.idx</code> suffix in a pack file pair) provides
 * random access to any object in the pack by associating an ObjectId to the
 * byte offset within the pack where the object's data can be read.
 */
public abstract class PackIndexWriter {
	/** Magic constant indicating post-version 1 format. */
	protected static final byte[] TOC = { -1, 't', 'O', 'c' };

	/**
	 * Create a new writer for the oldest (most widely understood) format.
	 * <p>
	 * This method selects an index format that can accurate describe the
	 * supplied objects and that will be the most compatible format with older
	 * Git implementations.
	 * <p>
	 * Index version 1 is widely recognized by all Git implementations, but
	 * index version 2 (and later) is not as well recognized as it was
	 * introduced more than a year later. Index version 1 can only be used if
	 * the resulting pack file is under 4 gigabytes in size; packs larger than
	 * that limit must use index version 2.
	 *
	 * @param dst
	 *            the stream the index data will be written to. If not already
	 *            buffered it will be automatically wrapped in a buffered
	 *            stream. Callers are always responsible for closing the stream.
	 * @param objs
	 *            the objects the caller needs to store in the index. Entries
	 *            will be examined until a format can be conclusively selected.
	 * @return a new writer to output an index file of the requested format to
	 *         the supplied stream.
	 * @throws java.lang.IllegalArgumentException
	 *             no recognized pack index version can support the supplied
	 *             objects. This is likely a bug in the implementation.
	 * @see #oldestPossibleFormat(List)
	 */
	public static PackIndexWriter createOldestPossible(final OutputStream dst,
			final List<? extends PackedObjectInfo> objs) {
		return createVersion(dst, oldestPossibleFormat(objs));
	}

	/**
	 * Return the oldest (most widely understood) index format.
	 * <p>
	 * This method selects an index format that can accurate describe the
	 * supplied objects and that will be the most compatible format with older
	 * Git implementations.
	 * <p>
	 * Index version 1 is widely recognized by all Git implementations, but
	 * index version 2 (and later) is not as well recognized as it was
	 * introduced more than a year later. Index version 1 can only be used if
	 * the resulting pack file is under 4 gigabytes in size; packs larger than
	 * that limit must use index version 2.
	 *
	 * @param objs
	 *            the objects the caller needs to store in the index. Entries
	 *            will be examined until a format can be conclusively selected.
	 * @return the index format.
	 * @throws java.lang.IllegalArgumentException
	 *             no recognized pack index version can support the supplied
	 *             objects. This is likely a bug in the implementation.
	 */
	public static int oldestPossibleFormat(
			final List<? extends PackedObjectInfo> objs) {
		for (PackedObjectInfo oe : objs) {
			if (!PackIndexWriterV1.canStore(oe))
				return 2;
		}
		return 1;
	}


	/**
	 * Create a new writer instance for a specific index format version.
	 *
	 * @param dst
	 *            the stream the index data will be written to. If not already
	 *            buffered it will be automatically wrapped in a buffered
	 *            stream. Callers are always responsible for closing the stream.
	 * @param version
	 *            index format version number required by the caller. Exactly
	 *            this formatted version will be written.
	 * @return a new writer to output an index file of the requested format to
	 *         the supplied stream.
	 * @throws java.lang.IllegalArgumentException
	 *             the version requested is not supported by this
	 *             implementation.
	 */
	public static PackIndexWriter createVersion(final OutputStream dst,
			final int version) {
		switch (version) {
		case 1:
			return new PackIndexWriterV1(dst);
		case 2:
			return new PackIndexWriterV2(dst);
		default:
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().unsupportedPackIndexVersion,
					Integer.valueOf(version)));
		}
	}

	/** The index data stream we are responsible for creating. */
	protected final DigestOutputStream out;

	/** A temporary buffer for use during IO to {link #out}. */
	protected final byte[] tmp;

	/** The entries this writer must pack. */
	protected List<? extends PackedObjectInfo> entries;

	/** SHA-1 checksum for the entire pack data. */
	protected byte[] packChecksum;

	/**
	 * Create a new writer instance.
	 *
	 * @param dst
	 *            the stream this instance outputs to. If not already buffered
	 *            it will be automatically wrapped in a buffered stream.
	 */
	protected PackIndexWriter(OutputStream dst) {
		out = new DigestOutputStream(dst instanceof BufferedOutputStream ? dst
				: new BufferedOutputStream(dst),
				Constants.newMessageDigest());
		tmp = new byte[4 + Constants.OBJECT_ID_LENGTH];
	}

	/**
	 * Write all object entries to the index stream.
	 * <p>
	 * After writing the stream passed to the factory is flushed but remains
	 * open. Callers are always responsible for closing the output stream.
	 *
	 * @param toStore
	 *            sorted list of objects to store in the index. The caller must
	 *            have previously sorted the list using
	 *            {@link org.eclipse.jgit.transport.PackedObjectInfo}'s native
	 *            {@link java.lang.Comparable} implementation.
	 * @param packDataChecksum
	 *            checksum signature of the entire pack data content. This is
	 *            traditionally the last 20 bytes of the pack file's own stream.
	 * @throws java.io.IOException
	 *             an error occurred while writing to the output stream, or this
	 *             index format cannot store the object data supplied.
	 */
	public void write(final List<? extends PackedObjectInfo> toStore,
			final byte[] packDataChecksum) throws IOException {
		entries = toStore;
		packChecksum = packDataChecksum;
		writeImpl();
		out.flush();
	}

	/**
	 * Writes the index file to {@link #out}.
	 * <p>
	 * Implementations should go something like:
	 *
	 * <pre>
	 * writeFanOutTable();
	 * for (final PackedObjectInfo po : entries)
	 * 	writeOneEntry(po);
	 * writeChecksumFooter();
	 * </pre>
	 *
	 * <p>
	 * Where the logic for <code>writeOneEntry</code> is specific to the index
	 * format in use. Additional headers/footers may be used if necessary and
	 * the {@link #entries} collection may be iterated over more than once if
	 * necessary. Implementors therefore have complete control over the data.
	 *
	 * @throws java.io.IOException
	 *             an error occurred while writing to the output stream, or this
	 *             index format cannot store the object data supplied.
	 */
	protected abstract void writeImpl() throws IOException;

	/**
	 * Output the version 2 (and later) TOC header, with version number.
	 * <p>
	 * Post version 1 all index files start with a TOC header that makes the
	 * file an invalid version 1 file, and then includes the version number.
	 * This header is necessary to recognize a version 1 from a version 2
	 * formatted index.
	 *
	 * @param version
	 *            version number of this index format being written.
	 * @throws java.io.IOException
	 *             an error occurred while writing to the output stream.
	 */
	protected void writeTOC(int version) throws IOException {
		out.write(TOC);
		NB.encodeInt32(tmp, 0, version);
		out.write(tmp, 0, 4);
	}

	/**
	 * Output the standard 256 entry first-level fan-out table.
	 * <p>
	 * The fan-out table is 4 KB in size, holding 256 32-bit unsigned integer
	 * counts. Each count represents the number of objects within this index
	 * whose {@link org.eclipse.jgit.lib.ObjectId#getFirstByte()} matches the
	 * count's position in the fan-out table.
	 *
	 * @throws java.io.IOException
	 *             an error occurred while writing to the output stream.
	 */
	protected void writeFanOutTable() throws IOException {
		final int[] fanout = new int[256];
		for (PackedObjectInfo po : entries)
			fanout[po.getFirstByte() & 0xff]++;
		for (int i = 1; i < 256; i++)
			fanout[i] += fanout[i - 1];
		for (int n : fanout) {
			NB.encodeInt32(tmp, 0, n);
			out.write(tmp, 0, 4);
		}
	}

	/**
	 * Output the standard two-checksum index footer.
	 * <p>
	 * The standard footer contains two checksums (20 byte SHA-1 values):
	 * <ol>
	 * <li>Pack data checksum - taken from the last 20 bytes of the pack file.</li>
	 * <li>Index data checksum - checksum of all index bytes written, including
	 * the pack data checksum above.</li>
	 * </ol>
	 *
	 * @throws java.io.IOException
	 *             an error occurred while writing to the output stream.
	 */
	protected void writeChecksumFooter() throws IOException {
		out.write(packChecksum);
		out.on(false);
		out.write(out.getMessageDigest().digest());
	}
}

/*
 * Copyright (C) 2023, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.eclipse.jgit.internal.storage.file.PackReverseIndex.VERSION_1;

import java.io.BufferedOutputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.text.MessageFormat;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.PackedObjectInfo;

/**
 * Writes reverse index files conforming to the requested version.
 * <p>
 * The reverse index file format is specified at
 * https://git-scm.com/docs/pack-format#_pack_rev_files_have_the_format.
 */
public abstract class PackReverseIndexWriter {
	/**
	 * Stream to write contents to while maintaining a checksum.
	 */
	protected final DigestOutputStream out;

	/**
	 * Stream to write primitive type contents to while maintaining a checksum.
	 */
	protected final DataOutput dataOutput;

	private static final int DEFAULT_VERSION = VERSION_1;

	/**
	 * Construct the components of a PackReverseIndexWriter that are shared
	 * between subclasses.
	 *
	 * @param dst
	 *            the OutputStream that the instance will write contents to
	 */
	protected PackReverseIndexWriter(OutputStream dst) {
		out = new DigestOutputStream(
				dst instanceof BufferedOutputStream ? dst
						: new BufferedOutputStream(dst),
				Constants.newMessageDigest());
		dataOutput = new SimpleDataOutput(out);
	}

	/**
	 * Create a writer instance for the default file format version.
	 *
	 * @param dst
	 *            the OutputStream that contents will be written to
	 * @return the new writer instance
	 */
	public static PackReverseIndexWriter createWriter(OutputStream dst) {
		return createWriter(dst, DEFAULT_VERSION);
	}

	/**
	 * Create a writer instance for the specified file format version.
	 *
	 * @param dst
	 *            the OutputStream that contents will be written to
	 * @param version
	 *            the reverse index format version to write contents as
	 * @return the new writer instance
	 */
	public static PackReverseIndexWriter createWriter(OutputStream dst,
			int version) {
		if (version == VERSION_1) {
			return new PackReverseIndexWriterV1(dst);
		}
		throw new IllegalArgumentException(MessageFormat.format(
				JGitText.get().unsupportedPackReverseIndexVersion,
				Integer.toString(version)));
	}

	/**
	 * Write the contents of a reverse index file for the given objects.
	 *
	 * @param objectsByIndexPos
	 *            the objects whose forward index file positions should be
	 *            written, sorted by forward index file position (currently SHA1
	 *            ordering)
	 * @param packChecksum
	 *            the checksum of the corresponding pack file
	 * @throws IOException
	 *             if writing the output fails
	 */
	public void write(
			List<? extends PackedObjectInfo> objectsByIndexPos,
			byte[] packChecksum) throws IOException {
		writeHeader();
		writeBody(objectsByIndexPos);
		writeFooter(packChecksum);
		out.flush();
	}

	/**
	 * Write the header of a reverse index file, usually the magic bytes and the
	 * file format version.
	 *
	 * @throws IOException
	 *             if writing the output fails
	 */
	protected abstract void writeHeader() throws IOException;

	/**
	 * Write the body of a reverse index file, usually the forward index
	 * positions of the given objects, sorted by those objects' pack file
	 * offsets.
	 *
	 * @param objectsSortedByIndexPosition
	 *            the objects whose forward index file positions should be
	 *            written, sorted by forward index file position; not modified
	 *            during method
	 * @throws IOException
	 *             if writing the output fails
	 */
	protected abstract void writeBody(
			List<? extends PackedObjectInfo> objectsSortedByIndexPosition)
			throws IOException;

	private void writeFooter(byte[] packChecksum) throws IOException {
		out.write(packChecksum);
		byte[] selfChecksum = out.getMessageDigest().digest();
		out.write(selfChecksum);
	}
}

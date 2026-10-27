/*
 * Copyright (C) 2026, SAP SE and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.Arrays;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.NB;

/**
 * Reads a {@code .mtimes} file for a cruft pack.
 */
class PackCruftMtimesLoader {

	private final InputStream in;

	private final int expectedCount;

	private final AnyObjectId expectedPackChecksum;

	/**
	 * Create a loader for a {@code .mtimes} stream.
	 *
	 * @param in
	 *            stream positioned at the beginning of the {@code .mtimes} data
	 * @param expectedCount
	 *            number of objects in the corresponding pack index; used to
	 *            validate the table length before allocating
	 * @param objectId
	 *            SHA-1 checksum of the pack file; verified against the checksum
	 *            embedded in the {@code .mtimes} trailer
	 */
	PackCruftMtimesLoader(InputStream in, int expectedCount,
			AnyObjectId objectId) {
		this.in = in;
		this.expectedCount = expectedCount;
		this.expectedPackChecksum = objectId;
	}

	/**
	 * Load a {@link PackCruftMtimes} from the stream.
	 *
	 * @return the loaded mtimes index
	 * @throws IOException
	 *             if the stream cannot be read or the content is malformed
	 */
	PackCruftMtimes load() throws IOException {
		MessageDigest md = Constants.newMessageDigest();
		DigestInputStream dis = new DigestInputStream(in, md);

		validateSignature(dis);
		readVersion(dis);
		int checksumLength = validateHashfunction(dis);

		validateTableSize();
		int dataLength = expectedCount * 4;
		byte[] data = readTable(dis, dataLength);

		validatePackChecksum(dis, checksumLength);
		validateChecksum(md, checksumLength);
		validateNoTrailingBytes();

		return new PackCruftMtimesV1(data, expectedCount);
	}

	private void validateSignature(DigestInputStream dis) throws IOException {
		byte[] buf = dis.readNBytes(4);
		if (buf.length < 4 || !Arrays.equals(buf, PackCruftMtimesWriter.MAGIC)) {
			throw new IOException(MessageFormat.format(
					JGitText.get().cruftMtimesBadMagic,
					Arrays.toString(buf)));
		}
	}

	private int readVersion(DigestInputStream dis) throws IOException {
		byte[] buf;
		buf = dis.readNBytes(4);
		if (buf.length < 4) {
			throw new IOException(JGitText.get().cruftMtimesTruncatedVersion);
		}
		int version = NB.decodeInt32(buf, 0);
		if (version != PackCruftMtimesWriter.VERSION_1) {
			throw new IOException(MessageFormat.format(
					JGitText.get().unsupportedPackCruftMtimesVersion,
					Integer.valueOf(version)));
		}
		return version;
	}

	private int validateHashfunction(DigestInputStream dis) throws IOException {
		byte[] buf;
		buf = dis.readNBytes(4);
		if (buf.length < 4) {
			throw new IOException(
					JGitText.get().cruftMtimesTruncatedHashFunctionId);
		}
		int hashId = NB.decodeInt32(buf, 0);
		int checksumLength;
		if (hashId == PackCruftMtimesWriter.HASH_ID_SHA1) {
			checksumLength = Constants.OBJECT_ID_LENGTH;
		} else {
			throw new IOException(MessageFormat.format(
					JGitText.get().unsupportedPackCruftMtimesHashFunctionId,
					Integer.valueOf(hashId)));
		}
		return checksumLength;
	}

	private void validateTableSize() throws IOException {
		if (expectedCount < 0 || expectedCount > (Integer.MAX_VALUE / 4)) {
			throw new IOException(MessageFormat.format(
					JGitText.get().cruftMtimesInvalidObjectCount,
					Integer.valueOf(expectedCount)));
		}
	}

	private byte[] readTable(DigestInputStream dis, int dataLength)
			throws IOException {
		byte[] data = dis.readNBytes(dataLength);
		if (data.length != dataLength) {
			throw new IOException(JGitText.get().cruftMtimesTruncatedTable);
		}
		return data;
	}

	private void validatePackChecksum(DigestInputStream dis, int checksumLength)
			throws IOException {
		byte[] embeddedPackChecksum = dis.readNBytes(checksumLength);
		if (embeddedPackChecksum.length != checksumLength) {
			throw new IOException(
					JGitText.get().cruftMtimesTruncatedPackChecksum);
		}
		ObjectId embeddedId = ObjectId.fromRaw(embeddedPackChecksum);
		if (!expectedPackChecksum.equals(embeddedId)) {
			throw new IOException(MessageFormat.format(
					JGitText.get().cruftMtimesPackChecksumMismatch,
					expectedPackChecksum.name(), embeddedId.name()));
		}
	}

	private void validateChecksum(MessageDigest md, int checksumLength)
			throws IOException {
		byte[] storedSelfChecksum = in.readNBytes(checksumLength);
		if (storedSelfChecksum.length != checksumLength) {
			throw new IOException(
					JGitText.get().cruftMtimesTruncatedSelfChecksum);
		}
		if (!Arrays.equals(md.digest(), storedSelfChecksum)) {
			throw new IOException(JGitText.get().cruftMtimesSelfChecksumMismatch);
		}
	}

	private void validateNoTrailingBytes() throws IOException {
		if (in.read() != -1) {
			throw new IOException(
					JGitText.get().cruftMtimesUnexpectedTrailingBytes);
		}
	}

	private static final class PackCruftMtimesV1 implements PackCruftMtimes {
		private final byte[] data;

		private final int count;

		PackCruftMtimesV1(byte[] data, int count) {
			this.data = data;
			this.count = count;
		}

		@Override
		public long getMtime(int indexPosition) {
			if (indexPosition < 0 || indexPosition >= count) {
				throw new IndexOutOfBoundsException(indexPosition);
			}
			return NB.decodeUInt32(data, indexPosition * 4);
		}

		@Override
		public int getObjectCount() {
			return count;
		}
	}
}

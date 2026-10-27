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
import java.text.MessageFormat;
import java.util.Arrays;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.NB;

/**
 * Reads a {@code .mtimes} file for a cruft pack.
 */
class PackCruftMtimesLoader {

	/**
	 * Load a {@link PackCruftMtimes} from an input stream.
	 *
	 * @param in
	 *            stream positioned at the beginning of the {@code .mtimes} data
	 * @return the loaded mtimes index
	 * @throws IOException
	 *             if the stream cannot be read or the content is malformed
	 */
	static PackCruftMtimes load(InputStream in) throws IOException {
		byte[] magic = in.readNBytes(4);
		if (!Arrays.equals(magic, PackCruftMtimesWriter.MAGIC)) {
			throw new IOException(MessageFormat.format(
					JGitText.get().unreadableCruftMtimes,
					Integer.valueOf(magic.length),
					Arrays.toString(magic)));
		}

		byte[] versionBuf = in.readNBytes(4);
		int version = NB.decodeInt32(versionBuf, 0);
		if (version != PackCruftMtimesWriter.VERSION_1) {
			throw new IOException(MessageFormat.format(
					JGitText.get().unsupportedPackCruftMtimesVersion,
					Integer.valueOf(version)));
		}

		byte[] countBuf = in.readNBytes(4);
		int count = NB.decodeInt32(countBuf, 0);

		byte[] data = in.readNBytes(count * 4);
		if (data.length < count * 4) {
			throw new IOException(MessageFormat.format(
					JGitText.get().unreadableCruftMtimes,
					Integer.valueOf(data.length),
					Arrays.toString(Arrays.copyOf(data, Math.min(data.length, 8)))));
		}

		return new PackCruftMtimesV1(data, count);
	}

	private PackCruftMtimesLoader() {
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

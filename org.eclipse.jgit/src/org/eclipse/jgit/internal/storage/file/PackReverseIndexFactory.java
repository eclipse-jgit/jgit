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

import static org.eclipse.jgit.internal.storage.file.PackReverseIndex.MAGIC;
import static org.eclipse.jgit.internal.storage.file.PackReverseIndex.VERSION_1;

import java.io.DataInput;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.text.MessageFormat;
import java.util.Arrays;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.io.SilentFileInputStream;

/**
 * Factory for creating instances of {@link PackReverseIndex}.
 */
public final class PackReverseIndexFactory {
	/**
	 * Create an in-memory pack reverse index by reading it from the given file
	 * if the file exists, or computing it from the given pack index if the file
	 * doesn't exist.
	 *
	 * @param idxFile
	 *            the file to read the pack file from, if it exists
	 * @param objectCount
	 *            the number of objects in the corresponding pack
	 * @param packIndexSupplier
	 *            a function to lazily get the corresponding forward index
	 * @return the reverse index instance
	 * @throws IOException
	 *             if reading from the file fails
	 */
	static PackReverseIndex openOrCompute(File idxFile, long objectCount,
			PackBitmapIndex.SupplierWithIOException<PackIndex> packIndexSupplier)
			throws IOException {
		try (SilentFileInputStream fd = new SilentFileInputStream(idxFile)) {
			return readFromFile(fd, objectCount, packIndexSupplier);
		} catch (FileNotFoundException e) {
			return computeFromIndex(packIndexSupplier.get());
		} catch (IOException e) {
			throw new IOException(
					MessageFormat.format(JGitText.get().unreadablePackIndex,
							idxFile.getAbsolutePath()),
					e);
		}
	}

	/**
	 * Compute an in-memory pack reverse index from the in-memory pack forward
	 * index. This computation uses insertion sort, which has a quadratic
	 * runtime on average.
	 *
	 * @param packIndex
	 *            the forward index to compute from
	 * @return the reverse index instance
	 */
	public static PackReverseIndex computeFromIndex(PackIndex packIndex) {
		return new PackReverseIndexComputed(packIndex);
	}

	/**
	 * Read an in-memory pack reverse index from the given input stream. This
	 * has a linear runtime.
	 *
	 * @param src
	 *            the input stream to read the contents from
	 * @param objectCount
	 *            the number of objects in the corresponding pack
	 * @param packIndexSupplier
	 *            a function to lazily get the corresponding forward index
	 * @return the reverse index instance
	 * @throws IOException
	 *             if reading from the input stream fails
	 */
	static PackReverseIndex readFromFile(InputStream src, long objectCount,
			PackBitmapIndex.SupplierWithIOException<PackIndex> packIndexSupplier)
			throws IOException {
		final DigestInputStream digestIn = new DigestInputStream(src,
				Constants.newMessageDigest());

		final byte[] magic = new byte[MAGIC.length];
		IO.readFully(digestIn, magic);
		if (!Arrays.equals(magic, MAGIC)) {
			throw new IOException(
					MessageFormat.format(JGitText.get().expectedGot,
							Arrays.toString(MAGIC), Arrays.toString(magic)));
		}

		DataInput dataIn = new SimpleDataInput(digestIn);
		int version = dataIn.readInt();
		switch (version) {
		case VERSION_1:
			return new PackReverseIndexV1(digestIn, objectCount,
					packIndexSupplier);
		default:
			throw new IOException(MessageFormat.format(
					JGitText.get().unsupportedPackReverseIndexVersion,
					String.valueOf(version)));
		}
	}
}

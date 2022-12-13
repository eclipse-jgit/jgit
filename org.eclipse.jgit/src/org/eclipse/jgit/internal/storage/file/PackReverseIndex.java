/*
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.DataInput;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.text.MessageFormat;
import java.util.Arrays;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.PackMismatchException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.io.SilentFileInputStream;

/**
 * <p>
 * Reverse index for forward pack index. Provides operations based on offset
 * instead of object id. Such offset-based reverse lookups are performed in
 * O(log n) time.
 * </p>
 *
 * @see PackIndex
 * @see Pack
 */
public abstract class PackReverseIndex {
	/**
	 * Magic bytes that uniquely identify git reverse index files.
	 */
	public static byte[] MAGIC = { 'R', 'I', 'D', 'X' };
	/**
	 * The version number of the first reverse index file version.
	 */
	public static final int VERSION_1 = 1;

	/*
	 * Compute an in-memory pack reverse index from the in-memory pack forward
	 * index. This computation uses insertion sort, which has a quadratic
	 * runtime on average.
	 *
	 * @param packIndex the forward index to compute from
	 * @return the reverse index instance
	 */
	public static PackReverseIndex computeFromIndex(PackIndex packIndex) {
		return new ComputedPackReverseIndex(packIndex);
	}

	public static PackReverseIndex openOrCompute(File idxFile, long objectCount,
			PackBitmapIndex.SupplierWithIOException<PackIndex> packIndexSupplier) throws IOException {
		try (SilentFileInputStream fd = new SilentFileInputStream(idxFile)) {
			return read(fd, objectCount, packIndexSupplier);
		} catch (FileNotFoundException e) {
			return computeFromIndex(packIndexSupplier.get());
		} catch (IOException e) {
			throw new IOException(MessageFormat.format(JGitText.get().unreadablePackIndex, idxFile.getAbsolutePath()),
					e);
		}
	}

	public static PackReverseIndex read(InputStream src, long objectCount,
			PackBitmapIndex.SupplierWithIOException<PackIndex> packIndexSupplier)
			throws IOException {
		final DigestInputStream digestIn =
				new DigestInputStream(src, Constants.newMessageDigest());

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
			PackReverseIndexV1 ri =
					new PackReverseIndexV1(digestIn, objectCount,
							packIndexSupplier);
			ri.parse();
			return ri;
		default:
			throw new IOException(MessageFormat.format(
					JGitText.get().unsupportedPackReverseIndexVersion,
					version));
		}
	}

	public abstract void verifyPackChecksum(String packFilePath) throws PackMismatchException;

	/**
	 * Search for object id with the specified start offset in this pack
	 * (reverse) index.
	 *
	 * @param offset start offset of object to find.
	 * @return object id for this offset, or null if no object was found.
	 */
	public abstract ObjectId findObject(long offset);

	/**
	 * Search for the next offset to the specified offset in this pack (reverse)
	 * index.
	 *
	 * @param offset    start offset of previous object (must be valid-existing
	 *                  offset).
	 * @param maxOffset maximum offset in a pack (returned when there is no next
	 *                  offset).
	 * @return offset of the next object in a pack or maxOffset if provided
	 * offset was the last one.
	 * @throws org.eclipse.jgit.errors.CorruptObjectException when there is no
	 *                                                        object with the
	 *                                                        provided offset.
	 */
	public abstract long findNextOffset(long offset, long maxOffset)
			throws CorruptObjectException;

	abstract int findPosition(long offset);

	abstract ObjectId findObjectByPosition(int nthPosition);
}

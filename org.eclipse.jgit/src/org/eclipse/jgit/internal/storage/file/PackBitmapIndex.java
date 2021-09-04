/*
 * Copyright (C) 2012, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.io.SilentFileInputStream;

import com.googlecode.javaewah.EWAHCompressedBitmap;

/**
 * Logical representation of the bitmap data stored in the pack index.
 * {@link org.eclipse.jgit.lib.ObjectId}s are encoded as a single integer in the
 * range [0, {@link #getObjectCount()}). Compressed bitmaps are available at
 * certain {@code ObjectId}s, which represent all of the objects reachable from
 * that {@code ObjectId} (include the {@code ObjectId} itself). The meaning of
 * the positions in the bitmaps can be decoded using {@link #getObject(int)} and
 * {@link #ofObjectType(EWAHCompressedBitmap, int)}. Furthermore,
 * {@link #findPosition(AnyObjectId)} can be used to build other bitmaps that a
 * compatible with the encoded bitmaps available from the index.
 */
public abstract class PackBitmapIndex {
	/** Flag bit denoting the bitmap should be reused during index creation. */
	public static final int FLAG_REUSE = 1;

	/**
	 * Read an existing pack bitmap index file from a buffered stream.
	 * <p>
	 * The format of the file will be automatically detected and a proper access
	 * implementation for that format will be constructed and returned to the
	 * caller. The file may or may not be held open by the returned instance.
	 *
	 * @param idxFile
	 *            existing pack .bitmap to read.
	 * @param packIndex
	 *            the pack index for the corresponding pack file.
	 * @param reverseIndex
	 *            the pack reverse index for the corresponding pack file.
	 * @return a copy of the index in-memory.
	 * @throws java.io.IOException
	 *             the stream cannot be read.
	 * @throws CorruptObjectException
	 *             the stream does not contain a valid pack bitmap index.
	 */
	public static PackBitmapIndex open(
			File idxFile, PackIndex packIndex, PackReverseIndex reverseIndex)
			throws IOException {
		try (SilentFileInputStream fd = new SilentFileInputStream(
				idxFile)) {
			try {
				return read(fd, packIndex, reverseIndex);
			} catch (IOException ioe) {
				throw new IOException(
						MessageFormat.format(JGitText.get().unreadablePackIndex,
								idxFile.getAbsolutePath()),
						ioe);
			}
		}
	}

	/**
	 * Read an existing pack bitmap index file from a buffered stream.
	 * <p>
	 * The format of the file will be automatically detected and a proper access
	 * implementation for that format will be constructed and returned to the
	 * caller. The file may or may not be held open by the returned instance.
	 *
	 * @param fd
	 *            stream to read the bitmap index file from. The stream must be
	 *            buffered as some small IOs are performed against the stream.
	 *            The caller is responsible for closing the stream.
	 * @param packIndex
	 *            the pack index for the corresponding pack file.
	 * @param reverseIndex
	 *            the pack reverse index for the corresponding pack file.
	 * @return a copy of the index in-memory.
	 * @throws java.io.IOException
	 *             the stream cannot be read.
	 * @throws CorruptObjectException
	 *             the stream does not contain a valid pack bitmap index.
	 */
	public static PackBitmapIndex read(
			InputStream fd, PackIndex packIndex, PackReverseIndex reverseIndex)
			throws IOException {
		return new PackBitmapIndexV1(fd, packIndex, reverseIndex);
	}

	/** Footer checksum applied on the bottom of the pack file. */
	byte[] packChecksum;

	/**
	 * Finds the position in the bitmap of the object.
	 *
	 * @param objectId
	 *            the id for which the bitmap position will be found.
	 * @return the bitmap id or -1 if the object was not found.
	 */
	public abstract int findPosition(AnyObjectId objectId);

	/**
	 * Get the object at the bitmap position.
	 *
	 * @param position
	 *            the id for which the object will be found.
	 * @return the ObjectId.
	 * @throws java.lang.IllegalArgumentException
	 *             when the item is not found.
	 */
	public abstract ObjectId getObject(int position) throws IllegalArgumentException;

	/**
	 * Returns a bitmap containing positions for objects that have the given Git
	 * type.
	 *
	 * @param bitmap
	 *            the object bitmap.
	 * @param type
	 *            the Git type.
	 * @return the object bitmap with only objects of the Git type.
	 */
	public abstract EWAHCompressedBitmap ofObjectType(
			EWAHCompressedBitmap bitmap, int type);

	/**
	 * Returns the previously constructed bitmap for the object.
	 *
	 * @param objectId
	 *            the id for which the bitmap will be found.
	 * @return the bitmap or null if the object was not found.
	 */
	public abstract EWAHCompressedBitmap getBitmap(AnyObjectId objectId);

	/**
	 * Obtain the total number of objects described by this index.
	 * {@code getObjectCount() - 1} is the largest bit that will be set in a
	 * bitmap.
	 *
	 * @return number of objects in this index, and likewise in the associated
	 *         pack that this index was generated from.
	 */
	public abstract int getObjectCount();

	/**
	 * Returns the number of bitmaps in this bitmap index.
	 *
	 * @return the number of bitmaps in this bitmap index.
	 */
	public abstract int getBitmapCount();
}

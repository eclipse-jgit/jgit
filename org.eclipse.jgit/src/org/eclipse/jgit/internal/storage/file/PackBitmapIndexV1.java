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

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.NB;

import com.googlecode.javaewah.EWAHCompressedBitmap;

/**
 * Support for the pack bitmap index v1 format.
 *
 * @see PackBitmapIndex
 */
class PackBitmapIndexV1 extends BasePackBitmapIndex {
	static final byte[] MAGIC = { 'B', 'I', 'T', 'M' };
	static final int OPT_FULL = 1;

	private static final int MAX_XOR_OFFSET = 126;

	private static final ExecutorService executor = Executors
			.newCachedThreadPool(new ThreadFactory() {
				private final ThreadFactory baseFactory = Executors
						.defaultThreadFactory();

				private final AtomicInteger threadNumber = new AtomicInteger(0);

				@Override
				public Thread newThread(Runnable runnable) {
					Thread thread = baseFactory.newThread(runnable);
					thread.setName("JGit-PackBitmapIndexV1-" //$NON-NLS-1$
							+ threadNumber.getAndIncrement());
					thread.setDaemon(true);
					return thread;
				}
			});

	private final PackIndex packIndex;
	private final PackReverseIndex reverseIndex;
	private final EWAHCompressedBitmap commits;
	private final EWAHCompressedBitmap trees;
	private final EWAHCompressedBitmap blobs;
	private final EWAHCompressedBitmap tags;

	private final ObjectIdOwnerMap<StoredBitmap> bitmaps;

	PackBitmapIndexV1(final InputStream fd, PackIndex packIndex,
			PackReverseIndex reverseIndex) throws IOException {
		this(fd, () -> packIndex, () -> reverseIndex, false);
	}

	PackBitmapIndexV1(final InputStream fd,
			SupplierWithIOException<PackIndex> packIndexSupplier,
			SupplierWithIOException<PackReverseIndex> reverseIndexSupplier,
			boolean loadParallelRevIndex)
			throws IOException {
		// An entry is object id, xor offset, flag byte, and a length encoded
		// bitmap. The object id is an int32 of the nth position sorted by name.
		super(new ObjectIdOwnerMap<StoredBitmap>());
		this.bitmaps = getBitmaps();

		// Optionally start loading reverse index in parallel to loading bitmap
		// from storage.
		Future<PackReverseIndex> reverseIndexFuture = null;
		if (loadParallelRevIndex) {
			reverseIndexFuture = executor.submit(reverseIndexSupplier::get);
		}

		final byte[] scratch = new byte[32];
		IO.readFully(fd, scratch, 0, scratch.length);

		// Check the magic bytes
		for (int i = 0; i < MAGIC.length; i++) {
			if (scratch[i] != MAGIC[i]) {
				byte[] actual = new byte[MAGIC.length];
				System.arraycopy(scratch, 0, actual, 0, MAGIC.length);
				throw new IOException(MessageFormat.format(
						JGitText.get().expectedGot, Arrays.toString(MAGIC),
						Arrays.toString(actual)));
			}
		}

		// Read the version (2 bytes)
		final int version = NB.decodeUInt16(scratch, 4);
		if (version != 1)
			throw new IOException(MessageFormat.format(
					JGitText.get().unsupportedPackIndexVersion,
					Integer.valueOf(version)));

		// Read the options (2 bytes)
		final int opts = NB.decodeUInt16(scratch, 6);
		if ((opts & OPT_FULL) == 0)
			throw new IOException(MessageFormat.format(
					JGitText.get().expectedGot, Integer.valueOf(OPT_FULL),
					Integer.valueOf(opts)));

		// Read the number of entries (1 int32)
		long numEntries = NB.decodeUInt32(scratch, 8);
		if (numEntries > Integer.MAX_VALUE)
			throw new IOException(JGitText.get().indexFileIsTooLargeForJgit);

		// Checksum applied on the bottom of the corresponding pack file.
		this.packChecksum = new byte[20];
		System.arraycopy(scratch, 12, packChecksum, 0, packChecksum.length);

		// Read the bitmaps for the Git types
		SimpleDataInput dataInput = new SimpleDataInput(fd);
		this.commits = readBitmap(dataInput);
		this.trees = readBitmap(dataInput);
		this.blobs = readBitmap(dataInput);
		this.tags = readBitmap(dataInput);

		// Read full bitmap from storage first.
		List<IdxPositionBitmap> idxPositionBitmapList = new ArrayList<>();
		// The xor offset is a single byte offset back in the list of entries.
		IdxPositionBitmap[] recentBitmaps = new IdxPositionBitmap[MAX_XOR_OFFSET];
		for (int i = 0; i < (int) numEntries; i++) {
			IO.readFully(fd, scratch, 0, 6);
			int nthObjectId = NB.decodeInt32(scratch, 0);
			int xorOffset = scratch[4];
			int flags = scratch[5];
			EWAHCompressedBitmap bitmap = readBitmap(dataInput);

			if (nthObjectId < 0) {
				throw new IOException(MessageFormat.format(
						JGitText.get().invalidId, String.valueOf(nthObjectId)));
			}
			if (xorOffset < 0) {
				throw new IOException(MessageFormat.format(
						JGitText.get().invalidId, String.valueOf(xorOffset)));
			}
			if (xorOffset > MAX_XOR_OFFSET) {
				throw new IOException(MessageFormat.format(
						JGitText.get().expectedLessThanGot,
						String.valueOf(MAX_XOR_OFFSET),
						String.valueOf(xorOffset)));
			}
			if (xorOffset > i) {
				throw new IOException(MessageFormat.format(
						JGitText.get().expectedLessThanGot, String.valueOf(i),
						String.valueOf(xorOffset)));
			}
			IdxPositionBitmap xorIdxPositionBitmap = null;
			if (xorOffset > 0) {
				int index = (i - xorOffset);
				xorIdxPositionBitmap = recentBitmaps[index
						% recentBitmaps.length];
				if (xorIdxPositionBitmap == null) {
					throw new IOException(MessageFormat.format(
							JGitText.get().invalidId,
							String.valueOf(xorOffset)));
				}
			}
			IdxPositionBitmap idxPositionBitmap = new IdxPositionBitmap(
					nthObjectId, xorIdxPositionBitmap, bitmap, flags);
			idxPositionBitmapList.add(idxPositionBitmap);
			recentBitmaps[i % recentBitmaps.length] = idxPositionBitmap;
		}

		this.packIndex = packIndexSupplier.get();
		for (int i = 0; i < idxPositionBitmapList.size(); ++i) {
			IdxPositionBitmap idxPositionBitmap = idxPositionBitmapList.get(i);
			ObjectId objectId = packIndex
					.getObjectId(idxPositionBitmap.nthObjectId);
			StoredBitmap sb = new StoredBitmap(objectId,
					idxPositionBitmap.bitmap,
					idxPositionBitmap.getXorStoredBitmap(),
					idxPositionBitmap.flags);
			// Save the StoredBitmap for a possible future XorStoredBitmap
			// reference.
			idxPositionBitmap.sb = sb;
			bitmaps.add(sb);
		}

		PackReverseIndex computedReverseIndex;
		if (loadParallelRevIndex && reverseIndexFuture != null) {
			try {
				computedReverseIndex = reverseIndexFuture.get();
			} catch (InterruptedException | ExecutionException e) {
				// Fallback to loading reverse index through a supplier.
				computedReverseIndex = reverseIndexSupplier.get();
			}
		} else {
			computedReverseIndex = reverseIndexSupplier.get();
		}
		this.reverseIndex = computedReverseIndex;
	}

	/** {@inheritDoc} */
	@Override
	public int findPosition(AnyObjectId objectId) {
		long offset = packIndex.findOffset(objectId);
		if (offset == -1)
			return -1;
		return reverseIndex.findPostion(offset);
	}

	/** {@inheritDoc} */
	@Override
	public ObjectId getObject(int position) throws IllegalArgumentException {
		ObjectId objectId = reverseIndex.findObjectByPosition(position);
		if (objectId == null)
			throw new IllegalArgumentException();
		return objectId;
	}

	/** {@inheritDoc} */
	@Override
	public int getObjectCount() {
		return (int) packIndex.getObjectCount();
	}

	/** {@inheritDoc} */
	@Override
	public EWAHCompressedBitmap ofObjectType(
			EWAHCompressedBitmap bitmap, int type) {
		switch (type) {
		case Constants.OBJ_BLOB:
			return blobs.and(bitmap);
		case Constants.OBJ_TREE:
			return trees.and(bitmap);
		case Constants.OBJ_COMMIT:
			return commits.and(bitmap);
		case Constants.OBJ_TAG:
			return tags.and(bitmap);
		}
		throw new IllegalArgumentException();
	}

	/** {@inheritDoc} */
	@Override
	public int getBitmapCount() {
		return bitmaps.size();
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(Object o) {
		// TODO(cranger): compare the pack checksum?
		if (o instanceof PackBitmapIndexV1)
			return getPackIndex() == ((PackBitmapIndexV1) o).getPackIndex();
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		return getPackIndex().hashCode();
	}

	PackIndex getPackIndex() {
		return packIndex;
	}

	private static EWAHCompressedBitmap readBitmap(DataInput dataInput)
			throws IOException {
		EWAHCompressedBitmap bitmap = new EWAHCompressedBitmap();
		bitmap.deserialize(dataInput);
		return bitmap;
	}

	/**
	 * Temporary holder of object position in pack index and other metadata for
	 * {@code StoredBitmap}.
	 */
	private static final class IdxPositionBitmap {
		int nthObjectId;

		IdxPositionBitmap xorIdxPositionBitmap;

		EWAHCompressedBitmap bitmap;

		int flags;

		StoredBitmap sb;

		IdxPositionBitmap(int nthObjectId,
				@Nullable IdxPositionBitmap xorIdxPositionBitmap,
				EWAHCompressedBitmap bitmap, int flags) {
			this.nthObjectId = nthObjectId;
			this.xorIdxPositionBitmap = xorIdxPositionBitmap;
			this.bitmap = bitmap;
			this.flags = flags;
		}

		StoredBitmap getXorStoredBitmap() {
			return xorIdxPositionBitmap == null ? null
					: xorIdxPositionBitmap.sb;
		}
	}
}

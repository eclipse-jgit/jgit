/*
 * Copyright (C) 2020, Marc Strapetz <marc.strapetz@syntevo.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;

/** Support for the pack index v2 format. */
class PackIndexV2m extends PackIndex {
	private static final long IS_O64 = 1L << 31;

	private static final int FANOUT = 256;

	private final byte[] idBuf = new byte[Constants.OBJECT_ID_LENGTH];
	private final Thread ownerThread;
	private final long objectCnt;
	private final long namesOffset;
	private final long crc32Offset;
	private final long offsets32Offset;
	private final long offsets64Offset;
	private final long hashOffset;
	private final long[] fanoutTable;

	private FileChannel channel;
	private RandomAccessFile raFile;
	private MappedByteBuffer buffer;

	PackIndexV2m(File file) throws IOException {
		// TODO:
		// (1) How to handle files larger than Integer.MAX limit?
		//     - use a list of multiple buffers, as suggested at https://stackoverflow.com/a/55301147
		//     - or introduce assertions, if we don't plan to support them
		// (2) How to handle possible multi-threaded access to buffers?
		//     - disallow, as currently done
		//     - have independent buffers, one per thread
		ownerThread = Thread.currentThread();
		raFile = new RandomAccessFile(file, "r");
		channel = raFile.getChannel();
		buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, raFile.length());
		buffer.get(new byte[8]); // signature

		fanoutTable = new long[FANOUT];
		for (int k = 0; k < FANOUT; k++)
			fanoutTable[k] = Integer.toUnsignedLong(buffer.getInt());
		objectCnt = fanoutTable[FANOUT - 1];
		packChecksum = new byte[20];
		namesOffset = 1032;
		crc32Offset = namesOffset + objectCnt * Constants.OBJECT_ID_LENGTH;
		offsets32Offset = crc32Offset + objectCnt * 4;
		offsets64Offset = offsets32Offset + objectCnt * 4;
		hashOffset = (int)raFile.length() - 40;
		buffer.position((int)hashOffset);
		buffer.get(packChecksum, 0, packChecksum.length);
	}

	/** {@inheritDoc} */
	@Override
	public long getObjectCount() {
		return objectCnt;
	}

	/** {@inheritDoc} */
	@Override
	public long getOffset64Count() {
		return (hashOffset - offsets64Offset) / 8;
	}

	/** {@inheritDoc} */
	@Override
	public ObjectId getObjectId(long nthPosition) {
		assertThread();

		return ObjectId.fromRaw(readNameShared(nthPosition));
	}

	/** {@inheritDoc} */
	@Override
	public long getOffset(long nthPosition) {
		assertThread();

		final long offset32 = Integer.toUnsignedLong(buffer.getInt(toBufferPos(offsets32Offset, nthPosition, 4)));
		if ((offset32 & IS_O64) == 0) {
			return offset32;
		}

		return buffer.getLong(toBufferPos(offsets64Offset, offset32 & ~IS_O64, 8));
	}

	/** {@inheritDoc} */
	@Override
	public long findOffset(AnyObjectId objId) {
		assertThread();

		final long objIndex = findObjectIndex(objId);
		if (objIndex == -1) {
			return -1;
		}

		return getOffset(objIndex);
	}

	/** {@inheritDoc} */
	@Override
	public long findCRC32(AnyObjectId objId) throws MissingObjectException {
		assertThread();

		final long objIndex = findObjectIndex(objId);
		if (objIndex == -1) {
			throw new MissingObjectException(objId.copy(), "unknown"); //$NON-NLS-1$
		}

		return Integer.toUnsignedLong(buffer.getInt(toBufferPos(crc32Offset, objIndex, 4)));
	}

	/** {@inheritDoc} */
	@Override
	public boolean hasCRC32Support() {
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public Iterator<MutableEntry> iterator() {
		assertThread();

		return new EntriesIteratorV2();
	}

	/** {@inheritDoc} */
	@Override
	public void resolve(Set<ObjectId> matches, AbbreviatedObjectId id,
			int matchLimit) throws IOException {
		// TODO: rethink
		final int levelOne = id.getFirstByte();
		final long start = levelOne > 0 ? fanoutTable[levelOne - 1] : 0;
		final long end = fanoutTable[levelOne];
		final long bucketCnt = end - start;
		int high = (int)bucketCnt;
		if (high == 0)
			return;
		int low = 0;
		do {
			int mid = (low + high) >>> 1;
			final int cmp = id.prefixCompare(readNameShared(start + mid), 0);
			if (cmp < 0)
				high = mid;
			else if (cmp == 0) {
				// We may have landed in the middle of the matches.  Move
				// backwards to the start of matches, then walk forwards.
				//
				while (0 < mid && id.prefixCompare(readNameShared(start + mid - 1), 0) == 0)
					mid--;
				for (; mid < bucketCnt && id.prefixCompare(readNameShared(start + mid), 0) == 0; mid++) {
					matches.add(ObjectId.fromRaw(readNameShared(start + mid)));
					if (matches.size() > matchLimit)
						break;
				}
				return;
			} else
				low = mid + 1;
		} while (low < high);
	}

	@Override
	public void close() {
		synchronized (this) {
			if (channel != null) {
				buffer = null;
				try {
					channel.close();
				}
				catch (IOException ignore) {
				}
				channel = null;
			}
			if (raFile != null) {
				try {
					raFile.close();
				}
				catch (IOException ignore) {
				}
				raFile = null;
			}
			if (buffer != null) {
				// This is important to allow garbage collection of the Buffer which is (as of JDK 14) the only way to unlock the file system file
				buffer = null;
			}
		}
	}

	private long findObjectIndex(AnyObjectId objId) {
		final int levelOne = objId.getFirstByte();
		final int levelTwo = binarySearchLevelTwo(objId, levelOne);
		if (levelTwo == -1) {
			return -1;
		}

		return (levelOne > 0 ? fanoutTable[levelOne - 1] : 0) + levelTwo;
	}

	private int toBufferPos(long offset, long objectIndex, int rowSize) {
		final long pos = offset + objectIndex * rowSize;
		if (pos > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Buffer position " + pos + " is too large for JGit.");
		}
		return (int)pos;
	}

	private int binarySearchLevelTwo(AnyObjectId objId, int levelOne) {
		final long start = levelOne > 0 ? fanoutTable[levelOne - 1] : 0;
		final long end = fanoutTable[levelOne];
		final long bucketCnt = end - start;
		int high = (int)bucketCnt;
		if (high == 0)
			return -1;
		int low = 0;
		do {
			final int mid = (low + high) >>> 1;
			final int cmp = objId.compareTo(readNameShared(start + mid), 0);
			if (cmp < 0)
				high = mid;
			else if (cmp == 0) {
				return mid;
			} else
				low = mid + 1;
		} while (low < high);
		return -1;
	}

	private byte[] readNameShared(long objectIndex) {
		buffer.position(toBufferPos(namesOffset, objectIndex, 20));
		buffer.get(idBuf);
		return idBuf;
	}

	private void assertThread() {
		// MappedByteBuffer is not thread-safe
		if (Thread.currentThread() != ownerThread) {
			throw new AssertionError("bad thread access (current=" + Thread.currentThread() + ",owner=" + ownerThread + ")"); //$NON-NLS-1$
		}
	}

	private class EntriesIteratorV2 extends EntriesIterator {
		private long objIndex = -1;

		@Override
		protected MutableEntry initEntry() {
			return new MutableEntry() {
				@Override
				protected void ensureId() {
					idBuffer.fromRaw(readNameShared(objIndex));
				}
			};
		}

		@Override
		public MutableEntry next() {
			objIndex++;
			if (objIndex < objectCnt) {
				entry.offset = getOffset(objIndex);
				returnedNumber++;
				return entry;
			}
			throw new NoSuchElementException();
		}
	}
}

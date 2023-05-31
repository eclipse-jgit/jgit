/*
 * Copyright (C) 2022, Google LLC and others
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
import java.util.List;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.NB;

/**
 * Write an object index in the output stream
 */
public abstract class PackObjectSizeIndexWriter {

	private static final int MAX_24BITS_UINT = 0xffffff;

	private static final PackObjectSizeIndexWriter NULL_WRITER = new PackObjectSizeIndexWriter() {
		@Override
		public void write(List<? extends PackedObjectInfo> objs) {
			// Do nothing
		}
	};

	/** Magic constant for the object size index file */
	protected static final byte[] HEADER = { -1, 's', 'i', 'z' };

	/**
	 * Returns a writer for the latest index version
	 *
	 * @param os
	 *            Output stream where to write the index
	 * @param minSize
	 *            objects strictly smaller than this size won't be added to the
	 *            index. Negative size won't write AT ALL. Other sizes could write
	 *            an empty index.
	 * @return the index writer
	 */
	public static PackObjectSizeIndexWriter createWriter(OutputStream os,
			int minSize) {
		if (minSize < 0) {
			return NULL_WRITER;
		}
		return new PackObjectSizeWriterV1(os, minSize);
	}

	/**
	 * Add the objects to the index
	 *
	 * @param objs
	 *            objects in the pack, in sha1 order. Their position in the list
	 *            matches their position in the primary index.
	 * @throws IOException
	 *             problem writing to the stream
	 */
	public abstract void write(List<? extends PackedObjectInfo> objs)
			throws IOException;

	/**
	 * Object size index v1.
	 *
	 * Store position (in the main index) to size as parallel arrays.
	 *
	 * <p>
	 * Positions in the main index fit well in unsigned 24 bits (16M) for most
	 * repositories, but some outliers have even more objects, so we need to
	 * store also 32 bits positions.
	 *
	 * <p>
	 * Sizes are stored as a first array parallel to positions. If a size
	 * doesn't fit in an element of that array, then we encode there a position
	 * on the next-size array. This "overflow" array doesn't have entries for
	 * all positions.
	 *
	 * <pre>
	 *
	 *      positions       [10, 500, 1000, 1001]
	 *      sizes (32bits)  [15MB, -1, 6MB, -2]
	*                          ___/  ______/
	 *                        /     /
	 *      sizes (64 bits) [3GB, 6GB]
	 * </pre>
	 *
	 * <p>
	 * For sizes we use 32 bits as the first level and 64 for the rare objects
	 * over 2GB.
	 *
	 * <p>
	 * A 24/32/64 bits hierarchy of arrays saves space if we have a lot of small
	 * objects, but wastes space if we have only big ones. The min size to index
	 * is controlled by conf and in principle we want to index only rather big
	 * objects (e.g. > 10MB). We could support more dynamics read/write of sizes
	 * (e.g. 24 only if the threshold will include many of those objects) but it
	 * complicates a lot code and spec. If needed it could go for a v2 of the
	 * protocol.
	 *
	 * <p>
	 * Format:
	 * <ul>
	 * <li>A header with the magic number (4 bytes)
	 * <li>The index version (1 byte)
	 * <li>The minimum object size (4 bytes)
	 * <li>Total count of objects indexed (C, 4 bytes)
	 * </ul>
	 * (if count == 0, stop here)
	 * <p>
	 * Blocks of
	 * <ul>
	 * <li>Size per entry in bits (1 byte, either 24 (0x18) or 32 (0x20))
	 * <li>Count of entries (4 bytes) (c, as a signed int)
	 * <li>positions encoded in s bytes each (i.e s*c bytes)
	 *
	 * <li>0 (as a "size-per-entry = 0", marking end of the section)
	 *
	 * <li>32 bit sizes (C * 4 bytes). Negative size means
	 * nextLevel[abs(size)-1]
	 * <li>Count of 64 bit sizes (s64) (or 0 if no more indirections)
	 * <li>64 bit sizes (s64 * 8 bytes)
	 * <li>0 (end)
	 * </ul>
	 */
	static class PackObjectSizeWriterV1 extends PackObjectSizeIndexWriter {

		private final OutputStream os;

		private final int minObjSize;

		private final byte[] intBuffer = new byte[4];

		PackObjectSizeWriterV1(OutputStream os, int minSize) {
			this.os = new BufferedOutputStream(os);
			this.minObjSize = minSize;
		}

		@Override
		public void write(List<? extends PackedObjectInfo> allObjects)
				throws IOException {
			os.write(HEADER);
			writeUInt8(1); // Version
			writeInt32(minObjSize);

			PackedObjectStats stats = countIndexableObjects(allObjects);
			int[] indexablePositions = findIndexablePositions(allObjects,
					stats.indexableObjs);
			writeInt32(indexablePositions.length); // Total # of objects
			if (indexablePositions.length == 0) {
				os.flush();
				return;
			}

			// Positions that fit in 3 bytes
			if (stats.pos24Bits > 0) {
				writeUInt8(24);
				writeInt32(stats.pos24Bits);
				applyToRange(indexablePositions, 0, stats.pos24Bits,
						this::writeInt24);
			}
			// Positions that fit in 4 bytes
			// We only use 31 bits due to sign,
			// but that covers 2 billion objs
			if (stats.pos31Bits > 0) {
				writeUInt8(32);
				writeInt32(stats.pos31Bits);
				applyToRange(indexablePositions, stats.pos24Bits,
						stats.pos24Bits + stats.pos31Bits, this::writeInt32);
			}
			writeUInt8(0);
			writeSizes(allObjects, indexablePositions, stats.sizeOver2GB);
			os.flush();
		}

		private void writeUInt8(int i) throws IOException {
			if (i > 255) {
				throw new IllegalStateException(
						JGitText.get().numberDoesntFit);
			}
			NB.encodeInt32(intBuffer, 0, i);
			os.write(intBuffer, 3, 1);
		}

		private void writeInt24(int i) throws IOException {
			NB.encodeInt24(intBuffer, 1, i);
			os.write(intBuffer, 1, 3);
		}

		private void writeInt32(int i) throws IOException {
			NB.encodeInt32(intBuffer, 0, i);
			os.write(intBuffer);
		}

		private void writeSizes(List<? extends PackedObjectInfo> allObjects,
				int[] indexablePositions, int objsBiggerThan2Gb)
				throws IOException {
			if (indexablePositions.length == 0) {
				writeInt32(0);
				return;
			}

			byte[] sizes64bits = new byte[8 * objsBiggerThan2Gb];
			int s64 = 0;
			for (int i = 0; i < indexablePositions.length; i++) {
				PackedObjectInfo info = allObjects.get(indexablePositions[i]);
				if (info.getFullSize() < Integer.MAX_VALUE) {
					writeInt32((int) info.getFullSize());
				} else {
					// Size needs more than 32 bits. Store -1 * offset in the
					// next table as size.
					writeInt32(-1 * (s64 + 1));
					NB.encodeInt64(sizes64bits, s64 * 8, info.getFullSize());
					s64++;
				}
			}
			if (objsBiggerThan2Gb > 0) {
				writeInt32(objsBiggerThan2Gb);
				os.write(sizes64bits);
			}
			writeInt32(0);
		}

		private int[] findIndexablePositions(
				List<? extends PackedObjectInfo> allObjects,
				int indexableObjs) {
			int[] positions = new int[indexableObjs];
			int positionIdx = 0;
			for (int i = 0; i < allObjects.size(); i++) {
				PackedObjectInfo o = allObjects.get(i);
				if (!shouldIndex(o)) {
					continue;
				}
				positions[positionIdx++] = i;
			}
			return positions;
		}

		private PackedObjectStats countIndexableObjects(
				List<? extends PackedObjectInfo> objs) {
			PackedObjectStats stats = new PackedObjectStats();
			for (int i = 0; i < objs.size(); i++) {
				PackedObjectInfo o = objs.get(i);
				if (!shouldIndex(o)) {
					continue;
				}
				stats.indexableObjs++;
				if (o.getFullSize() > Integer.MAX_VALUE) {
					stats.sizeOver2GB++;
				}
				if (i <= MAX_24BITS_UINT) {
					stats.pos24Bits++;
				} else {
					stats.pos31Bits++;
					// i is a positive int, cannot be bigger than this
				}
			}
			return stats;
		}

		private boolean shouldIndex(PackedObjectInfo o) {
			return (o.getType() == Constants.OBJ_BLOB)
					&& (o.getFullSize() >= minObjSize);
		}

		private static class PackedObjectStats {
			int indexableObjs;

			int pos24Bits;

			int pos31Bits;

			int sizeOver2GB;
		}

		@FunctionalInterface
		interface IntEncoder {
			void encode(int i) throws IOException;
		}

		private static void applyToRange(int[] allPositions, int start, int end,
				IntEncoder encoder) throws IOException {
			for (int i = start; i < end; i++) {
				encoder.encode(allPositions[i]);
			}
		}
	}
}

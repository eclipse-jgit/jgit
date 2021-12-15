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

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.NB;

/**
 * Write an object index in the output stream
 */
public abstract class PackObjectSizeIndexWriter {

	private static final PackObjectSizeIndexWriter NULL_WRITER = new PackObjectSizeIndexWriter() {
		@Override
		public void write(List<? extends PackedObjectInfo> objs)
				throws IOException {
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
	 *            index.
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
	 *            objects to include.
	 * @throws IOException
	 */
	public abstract void write(List<? extends PackedObjectInfo> objs)
			throws IOException;

	/**
	 * Object size index v1.
	 *
	 * Offsets in the packs can be 64bits, but mostly 32. Use the same approach
	 * than pack index: store first 32bits offsets and then (if needed) 64bits
	 * offsets.
	 *
	 * Format:
	 *
	 * <li>A header with the magic number (4 bytes)
	 * <li>The index version (4 bytes)	TODO: only 1
	 * <li>The minimum object size (4 bytes)
	 * <li>Count of objects indexed (C)
	 * <li>32 bit positions in the main idx (C * 4 bytes)
	 * <li>32 bit sizes (C * 4 bytes). Negative size means
	 * nextLevel[abs(size)-1]
	 * <li>Count of 64 bit sizes (s64) (or 0 if no more indirections)
	 * <li>64 bit sizes (s64 * 8 bytes)
	 * <li>Count of 128 bit sizes (s128) (or 0 if no more indirections)
	 * <li>...
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
			writeInt(1); // version
			writeInt(minObjSize);

			PackedObjectStats stats = countIndexableObjects(allObjects);
			int[] indexablePositions = findIndexablePositions(allObjects, stats.indexableObjs);
			writeInt(indexablePositions.length);
			for (int i = 0; i < indexablePositions.length; i++) {
				writeInt(indexablePositions[i]);
			}

			writeSizes(allObjects, indexablePositions, stats.biggerThan2Gbs);
			os.flush();
		}


		private void writeInt(int i) throws IOException {
			NB.encodeInt32(intBuffer, 0, i);
			os.write(intBuffer);
		}

		private void writeSizes(
				List<? extends PackedObjectInfo> allObjects,
				int[] indexablePositions,
				int objsBiggerThan2Gb) throws IOException {
			if (indexablePositions.length == 0) {
				writeInt(0);
				return;
			}

			byte[] sizes64bits = new byte[8 * objsBiggerThan2Gb];
			int s64 = 0;
			for (int i = 0; i < indexablePositions.length; i++) {
				PackedObjectInfo info = allObjects.get(indexablePositions[i]);
				if (info.getFullSize() < Integer.MAX_VALUE) {
					writeInt((int) info.getFullSize());
				} else {
					// Size needs more than 32 bits. Store -1 * offset in the
					// next table as size.
					writeInt(-1 * (s64 + 1));
					NB.encodeInt64(sizes64bits, s64 * 8, info.getFullSize());
					s64++;
				}
			}
			if (objsBiggerThan2Gb > 0) {
				writeInt(objsBiggerThan2Gb);
				os.write(sizes64bits);
			}
			writeInt(0);
		}

		private int[] findIndexablePositions(
				List<? extends PackedObjectInfo> allObjects,
				int indexableObjs) {
			int[] positions = new int[indexableObjs];
			int positionIdx = 0;
			for (int i = 0; i  < allObjects.size(); i++) {
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
			for (PackedObjectInfo o: objs) {
				if (!shouldIndex(o)) {
					continue;
				}
				stats.indexableObjs++;
				if (o.getFullSize() > Integer.MAX_VALUE) {
					stats.biggerThan2Gbs++;
				}
			}
			return stats;
		}

		private boolean shouldIndex(PackedObjectInfo o) {
			return (o.getType() == Constants.OBJ_BLOB) && (o.getFullSize() >= minObjSize);
		}

		private static class PackedObjectStats {
			int indexableObjs;
			int biggerThan2Gbs;
		}
	}
}

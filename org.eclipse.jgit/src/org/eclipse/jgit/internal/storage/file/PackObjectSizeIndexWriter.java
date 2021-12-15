/*
 * Copyright (C) 2021, Google Inc. and others
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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.NB;

/**
 * Write an object index in the output stream
 */
public abstract class PackObjectSizeIndexWriter {


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
			return new PackObjectSizeIndexWriter() {
				@Override
				public void write(List<PackedObjectInfo> objs)
						throws IOException {
					// Do nothing
				}
			};
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
	public abstract void write(List<PackedObjectInfo> objs) throws IOException;

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
	 * <li>The index version (4 bytes)
	 * <li>The minimum object size (4 bytes)
	 * <li>Count of 32 bit offsets (c32)
	 * <li>Count of 64 bit offsets (c64)
	 * <li>32 bit offsets (c32 * 4 bytes)
	 * <li>64 bit offsets (c64 * 8 bytes)
	 * <li>32 bit sizes (c32 + c64 * 4 bytes). Negative size means
	 * nextLevel[abs(size)-1]
	 * <li>Count of 64 bit sizes (s64) (or 0 if no more indirections)
	 * <li>64 bit sizes (s64 * 8 bytes)
	 * <li>Count of 128 bit sizes (s128)
	 * <li>...
	 */
	static class PackObjectSizeWriterV1 extends PackObjectSizeIndexWriter {

		private final OutputStream os;

		private final int minObjSize;

		PackObjectSizeWriterV1(OutputStream os, int minSize) {
			this.os = new BufferedOutputStream(os);
			this.minObjSize = minSize;
		}

		@Override
		public void write(List<PackedObjectInfo> allObjects)
				throws IOException {
			allObjects
					.forEach(o -> System.out.println("Indexing: " + o.getName()
							+ " t:" + o.getType() + " offset: "
							+ o.getOffset() + " size: " + o.getFullSize()));
			List<PackedObjectInfo> objs = allObjects.stream()
					.filter(o -> o.getType() == Constants.OBJ_BLOB)
					.filter(o -> o.getFullSize() >= minObjSize)
					.sorted(new SortByOffset()).collect(Collectors.toList());
			os.write(HEADER);
			writeInt(1); // version
			writeInt(minObjSize);

			int biggerThan2Gb = 0;
			int c32 = 0;
			byte[] offsets32 = new byte[4 * objs.size()];

			// Offsets are in asc order, everything fitting in 32 bits comes
			// first.

			// 32 bits offsets
			for (PackedObjectInfo poi: objs) {
				if (!fitsInInt(poi.getOffset())) {
					break;
				}
				NB.encodeInt32(offsets32, c32 * 4, (int) poi.getOffset());
				c32++;

				if (poi.getFullSize() >= Integer.MAX_VALUE) {
					biggerThan2Gb += 1;
				}
			}

			// 64 bits offsets
			int c64 = 0;
			byte[] offsets64 = new byte[8 * (objs.size() - c32)];
			for (PackedObjectInfo packedObjectInfo : objs.subList(c32,
					objs.size())) {
				NB.encodeInt64(offsets64, c64 * 8,
						packedObjectInfo.getOffset());
				c64++;
				if (packedObjectInfo.getFullSize() >= Integer.MAX_VALUE) {
					biggerThan2Gb += 1;
				}
			}

			writeInt(c32);
			writeInt(c64);
			os.write(offsets32, 0, c32 * 4);
			os.write(offsets64, 0, c64 * 8);
			writeSizes(objs, biggerThan2Gb);
			os.flush();
		}


		private void writeInt(int i) throws IOException {
			byte[] n = new byte[4];
			NB.encodeInt32(n, 0, i);
			os.write(n);
		}

		private void writeSizes(List<PackedObjectInfo> objs,
				int objsBiggerThan2Gb) throws IOException {
			byte[] sizes32bits = new byte[4 * objs.size()];
			byte[] sizes64bits = new byte[8 * objsBiggerThan2Gb];
			int s64 = 0;
			int s32 = 0;
			for (PackedObjectInfo info : objs) {
				if (info.getFullSize() < Integer.MAX_VALUE) {
					NB.encodeInt32(sizes32bits, s32 * 4,
							(int) info.getFullSize());
					s32 += 1;
				} else {
					// Size needs more than 32 bits. Store -1 * offset in the
					// next table as size.
					NB.encodeInt32(sizes32bits, s32 * 4, -1 * (s64 + 1));
					s32++;
					NB.encodeInt64(sizes64bits, s64 * 8, info.getFullSize());
					s64++;
				}
			}
			os.write(sizes32bits);
			if (objsBiggerThan2Gb > 0) {
				writeInt(objsBiggerThan2Gb);
				os.write(sizes64bits);
			}
			writeInt(0);
		}
	}

	private static boolean fitsInInt(long n) {
		return n < Integer.MAX_VALUE;
	}

	private static class SortByOffset implements Comparator<PackedObjectInfo> {
		@Override
		public int compare(PackedObjectInfo a, PackedObjectInfo b) {
			if (a.getOffset() == b.getOffset()) {
				return 0;
			}

			return a.getOffset() > b.getOffset() ? 1 : -1;
		}
	}
}

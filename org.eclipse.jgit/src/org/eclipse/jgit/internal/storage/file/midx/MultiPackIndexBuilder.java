/*
 * Copyright (C) 2024, GerritForge Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file.midx;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.internal.storage.file.midx.MultiPackIndexConstants.MULTI_PACK_INDEX_BITMAPPED_PACKFILES;
import static org.eclipse.jgit.internal.storage.file.midx.MultiPackIndexConstants.MULTI_PACK_INDEX_BITMAP_PACK_ORDER;
import static org.eclipse.jgit.internal.storage.file.midx.MultiPackIndexConstants.MULTI_PACK_INDEX_ID_OID_FANOUT;
import static org.eclipse.jgit.internal.storage.file.midx.MultiPackIndexConstants.MULTI_PACK_INDEX_ID_OID_LOOKUP;
import static org.eclipse.jgit.internal.storage.file.midx.MultiPackIndexConstants.MULTI_PACK_INDEX_OBJECT_LARGE_OFFSETS;
import static org.eclipse.jgit.internal.storage.file.midx.MultiPackIndexConstants.MULTI_PACK_INDEX_OBJECT_OFFSETS;
import static org.eclipse.jgit.internal.storage.file.midx.MultiPackIndexConstants.MULTI_PACK_INDEX_PACKFILE_NAMES;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;

import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.MultiPackIndex;
import org.eclipse.jgit.internal.storage.file.MultiPackIndexV1;

/**
 * Builder for {@link MultiPackIndex}.
 */
public class MultiPackIndexBuilder {

	private final int hashLength;

	private long packFilesCnt;

	private byte[] oidFanout;

	private byte[] oidLookup;

	private String[] packfileNames;

	private byte[] bitmappedPackfiles;

	private byte[] objectOffsets;

	// Optional
	private byte[] objectLargeOffsets;

	// Optional
	private byte[] bitmapPackOrder;

	private MultiPackIndexBuilder(int hashLength) {
		this.hashLength = hashLength;
	}

	/**
	 * Create builder
	 *
	 * @return A builder of {@link MultiPackIndex}.
	 */
	static MultiPackIndexBuilder builder() {
		return new MultiPackIndexBuilder(OBJECT_ID_LENGTH);
	}

	MultiPackIndexBuilder packfilesCnt(long packFilesCnt) {
		this.packFilesCnt = packFilesCnt;
		return this;
	}

	MultiPackIndexBuilder addOidFanout(byte[] buffer)
			throws MultiPackIndexFormatException {
		assertChunkNotSeenYet(oidFanout, MULTI_PACK_INDEX_ID_OID_FANOUT);
		oidFanout = buffer;
		return this;
	}

	MultiPackIndexBuilder addOidLookUp(byte[] buffer)
			throws MultiPackIndexFormatException {
		assertChunkNotSeenYet(oidLookup, MULTI_PACK_INDEX_ID_OID_LOOKUP);
		oidLookup = buffer;
		return this;
	}

	MultiPackIndexBuilder addPackFileNames(byte[] buffer)
			throws MultiPackIndexFormatException {
		assertChunkNotSeenYet(packfileNames, MULTI_PACK_INDEX_PACKFILE_NAMES);
		packfileNames = new String(buffer, UTF_8).split("\u0000");
		return this;
	}

	MultiPackIndexBuilder addBitmappedPackfiles(byte[] buffer)
			throws MultiPackIndexFormatException {
		assertChunkNotSeenYet(bitmappedPackfiles,
				MULTI_PACK_INDEX_BITMAPPED_PACKFILES);
		bitmappedPackfiles = buffer;
		return this;
	}

	MultiPackIndexBuilder addObjectOffsets(byte[] buffer)
			throws MultiPackIndexFormatException {
		assertChunkNotSeenYet(objectOffsets, MULTI_PACK_INDEX_OBJECT_OFFSETS);
		objectOffsets = buffer;
		return this;
	}

	MultiPackIndexBuilder addObjectLargeOffsets(byte[] buffer)
			throws MultiPackIndexFormatException {
		assertChunkNotSeenYet(objectLargeOffsets,
				MULTI_PACK_INDEX_OBJECT_LARGE_OFFSETS);
		objectLargeOffsets = buffer;
		return this;
	}

	MultiPackIndexBuilder addBitmapPackOrder(byte[] buffer)
			throws MultiPackIndexFormatException {
		assertChunkNotSeenYet(bitmapPackOrder,
				MULTI_PACK_INDEX_BITMAP_PACK_ORDER);
		bitmapPackOrder = buffer;
		return this;
	}

	MultiPackIndex build() throws MultiPackIndexFormatException {
		assertChunkNotNull(oidFanout, MULTI_PACK_INDEX_ID_OID_FANOUT);
		assertChunkNotNull(oidLookup, MULTI_PACK_INDEX_ID_OID_LOOKUP);
		assertChunkNotNull(packfileNames, MULTI_PACK_INDEX_PACKFILE_NAMES);
		assertChunkNotNull(objectOffsets, MULTI_PACK_INDEX_OBJECT_OFFSETS);

		MultiPackIndexIndex index = new MultiPackIndexIndex(hashLength,
				oidFanout, oidLookup, packFilesCnt);

		return new MultiPackIndexV1(index, packfileNames, bitmappedPackfiles,
				new ObjectOffsets(objectOffsets));
	}

	private void assertChunkNotNull(Object object, int chunkId)
			throws MultiPackIndexFormatException {
		if (object == null) {
			throw new MultiPackIndexFormatException(
					MessageFormat.format(JGitText.get().midxChunkNeeded,
							Integer.toHexString(chunkId)));
		}
	}

	private void assertChunkNotSeenYet(Object object, int chunkId)
			throws MultiPackIndexFormatException {
		if (object != null) {
			throw new MultiPackIndexFormatException(
					MessageFormat.format(JGitText.get().midxChunkRepeated,
							Integer.toHexString(chunkId)));
		}
	}
}

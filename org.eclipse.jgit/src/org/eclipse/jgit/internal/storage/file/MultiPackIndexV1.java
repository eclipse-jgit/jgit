/*
 * Copyright (C) 2024, GerritForge Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.internal.storage.file.midx.MultiPackIndexIndex;
import org.eclipse.jgit.internal.storage.file.midx.ObjectOffsets;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support for the MultiPackIndex v1 format.
 *
 * @see MultiPackIndex
 */
public class MultiPackIndexV1 implements MultiPackIndex {
	private final static Logger LOG = LoggerFactory.getLogger(
			MultiPackIndexV1.class);

	private final MultiPackIndexIndex idx;

	private final String[] packfileNames;

	private final byte[] bitmappedPackfiles;

	private final ObjectOffsets objectOffsets;

	private PackDirectory packDir;

	/**
	 * Container for {@code Pack} and object offset within that Pack.
	 */
	public static class PackAndOffset {
		private final Pack pack;

		private final long offset;

		/**
		 * Container for {@code Pack} and object offset within that Pack.
		 *
		 * @param pack
		 * 		The pack
		 * @param offset
		 * 		Object offset within the pack
		 */
		public PackAndOffset(Pack pack, long offset) {
			this.pack = pack;
			this.offset = offset;
		}

		/**
		 * @return Pack for this object offset
		 */
		public Pack getPack() {
			return pack;
		}

		/**
		 * @return offset within this Pack
		 */
		public long getOffset() {
			return offset;
		}
	}

	/**
	 * Support for the MultiPackIndex v1 format.
	 *
	 * @param index
	 * 		index for this multi-pack-index
	 * @param packfileNames
	 * 		names of pack files with objects indexed in {@code index}
	 * @param bitmappedPackfiles
	 * 		array of bitmapped pack files
	 * @param objectOffsets
	 * 		object offset chunk data
	 * @see MultiPackIndex
	 */
	public MultiPackIndexV1(MultiPackIndexIndex index, String[] packfileNames,
			byte[] bitmappedPackfiles, ObjectOffsets objectOffsets) {
		this.bitmappedPackfiles = bitmappedPackfiles;
		this.idx = index;
		this.objectOffsets = objectOffsets;
		this.packfileNames = packfileNames;
	}

	@Override
	public void setPackDir(PackDirectory packDir) {
		this.packDir = packDir;
	}

	@Override
	public String toString() {
		return "MultiPackIndexV1 {" + "idx=" + idx + ", packfileNames="
				+ Arrays.toString(packfileNames) + ", bitmappedPackfiles="
				+ byteArrayToString(bitmappedPackfiles) + ", objectOffsets="
				+ objectOffsets + '}';
	}

	private String byteArrayToString(byte[] array) {
		return array == null ? "null" : new String(array);
	}

	@Override
	public String[] getPackFileNames() {
		return packfileNames;
	}

	@Override
	public long getPackFilesCount() {
		return idx.getPackFilesCnt();
	}

	String getPackFileName(ObjectOffsets.ObjectOffset objectOffset) {
		return getPackFileNames()[objectOffset.getPackIntId()];
	}

	@Override
	public boolean containsPack(Pack pack) {
		Set<String> packNames = new HashSet<>();
		for (String packFileName : getPackFileNames()) {
			packNames.add(
					new PackFile(packDir.getDirectory(), packFileName).getId());
		}
		return packNames.contains(pack.getPackName());
	}

	@Override
	public Optional<LocalObjectRepresentation> representation(
			final WindowCursor curs, final AnyObjectId objectId)
			throws IOException {
		Optional<PackAndOffset> offset = findOffset(objectId);
		if (offset.isPresent()) {
			PackAndOffset packAndOffset = offset.get();
			return Optional.of(packAndOffset.pack.representation(curs,
					packAndOffset.offset));
		}
		return Optional.empty();
	}

	@Override
	public long getObjectSize(WindowCursor curs, AnyObjectId objectId)
			throws IOException {
		Optional<PackAndOffset> offset = findOffset(objectId);
		if (offset.isPresent()) {
			PackAndOffset packAndOffset = offset.get();
			return packAndOffset.pack.getObjectSize(curs, packAndOffset.offset);
		}
		return -1;
	}

	@Override
	public Optional<Pack> getPack(AnyObjectId objectId) {
		Optional<PackAndOffset> offset = findOffset(objectId);
		if (offset.isPresent()) {
			PackAndOffset packAndOffset = offset.get();
			return Optional.ofNullable(packAndOffset.pack);
		}
		return Optional.empty();
	}

	@Override
	public ObjectLoader open(WindowCursor curs, AnyObjectId objectId)
			throws IOException {
		Optional<PackAndOffset> offset = findOffset(objectId);
		if (offset.isPresent()) {
			PackAndOffset packAndOffset = offset.get();
			return packAndOffset.pack.load(curs, packAndOffset.offset);
		}
		return null;
	}

	@Override
	public void resolve(Set<ObjectId> matches, AbbreviatedObjectId id,
			int matchLimit) {
		Set<MultiPackIndexIndex.ObjectIdAndPosition> possibleMatches = new HashSet<>();
		idx.resolve(possibleMatches, id, matchLimit - matches.size());
		for (MultiPackIndexIndex.ObjectIdAndPosition match : possibleMatches) {
			ObjectOffsets.ObjectOffset objectOffset = objectOffsets.getObjectOffset(
					match.getPosition());
			Optional<Pack> optionalPack = getValidPack(
					getPackFileName(objectOffset));
			if (optionalPack.isPresent()) {
				matches.add(match.getId());
			}
		}
	}

	/**
	 * Locate the pack and file offset position within it for the requested
	 * object.
	 *
	 * If all offsets are less than 2^32, then the large offset chunk will not
	 * exist and offsets are stored as in IDX v1. If there is at least one
	 * offset value larger than 2^32-1, then the large offset chunk must exist,
	 * and offsets larger than 2^31-1 must be stored in it instead. If the large
	 * offset chunk exists and the 31st bit is on, then removing that bit
	 * reveals the row in the large offsets containing the 8-byte offset of this
	 * object.
	 *
	 * @param objectId
	 * 		name of the object to locate within the pack.
	 * @return 1: The pack-int-id for the pack storing this object. 2: The
	 * 		offset within the pack. offset of the object's header and compressed
	 * 		content; -1 if the object does not exist in this index and is thus not
	 * 		stored in the associated pack.
	 */
	Optional<PackAndOffset> findOffset(AnyObjectId objectId) {
		Optional<ObjectOffsets.ObjectOffset> objectOffset = getObjectOffset(
				objectId);
		if (objectOffset.isEmpty()) {
			return Optional.empty();
		}
		String packFileName = getPackFileName(objectOffset.get());
		Optional<Pack> optionalPack = getValidPack(packFileName);
		return optionalPack.map(pack -> new PackAndOffset(pack,
				objectOffset.get().getOffset()));
	}

	/**
	 * Obtain the ObjectOffset in the MultiPackIndex.
	 *
	 * @param objectId
	 * 		objectId to read.
	 * @return ObjectOffset from the MultiPackIndex.
	 */
	Optional<ObjectOffsets.ObjectOffset> getObjectOffset(AnyObjectId objectId) {
		int position = idx.findMultiPackIndexPosition(objectId);
		if (position == -1) {
			return Optional.empty();
		}
		return Optional.of(objectOffsets.getObjectOffset(position));
	}

	private Optional<Pack> getValidPack(String packFileName) {
		for (Pack pack : packDir.getPacks()) {
			if (pack.getPackName().equals(new PackFile(packDir.getDirectory(),
					packFileName).getId())) {
				LOG.info("Using pack {} found in MIDX", pack);
				return Optional.of(pack);
			}
		}
		LOG.warn(
				"Ignoring packfile {} registered in MIDX and not found in scanned packs",
				packFileName);
		return Optional.empty();
	}
}

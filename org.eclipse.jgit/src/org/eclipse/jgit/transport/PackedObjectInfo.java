/*
 * Copyright (C) 2008-2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;

/**
 * Description of an object stored in a pack file, including offset.
 * <p>
 * When objects are stored in packs Git needs the ObjectId and the offset
 * (starting position of the object data) to perform random-access reads of
 * objects from the pack. This extension of ObjectId includes the offset.
 */
public class PackedObjectInfo extends ObjectIdOwnerMap.Entry {
	private long offset;

	private int crc;

	private int type = Constants.OBJ_BAD;

	private long sizeBeforeInflating;

	private long fullSize;

	PackedObjectInfo(final long headerOffset, final int packedCRC,
			final AnyObjectId id) {
		super(id);
		offset = headerOffset;
		crc = packedCRC;
	}

	/**
	 * Create a new structure to remember information about an object.
	 *
	 * @param id
	 *            the identity of the object the new instance tracks.
	 */
	public PackedObjectInfo(AnyObjectId id) {
		super(id);
	}

	/**
	 * Get offset in pack when object has been already written
	 *
	 * @return offset in pack when object has been already written, or 0 if it
	 *         has not been written yet
	 */
	public long getOffset() {
		return offset;
	}

	/**
	 * Set the offset in pack when object has been written to.
	 *
	 * @param offset
	 *            offset where written object starts
	 */
	public void setOffset(long offset) {
		this.offset = offset;
	}

	/**
	 * Get the 32 bit CRC checksum for the packed data.
	 *
	 * @return the 32 bit CRC checksum for the packed data.
	 */
	public int getCRC() {
		return crc;
	}

	/**
	 * Record the 32 bit CRC checksum for the packed data.
	 *
	 * @param crc
	 *            checksum of all packed data (including object type code,
	 *            inflated length and delta base reference) as computed by
	 *            {@link java.util.zip.CRC32}.
	 */
	public void setCRC(int crc) {
		this.crc = crc;
	}

	/**
	 * Get the object type.
	 *
	 * @return the object type. The default type is OBJ_BAD, which is considered
	 *         as unknown or invalid type.
	 * @since 4.9
	 */
	public int getType() {
		return type;
	}

	/**
	 * Record the object type if applicable.
	 *
	 * @param type
	 *            the object type.
	 * @since 4.9
	 */
	public void setType(int type) {
		this.type = type;
	}

	void setSize(long sizeBeforeInflating) {
		this.sizeBeforeInflating = sizeBeforeInflating;
	}

	long getSize() {
		return sizeBeforeInflating;
	}

	/**
	 * Size before deltifying
	 *
	 * @param size
	 *            size of the object in bytes, without deltifying
	 */
	public void setFullSize(long size) {
		this.fullSize = size;
	}

	/**
	 * @return size of the fully formed object
	 */
	public long getFullSize() {
		return fullSize;
	}
}

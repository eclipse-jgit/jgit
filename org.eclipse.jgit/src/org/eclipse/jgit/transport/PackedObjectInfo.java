/*
 * Copyright (C) 2008-2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
	public PackedObjectInfo(final AnyObjectId id) {
		super(id);
	}

	/**
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
	public void setOffset(final long offset) {
		this.offset = offset;
	}

	/**
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
	public void setCRC(final int crc) {
		this.crc = crc;
	}

	/**
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
}

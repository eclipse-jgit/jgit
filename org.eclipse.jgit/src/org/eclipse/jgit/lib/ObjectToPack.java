/*
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
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

package org.eclipse.jgit.lib;

import java.io.IOException;

import org.eclipse.jgit.transport.PackedObjectInfo;

/**
 * Class holding information about object that is going to be packed by
 * {@link PackWriter}. Information include object representation in a
 * pack-file and object status.
 *
 */
class ObjectToPack extends PackedObjectInfo {
	/** Other object being packed that this will delta against. */
	private ObjectId deltaBase;

	/** Pack to reuse compressed data from, otherwise null. */
	private PackFile copyFromPack;

	/** Offset of the object's header in {@link #copyFromPack}. */
	private long copyOffset;

	/**
	 * Bit field, from bit 0 to bit 31:
	 * <ul>
	 * <li>1 bit: wantWrite</li>
	 * <li>3 bits: type</li>
	 * <li>28 bits: deltaDepth</li>
	 * </ul>
	 */
	private int flags;

	/**
	 * Construct object for specified object id. <br/> By default object is
	 * marked as not written and non-delta packed (as a whole object).
	 *
	 * @param src
	 *            object id of object for packing
	 * @param type
	 *            real type code of the object, not its in-pack type.
	 */
	ObjectToPack(AnyObjectId src, final int type) {
		super(src);
		flags |= type << 1;
	}

	/**
	 * @return delta base object id if object is going to be packed in delta
	 *         representation; null otherwise - if going to be packed as a
	 *         whole object.
	 */
	ObjectId getDeltaBaseId() {
		return deltaBase;
	}

	/**
	 * @return delta base object to pack if object is going to be packed in
	 *         delta representation and delta is specified as object to
	 *         pack; null otherwise - if going to be packed as a whole
	 *         object or delta base is specified only as id.
	 */
	ObjectToPack getDeltaBase() {
		if (deltaBase instanceof ObjectToPack)
			return (ObjectToPack) deltaBase;
		return null;
	}

	/**
	 * Set delta base for the object. Delta base set by this method is used
	 * by {@link PackWriter} to write object - determines its representation
	 * in a created pack.
	 *
	 * @param deltaBase
	 *            delta base object or null if object should be packed as a
	 *            whole object.
	 *
	 */
	void setDeltaBase(ObjectId deltaBase) {
		this.deltaBase = deltaBase;
	}

	void clearDeltaBase() {
		this.deltaBase = null;
	}

	/**
	 * @return true if object is going to be written as delta; false
	 *         otherwise.
	 */
	boolean isDeltaRepresentation() {
		return deltaBase != null;
	}

	/**
	 * Check if object is already written in a pack. This information is
	 * used to achieve delta-base precedence in a pack file.
	 *
	 * @return true if object is already written; false otherwise.
	 */
	boolean isWritten() {
		return getOffset() != 0;
	}

	boolean isCopyable() {
		return copyFromPack != null;
	}

	PackedObjectLoader getCopyLoader(WindowCursor curs) throws IOException {
		return copyFromPack.resolveBase(curs, copyOffset);
	}

	void setCopyFromPack(PackedObjectLoader loader) {
		this.copyFromPack = loader.pack;
		this.copyOffset = loader.objectOffset;
	}

	void clearSourcePack() {
		copyFromPack = null;
	}

	int getType() {
		return (flags>>1) & 0x7;
	}

	int getDeltaDepth() {
		return flags >>> 4;
	}

	void updateDeltaDepth() {
		final int d;
		if (deltaBase instanceof ObjectToPack)
			d = ((ObjectToPack) deltaBase).getDeltaDepth() + 1;
		else if (deltaBase != null)
			d = 1;
		else
			d = 0;
		flags = (d << 4) | flags & 0x15;
	}

	boolean wantWrite() {
		return (flags & 1) == 1;
	}

	void markWantWrite() {
		flags |= 1;
	}
}

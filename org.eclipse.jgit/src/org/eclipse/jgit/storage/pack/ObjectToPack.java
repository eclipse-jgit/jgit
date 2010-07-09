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

package org.eclipse.jgit.storage.pack;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.transport.PackedObjectInfo;

/**
 * Per-object state used by {@link PackWriter}.
 * <p>
 * {@code PackWriter} uses this class to track the things it needs to include in
 * the newly generated pack file, and how to efficiently obtain the raw data for
 * each object as they are written to the output stream.
 */
public class ObjectToPack extends PackedObjectInfo {
	private static final int WANT_WRITE = 1 << 0;

	private static final int REUSE_AS_IS = 1 << 1;

	private static final int DO_NOT_DELTA = 1 << 2;

	private static final int TYPE_SHIFT = 5;

	private static final int DELTA_SHIFT = 8;

	private static final int NON_DELTA_MASK = 0xff;

	/** Other object being packed that this will delta against. */
	private ObjectId deltaBase;

	/**
	 * Bit field, from bit 0 to bit 31:
	 * <ul>
	 * <li>1 bit: wantWrite</li>
	 * <li>1 bit: canReuseAsIs</li>
	 * <li>1 bit: doNotDelta</li>
	 * <li>2 bits: unused</li>
	 * <li>3 bits: type</li>
	 * <li>--</li>
	 * <li>24 bits: deltaDepth</li>
	 * </ul>
	 */
	private int flags;

	/** Hash of the object's tree path. */
	private int pathHash;

	/**
	 * Construct for the specified object id.
	 *
	 * @param src
	 *            object id of object for packing
	 * @param type
	 *            real type code of the object, not its in-pack type.
	 */
	public ObjectToPack(AnyObjectId src, final int type) {
		super(src);
		flags = type << TYPE_SHIFT;
	}

	/**
	 * Construct for the specified object.
	 *
	 * @param obj
	 *            identity of the object that will be packed. The object's
	 *            parsed status is undefined here. Implementers must not rely on
	 *            the object being parsed.
	 */
	public ObjectToPack(RevObject obj) {
		this(obj, obj.getType());
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

	int getType() {
		return (flags >> TYPE_SHIFT) & 0x7;
	}

	int getDeltaDepth() {
		return flags >>> DELTA_SHIFT;
	}

	void setDeltaDepth(int d) {
		flags = (d << DELTA_SHIFT) | (flags & NON_DELTA_MASK);
	}

	boolean wantWrite() {
		return (flags & WANT_WRITE) != 0;
	}

	void markWantWrite() {
		flags |= WANT_WRITE;
	}

	boolean isReuseAsIs() {
		return (flags & REUSE_AS_IS) != 0;
	}

	void setReuseAsIs() {
		flags |= REUSE_AS_IS;
	}

	/**
	 * Forget the reuse information previously stored.
	 * <p>
	 * Implementations may subclass this method, but they must also invoke the
	 * super version with {@code super.clearReuseAsIs()} to ensure the flag is
	 * properly cleared for the writer.
	 */
	protected void clearReuseAsIs() {
		flags &= ~REUSE_AS_IS;
	}

	boolean isDoNotDelta() {
		return (flags & DO_NOT_DELTA) != 0;
	}

	void setDoNotDelta(boolean noDelta) {
		if (noDelta)
			flags |= DO_NOT_DELTA;
		else
			flags &= ~DO_NOT_DELTA;
	}

	int getFormat() {
		if (isReuseAsIs()) {
			if (isDeltaRepresentation())
				return StoredObjectRepresentation.PACK_DELTA;
			return StoredObjectRepresentation.PACK_WHOLE;
		}
		return StoredObjectRepresentation.FORMAT_OTHER;
	}

	// Overload weight into CRC since we don't need them at the same time.
	int getWeight() {
		return getCRC();
	}

	void setWeight(int weight) {
		setCRC(weight);
	}

	int getPathHash() {
		return pathHash;
	}

	void setPathHash(int hc) {
		pathHash = hc;
	}

	/**
	 * Remember a specific representation for reuse at a later time.
	 * <p>
	 * Implementers should remember the representation chosen, so it can be
	 * reused at a later time. {@link PackWriter} may invoke this method
	 * multiple times for the same object, each time saving the current best
	 * representation found.
	 *
	 * @param ref
	 *            the object representation.
	 */
	public void select(StoredObjectRepresentation ref) {
		// Empty by default.
	}

	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("ObjectToPack[");
		buf.append(Constants.typeString(getType()));
		buf.append(" ");
		buf.append(name());
		if (wantWrite())
			buf.append(" wantWrite");
		if (isReuseAsIs())
			buf.append(" reuseAsIs");
		if (isDoNotDelta())
			buf.append(" doNotDelta");
		if (getDeltaDepth() > 0)
			buf.append(" depth=" + getDeltaDepth());
		if (isDeltaRepresentation()) {
			if (getDeltaBase() != null)
				buf.append(" base=inpack:" + getDeltaBase().name());
			else
				buf.append(" base=edge:" + getDeltaBaseId().name());
		}
		if (isWritten())
			buf.append(" offset=" + getOffset());
		buf.append("]");
		return buf.toString();
	}
}

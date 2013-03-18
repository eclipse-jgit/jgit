/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.internal.storage.pack;

import org.eclipse.jgit.lib.ObjectId;

/**
 * An object representation {@link PackWriter} can consider for packing.
 */
public class StoredObjectRepresentation {
	/** Special unknown value for {@link #getWeight()}. */
	public static final int WEIGHT_UNKNOWN = Integer.MAX_VALUE;

	/** Stored in pack format, as a delta to another object. */
	public static final int PACK_DELTA = 0;

	/** Stored in pack format, without delta. */
	public static final int PACK_WHOLE = 1;

	/** Only available after inflating to canonical format. */
	public static final int FORMAT_OTHER = 2;

	/**
	 * @return relative size of this object's packed form. The special value
	 *         {@link #WEIGHT_UNKNOWN} can be returned to indicate the
	 *         implementation doesn't know, or cannot supply the weight up
	 *         front.
	 */
	public int getWeight() {
		return WEIGHT_UNKNOWN;
	}

	/**
	 * @return the storage format type, which must be one of
	 *         {@link #PACK_DELTA}, {@link #PACK_WHOLE}, or
	 *         {@link #FORMAT_OTHER}.
	 */
	public int getFormat() {
		return FORMAT_OTHER;
	}

	/**
	 * @return identity of the object this delta applies to in order to recover
	 *         the original object content. This method should only be called if
	 *         {@link #getFormat()} returned {@link #PACK_DELTA}.
	 */
	public ObjectId getDeltaBase() {
		return null;
	}

	/**
	 * @return whether the current representation of the object has had delta
	 *         compression attempted on it.
	 */
	public boolean wasDeltaAttempted() {
		int fmt = getFormat();
		return fmt == PACK_DELTA || fmt == PACK_WHOLE;
	}
}

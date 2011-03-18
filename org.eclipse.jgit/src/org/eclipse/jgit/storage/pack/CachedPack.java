/*
 * Copyright (C) 2011, Google Inc.
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

import java.io.IOException;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;

/** Describes a pack file {@link ObjectReuseAsIs} can append onto a stream. */
public abstract class CachedPack {
	/**
	 * Objects that start this pack.
	 * <p>
	 * All objects reachable from the tips are contained within this pack. If
	 * {@link PackWriter} is going to include everything reachable from all of
	 * these objects, this cached pack is eligible to be appended directly onto
	 * the output pack stream.
	 *
	 * @return the tip objects that describe this pack.
	 */
	public abstract Set<ObjectId> getTips();

	/**
	 * Get the number of objects in this pack.
	 *
	 * @return the total object count for the pack.
	 * @throws IOException
	 *             if the object count cannot be read.
	 */
	public abstract long getObjectCount() throws IOException;

	/**
	 * Get the number of delta objects stored in this pack.
	 * <p>
	 * This is an optional method, not every cached pack storage system knows
	 * the precise number of deltas stored within the pack. This number must be
	 * smaller than {@link #getObjectCount()} as deltas are not supposed to span
	 * across pack files.
	 * <p>
	 * This method must be fast, if the only way to determine delta counts is to
	 * scan the pack file's contents one object at a time, implementors should
	 * return 0 and avoid the high cost of the scan.
	 *
	 * @return the number of deltas; 0 if the number is not known or there are
	 *         no deltas.
	 * @throws IOException
	 *             if the delta count cannot be read.
	 */
	public long getDeltaCount() throws IOException {
		return 0;
	}

	/**
	 * Determine if this pack contains the object representation given.
	 * <p>
	 * PackWriter uses this method during the finding sources phase to prune
	 * away any objects from the leading thin-pack that already appear within
	 * this pack and should not be sent twice.
	 * <p>
	 * Implementors are strongly encouraged to rely on looking at {@code rep}
	 * only and using its internal state to decide if this object is within this
	 * pack. Implementors should ensure a representation from this cached pack
	 * is tested as part of
	 * {@link ObjectReuseAsIs#selectObjectRepresentation(PackWriter, org.eclipse.jgit.lib.ProgressMonitor, Iterable)}
	 * , ensuring this method would eventually return true if the object would
	 * be included by this cached pack.
	 *
	 * @param obj
	 *            the object being packed. Can be used as an ObjectId.
	 * @param rep
	 *            representation from the {@link ObjectReuseAsIs} instance that
	 *            originally supplied this CachedPack.
	 * @return true if this pack contains this object.
	 */
	public abstract boolean hasObject(ObjectToPack obj,
			StoredObjectRepresentation rep);
}

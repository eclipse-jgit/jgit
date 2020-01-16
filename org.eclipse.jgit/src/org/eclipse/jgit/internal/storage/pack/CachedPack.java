/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.pack;

import java.io.IOException;

/**
 * Describes a pack file
 * {@link org.eclipse.jgit.internal.storage.pack.ObjectReuseAsIs} can append
 * onto a stream.
 */
public abstract class CachedPack {
	/**
	 * Get the number of objects in this pack.
	 *
	 * @return the total object count for the pack.
	 * @throws java.io.IOException
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
	 * @throws java.io.IOException
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
	 * {@link org.eclipse.jgit.internal.storage.pack.ObjectReuseAsIs#selectObjectRepresentation(PackWriter, org.eclipse.jgit.lib.ProgressMonitor, Iterable)}
	 * , ensuring this method would eventually return true if the object would
	 * be included by this cached pack.
	 *
	 * @param obj
	 *            the object being packed. Can be used as an ObjectId.
	 * @param rep
	 *            representation from the
	 *            {@link org.eclipse.jgit.internal.storage.pack.ObjectReuseAsIs}
	 *            instance that originally supplied this CachedPack.
	 * @return true if this pack contains this object.
	 */
	public abstract boolean hasObject(ObjectToPack obj,
			StoredObjectRepresentation rep);
}

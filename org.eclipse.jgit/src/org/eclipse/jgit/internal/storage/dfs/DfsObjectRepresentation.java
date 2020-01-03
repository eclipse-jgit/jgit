/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import org.eclipse.jgit.internal.storage.pack.StoredObjectRepresentation;
import org.eclipse.jgit.lib.ObjectId;

class DfsObjectRepresentation extends StoredObjectRepresentation {
	final DfsPackFile pack;
	int format;
	long offset;
	long length;
	ObjectId baseId;

	DfsObjectRepresentation(DfsPackFile pack) {
		this.pack = pack;
	}

	/** {@inheritDoc} */
	@Override
	public int getFormat() {
		return format;
	}

	/** {@inheritDoc} */
	@Override
	public int getWeight() {
		return (int) Math.min(length, Integer.MAX_VALUE);
	}

	/** {@inheritDoc} */
	@Override
	public ObjectId getDeltaBase() {
		return baseId;
	}

	/** {@inheritDoc} */
	@Override
	public boolean wasDeltaAttempted() {
		switch (pack.getPackDescription().getPackSource()) {
		case GC:
		case GC_REST:
		case GC_TXN:
			return true;
		default:
			return false;
		}
	}
}

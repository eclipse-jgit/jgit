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

import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.StoredObjectRepresentation;
import org.eclipse.jgit.lib.AnyObjectId;

/** {@link ObjectToPack} for {@link DfsObjDatabase}. */
class DfsObjectToPack extends ObjectToPack {
	private static final int FLAG_FOUND = 1 << 0;

	/** Pack to reuse compressed data from, otherwise null. */
	DfsPackFile pack;

	/** Offset of the object's header in {@link #pack}. */
	long offset;

	/** Length of the data section of the object. */
	long length;

	DfsObjectToPack(AnyObjectId src, int type) {
		super(src, type);
	}

	final boolean isFound() {
		return isExtendedFlag(FLAG_FOUND);
	}

	final void setFound() {
		setExtendedFlag(FLAG_FOUND);
	}

	/** {@inheritDoc} */
	@Override
	protected void clearReuseAsIs() {
		super.clearReuseAsIs();
		pack = null;
	}

	/** {@inheritDoc} */
	@Override
	public void select(StoredObjectRepresentation ref) {
		DfsObjectRepresentation ptr = (DfsObjectRepresentation) ref;
		this.pack = ptr.pack;
		this.offset = ptr.offset;
		this.length = ptr.length;
	}
}

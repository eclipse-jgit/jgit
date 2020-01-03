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

import java.io.IOException;

import org.eclipse.jgit.internal.storage.pack.CachedPack;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.internal.storage.pack.StoredObjectRepresentation;

/**
 * A DfsPackFile available for reuse as-is.
 */
public class DfsCachedPack extends CachedPack {
	private final DfsPackFile pack;

	DfsCachedPack(DfsPackFile pack) {
		this.pack = pack;
	}

	/**
	 * @return the pack passed to the constructor
	 */
	public DfsPackFile getPackFile() {
		return pack;
	}

	/**
	 * Get the description of the pack.
	 *
	 * @return the description of the pack.
	 */
	public DfsPackDescription getPackDescription() {
		return pack.getPackDescription();
	}

	/** {@inheritDoc} */
	@Override
	public long getObjectCount() throws IOException {
		return getPackDescription().getObjectCount();
	}

	/** {@inheritDoc} */
	@Override
	public long getDeltaCount() throws IOException {
		return getPackDescription().getDeltaCount();
	}

	/** {@inheritDoc} */
	@Override
	public boolean hasObject(ObjectToPack obj, StoredObjectRepresentation rep) {
		return ((DfsObjectRepresentation) rep).pack == pack;
	}

	void copyAsIs(PackOutputStream out, DfsReader ctx) throws IOException {
		pack.copyPackAsIs(out, ctx);
	}
}

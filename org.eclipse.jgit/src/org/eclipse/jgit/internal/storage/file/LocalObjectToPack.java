/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.StoredObjectRepresentation;
import org.eclipse.jgit.lib.AnyObjectId;

/** {@link ObjectToPack} for {@link ObjectDirectory}. */
class LocalObjectToPack extends ObjectToPack {
	/** Pack to reuse compressed data from, otherwise null. */
	PackFile pack;

	/** Offset of the object's header in {@link #pack}. */
	long offset;

	/** Length of the data section of the object. */
	long length;

	LocalObjectToPack(AnyObjectId src, int type) {
		super(src, type);
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
		LocalObjectRepresentation ptr = (LocalObjectRepresentation) ref;
		this.pack = ptr.pack;
		this.offset = ptr.offset;
		this.length = ptr.length;
	}
}

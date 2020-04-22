/*
 * Copyright (c) 2020, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.pack;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

/**
 * A commit object for which a bitmap index should be built.
 */
public final class BitmapCommit extends ObjectId {
	private final boolean reuseWalker;
	private final int flags;

	BitmapCommit(AnyObjectId objectId, boolean reuseWalker, int flags) {
		super(objectId);
		this.reuseWalker = reuseWalker;
		this.flags = flags;
	}

	boolean isReuseWalker() {
		return reuseWalker;
	}

	int getFlags() {
		return flags;
	}
}
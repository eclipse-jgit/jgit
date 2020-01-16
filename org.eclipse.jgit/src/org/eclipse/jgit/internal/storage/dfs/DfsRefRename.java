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

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;

final class DfsRefRename extends RefRename {
	DfsRefRename(RefUpdate src, RefUpdate dst) {
		super(src, dst);
	}

	/** {@inheritDoc} */
	@Override
	protected Result doRename() throws IOException {
		// TODO Correctly handle renaming foo/bar to foo.
		// TODO Batch these together into one log update.

		destination.setExpectedOldObjectId(ObjectId.zeroId());
		destination.setNewObjectId(source.getRef().getObjectId());
		switch (destination.update()) {
		case NEW:
			source.delete();
			return Result.RENAMED;

		default:
			return destination.getResult();
		}
	}
}

/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.reftable;

import java.io.IOException;

import org.eclipse.jgit.lib.ReflogEntry;

/** Empty {@link LogCursor} with no results. */
class EmptyLogCursor extends LogCursor {
	/** {@inheritDoc} */
	@Override
	public boolean next() throws IOException {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public String getRefName() {
		return null;
	}

	/** {@inheritDoc} */
	@Override
	public long getUpdateIndex() {
		return 0;
	}

	/** {@inheritDoc} */
	@Override
	public ReflogEntry getReflogEntry() {
		return null;
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		// Do nothing.
	}
}

/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.reftree;

import java.io.IOException;

import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

/** Update that always rejects with {@code LOCK_FAILURE}. */
class AlwaysFailUpdate extends RefUpdate {
	private final RefTreeDatabase refdb;

	AlwaysFailUpdate(RefTreeDatabase refdb, String name) {
		super(new ObjectIdRef.Unpeeled(Ref.Storage.NEW, name, null));
		this.refdb = refdb;
		setCheckConflicting(false);
	}

	/** {@inheritDoc} */
	@Override
	protected RefDatabase getRefDatabase() {
		return refdb;
	}

	/** {@inheritDoc} */
	@Override
	protected Repository getRepository() {
		return refdb.getRepository();
	}

	/** {@inheritDoc} */
	@Override
	protected boolean tryLock(boolean deref) throws IOException {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	protected void unlock() {
		// No locks are held here.
	}

	/** {@inheritDoc} */
	@Override
	protected Result doUpdate(Result desiredResult) {
		return Result.LOCK_FAILURE;
	}

	/** {@inheritDoc} */
	@Override
	protected Result doDelete(Result desiredResult) {
		return Result.LOCK_FAILURE;
	}

	/** {@inheritDoc} */
	@Override
	protected Result doLink(String target) {
		return Result.LOCK_FAILURE;
	}
}

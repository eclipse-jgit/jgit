/*
 * Copyright (C) 2019, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.reftable;

import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;

/**
 * Implement the ReflogReader interface for a reflog stored in reftable.
 */
public class ReftableReflogReader implements ReflogReader {
	private final Lock lock;

	private final Reftable reftable;

	private final String refname;

	ReftableReflogReader(Lock lock, Reftable merged, String refname) {
		this.lock = lock;
		this.reftable = merged;
		this.refname = refname;
	}

	/** {@inheritDoc} */
	@Override
	public ReflogEntry getLastEntry() throws IOException {
		lock.lock();
		try {
			LogCursor cursor = reftable.seekLog(refname);
			return cursor.next() ? cursor.getReflogEntry() : null;
		} finally {
			lock.unlock();
		}
	}

	/** {@inheritDoc} */
	@Override
	public List<ReflogEntry> getReverseEntries() throws IOException {
		return getReverseEntries(Integer.MAX_VALUE);
	}

	/** {@inheritDoc} */
	@Override
	public ReflogEntry getReverseEntry(int number) throws IOException {
		lock.lock();
		try {
			LogCursor cursor = reftable.seekLog(refname);
			while (true) {
				if (!cursor.next() || number < 0) {
					return null;
				}
				if (number == 0) {
					return cursor.getReflogEntry();
				}
				number--;
			}
		} finally {
			lock.unlock();
		}
	}

	/** {@inheritDoc} */
	@Override
	public List<ReflogEntry> getReverseEntries(int max) throws IOException {
		lock.lock();
		try {
			LogCursor cursor = reftable.seekLog(refname);

			List<ReflogEntry> result = new ArrayList<>();
			while (cursor.next() && result.size() < max) {
				result.add(cursor.getReflogEntry());
			}

			return result;
		} finally {
			lock.unlock();
		}
	}
}

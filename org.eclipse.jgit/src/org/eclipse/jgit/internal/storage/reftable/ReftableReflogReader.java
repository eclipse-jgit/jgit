/*
 * Copyright (C) 2019, Google LLC
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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

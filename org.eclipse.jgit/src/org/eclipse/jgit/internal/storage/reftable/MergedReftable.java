/*
 * Copyright (C) 2017, Google Inc.
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

import java.io.IOException;
import java.util.List;
import java.util.PriorityQueue;

import org.eclipse.jgit.lib.Ref;

/**
 * Merges multiple reference tables together.
 * <p>
 * A {@link MergedReftable} merge-joins multiple {@link ReftableReader} on the
 * fly. Tables higher/later in the stack shadow lower/earlier tables, hiding
 * references that been updated/replaced.
 * <p>
 * By default deleted references are skipped and not returned to the caller.
 * {@link #setIncludeDeletes(boolean)} can be used to modify this behavior if
 * the caller needs to preserve deletions during partial compaction.
 * <p>
 * A {@code MergedReftable} is not thread-safe.
 */
public class MergedReftable extends RefCursor {
	private final TableRef[] tables;
	private final PriorityQueue<TableRef> queue;

	private boolean includeDeletes;
	private Ref ref;

	/**
	 * Initialize a merged table reader.
	 * <p>
	 * The tables in {@code tableStack} will be closed when this
	 * {@code MergedReftable} is closed.
	 *
	 * @param tableStack
	 *            stack of tables to read from. The base of the stack is at
	 *            index 0, the most recent should be at the top of the stack at
	 *            {@code tableStack.size() - 1}. The top of the stack (higher
	 *            index) shadows the base of the stack (lower index).
	 */
	public MergedReftable(List<RefCursor> tableStack) {
		tables = new TableRef[tableStack.size()];
		for (int i = 0; i < tableStack.size(); i++) {
			tables[i] = new TableRef(tableStack.get(i), i);
		}
		queue = new PriorityQueue<>(tables.length, TableRef::compare);
	}

	/**
	 * @param deletes
	 *            if {@code true} deleted references will be returned. If
	 *            {@code false} (default behavior), deleted references will be
	 *            skipped, and not returned.
	 * @return {@code this}
	 */
	public MergedReftable setIncludeDeletes(boolean deletes) {
		includeDeletes = deletes;
		return this;
	}

	@Override
	public void seekToFirstRef() throws IOException {
		queue.clear();
		for (TableRef t : tables) {
			t.table.seekToFirstRef();
			next(t);
		}
	}

	@Override
	public void seek(String refName) throws IOException {
		queue.clear();
		for (TableRef t : tables) {
			t.table.seek(refName);
			next(t);
		}
	}

	@Override
	public boolean next() throws IOException {
		for (;;) {
			TableRef t = queue.poll();
			if (t == null) {
				return false;
			}

			ref = t.table.getRef();
			boolean include = includeDeletes || !t.table.wasDeleted();
			skipShadowedRefs();
			next(t);
			if (include) {
				return true;
			}
		}
	}

	private void skipShadowedRefs() throws IOException {
		for (;;) {
			TableRef t = queue.peek();
			if (t == null) {
				break;
			}

			Ref tr = t.table.getRef();
			if (!tr.getName().equals(ref.getName())) {
				break;
			}

			next(queue.remove());
		}
	}

	private void next(TableRef t) throws IOException {
		if (t.table.next()) {
			queue.add(t);
		}
	}

	@Override
	public Ref getRef() {
		return ref;
	}

	@Override
	public void close() throws IOException {
		for (TableRef t : tables) {
			t.table.close();
		}
	}

	private static class TableRef {
		static int compare(TableRef ta, TableRef tb) {
			Ref a = ta.table.getRef();
			Ref b = tb.table.getRef();
			int cmp = a.getName().compareTo(b.getName());
			if (cmp == 0) {
				// higher index shadows lower index, so higher index first.
				cmp = tb.stackIdx - ta.stackIdx;
			}
			return cmp;
		}

		final RefCursor table;
		final int stackIdx;

		TableRef(RefCursor table, int stackIdx) {
			this.table = table;
			this.stackIdx = stackIdx;
		}
	}
}

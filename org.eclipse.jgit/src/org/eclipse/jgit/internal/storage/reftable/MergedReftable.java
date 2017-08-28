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

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.ReflogEntry;

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
public class MergedReftable extends Reftable {
	private final Reftable[] tables;

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
	public MergedReftable(List<Reftable> tableStack) {
		tables = tableStack.toArray(new Reftable[0]);

		// Tables must expose deletes to this instance to correctly
		// shadow references from lower tables.
		for (Reftable t : tables) {
			t.setIncludeDeletes(true);
		}
	}

	@Override
	public RefCursor allRefs() throws IOException {
		MergedRefCursor m = new MergedRefCursor();
		for (int i = 0; i < tables.length; i++) {
			m.add(new RefQueueEntry(tables[i].allRefs(), i));
		}
		return m;
	}

	@Override
	public RefCursor seekRef(String name) throws IOException {
		MergedRefCursor m = new MergedRefCursor();
		for (int i = 0; i < tables.length; i++) {
			m.add(new RefQueueEntry(tables[i].seekRef(name), i));
		}
		return m;
	}

	@Override
	public RefCursor byObjectId(AnyObjectId name) throws IOException {
		MergedRefCursor m = new MergedRefCursor();
		for (int i = 0; i < tables.length; i++) {
			m.add(new RefQueueEntry(tables[i].byObjectId(name), i));
		}
		return m;
	}

	@Override
	public LogCursor allLogs() throws IOException {
		MergedLogCursor m = new MergedLogCursor();
		for (int i = 0; i < tables.length; i++) {
			m.add(new LogQueueEntry(tables[i].allLogs(), i));
		}
		return m;
	}

	@Override
	public LogCursor seekLog(String refName, long updateIdx)
			throws IOException {
		MergedLogCursor m = new MergedLogCursor();
		for (int i = 0; i < tables.length; i++) {
			m.add(new LogQueueEntry(tables[i].seekLog(refName, updateIdx), i));
		}
		return m;
	}

	@Override
	public void close() throws IOException {
		for (Reftable t : tables) {
			t.close();
		}
	}

	int queueSize() {
		return Math.max(1, tables.length);
	}

	private class MergedRefCursor extends RefCursor {
		private final PriorityQueue<RefQueueEntry> queue;
		private RefQueueEntry head;
		private Ref ref;
		private long updateIndex;

		MergedRefCursor() {
			queue = new PriorityQueue<>(queueSize(), RefQueueEntry::compare);
		}

		void add(RefQueueEntry t) throws IOException {
			// Common case is many iterations over the same RefQueueEntry
			// for the bottom of the stack (scanning all refs). Its almost
			// always less than the top of the queue. Avoid the queue's
			// O(log N) insertion and removal costs for this common case.
			if (!t.rc.next()) {
				t.rc.close();
			} else if (head == null) {
				RefQueueEntry p = queue.peek();
				if (p == null || RefQueueEntry.compare(t, p) < 0) {
					head = t;
				} else {
					head = queue.poll();
					queue.add(t);
				}
			} else if (RefQueueEntry.compare(t, head) > 0) {
				queue.add(t);
			} else {
				queue.add(head);
				head = t;
			}
		}

		@Override
		public boolean next() throws IOException {
			for (;;) {
				RefQueueEntry t = poll();
				if (t == null) {
					return false;
				}

				ref = t.rc.getRef();
				updateIndex = t.rc.getUpdateIndex();
				boolean include = includeDeletes || !t.rc.wasDeleted();
				skipShadowedRefs(ref.getName());
				add(t);
				if (include) {
					return true;
				}
			}
		}

		private RefQueueEntry poll() {
			RefQueueEntry e = head;
			if (e != null) {
				head = null;
				return e;
			}
			return queue.poll();
		}

		private void skipShadowedRefs(String name) throws IOException {
			for (;;) {
				RefQueueEntry t = head != null ? head : queue.peek();
				if (t != null && name.equals(t.name())) {
					add(poll());
				} else {
					break;
				}
			}
		}

		@Override
		public Ref getRef() {
			return ref;
		}

		@Override
		public long getUpdateIndex() {
			return updateIndex;
		}

		@Override
		public void close() {
			if (head != null) {
				head.rc.close();
				head = null;
			}
			while (!queue.isEmpty()) {
				queue.remove().rc.close();
			}
		}
	}

	private static class RefQueueEntry {
		static int compare(RefQueueEntry a, RefQueueEntry b) {
			int cmp = a.name().compareTo(b.name());
			if (cmp == 0) {
				// higher updateIndex shadows lower updateIndex.
				cmp = Long.signum(b.updateIndex() - a.updateIndex());
			}
			if (cmp == 0) {
				// higher index shadows lower index, so higher index first.
				cmp = b.stackIdx - a.stackIdx;
			}
			return cmp;
		}

		final RefCursor rc;
		final int stackIdx;

		RefQueueEntry(RefCursor rc, int stackIdx) {
			this.rc = rc;
			this.stackIdx = stackIdx;
		}

		String name() {
			return rc.getRef().getName();
		}

		long updateIndex() {
			return rc.getUpdateIndex();
		}
	}

	private class MergedLogCursor extends LogCursor {
		private final PriorityQueue<LogQueueEntry> queue;
		private String refName;
		private long updateIndex;
		private ReflogEntry entry;

		MergedLogCursor() {
			queue = new PriorityQueue<>(queueSize(), LogQueueEntry::compare);
		}

		void add(LogQueueEntry t) throws IOException {
			if (t.lc.next()) {
				queue.add(t);
			} else {
				t.lc.close();
			}
		}

		@Override
		public boolean next() throws IOException {
			for (;;) {
				LogQueueEntry t = queue.poll();
				if (t == null) {
					return false;
				}

				refName = t.lc.getRefName();
				updateIndex = t.lc.getUpdateIndex();
				entry = t.lc.getReflogEntry();
				boolean include = includeDeletes || entry != null;
				skipShadowed(refName, updateIndex);
				add(t);
				if (include) {
					return true;
				}
			}
		}

		private void skipShadowed(String name, long index) throws IOException {
			for (;;) {
				LogQueueEntry t = queue.peek();
				if (t != null && name.equals(t.name()) && index == t.index()) {
					add(queue.remove());
				} else {
					break;
				}
			}
		}

		@Override
		public String getRefName() {
			return refName;
		}

		@Override
		public long getUpdateIndex() {
			return updateIndex;
		}

		@Override
		public ReflogEntry getReflogEntry() {
			return entry;
		}

		@Override
		public void close() {
			while (!queue.isEmpty()) {
				queue.remove().lc.close();
			}
		}
	}

	private static class LogQueueEntry {
		static int compare(LogQueueEntry a, LogQueueEntry b) {
			int cmp = a.name().compareTo(b.name());
			if (cmp == 0) {
				// higher update index sorts first.
				cmp = Long.signum(b.index() - a.index());
			}
			if (cmp == 0) {
				// higher index comes first.
				cmp = b.stackIdx - a.stackIdx;
			}
			return cmp;
		}

		final LogCursor lc;
		final int stackIdx;

		LogQueueEntry(LogCursor lc, int stackIdx) {
			this.lc = lc;
			this.stackIdx = stackIdx;
		}

		String name() {
			return lc.getRefName();
		}

		long index() {
			return lc.getUpdateIndex();
		}
	}
}

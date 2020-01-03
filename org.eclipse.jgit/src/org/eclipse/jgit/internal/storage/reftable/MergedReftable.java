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
import java.util.List;
import java.util.PriorityQueue;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.ReflogEntry;

/**
 * Merges multiple reference tables together.
 * <p>
 * A {@link org.eclipse.jgit.internal.storage.reftable.MergedReftable}
 * merge-joins multiple
 * {@link org.eclipse.jgit.internal.storage.reftable.ReftableReader} on the fly.
 * Tables higher/later in the stack shadow lower/earlier tables, hiding
 * references that been updated/replaced.
 * <p>
 * By default deleted references are skipped and not returned to the caller.
 * {@link #setIncludeDeletes(boolean)} can be used to modify this behavior if
 * the caller needs to preserve deletions during partial compaction.
 * <p>
 * A {@code MergedReftable} is not thread-safe.
 */
public class MergedReftable extends Reftable {
	private final ReftableReader[] tables;

	/**
	 * Initialize a merged table reader.
	 * <p>
	 *
	 * @param tableStack
	 *            stack of tables to read from. The base of the stack is at
	 *            index 0, the most recent should be at the top of the stack at
	 *            {@code tableStack.size() - 1}. The top of the stack (higher
	 *            index) shadows the base of the stack (lower index).
	 */
	public MergedReftable(List<ReftableReader> tableStack) {
		tables = tableStack.toArray(new ReftableReader[0]);

		// Tables must expose deletes to this instance to correctly
		// shadow references from lower tables.
		for (ReftableReader t : tables) {
			t.setIncludeDeletes(true);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long maxUpdateIndex() throws IOException {
		return tables.length > 0 ? tables[tables.length - 1].maxUpdateIndex()
				: 0;
	}

	/** {@inheritDoc} */
	@Override
	public boolean hasObjectMap() throws IOException {
		boolean has = true;
		for (int i = 0; has && i < tables.length; i++) {
			has = has && tables[i].hasObjectMap();
		}
		return has;
	}

	/** {@inheritDoc} */
	@Override
	public RefCursor allRefs() throws IOException {
		MergedRefCursor m = new MergedRefCursor();
		for (int i = 0; i < tables.length; i++) {
			m.add(new RefQueueEntry(tables[i].allRefs(), i));
		}
		return m;
	}

	/** {@inheritDoc} */
	@Override
	public RefCursor seekRef(String name) throws IOException {
		MergedRefCursor m = new MergedRefCursor();
		for (int i = 0; i < tables.length; i++) {
			m.add(new RefQueueEntry(tables[i].seekRef(name), i));
		}
		return m;
	}

	/** {@inheritDoc} */
	@Override
	public RefCursor seekRefsWithPrefix(String prefix) throws IOException {
		MergedRefCursor m = new MergedRefCursor();
		for (int i = 0; i < tables.length; i++) {
			m.add(new RefQueueEntry(tables[i].seekRefsWithPrefix(prefix), i));
		}
		return m;
	}

	/** {@inheritDoc} */
	@Override
	public RefCursor byObjectId(AnyObjectId name) throws IOException {
		MergedRefCursor m = new FilteringMergedRefCursor(name);
		for (int i = 0; i < tables.length; i++) {
			m.add(new RefQueueEntry(tables[i].byObjectId(name), i));
		}
		return m;
	}

	/** {@inheritDoc} */
	@Override
	public LogCursor allLogs() throws IOException {
		MergedLogCursor m = new MergedLogCursor();
		for (int i = 0; i < tables.length; i++) {
			m.add(new LogQueueEntry(tables[i].allLogs(), i));
		}
		return m;
	}

	/** {@inheritDoc} */
	@Override
	public LogCursor seekLog(String refName, long updateIdx)
			throws IOException {
		MergedLogCursor m = new MergedLogCursor();
		for (int i = 0; i < tables.length; i++) {
			m.add(new LogQueueEntry(tables[i].seekLog(refName, updateIdx), i));
		}
		return m;
	}

	int queueSize() {
		return Math.max(1, tables.length);
	}

	private class MergedRefCursor extends RefCursor {
		private final PriorityQueue<RefQueueEntry> queue;
		private RefQueueEntry head;
		private Ref ref;

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
				boolean include = includeDeletes || !t.rc.wasDeleted();
				add(t);
				skipShadowedRefs(ref.getName());
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

	private class FilteringMergedRefCursor extends MergedRefCursor {
		final AnyObjectId filterId;
		Ref filteredRef;

		FilteringMergedRefCursor(AnyObjectId id) {
			filterId = id;
			filteredRef = null;
		}

		@Override
		public Ref getRef() {
			return filteredRef;
		}

		@Override
		public boolean next() throws IOException {
			for (;;) {
				boolean ok = super.next();
				if (!ok) {
					return false;
				}

				String name = super.getRef().getName();

				try (RefCursor c = seekRef(name)) {
					if (c.next()) {
						if (filterId.equals(c.getRef().getObjectId())) {
							filteredRef = c.getRef();
							return true;
						}
					}
				}
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
			return rc.getRef().getUpdateIndex();
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

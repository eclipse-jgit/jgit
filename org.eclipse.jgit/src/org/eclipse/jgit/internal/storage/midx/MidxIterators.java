/*
 * Copyright (C) 2026, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.midx;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex.MidxIterator;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex.MutableEntry;
import org.eclipse.jgit.lib.MutableObjectId;

/**
 * Helpers for midx iterators
 */
public final class MidxIterators {

	/**
	 * Wrap a PackIndex iterator so it looks like a midx iterator with a fixed
	 * packId
	 *
	 * @param idxIt
	 *            iterator over a PackIndex
	 * @return a midx iterator that returns the objects of the pack index in the
	 *         original iterator order
	 */
	public static MidxIterator fromPackIndexIterator(String packName,
			Iterator<PackIndex.MutableEntry> idxIt) {
		return new MidxIteratorOverPackIndex(packName, idxIt);
	}

	/**
	 * Merge results from multiple midx iterators.
	 * <p>
	 * This iterator can return duplicates (in ascending packId order). Wrap
	 * with {@link #dedup(MidxIterator)} to remove duplicates.
	 * <p>
	 * This iterator shifts the pack ids in iterator order. If the first
	 * iterator covers 3 packs, packIds of the second iterator are increased by
	 * 3.
	 * 
	 * @param iterators
	 *            midx iterators to combine
	 * @return a unique iterator that returns the union of the iterators in sha1
	 *         order. The packIds of the entries are shi
	 */
	public static MidxIterator join(List<MidxIterator> iterators) {
		return new JoinMidxIterator(iterators);
	}

	/**
	 * Dedup results from a midx iterator
	 *
	 * @param source
	 *            a midx iterator that can emite duplicated sha1s.
	 * @return instance of the duplicated entries.
	 */
	public static MidxIterator dedup(MidxIterator source) {
		return new DedupMidxIterator(source);
	}

	private MidxIterators() {
	}

	/**
	 * Convert a PackIndex iterator into a MidxIterator with a fixed packId
	 */
	private static class MidxIteratorOverPackIndex implements MidxIterator {

		private final List<String> packNames;

		private final Iterator<PackIndex.MutableEntry> idxIt;

		private boolean peeked;

		private final MutableEntry entry;

		MidxIteratorOverPackIndex(String packName,
				Iterator<PackIndex.MutableEntry> idxIt) {
			this.packNames = List.of(packName);
			this.idxIt = idxIt;
			this.entry = new MutableEntry();
		}

		@Override
		public MutableEntry peek() {
			if (peeked) {
				return entry;
			}

			peeked = true;
			readNext();
			return entry;
		}

		@Override
		public List<String> getPackNames() {
			return packNames;
		}

		@Override
		public boolean hasNext() {
			if (peeked) {
				return true;
			}
			return idxIt.hasNext();
		}

		@Override
		public MutableEntry next() {
			if (peeked) {
				peeked = false;
				return entry;
			}
			readNext();
			return entry;
		}

		private void readNext() {
			PackIndex.MutableEntry idx = idxIt.next();
			idx.copyOidTo(entry.oid);
			entry.packOffset.setValues(0, idx.getOffset());
		}
	}

	private static class JoinMidxIterator implements MidxIterator {

		private final List<String> packNames;

		private final List<MidxIterator> indexIterators;

		private final int[] packCountAgg;

		private MutableEntry local = new MutableEntry();

		public JoinMidxIterator(List<MidxIterator> indexIterators) {
			this.indexIterators = indexIterators;
			packCountAgg = new int[indexIterators.size()];
			for (int i = 1; i < indexIterators.size(); i++) {
				packCountAgg[i] = indexIterators.get(i - 1).getPackNames()
						.size() + packCountAgg[i - 1];
			}
			packNames = indexIterators.stream().map(MidxIterator::getPackNames)
					.flatMap(List::stream).toList();
		}

		@Override
		public MutableEntry peek() {
			int p = best();
			MidxIterator it = indexIterators.get(p);
			return shiftPackId(it.peek(), packCountAgg[p]);
		}

		@Override
		public List<String> getPackNames() {
			return packNames;
		}

		@Override
		public boolean hasNext() {
			return indexIterators.stream().anyMatch(it -> it.hasNext());
		}

		@Override
		public MutableEntry next() {
			int p = best();
			MidxIterator it = indexIterators.get(p);
			return shiftPackId(it.next(), packCountAgg[p]);
		}

		private MutableEntry shiftPackId(MutableEntry entry, int shift) {
			local.fill(entry, shift);
			return local;
		}

		private int best() {
			int winnerPos = -1;
			MidxIterator winner = null;
			for (int index = 0; index < indexIterators.size(); index++) {
				MidxIterator current = indexIterators.get(index);
				if (!current.hasNext()) {
					continue;
				}
				if (winner == null
						|| current.peek().compareTo(winner.peek()) < 0) {
					winner = current;
					winnerPos = index;
				}
			}

			if (winner == null) {
				throw new NoSuchElementException();
			}

			return winnerPos;
		}
	}

	private static class DedupMidxIterator implements MidxIterator {
		private final MidxIterator src;

		private final MutableObjectId lastOid = new MutableObjectId();

		private MutableEntry next;

		private MutableEntry copy = new MutableEntry();

		/**
		 * Iterator over sorted by sha1 entries that removes duplicates choosing
		 * the first one found.
		 *
		 * @param src
		 *            iterator in sha1 order
		 */
		DedupMidxIterator(MidxIterator src) {
			this.src = src;
			readNext();
		}

		@Override
		public MutableEntry peek() {
			return next;
		}

		@Override
		public List<String> getPackNames() {
			return src.getPackNames();
		}

		@Override
		public boolean hasNext() {
			return next != null;
		}

		@Override
		public MutableEntry next() {
			copy.fill(next, 0);
			readNext();
			return copy;
		}

		private void readNext() {
			while (true) {
				if (!src.hasNext()) {
					next = null;
					return;
				}

				next = src.next();
				if (!lastOid.equals(next.oid)) {
					lastOid.fromObjectId(next.oid);
					return;
				}
			}
		}
	}
}

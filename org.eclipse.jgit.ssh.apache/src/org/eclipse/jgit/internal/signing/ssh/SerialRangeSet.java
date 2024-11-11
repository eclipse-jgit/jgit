/*
 * Copyright (C) 2024, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.signing.ssh;

import java.util.TreeMap;

import org.eclipse.jgit.internal.transport.sshd.SshdText;

/**
 * Encapsulates the storage for revoked certificate serial numbers.
 */
class SerialRangeSet {

	/**
	 * A range of certificate serial numbers [from..to], i.e., with both range
	 * limits included.
	 */
	private interface SerialRange {

		long from();

		long to();
	}

	private static record Singleton(long from) implements SerialRange {

		@Override
		public long to() {
			return from;
		}
	}

	private static record Range(long from, long to) implements SerialRange {

		public Range(long from, long to) {
			if (Long.compareUnsigned(from, to) > 0) {
				throw new IllegalArgumentException(
						SshdText.get().signKrlEmptyRange);
			}
			this.from = from;
			this.to = to;
		}
	}

	// We use the same data structure as OpenSSH; basically a TreeSet of mutable
	// SerialRanges. To get "mutability", the set is implemented as a TreeMap
	// with the same elements as keys and values.
	//
	// get(x) will return null if none of the serial numbers in the range x is
	// in the set, and some range (partially) overlapping with x otherwise.
	//
	// containsKey(x) will return true if there is any (partially) overlapping
	// range in the TreeMap.
	private final TreeMap<SerialRange, SerialRange> ranges = new TreeMap<>(
			SerialRangeSet::compare);

	private static int compare(SerialRange a, SerialRange b) {
		// Return == if they overlap
		if (Long.compareUnsigned(a.to(), b.from()) >= 0
				&& Long.compareUnsigned(a.from(), b.to()) <= 0) {
			return 0;
		}
		return Long.compareUnsigned(a.from(), b.from());
	}

	void add(long serial) {
		add(ranges, new Singleton(serial));
	}

	void add(long from, long to) {
		add(ranges, new Range(from, to));
	}

	boolean contains(long serial) {
		return ranges.containsKey(new Singleton(serial));
	}

	int size() {
		return ranges.size();
	}

	boolean isEmpty() {
		return ranges.isEmpty();
	}

	private static void add(TreeMap<SerialRange, SerialRange> ranges,
			SerialRange newRange) {
		for (;;) {
			SerialRange existing = ranges.get(newRange);
			if (existing == null) {
				break;
			}
			if (Long.compareUnsigned(existing.from(), newRange.from()) <= 0
					&& Long.compareUnsigned(existing.to(),
							newRange.to()) >= 0) {
				// newRange completely contained in existing
				return;
			}
			ranges.remove(existing);
			long newFrom = newRange.from();
			if (Long.compareUnsigned(existing.from(), newFrom) < 0) {
				newFrom = existing.from();
			}
			long newTo = newRange.to();
			if (Long.compareUnsigned(existing.to(), newTo) > 0) {
				newTo = existing.to();
			}
			newRange = new Range(newFrom, newTo);
		}
		// No overlapping range exists: check for coalescing with the
		// previous/next range
		SerialRange prev = ranges.floorKey(newRange);
		if (prev != null && newRange.from() - prev.to() == 1) {
			ranges.remove(prev);
			newRange = new Range(prev.from(), newRange.to());
		}
		SerialRange next = ranges.ceilingKey(newRange);
		if (next != null && next.from() - newRange.to() == 1) {
			ranges.remove(next);
			newRange = new Range(newRange.from(), next.to());
		}
		ranges.put(newRange, newRange);
	}
}

/*
 * Copyright (C) 2019, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.treewalk;

import java.time.Instant;
import java.util.Comparator;

/**
 * Specialized comparator for {@link Instant}s. If either timestamp has a zero
 * fraction, compares only seconds. If either timestamp has no time fraction
 * smaller than a millisecond, compares only milliseconds. If either timestamp
 * has no fraction smaller than a microsecond, compares only microseconds.
 */
class InstantComparator implements Comparator<Instant> {

	@Override
	public int compare(Instant a, Instant b) {
		return compare(a, b, false);
	}

	/**
	 * Compares two {@link Instant}s to the lower resolution of the two
	 * instants. See {@link InstantComparator}.
	 *
	 * @param a
	 *            first {@link Instant} to compare
	 * @param b
	 *            second {@link Instant} to compare
	 * @param forceSecondsOnly
	 *            whether to omit all fraction comparison
	 * @return a value &lt; 0 if a &lt; b, a value &gt; 0 if a &gt; b, and 0 if
	 *         a == b
	 */
	public int compare(Instant a, Instant b, boolean forceSecondsOnly) {
		long aSeconds = a.getEpochSecond();
		long bSeconds = b.getEpochSecond();
		int result = Long.compare(aSeconds, bSeconds);
		if (result != 0) {
			return result;
		}
		int aSubSecond = a.getNano();
		int bSubSecond = b.getNano();
		if (forceSecondsOnly || (aSubSecond == 0)
				|| (bSubSecond == 0)) {
			// Don't check the subseconds part.
			return 0;
		} else if (aSubSecond != bSubSecond) {
			// If either has nothing smaller than a millisecond, compare only
			// milliseconds.
			int aSubMillis = aSubSecond % 1_000_000;
			int bSubMillis = bSubSecond % 1_000_000;
			if (aSubMillis == 0) {
				bSubSecond -= bSubMillis;
			} else if (bSubMillis == 0) {
				aSubSecond -= aSubMillis;
			} else {
				// Same again, but for microsecond resolution. NTFS has 100ns
				// resolution, but WindowsFileAttributes may provide only
				// microseconds (1000ns). Similar for some Unix file systems.
				int aSubMicros = aSubSecond % 1000;
				int bSubMicros = bSubSecond % 1000;
				if (aSubMicros == 0) {
					bSubSecond -= bSubMicros;
				} else if (bSubMicros == 0) {
					aSubSecond -= aSubMicros;
				}
			}
		}
		return Integer.compare(aSubSecond, bSubSecond);
	}

}

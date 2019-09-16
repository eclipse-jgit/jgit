/*
 * Copyright (C) 2019, Thomas Wolf <thomas.wolf@paranor.ch>
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

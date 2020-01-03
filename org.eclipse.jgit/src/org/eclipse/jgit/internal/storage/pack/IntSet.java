/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.pack;

class IntSet {
	private int[] set;

	private int cnt;

	IntSet() {
		set = new int[64];
	}

	boolean add(int key) {
		int high = cnt;
		int low = 0;

		if (high == 0) {
			set[0] = key;
			cnt = 1;
			return true;
		}

		do {
			int p = (low + high) >>> 1;
			if (key < set[p])
				high = p;
			else if (key == set[p])
				return false;
			else
				low = p + 1;
		} while (low < high);

		if (cnt == set.length) {
			int[] n = new int[set.length * 2];
			System.arraycopy(set, 0, n, 0, cnt);
			set = n;
		}

		if (low < cnt)
			System.arraycopy(set, low, set, low + 1, cnt - low);
		set[low] = key;
		cnt++;
		return true;
	}
}

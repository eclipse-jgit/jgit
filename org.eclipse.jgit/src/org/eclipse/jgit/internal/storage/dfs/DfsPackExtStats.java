/*
 * Copyright (c) 2024, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import java.util.Arrays;

import org.eclipse.jgit.internal.storage.pack.PackExt;

/**
 * Class holding the array of pack extension stats values returned by the Block
 * Cache tables. Offers utilities for manipulating multiple {@code long[]}
 * arrays of the same {@code long[PackExt.values().length]} format.
 */
class DfsPackExtStats {
	private final long[] values;

	DfsPackExtStats() {
		values = emptyPackStats();
	}

	DfsPackExtStats(long[] values) {
		this.values = Arrays.copyOf(values, values.length);
	}

	long[] getValues() {
		return values;
	}

	void add(long[] otherValues) {
		for (int i = 0; i < PackExt.values().length; i++) {
			values[i] += otherValues[i];
		}
	}

	private static long[] emptyPackStats() {
		long[] values = new long[PackExt.values().length];
		for (int i = 0; i < PackExt.values().length; i++) {
			values[i] = 0;
		}
		return values;
	}
}

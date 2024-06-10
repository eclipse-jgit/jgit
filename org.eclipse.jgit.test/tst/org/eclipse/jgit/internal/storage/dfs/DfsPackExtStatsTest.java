/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import static org.junit.Assert.assertArrayEquals;

import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.junit.Test;

public class DfsPackExtStatsTest {
	@Test
	public void addOtherArray() {
		long[] otherValues = new long[PackExt.values().length];
		for (int i = 0; i < PackExt.values().length; i++) {
			otherValues[i] = i;
		}
		DfsPackExtStats extStats = new DfsPackExtStats();
		extStats.add(otherValues);

		assertArrayEquals(extStats.getValues(), otherValues);
	}
}

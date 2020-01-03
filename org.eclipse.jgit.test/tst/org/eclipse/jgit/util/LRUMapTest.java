/*
 * Copyright (C) 2018, Konrad Windszus <konrad_w@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util;

import java.util.LinkedHashMap;
import java.util.Map;

import org.hamcrest.collection.IsIterableContainingInOrder;
import org.junit.Assert;
import org.junit.Test;

public class LRUMapTest {

	@SuppressWarnings("boxing")
	@Test
	public void testLRUEntriesAreEvicted() {
		Map<Integer, Integer> map = new LRUMap<>(3, 3);
		for (int i = 0; i < 3; i++) {
			map.put(i, i);
		}
		// access the last ones
		map.get(2);
		map.get(0);

		// put another one which exceeds the limit (entry with key "1" is
		// evicted)
		map.put(3, 3);

		Map<Integer, Integer> expectedMap = new LinkedHashMap<>();
		expectedMap.put(2, 2);
		expectedMap.put(0, 0);
		expectedMap.put(3, 3);

		Assert.assertThat(map.entrySet(),
				IsIterableContainingInOrder
						.contains(expectedMap.entrySet().toArray()));
	}
}

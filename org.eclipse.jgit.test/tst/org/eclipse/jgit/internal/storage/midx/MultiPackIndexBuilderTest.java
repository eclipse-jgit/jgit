/*
 * Copyright (C) 2024, GerritForge Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.midx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.eclipse.jgit.internal.storage.midx.MultiPackIndexLoader.MultiPackIndexBuilder;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndexLoader.MultiPackIndexFormatException;
import org.junit.Test;

public class MultiPackIndexBuilderTest {

	@Test
	public void testRepeatedChunk() throws Exception {
		byte[] buffer = new byte[2048];

		MultiPackIndexBuilder builder1 = MultiPackIndexBuilder.builder();
		builder1.addOidFanout(buffer);
		Exception e1 = assertThrows(MultiPackIndexFormatException.class, () -> {
			builder1.addOidFanout(buffer);
		});
		assertEquals("midx chunk id 0x4f494446 appears multiple times",
				e1.getMessage());

		MultiPackIndexBuilder builder2 = MultiPackIndexBuilder.builder();
		builder2.addOidLookUp(buffer);
		Exception e2 = assertThrows(MultiPackIndexFormatException.class, () -> {
			builder2.addOidLookUp(buffer);
		});
		assertEquals("midx chunk id 0x4f49444c appears multiple times",
				e2.getMessage());

		MultiPackIndexBuilder builder3 = MultiPackIndexBuilder.builder();
		builder3.addObjectOffsets(buffer);
		Exception e3 = assertThrows(MultiPackIndexFormatException.class, () -> {
			builder3.addObjectOffsets(buffer);
		});
		assertEquals("midx chunk id 0x4f4f4646 appears multiple times",
				e3.getMessage());

		MultiPackIndexBuilder builder4 = MultiPackIndexBuilder.builder();
		builder4.addPackNames(buffer);
		Exception e4 = assertThrows(MultiPackIndexFormatException.class, () -> {
			builder4.addPackNames(buffer);
		});
		assertEquals("midx chunk id 0x504e414d appears multiple times",
				e4.getMessage());

		MultiPackIndexBuilder builder5 = MultiPackIndexBuilder.builder();
		builder5.addBitmappedPacks(buffer);
		Exception e5 = assertThrows(MultiPackIndexFormatException.class, () -> {
			builder5.addBitmappedPacks(buffer);
		});
		assertEquals("midx chunk id 0x42544d50 appears multiple times",
				e5.getMessage());

		MultiPackIndexBuilder builder6 = MultiPackIndexBuilder.builder();
		builder6.addObjectLargeOffsets(buffer);
		Exception e6 = assertThrows(MultiPackIndexFormatException.class, () -> {
			builder6.addObjectLargeOffsets(buffer);
		});
		assertEquals("midx chunk id 0x4c4f4646 appears multiple times",
				e6.getMessage());

		MultiPackIndexBuilder builder7 = MultiPackIndexBuilder.builder();
		builder7.addReverseIndex(buffer);
		Exception e7 = assertThrows(MultiPackIndexFormatException.class, () -> {
			builder7.addReverseIndex(buffer);
		});
		assertEquals("midx chunk id 0x52494458 appears multiple times",
				e7.getMessage());
	}

	@Test
	public void testNeededChunk() {
		byte[] buffer = new byte[2048];

		Exception e1 = assertThrows(MultiPackIndexFormatException.class, () -> {
			MultiPackIndexBuilder.builder().addOidLookUp(buffer).build();
		});
		assertEquals("midx 0x4f494446 chunk has not been loaded",
				e1.getMessage());

		Exception e2 = assertThrows(MultiPackIndexFormatException.class, () -> {
			MultiPackIndexBuilder.builder().addOidFanout(buffer)
					.addOidLookUp(buffer).build();
		});
		assertEquals("midx 0x504e414d chunk has not been loaded",
				e2.getMessage());

		Exception e3 = assertThrows(MultiPackIndexFormatException.class, () -> {
			MultiPackIndexBuilder.builder().addOidFanout(buffer)
					.addOidLookUp(buffer).addPackNames(buffer).build();
		});
		assertEquals("midx 0x4f4f4646 chunk has not been loaded",
				e3.getMessage());
	}
}

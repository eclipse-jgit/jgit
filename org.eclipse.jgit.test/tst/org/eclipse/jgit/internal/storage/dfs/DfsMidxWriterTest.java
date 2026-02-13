/*
 * Copyright (C) 2026, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.dfs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.junit.Test;

public class DfsMidxWriterTest {
	private static final List<String> BLOBS = List.of("blob one", "blob two",
			"blob three", "blob four", "blob five", "blob six");

	private static final ProgressMonitor PM = NullProgressMonitor.INSTANCE;

	@Test
	public void writeMidx_fromPlainPacks_noBase() throws IOException {
		InMemoryRepository db = new InMemoryRepository(
				new DfsRepositoryDescription("write-midx"));
		BLOBS.stream().forEach(s -> {
			try {
				MidxTestUtils.writePackWithBlob(db, s);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		List<DfsPackDescription> packDescriptions = Arrays.stream(packs)
				.map(DfsPackFile::getPackDescription)
				.collect(Collectors.toUnmodifiableList());

		DfsPackDescription desc = DfsMidxWriter.writeMidx(PM,
				db.getObjectDatabase(), Arrays.asList(packs), null);

		assertEquals(BLOBS.size(), desc.getObjectCount());
		assertEquals(descsToNames(packDescriptions),
				descsToNames(desc.getCoveredPacks()));
		assertNull(desc.getMultiPackIndexBase());
		assertTrue(desc.hasFileExt(PackExt.MULTI_PACK_INDEX));
		assertEquals(1464, desc.getFileSize(PackExt.MULTI_PACK_INDEX));
	}

	@Test
	public void writeMidx_mergingMidxChain() throws IOException {
		InMemoryRepository db = new InMemoryRepository(
				new DfsRepositoryDescription("two_midx"));

		BLOBS.stream().forEach(s -> {
			try {
				MidxTestUtils.writePackWithBlob(db, s);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});

		List<DfsPackFile> packs = List.of(db.getObjectDatabase().getPacks());
		DfsPackDescription base = DfsMidxWriter.writeMidx(PM,
				db.getObjectDatabase(), packs.subList(3, 6), null);
		DfsPackDescription tip = DfsMidxWriter.writeMidx(PM,
				db.getObjectDatabase(), packs.subList(0, 3), base);
		DfsPackFileMidx tipMidx = createDfsPackFileMidx(tip, packs);

		DfsPackDescription merged = DfsMidxWriter.writeMidx(PM,
				db.getObjectDatabase(),
				List.of(tipMidx.getMultipackIndexBase(), tipMidx), null);

		assertEquals(BLOBS.size(), merged.getObjectCount());
		assertTrue(merged.hasFileExt(PackExt.MULTI_PACK_INDEX));
		assertNull(merged.getMultiPackIndexBase());
		List<DfsPackDescription> expected = Stream
				.concat(base.getCoveredPacks().stream(),
						tip.getCoveredPacks().stream())
				.collect(Collectors.toUnmodifiableList());
		assertEquals(descsToNames(expected),
				descsToNames(merged.getCoveredPacks()));
	}

	private static DfsPackFileMidx createDfsPackFileMidx(DfsPackDescription dsc,
			List<DfsPackFile> knownPacks) {
		DfsPackFileMidx baseMidx = null;
		if (dsc.getMultiPackIndexBase() != null) {
			baseMidx = createDfsPackFileMidx(dsc.getMultiPackIndexBase(),
					knownPacks);
		}
		return DfsPackFileMidx.create(DfsBlockCache.getInstance(), dsc,
				knownPacks, baseMidx);
	}

	private List<String> descsToNames(List<DfsPackDescription> packss) {
		return packss.stream().map(DfsPackDescription::getPackName)
				.collect(Collectors.toUnmodifiableList());
	}
}

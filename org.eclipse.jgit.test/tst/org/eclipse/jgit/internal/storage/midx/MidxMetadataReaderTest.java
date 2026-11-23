/*
 * Copyright (C) 2026, Google LLC
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.midx;

import static org.eclipse.jgit.lib.ObjectFormat.SHA1;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.junit.FakeIndexFactory;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MidxMetadataReaderTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();
	@Test
	public void load_validFile_basic_upstream() throws Exception {
		MidxMetadataReader.MidxMetadata meta = MidxMetadataReader
				.read(JGitTestUtil.getTestResourceFile("multi-pack-index.v1"));
		assertEquals(26, meta.packNames().size());

		byte[] expected = new byte[] { 15, -10, -96, 97, 76, 13, -72, 95, 106,
				15, -55, -12, 18, -13, 50, 33, 120, -113, 71, -119 };
		assertArrayEquals(expected, meta.checksum());
	}

	@Test
	public void read_largeFileOverBufferBoundaryAndErrorCases()
			throws Exception {
		List<FakeIndexFactory.IndexObject> p1 = new ArrayList<>();
		List<FakeIndexFactory.IndexObject> p2 = new ArrayList<>();
		for (int i = 1; i <= 600; i++) {
			FakeIndexFactory.IndexObject obj = new FakeIndexFactory.IndexObject(
					String.format("%040x", Integer.valueOf(i)), 12L + i * 32L);
			if ((i & 1) == 0) {
				p1.add(obj);
			} else {
				p2.add(obj);
			}
		}
		PackIndexMerger data = PackIndexMerger.builder()
				.addPack("pack-a.idx", FakeIndexFactory.indexOf(p1))
				.addPack("pack-b.idx", FakeIndexFactory.indexOf(p2)).build();
		ByteArrayOutputStream rawOut = new ByteArrayOutputStream();
		new MultiPackIndexWriter(SHA1).write(NullProgressMonitor.INSTANCE,
				rawOut,
				data);
		byte[] rawMidx = rawOut.toByteArray();
		assertTrue(rawMidx.length > 16 * 1024);

		File midxFile = tmp.newFile("multi-pack-index");
		try (FileOutputStream out = new FileOutputStream(midxFile)) {
			out.write(rawMidx);
		}

		MidxMetadataReader.MidxMetadata meta = MidxMetadataReader
				.read(midxFile);
		MultiPackIndex loaded = MultiPackIndexLoader.open(midxFile);
		assertEquals(List.of("pack-a.idx", "pack-b.idx"), meta.packNames());
		assertArrayEquals(loaded.getChecksum(), meta.checksum());

		assertThrows(FileNotFoundException.class, () -> MidxMetadataReader
				.read(new File(tmp.getRoot(), "missing-midx")));

		File truncatedFile = tmp.newFile("truncated-midx");
		try (FileOutputStream out = new FileOutputStream(truncatedFile)) {
			out.write(Arrays.copyOf(rawMidx, rawMidx.length - 5));
		}
		assertThrows(IOException.class,
				() -> MidxMetadataReader.read(truncatedFile));
	}
}

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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.ObjectFormat.SHA1;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.junit.FakeIndexFactory;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Test that the loader accepts valid files, discard broken files
 * <p>
 * Contents and lookups are covered in the MultiPackIndexTest
 */
public class MultiPackIndexLoaderTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void load_validFile_basic_upstream() throws Exception {
		MultiPackIndex midx = MultiPackIndexLoader
				.open(JGitTestUtil.getTestResourceFile("multi-pack-index.v1"));
		assertNotNull(midx);
	}

	@Test
	public void load_validFile_basic_jgit() throws Exception {
		PackIndex idxOne = FakeIndexFactory.indexOf(List.of(
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000001", 500),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000005", 12),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000010", 1500)));
		PackIndex idxTwo = FakeIndexFactory.indexOf(List.of(
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000002", 501),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000003", 13),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000015", 1501)));
		PackIndex idxThree = FakeIndexFactory.indexOf(List.of(
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000004", 502),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000007", 14),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000012", 1502)));

		PackIndexMerger data = PackIndexMerger.builder().addPack("p1", idxOne)
				.addPack("p2", idxTwo).addPack("p3", idxThree).build();
		MultiPackIndexWriter writer = new MultiPackIndexWriter(SHA1);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, data);

		MultiPackIndex midx = MultiPackIndexLoader
				.read(new ByteArrayInputStream(out.toByteArray()));
		assertNotNull(midx);
	}

	@Test
	public void load_emptyFile() {
		assertThrows(IOException.class, () -> MultiPackIndexLoader
				.read(new ByteArrayInputStream(new byte[0])));
	}

	@Test
	public void load_rubbishFile() {
		assertThrows(MultiPackIndexLoader.MultiPackIndexFormatException.class,
				() -> MultiPackIndexLoader.read(new ByteArrayInputStream(
						"More than 12 bytes of not-midx".getBytes(UTF_8))));
	}

	@Test
	public void open_largeFileAndErrorCases() throws Exception {
		byte[] rawMidx = buildLargeMidxBytes(600);
		assertTrue(rawMidx.length > 16 * 1024);

		File midxFile = tmp.newFile("multi-pack-index");
		try (FileOutputStream out = new FileOutputStream(midxFile)) {
			out.write(rawMidx);
		}

		MultiPackIndex fromFile = MultiPackIndexLoader.open(midxFile);
		MultiPackIndex fromMem = MultiPackIndexLoader
				.read(new ByteArrayInputStream(rawMidx));
		assertEquals(600, fromFile.getObjectCount());
		assertArrayEquals(fromMem.getChecksum(), fromFile.getChecksum());
		assertEquals(fromMem.findPosition(ObjectId
				.fromString(String.format("%040x", Integer.valueOf(1)))),
				fromFile.findPosition(ObjectId.fromString(
						String.format("%040x", Integer.valueOf(1)))));
		assertEquals(fromMem.findPosition(ObjectId
				.fromString(String.format("%040x", Integer.valueOf(600)))),
				fromFile.findPosition(ObjectId.fromString(
						String.format("%040x", Integer.valueOf(600)))));

		assertThrows(FileNotFoundException.class, () -> MultiPackIndexLoader
				.open(new File(tmp.getRoot(), "missing-midx")));

		File truncatedFile = tmp.newFile("truncated-midx");
		try (FileOutputStream out = new FileOutputStream(truncatedFile)) {
			out.write(Arrays.copyOf(rawMidx, rawMidx.length - 10));
		}
		assertThrows(IOException.class,
				() -> MultiPackIndexLoader.open(truncatedFile));
	}

	private static byte[] buildLargeMidxBytes(int totalObjects)
			throws IOException {
		List<FakeIndexFactory.IndexObject> p1 = new ArrayList<>();
		List<FakeIndexFactory.IndexObject> p2 = new ArrayList<>();
		for (int i = 1; i <= totalObjects; i++) {
			FakeIndexFactory.IndexObject obj = new FakeIndexFactory.IndexObject(
					String.format("%040x", Integer.valueOf(i)), 12L + i * 32L);
			if ((i & 1) == 0) {
				p1.add(obj);
			} else {
				p2.add(obj);
			}
		}
		PackIndexMerger data = PackIndexMerger.builder()
				.addPack("pack-1.idx", FakeIndexFactory.indexOf(p1))
				.addPack("pack-2.idx", FakeIndexFactory.indexOf(p2)).build();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new MultiPackIndexWriter(SHA1).write(NullProgressMonitor.INSTANCE, out,
				data);
		return out.toByteArray();
	}
}

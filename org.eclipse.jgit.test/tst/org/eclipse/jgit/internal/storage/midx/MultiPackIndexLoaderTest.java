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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.junit.FakeIndexFactory;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.junit.Test;

/**
 * Test that the loader accepts valid files, discard broken files
 * <p>
 * Contents and lookups are covered in the MultiPackIndexTest
 */
public class MultiPackIndexLoaderTest {

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
		MultiPackIndexWriter writer = new MultiPackIndexWriter();
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
}

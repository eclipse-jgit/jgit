/*
 * Copyright (C) 2019 Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static org.eclipse.jgit.lib.Ref.Storage.PACKED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.internal.storage.file.FileReftableStack.Segment;
import org.eclipse.jgit.internal.storage.reftable.MergedReftable;
import org.eclipse.jgit.internal.storage.reftable.RefCursor;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class FileReftableStackTest {

	private static Ref newRef(String name, ObjectId id) {
		return new ObjectIdRef.PeeledNonTag(PACKED, name, id);
	}

	private File reftableDir;

	@Before
	public void setup() throws Exception {
		reftableDir = FileUtils.createTempDir("rtstack", "", null);
	}

	@After
	public void tearDown() throws Exception {
		if (reftableDir != null) {
			FileUtils.delete(reftableDir, FileUtils.RECURSIVE);
		}
	}

	void writeBranches(FileReftableStack stack, String template, int start,
			int N) throws IOException {
		for (int i = 0; i < N; i++) {
			while (true) {
				final long next = stack.getMergedReftable().maxUpdateIndex()
						+ 1;

				String name = String.format(template,
						Integer.valueOf(start + i));
				Ref r = newRef(name, ObjectId.zeroId());
				boolean ok = stack.addReftable(rw -> {
					rw.setMinUpdateIndex(next).setMaxUpdateIndex(next).begin()
							.writeRef(r);
				});
				if (ok) {
					break;
				}
			}
		}
	}

	public void testCompaction(int N) throws Exception {
		try (FileReftableStack stack = new FileReftableStack(
				new File(reftableDir, "refs"), reftableDir, null,
				() -> new Config())) {
			writeBranches(stack, "refs/heads/branch%d", 0, N);
			MergedReftable table = stack.getMergedReftable();
			for (int i = 1; i < N; i++) {
				String name = String.format("refs/heads/branch%d",
						Integer.valueOf(i));
				RefCursor c = table.seekRef(name);
				assertTrue(c.next());
				assertEquals(ObjectId.zeroId(), c.getRef().getObjectId());
			}

			List<String> files = Arrays.asList(reftableDir.listFiles()).stream()
					.map(File::getName).collect(Collectors.toList());
			Collections.sort(files);

			assertTrue(files.size() < 20);

			FileReftableStack.CompactionStats stats = stack.getStats();
			assertEquals(0, stats.failed);
			assertTrue(stats.attempted < N);
			assertTrue(stats.refCount < FileReftableStack.log(N) * N);
		}
	}

	@Test
	public void testCompaction9() throws Exception {
		testCompaction(9);
	}

	@Test
	public void testCompaction1024() throws Exception {
		testCompaction(1024);
	}

	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	@SuppressWarnings({ "resource", "unused" })
	@Test
	public void missingReftable() throws Exception {
		try (FileReftableStack stack = new FileReftableStack(
				new File(reftableDir, "refs"), reftableDir, null,
				() -> new Config())) {
			outer: for (int i = 0; i < 10; i++) {
				final long next = stack.getMergedReftable().maxUpdateIndex()
						+ 1;
				String name = String.format("branch%d", Integer.valueOf(i));
				Ref r = newRef(name, ObjectId.zeroId());
				boolean ok = stack.addReftable(rw -> {
					rw.setMinUpdateIndex(next).setMaxUpdateIndex(next).begin()
							.writeRef(r);
				});
				assertTrue(ok);

				List<File> files = Arrays.asList(reftableDir.listFiles());
				for (int j = 0; j < files.size(); j++) {
					File f = files.get(j);
					if (f.getName().endsWith(".ref")) {
						assertTrue(f.delete());
						break outer;
					}
				}
			}
		}
		thrown.expect(FileNotFoundException.class);
		new FileReftableStack(new File(reftableDir, "refs"), reftableDir, null,
				() -> new Config());
	}

	@Test
	public void testSegments() {
		long in[] = { 1024, 1024, 1536, 100, 64, 50, 25, 24 };
		List<Segment> got = FileReftableStack.segmentSizes(in);
		Segment want[] = { new Segment(0, 3, 10, 3584),
				new Segment(3, 5, 6, 164), new Segment(5, 6, 5, 50),
				new Segment(6, 8, 4, 49), };
		assertEquals(got.size(), want.length);
		for (int i = 0; i < want.length; i++) {
			assertTrue(want[i].equals(got.get(i)));
		}
	}

	@Test
	public void testLog2() throws Exception {
		assertEquals(10, FileReftableStack.log(1024));
		assertEquals(10, FileReftableStack.log(1025));
		assertEquals(10, FileReftableStack.log(2047));
	}
}

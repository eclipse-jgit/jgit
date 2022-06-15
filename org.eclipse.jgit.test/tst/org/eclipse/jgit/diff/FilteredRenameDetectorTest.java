/*
 * Copyright (C) 2022, Simeon Andreev and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.diff;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.internal.diff.FilteredRenameDetector;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.junit.Before;
import org.junit.Test;

public class FilteredRenameDetectorTest extends AbstractRenameDetectionTestCase {

	private FilteredRenameDetector frd;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		frd = new FilteredRenameDetector(db);
	}

	@Test
	public void testExactRename() throws Exception {
		ObjectId foo = blob("foo");
		ObjectId bar = blob("bar");

		DiffEntry a = DiffEntry.add(PATH_A, foo);
		DiffEntry b = DiffEntry.delete(PATH_Q, foo);

		DiffEntry c = DiffEntry.add(PATH_H, bar);
		DiffEntry d = DiffEntry.delete(PATH_B, bar);

		List<DiffEntry> changes = Arrays.asList(a, b, c, d);
		PathFilter filter = PathFilter.create(PATH_A);
		List<DiffEntry> entries = frd.compute(changes, filter);
		assertEquals("Unexpected entries in: " + entries, 1, entries.size());
		assertRename(b, a, 100, entries.get(0));
	}

	@Test
	public void testExactRename_multipleFilters() throws Exception {
		ObjectId foo = blob("foo");
		ObjectId bar = blob("bar");

		DiffEntry a = DiffEntry.add(PATH_A, foo);
		DiffEntry b = DiffEntry.delete(PATH_Q, foo);

		DiffEntry c = DiffEntry.add(PATH_H, bar);
		DiffEntry d = DiffEntry.delete(PATH_B, bar);

		List<DiffEntry> changes = Arrays.asList(a, b, c, d);
		List<PathFilter> filters = Arrays.asList(PathFilter.create(PATH_A),
				PathFilter.create(PATH_H));
		List<DiffEntry> entries = frd.compute(changes, filters);
		assertEquals("Unexpected entries in: " + entries, 2, entries.size());
		assertRename(b, a, 100, entries.get(0));
		assertRename(d, c, 100, entries.get(1));
	}

	@Test
	public void testInexactRename() throws Exception {
		ObjectId aId = blob("foo\nbar\nbaz\nblarg\n");
		ObjectId bId = blob("foo\nbar\nbaz\nblah\n");
		DiffEntry a = DiffEntry.add(PATH_A, aId);
		DiffEntry b = DiffEntry.delete(PATH_Q, bId);

		ObjectId cId = blob("some\nsort\nof\ntext\n");
		ObjectId dId = blob("completely\nunrelated\ntext\n");
		DiffEntry c = DiffEntry.add(PATH_B, cId);
		DiffEntry d = DiffEntry.delete(PATH_H, dId);

		List<DiffEntry> changes = Arrays.asList(a, b, c, d);
		PathFilter filter = PathFilter.create(PATH_A);
		List<DiffEntry> entries = frd.compute(changes, filter);
		assertEquals("Unexpected entries: " + entries, 1, entries.size());
		assertRename(b, a, 66, entries.get(0));
	}

	@Test
	public void testInexactRename_multipleFilters() throws Exception {
		ObjectId aId = blob("foo\nbar\nbaz\nblarg\n");
		ObjectId bId = blob("foo\nbar\nbaz\nblah\n");
		DiffEntry a = DiffEntry.add(PATH_A, aId);
		DiffEntry b = DiffEntry.delete(PATH_Q, bId);

		ObjectId cId = blob("some\nsort\nof\ntext\n");
		ObjectId dId = blob("completely\nunrelated\ntext\n");
		DiffEntry c = DiffEntry.add(PATH_B, cId);
		DiffEntry d = DiffEntry.delete(PATH_H, dId);

		List<DiffEntry> changes = Arrays.asList(a, b, c, d);
		List<PathFilter> filters = Arrays.asList(PathFilter.create(PATH_A),
				PathFilter.create(PATH_H));
		List<DiffEntry> entries = frd.compute(changes, filters);
		assertEquals("Unexpected entries: " + entries, 2, entries.size());
		assertRename(b, a, 66, entries.get(0));
		assertSame(d, entries.get(1));
	}

	@Test
	public void testNoRenames() throws Exception {
		ObjectId aId = blob("");
		ObjectId bId = blob("blah1");
		ObjectId cId = blob("");
		ObjectId dId = blob("blah2");

		DiffEntry a = DiffEntry.add(PATH_A, aId);
		DiffEntry b = DiffEntry.delete(PATH_Q, bId);

		DiffEntry c = DiffEntry.add(PATH_H, cId);
		DiffEntry d = DiffEntry.delete(PATH_B, dId);

		List<DiffEntry> changes = Arrays.asList(a, b, c, d);
		PathFilter filter = PathFilter.create(PATH_A);
		List<DiffEntry> entries = frd.compute(changes, filter);
		assertEquals("Unexpected entries in: " + entries, 1, entries.size());
		assertSame(a, entries.get(0));
	}

	@Test
	public void testNoRenames_multipleFilters() throws Exception {
		ObjectId aId = blob("");
		ObjectId bId = blob("blah1");
		ObjectId cId = blob("");
		ObjectId dId = blob("blah2");

		DiffEntry a = DiffEntry.add(PATH_A, aId);
		DiffEntry b = DiffEntry.delete(PATH_Q, bId);

		DiffEntry c = DiffEntry.add(PATH_H, cId);
		DiffEntry d = DiffEntry.delete(PATH_B, dId);

		List<DiffEntry> changes = Arrays.asList(a, b, c, d);
		List<PathFilter> filters = Arrays.asList(PathFilter.create(PATH_A),
				PathFilter.create(PATH_H));
		List<DiffEntry> entries = frd.compute(changes, filters);
		assertEquals("Unexpected entries in: " + entries, 2, entries.size());
		assertSame(a, entries.get(0));
		assertSame(c, entries.get(1));
	}
}

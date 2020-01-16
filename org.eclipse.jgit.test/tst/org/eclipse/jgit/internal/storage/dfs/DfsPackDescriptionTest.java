/*
 * Copyright (C) 2018, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.COMPACT;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC_REST;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC_TXN;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.INSERT;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.RECEIVE;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.UNREACHABLE_GARBAGE;
import static org.eclipse.jgit.internal.storage.pack.PackExt.INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.PACK;
import static org.junit.Assert.assertEquals;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource;
import org.junit.Before;
import org.junit.Test;

public final class DfsPackDescriptionTest {
	private AtomicInteger counter;

	@Before
	public void setUp() {
		counter = new AtomicInteger();
	}

	@Test
	public void objectLookupComparatorEqual() throws Exception {
		DfsPackDescription a = create(RECEIVE);
		a.setFileSize(PACK, 1);
		a.setFileSize(INDEX, 1);
		a.setLastModified(1);
		a.setObjectCount(1);
		a.setMaxUpdateIndex(1);

		DfsPackDescription b = create(INSERT);
		b.setFileSize(PACK, 1);
		b.setFileSize(INDEX, 2);
		b.setLastModified(1);
		b.setObjectCount(1);
		b.setMaxUpdateIndex(2);

		assertComparesEqual(DfsPackDescription.objectLookupComparator(), a, b);
	}

	@Test
	public void objectLookupComparatorPackSource() throws Exception {
		DfsPackDescription a = create(COMPACT);
		a.setFileSize(PACK, 2);
		a.setLastModified(1);
		a.setObjectCount(2);

		DfsPackDescription b = create(GC);
		b.setFileSize(PACK, 1);
		b.setLastModified(2);
		b.setObjectCount(1);

		assertComparesLessThan(DfsPackDescription.objectLookupComparator(), a, b);
	}

	@Test
	public void objectLookupComparatorCustomPackSourceComparator()
			throws Exception {
		DfsPackDescription a = create(GC);

		DfsPackDescription b = create(COMPACT);

		assertComparesLessThan(DfsPackDescription.objectLookupComparator(), b, a);
		assertComparesLessThan(
				DfsPackDescription.objectLookupComparator(
					new PackSource.ComparatorBuilder()
						.add(GC)
						.add(INSERT, RECEIVE, GC_REST, GC_TXN, UNREACHABLE_GARBAGE)
						.add(COMPACT)
						.build()),
				a, b);
	}

	@Test
	public void objectLookupComparatorGcFileSize() throws Exception {
		// a is older and smaller.
		DfsPackDescription a = create(GC_REST);
		a.setFileSize(PACK, 100);
		a.setLastModified(1);
		a.setObjectCount(2);

		// b is newer and larger.
		DfsPackDescription b = create(GC_REST);
		b.setFileSize(PACK, 200);
		b.setLastModified(2);
		b.setObjectCount(1);

		// Since they have the same GC type, tiebreaker is size, and a comes first.
		assertComparesLessThan(DfsPackDescription.objectLookupComparator(), a, b);
	}

	@Test
	public void objectLookupComparatorNonGcLastModified()
			throws Exception {
		// a is older and smaller.
		DfsPackDescription a = create(INSERT);
		a.setFileSize(PACK, 100);
		a.setLastModified(1);
		a.setObjectCount(2);

		// b is newer and larger.
		DfsPackDescription b = create(INSERT);
		b.setFileSize(PACK, 200);
		b.setLastModified(2);
		b.setObjectCount(1);

		// Since they have the same type but not GC, tiebreaker is last modified,
		// and b comes first.
		assertComparesLessThan(DfsPackDescription.objectLookupComparator(), b, a);
	}

	@Test
	public void objectLookupComparatorObjectCount() throws Exception {
		DfsPackDescription a = create(INSERT);
		a.setObjectCount(1);

		DfsPackDescription b = create(INSERT);
		b.setObjectCount(2);

		assertComparesLessThan(DfsPackDescription.objectLookupComparator(), a, b);
	}

	@Test
	public void reftableComparatorEqual() throws Exception {
		DfsPackDescription a = create(INSERT);
		a.setFileSize(PACK, 100);
		a.setObjectCount(1);

		DfsPackDescription b = create(INSERT);
		b.setFileSize(PACK, 200);
		a.setObjectCount(2);

		assertComparesEqual(DfsPackDescription.reftableComparator(), a, b);
	}

	@Test
	public void reftableComparatorPackSource() throws Exception {
		DfsPackDescription a = create(INSERT);
		a.setMaxUpdateIndex(1);
		a.setLastModified(1);

		DfsPackDescription b = create(GC);
		b.setMaxUpdateIndex(2);
		b.setLastModified(2);

		assertComparesLessThan(DfsPackDescription.reftableComparator(), b, a);
	}

	@Test
	public void reftableComparatorMaxUpdateIndex() throws Exception {
		DfsPackDescription a = create(INSERT);
		a.setMaxUpdateIndex(1);
		a.setLastModified(2);

		DfsPackDescription b = create(INSERT);
		b.setMaxUpdateIndex(2);
		b.setLastModified(1);

		assertComparesLessThan(DfsPackDescription.reftableComparator(), a, b);
	}

	@Test
	public void reftableComparatorLastModified() throws Exception {
		DfsPackDescription a = create(INSERT);
		a.setLastModified(1);

		DfsPackDescription b = create(INSERT);
		b.setLastModified(2);

		assertComparesLessThan(DfsPackDescription.reftableComparator(), a, b);
	}

	@Test
	public void reuseComparatorEqual() throws Exception {
		DfsPackDescription a = create(RECEIVE);
		a.setFileSize(PACK, 1);
		a.setFileSize(INDEX, 1);
		a.setLastModified(1);
		a.setObjectCount(1);
		a.setMaxUpdateIndex(1);

		DfsPackDescription b = create(INSERT);
		b.setFileSize(PACK, 2);
		b.setFileSize(INDEX, 2);
		b.setLastModified(2);
		b.setObjectCount(2);
		b.setMaxUpdateIndex(2);

		assertComparesEqual(DfsPackDescription.reuseComparator(), a, b);
	}

	@Test
	public void reuseComparatorGcPackSize() throws Exception {
		DfsPackDescription a = create(GC_REST);
		a.setFileSize(PACK, 1);
		a.setFileSize(INDEX, 1);
		a.setLastModified(2);
		a.setObjectCount(1);
		a.setMaxUpdateIndex(1);

		DfsPackDescription b = create(GC_REST);
		b.setFileSize(PACK, 2);
		b.setFileSize(INDEX, 2);
		b.setLastModified(1);
		b.setObjectCount(2);
		b.setMaxUpdateIndex(2);

		assertComparesLessThan(DfsPackDescription.reuseComparator(), b, a);
	}

	private DfsPackDescription create(PackSource source) {
		return new DfsPackDescription(
				new DfsRepositoryDescription("repo"),
				"pack_" + counter.incrementAndGet(),
				source);
	}

	private static <T> void assertComparesEqual(
			Comparator<T> comparator, T o1, T o2) {
		assertEquals(
				"first object must compare equal to itself",
				0, comparator.compare(o1, o1));
		assertEquals(
				"second object must compare equal to itself",
				0, comparator.compare(o2, o2));
		assertEquals(
				"first object must compare equal to second object",
				0, comparator.compare(o1, o2));
	}

	private static <T> void assertComparesLessThan(
			Comparator<T> comparator, T o1, T o2) {
		assertEquals(
				"first object must compare equal to itself",
				0, comparator.compare(o1, o1));
		assertEquals(
				"second object must compare equal to itself",
				0, comparator.compare(o2, o2));
		assertEquals(
				"first object must compare less than second object",
				-1, comparator.compare(o1, o2));
	}
}

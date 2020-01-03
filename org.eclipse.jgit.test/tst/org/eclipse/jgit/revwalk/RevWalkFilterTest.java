/*
 * Copyright (C) 2009-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.util.Date;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.revwalk.filter.AndRevFilter;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.revwalk.filter.NotRevFilter;
import org.eclipse.jgit.revwalk.filter.OrRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.junit.Test;

public class RevWalkFilterTest extends RevWalkTestCase {
	private static final MyAll MY_ALL = new MyAll();

	@Test
	public void testFilter_ALL() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);

		rw.setRevFilter(RevFilter.ALL);
		markStart(c);
		assertCommit(c, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testFilter_Negate_ALL() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);

		rw.setRevFilter(RevFilter.ALL.negate());
		markStart(c);
		assertNull(rw.next());
	}

	@Test
	public void testFilter_NOT_ALL() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);

		rw.setRevFilter(NotRevFilter.create(RevFilter.ALL));
		markStart(c);
		assertNull(rw.next());
	}

	@Test
	public void testFilter_NONE() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);

		rw.setRevFilter(RevFilter.NONE);
		markStart(c);
		assertNull(rw.next());
	}

	@Test
	public void testFilter_NOT_NONE() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);

		rw.setRevFilter(NotRevFilter.create(RevFilter.NONE));
		markStart(c);
		assertCommit(c, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testFilter_ALL_And_NONE() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);

		rw.setRevFilter(AndRevFilter.create(RevFilter.ALL, RevFilter.NONE));
		markStart(c);
		assertNull(rw.next());
	}

	@Test
	public void testFilter_NONE_And_ALL() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);

		rw.setRevFilter(AndRevFilter.create(RevFilter.NONE, RevFilter.ALL));
		markStart(c);
		assertNull(rw.next());
	}

	@Test
	public void testFilter_ALL_Or_NONE() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);

		rw.setRevFilter(OrRevFilter.create(RevFilter.ALL, RevFilter.NONE));
		markStart(c);
		assertCommit(c, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testFilter_NONE_Or_ALL() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);

		rw.setRevFilter(OrRevFilter.create(RevFilter.NONE, RevFilter.ALL));
		markStart(c);
		assertCommit(c, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testFilter_MY_ALL_And_NONE() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);

		rw.setRevFilter(AndRevFilter.create(MY_ALL, RevFilter.NONE));
		markStart(c);
		assertNull(rw.next());
	}

	@Test
	public void testFilter_NONE_And_MY_ALL() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);

		rw.setRevFilter(AndRevFilter.create(RevFilter.NONE, MY_ALL));
		markStart(c);
		assertNull(rw.next());
	}

	@Test
	public void testFilter_MY_ALL_Or_NONE() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);

		rw.setRevFilter(OrRevFilter.create(MY_ALL, RevFilter.NONE));
		markStart(c);
		assertCommit(c, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testFilter_NONE_Or_MY_ALL() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);

		rw.setRevFilter(OrRevFilter.create(RevFilter.NONE, MY_ALL));
		markStart(c);
		assertCommit(c, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testFilter_NO_MERGES() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c1 = commit(b);
		final RevCommit c2 = commit(b);
		final RevCommit d = commit(c1, c2);
		final RevCommit e = commit(d);

		rw.setRevFilter(RevFilter.NO_MERGES);
		markStart(e);
		assertCommit(e, rw.next());
		assertCommit(c2, rw.next());
		assertCommit(c1, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testCommitTimeRevFilter() throws Exception {
		final RevCommit a = commit();
		tick(100);

		final RevCommit b = commit(a);
		tick(100);

		Date since = getDate();
		final RevCommit c1 = commit(b);
		tick(100);

		final RevCommit c2 = commit(b);
		tick(100);

		Date until = getDate();
		final RevCommit d = commit(c1, c2);
		tick(100);

		final RevCommit e = commit(d);

		{
			RevFilter after = CommitTimeRevFilter.after(since);
			assertNotNull(after);
			rw.setRevFilter(after);
			markStart(e);
			assertCommit(e, rw.next());
			assertCommit(d, rw.next());
			assertCommit(c2, rw.next());
			assertCommit(c1, rw.next());
			assertNull(rw.next());
		}

		{
			RevFilter before = CommitTimeRevFilter.before(until);
			assertNotNull(before);
			rw.reset();
			rw.setRevFilter(before);
			markStart(e);
			assertCommit(c2, rw.next());
			assertCommit(c1, rw.next());
			assertCommit(b, rw.next());
			assertCommit(a, rw.next());
			assertNull(rw.next());
		}

		{
			RevFilter between = CommitTimeRevFilter.between(since, until);
			assertNotNull(between);
			rw.reset();
			rw.setRevFilter(between);
			markStart(e);
			assertCommit(c2, rw.next());
			assertCommit(c1, rw.next());
			assertNull(rw.next());
		}
	}

	private static class MyAll extends RevFilter {
		@Override
		public RevFilter clone() {
			return this;
		}

		@Override
		public boolean include(RevWalk walker, RevCommit cmit)
				throws StopWalkException, MissingObjectException,
				IncorrectObjectTypeException, IOException {
			return true;
		}

		@Override
		public boolean requiresCommitBody() {
			return false;
		}
	}
}

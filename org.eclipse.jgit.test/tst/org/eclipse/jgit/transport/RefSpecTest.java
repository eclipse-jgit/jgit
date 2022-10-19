/*
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2013, Robin Stocker <robin@nibor.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefSpec.WildcardMode;
import org.junit.jupiter.api.Test;

public class RefSpecTest {
	@Test
	void testMasterMaster() {
		final String sn = "refs/heads/master";
		final RefSpec rs = new RefSpec(sn + ":" + sn);
		assertFalse(rs.isForceUpdate());
		assertFalse(rs.isWildcard());
		assertEquals(sn, rs.getSource());
		assertEquals(sn, rs.getDestination());
		assertEquals(sn + ":" + sn, rs.toString());
		assertEquals(rs, new RefSpec(rs.toString()));

		Ref r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, sn, null);
		assertTrue(rs.matchSource(r));
		assertTrue(rs.matchDestination(r));
		assertSame(rs, rs.expandFromSource(r));

		r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, sn + "-and-more", null);
		assertFalse(rs.matchSource(r));
		assertFalse(rs.matchDestination(r));
	}

	@Test
	void testSplitLastColon() {
		final String lhs = ":m:a:i:n:t";
		final String rhs = "refs/heads/maint";
		final RefSpec rs = new RefSpec(lhs + ":" + rhs);
		assertFalse(rs.isForceUpdate());
		assertFalse(rs.isWildcard());
		assertEquals(lhs, rs.getSource());
		assertEquals(rhs, rs.getDestination());
		assertEquals(lhs + ":" + rhs, rs.toString());
		assertEquals(rs, new RefSpec(rs.toString()));
	}

	@Test
	void testForceMasterMaster() {
		final String sn = "refs/heads/master";
		final RefSpec rs = new RefSpec("+" + sn + ":" + sn);
		assertTrue(rs.isForceUpdate());
		assertFalse(rs.isWildcard());
		assertEquals(sn, rs.getSource());
		assertEquals(sn, rs.getDestination());
		assertEquals("+" + sn + ":" + sn, rs.toString());
		assertEquals(rs, new RefSpec(rs.toString()));

		Ref r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, sn, null);
		assertTrue(rs.matchSource(r));
		assertTrue(rs.matchDestination(r));
		assertSame(rs, rs.expandFromSource(r));

		r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, sn + "-and-more", null);
		assertFalse(rs.matchSource(r));
		assertFalse(rs.matchDestination(r));
	}

	@Test
	void testMaster() {
		final String sn = "refs/heads/master";
		final RefSpec rs = new RefSpec(sn);
		assertFalse(rs.isForceUpdate());
		assertFalse(rs.isWildcard());
		assertEquals(sn, rs.getSource());
		assertNull(rs.getDestination());
		assertEquals(sn, rs.toString());
		assertEquals(rs, new RefSpec(rs.toString()));

		Ref r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, sn, null);
		assertTrue(rs.matchSource(r));
		assertFalse(rs.matchDestination(r));
		assertSame(rs, rs.expandFromSource(r));

		r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, sn + "-and-more", null);
		assertFalse(rs.matchSource(r));
		assertFalse(rs.matchDestination(r));
	}

	@Test
	void testForceMaster() {
		final String sn = "refs/heads/master";
		final RefSpec rs = new RefSpec("+" + sn);
		assertTrue(rs.isForceUpdate());
		assertFalse(rs.isWildcard());
		assertEquals(sn, rs.getSource());
		assertNull(rs.getDestination());
		assertEquals("+" + sn, rs.toString());
		assertEquals(rs, new RefSpec(rs.toString()));

		Ref r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, sn, null);
		assertTrue(rs.matchSource(r));
		assertFalse(rs.matchDestination(r));
		assertSame(rs, rs.expandFromSource(r));

		r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, sn + "-and-more", null);
		assertFalse(rs.matchSource(r));
		assertFalse(rs.matchDestination(r));
	}

	@Test
	void testDeleteMaster() {
		final String sn = "refs/heads/master";
		final RefSpec rs = new RefSpec(":" + sn);
		assertFalse(rs.isForceUpdate());
		assertFalse(rs.isWildcard());
		assertNull(rs.getSource());
		assertEquals(sn, rs.getDestination());
		assertEquals(":" + sn, rs.toString());
		assertEquals(rs, new RefSpec(rs.toString()));

		Ref r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, sn, null);
		assertFalse(rs.matchSource(r));
		assertTrue(rs.matchDestination(r));
		assertSame(rs, rs.expandFromSource(r));

		r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, sn + "-and-more", null);
		assertFalse(rs.matchSource(r));
		assertFalse(rs.matchDestination(r));
	}

	@Test
	void testForceRemotesOrigin() {
		final String srcn = "refs/heads/*";
		final String dstn = "refs/remotes/origin/*";
		final RefSpec rs = new RefSpec("+" + srcn + ":" + dstn);
		assertTrue(rs.isForceUpdate());
		assertTrue(rs.isWildcard());
		assertEquals(srcn, rs.getSource());
		assertEquals(dstn, rs.getDestination());
		assertEquals("+" + srcn + ":" + dstn, rs.toString());
		assertEquals(rs, new RefSpec(rs.toString()));

		Ref r;
		RefSpec expanded;

		r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/heads/master", null);
		assertTrue(rs.matchSource(r));
		assertFalse(rs.matchDestination(r));
		expanded = rs.expandFromSource(r);
		assertNotSame(rs, expanded);
		assertTrue(expanded.isForceUpdate());
		assertFalse(expanded.isWildcard());
		assertEquals(r.getName(), expanded.getSource());
		assertEquals("refs/remotes/origin/master", expanded.getDestination());

		r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/remotes/origin/next", null);
		assertFalse(rs.matchSource(r));
		assertTrue(rs.matchDestination(r));

		r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, "refs/tags/v1.0", null);
		assertFalse(rs.matchSource(r));
		assertFalse(rs.matchDestination(r));
	}

	@Test
	void testCreateEmpty() {
		final RefSpec rs = new RefSpec();
		assertFalse(rs.isForceUpdate());
		assertFalse(rs.isWildcard());
		assertEquals("HEAD", rs.getSource());
		assertNull(rs.getDestination());
		assertEquals("HEAD", rs.toString());
	}

	@Test
	void testSetForceUpdate() {
		final String s = "refs/heads/*:refs/remotes/origin/*";
		final RefSpec a = new RefSpec(s);
		assertFalse(a.isForceUpdate());
		RefSpec b = a.setForceUpdate(true);
		assertNotSame(a, b);
		assertFalse(a.isForceUpdate());
		assertTrue(b.isForceUpdate());
		assertEquals(s, a.toString());
		assertEquals("+" + s, b.toString());
	}

	@Test
	void testSetSource() {
		final RefSpec a = new RefSpec();
		final RefSpec b = a.setSource("refs/heads/master");
		assertNotSame(a, b);
		assertEquals("HEAD", a.toString());
		assertEquals("refs/heads/master", b.toString());
	}

	@Test
	void testSetDestination() {
		final RefSpec a = new RefSpec();
		final RefSpec b = a.setDestination("refs/heads/master");
		assertNotSame(a, b);
		assertEquals("HEAD", a.toString());
		assertEquals("HEAD:refs/heads/master", b.toString());
	}

	@Test
	void testSetDestination_SourceNull() {
		final RefSpec a = new RefSpec();
		RefSpec b;

		b = a.setDestination("refs/heads/master");
		b = b.setSource(null);
		assertNotSame(a, b);
		assertEquals("HEAD", a.toString());
		assertEquals(":refs/heads/master", b.toString());
	}

	@Test
	void testSetSourceDestination() {
		final RefSpec a = new RefSpec();
		final RefSpec b;
		b = a.setSourceDestination("refs/heads/*", "refs/remotes/origin/*");
		assertNotSame(a, b);
		assertEquals("HEAD", a.toString());
		assertEquals("refs/heads/*:refs/remotes/origin/*", b.toString());
	}

	@Test
	void testExpandFromDestination_NonWildcard() {
		final String src = "refs/heads/master";
		final String dst = "refs/remotes/origin/master";
		final RefSpec a = new RefSpec(src + ":" + dst);
		final RefSpec r = a.expandFromDestination(dst);
		assertSame(a, r);
		assertFalse(r.isWildcard());
		assertEquals(src, r.getSource());
		assertEquals(dst, r.getDestination());
	}

	@Test
	void testExpandFromDestination_Wildcard() {
		final String src = "refs/heads/master";
		final String dst = "refs/remotes/origin/master";
		final RefSpec a = new RefSpec("refs/heads/*:refs/remotes/origin/*");
		final RefSpec r = a.expandFromDestination(dst);
		assertNotSame(a, r);
		assertFalse(r.isWildcard());
		assertEquals(src, r.getSource());
		assertEquals(dst, r.getDestination());
	}

	@Test
	void isWildcardShouldWorkForWildcardSuffixAndComponent() {
		assertTrue(RefSpec.isWildcard("refs/heads/*"));
		assertTrue(RefSpec.isWildcard("refs/pull/*/head"));
		assertFalse(RefSpec.isWildcard("refs/heads/a"));
	}

	@Test
	void testWildcardInMiddleOfSource() {
		RefSpec a = new RefSpec("+refs/pull/*/head:refs/remotes/origin/pr/*");
		assertTrue(a.isWildcard());
		assertTrue(a.matchSource("refs/pull/a/head"));
		assertTrue(a.matchSource("refs/pull/foo/head"));
		assertTrue(a.matchSource("refs/pull/foo/bar/head"));
		assertFalse(a.matchSource("refs/pull/foo"));
		assertFalse(a.matchSource("refs/pull/head"));
		assertFalse(a.matchSource("refs/pull/foo/head/more"));
		assertFalse(a.matchSource("refs/pullx/head"));

		RefSpec b = a.expandFromSource("refs/pull/foo/head");
		assertEquals("refs/remotes/origin/pr/foo", b.getDestination());
		RefSpec c = a.expandFromDestination("refs/remotes/origin/pr/foo");
		assertEquals("refs/pull/foo/head", c.getSource());
	}

	@Test
	void testWildcardInMiddleOfDestionation() {
		RefSpec a = new RefSpec("+refs/heads/*:refs/remotes/origin/*/head");
		assertTrue(a.isWildcard());
		assertTrue(a.matchDestination("refs/remotes/origin/a/head"));
		assertTrue(a.matchDestination("refs/remotes/origin/foo/head"));
		assertTrue(a.matchDestination("refs/remotes/origin/foo/bar/head"));
		assertFalse(a.matchDestination("refs/remotes/origin/foo"));
		assertFalse(a.matchDestination("refs/remotes/origin/head"));
		assertFalse(a.matchDestination("refs/remotes/origin/foo/head/more"));
		assertFalse(a.matchDestination("refs/remotes/originx/head"));

		RefSpec b = a.expandFromSource("refs/heads/foo");
		assertEquals("refs/remotes/origin/foo/head", b.getDestination());
		RefSpec c = a.expandFromDestination("refs/remotes/origin/foo/head");
		assertEquals("refs/heads/foo", c.getSource());
	}

	@Test
	void testWildcardAfterText1() {
		RefSpec a = new RefSpec("refs/heads/*/for-linus:refs/remotes/mine/*-blah");
		assertTrue(a.isWildcard());
		assertTrue(a.matchDestination("refs/remotes/mine/x-blah"));
		assertTrue(a.matchDestination("refs/remotes/mine/foo-blah"));
		assertTrue(a.matchDestination("refs/remotes/mine/foo/x-blah"));
		assertFalse(a.matchDestination("refs/remotes/origin/foo/x-blah"));

		RefSpec b = a.expandFromSource("refs/heads/foo/for-linus");
		assertEquals("refs/remotes/mine/foo-blah", b.getDestination());
		RefSpec c = a.expandFromDestination("refs/remotes/mine/foo-blah");
		assertEquals("refs/heads/foo/for-linus", c.getSource());
	}

	@Test
	void testWildcardAfterText2() {
		RefSpec a = new RefSpec("refs/heads*/for-linus:refs/remotes/mine/*");
		assertTrue(a.isWildcard());
		assertTrue(a.matchSource("refs/headsx/for-linus"));
		assertTrue(a.matchSource("refs/headsfoo/for-linus"));
		assertTrue(a.matchSource("refs/headsx/foo/for-linus"));
		assertFalse(a.matchSource("refs/headx/for-linus"));

		RefSpec b = a.expandFromSource("refs/headsx/for-linus");
		assertEquals("refs/remotes/mine/x", b.getDestination());
		RefSpec c = a.expandFromDestination("refs/remotes/mine/x");
		assertEquals("refs/headsx/for-linus", c.getSource());

		RefSpec d = a.expandFromSource("refs/headsx/foo/for-linus");
		assertEquals("refs/remotes/mine/x/foo", d.getDestination());
		RefSpec e = a.expandFromDestination("refs/remotes/mine/x/foo");
		assertEquals("refs/headsx/foo/for-linus", e.getSource());
	}

	@Test
	void testWildcardMirror() {
		RefSpec a = new RefSpec("*:*");
		assertTrue(a.isWildcard());
		assertTrue(a.matchSource("a"));
		assertTrue(a.matchSource("foo"));
		assertTrue(a.matchSource("foo/bar"));
		assertTrue(a.matchDestination("a"));
		assertTrue(a.matchDestination("foo"));
		assertTrue(a.matchDestination("foo/bar"));

		RefSpec b = a.expandFromSource("refs/heads/foo");
		assertEquals("refs/heads/foo", b.getDestination());
		RefSpec c = a.expandFromDestination("refs/heads/foo");
		assertEquals("refs/heads/foo", c.getSource());
	}

	@Test
	void testWildcardAtStart() {
		RefSpec a = new RefSpec("*/head:refs/heads/*");
		assertTrue(a.isWildcard());
		assertTrue(a.matchSource("a/head"));
		assertTrue(a.matchSource("foo/head"));
		assertTrue(a.matchSource("foo/bar/head"));
		assertFalse(a.matchSource("/head"));
		assertFalse(a.matchSource("a/head/extra"));

		RefSpec b = a.expandFromSource("foo/head");
		assertEquals("refs/heads/foo", b.getDestination());
		RefSpec c = a.expandFromDestination("refs/heads/foo");
		assertEquals("foo/head", c.getSource());
	}

	@Test
	void invalidWhenSourceEndsWithSlash() {
		assertThrows(IllegalArgumentException.class, () -> {
			assertNotNull(new RefSpec("refs/heads/"));
		});
	}

	@Test
	void invalidWhenDestinationEndsWithSlash() {
		assertThrows(IllegalArgumentException.class, () -> {
			assertNotNull(new RefSpec("refs/heads/master:refs/heads/"));
		});
	}

	@Test
	void invalidWhenSourceOnlyAndWildcard() {
		assertThrows(IllegalArgumentException.class, () -> {
			assertNotNull(new RefSpec("refs/heads/*"));
		});
	}

	@Test
	void invalidWhenDestinationOnlyAndWildcard() {
		assertThrows(IllegalArgumentException.class, () -> {
			assertNotNull(new RefSpec(":refs/heads/*"));
		});
	}

	@Test
	void invalidWhenOnlySourceWildcard() {
		assertThrows(IllegalArgumentException.class, () -> {
			assertNotNull(new RefSpec("refs/heads/*:refs/heads/foo"));
		});
	}

	@Test
	void invalidWhenOnlyDestinationWildcard() {
		assertThrows(IllegalArgumentException.class, () -> {
			assertNotNull(new RefSpec("refs/heads/foo:refs/heads/*"));
		});
	}

	@Test
	void invalidWhenMoreThanOneWildcardInSource() {
		assertThrows(IllegalArgumentException.class, () -> {
			assertNotNull(new RefSpec("refs/heads/*/*:refs/heads/*"));
		});
	}

	@Test
	void invalidWhenMoreThanOneWildcardInDestination() {
		assertThrows(IllegalArgumentException.class, () -> {
			assertNotNull(new RefSpec("refs/heads/*:refs/heads/*/*"));
		});
	}

	@Test
	void invalidSourceDoubleSlashes() {
		assertThrows(IllegalArgumentException.class, () -> {
			assertNotNull(new RefSpec("refs/heads//wrong"));
		});
	}

	@Test
	void invalidSlashAtStart() {
		assertThrows(IllegalArgumentException.class, () -> {
			assertNotNull(new RefSpec("/foo:/foo"));
		});
	}

	@Test
	void invalidDestinationDoubleSlashes() {
		assertThrows(IllegalArgumentException.class, () -> {
			assertNotNull(new RefSpec(":refs/heads//wrong"));
		});
	}

	@Test
	void invalidSetSource() {
		assertThrows(IllegalArgumentException.class, () -> {
			RefSpec a = new RefSpec("refs/heads/*:refs/remotes/origin/*");
			a.setSource("refs/heads/*/*");
		});
	}

	@Test
	void invalidSetDestination() {
		assertThrows(IllegalArgumentException.class, () -> {
			RefSpec a = new RefSpec("refs/heads/*:refs/remotes/origin/*");
			a.setDestination("refs/remotes/origin/*/*");
		});
	}

	@Test
	void invalidNegativeAndForce() {
		assertThrows(IllegalArgumentException.class, () -> {
			assertNotNull(new RefSpec("^+refs/heads/master"));
		});
	}

	@Test
	void invalidForceAndNegative() {
		assertThrows(IllegalArgumentException.class, () -> {
			assertNotNull(new RefSpec("+^refs/heads/master"));
		});
	}

	@Test
	void invalidNegativeNoSrcDest() {
		assertThrows(IllegalArgumentException.class, () -> {
			assertNotNull(new RefSpec("^"));
		});
	}

	@Test
	void invalidNegativeBothSrcDest() {
		assertThrows(IllegalArgumentException.class, () -> {
			assertNotNull(new RefSpec("^refs/heads/*:refs/heads/*"));
		});
	}

	@Test
	void sourceOnlywithWildcard() {
		RefSpec a = new RefSpec("refs/heads/*",
				WildcardMode.ALLOW_MISMATCH);
		assertTrue(a.matchSource("refs/heads/master"));
		assertNull(a.getDestination());
	}

	@Test
	void destinationWithWildcard() {
		RefSpec a = new RefSpec("refs/heads/master:refs/heads/*",
				WildcardMode.ALLOW_MISMATCH);
		assertTrue(a.matchSource("refs/heads/master"));
		assertTrue(a.matchDestination("refs/heads/master"));
		assertTrue(a.matchDestination("refs/heads/foo"));
	}

	@Test
	void onlyWildCard() {
		RefSpec a = new RefSpec("*", WildcardMode.ALLOW_MISMATCH);
		assertTrue(a.matchSource("refs/heads/master"));
		assertNull(a.getDestination());
	}

	@Test
	void matching() {
		RefSpec a = new RefSpec(":");
		assertTrue(a.isMatching());
		assertFalse(a.isForceUpdate());
	}

	@Test
	void matchingForced() {
		RefSpec a = new RefSpec("+:");
		assertTrue(a.isMatching());
		assertTrue(a.isForceUpdate());
	}

	@Test
	void negativeRefSpecWithDest() {
		RefSpec a = new RefSpec("^:refs/readonly/*");
		assertTrue(a.isNegative());
		assertNull(a.getSource());
		assertEquals(a.getDestination(), "refs/readonly/*");
	}

	// Because of some of the API's existing behavior, without a colon at the
	// end of the refspec, dest will be null.
	@Test
	void negativeRefSpecWithSrcAndNullDest() {
		RefSpec a = new RefSpec("^refs/testdata/*");
		assertTrue(a.isNegative());
		assertNull(a.getDestination());
		assertEquals(a.getSource(), "refs/testdata/*");
	}

	// Because of some of the API's existing behavior, with a colon at the end
	// of the refspec, dest will be empty.
	@Test
	void negativeRefSpecWithSrcAndEmptyDest() {
		RefSpec a = new RefSpec("^refs/testdata/*:");
		assertTrue(a.isNegative());
		assertTrue(a.getDestination().isEmpty());
		assertEquals(a.getSource(), "refs/testdata/*");
	}
}

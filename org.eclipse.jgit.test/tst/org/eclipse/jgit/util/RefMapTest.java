/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.SymbolicRef;
import org.junit.Before;
import org.junit.Test;

public class RefMapTest {
	private static final ObjectId ID_ONE = ObjectId
			.fromString("41eb0d88f833b558bddeb269b7ab77399cdf98ed");

	private static final ObjectId ID_TWO = ObjectId
			.fromString("698dd0b8d0c299f080559a1cffc7fe029479a408");

	private RefList<Ref> packed;

	private RefList<Ref> loose;

	private RefList<Ref> resolved;

	@Before
	public void setUp() throws Exception {
		packed = RefList.emptyList();
		loose = RefList.emptyList();
		resolved = RefList.emptyList();
	}

	@Test
	public void testEmpty_NoPrefix1() {
		RefMap map = new RefMap("", packed, loose, resolved);
		assertTrue(map.isEmpty()); // before size was computed
		assertEquals(0, map.size());
		assertTrue(map.isEmpty()); // after size was computed

		assertFalse(map.entrySet().iterator().hasNext());
		assertFalse(map.keySet().iterator().hasNext());
		assertFalse(map.containsKey("a"));
		assertNull(map.get("a"));
	}

	@Test
	public void testEmpty_NoPrefix2() {
		RefMap map = new RefMap();
		assertTrue(map.isEmpty()); // before size was computed
		assertEquals(0, map.size());
		assertTrue(map.isEmpty()); // after size was computed

		assertFalse(map.entrySet().iterator().hasNext());
		assertFalse(map.keySet().iterator().hasNext());
		assertFalse(map.containsKey("a"));
		assertNull(map.get("a"));
	}

	@Test
	public void testNotEmpty_NoPrefix() {
		final Ref master = newRef("refs/heads/master", ID_ONE);
		packed = toList(master);

		RefMap map = new RefMap("", packed, loose, resolved);
		assertFalse(map.isEmpty()); // before size was computed
		assertEquals(1, map.size());
		assertFalse(map.isEmpty()); // after size was computed
		assertSame(master, map.values().iterator().next());
	}

	@Test
	public void testEmpty_WithPrefix() {
		final Ref master = newRef("refs/heads/master", ID_ONE);
		packed = toList(master);

		RefMap map = new RefMap("refs/tags/", packed, loose, resolved);
		assertTrue(map.isEmpty()); // before size was computed
		assertEquals(0, map.size());
		assertTrue(map.isEmpty()); // after size was computed

		assertFalse(map.entrySet().iterator().hasNext());
		assertFalse(map.keySet().iterator().hasNext());
	}

	@Test
	public void testNotEmpty_WithPrefix() {
		final Ref master = newRef("refs/heads/master", ID_ONE);
		packed = toList(master);

		RefMap map = new RefMap("refs/heads/", packed, loose, resolved);
		assertFalse(map.isEmpty()); // before size was computed
		assertEquals(1, map.size());
		assertFalse(map.isEmpty()); // after size was computed
		assertSame(master, map.values().iterator().next());
	}

	@Test
	public void testClear() {
		final Ref master = newRef("refs/heads/master", ID_ONE);
		loose = toList(master);

		RefMap map = new RefMap("", packed, loose, resolved);
		assertSame(master, map.get("refs/heads/master"));

		map.clear();
		assertNull(map.get("refs/heads/master"));
		assertTrue(map.isEmpty());
		assertEquals(0, map.size());
	}

	@Test
	public void testIterator_RefusesRemove() {
		final Ref master = newRef("refs/heads/master", ID_ONE);
		loose = toList(master);

		RefMap map = new RefMap("", packed, loose, resolved);
		Iterator<Ref> itr = map.values().iterator();
		assertTrue(itr.hasNext());
		assertSame(master, itr.next());
		try {
			itr.remove();
			fail("iterator allowed remove");
		} catch (UnsupportedOperationException err) {
			// expected
		}
	}

	@Test
	public void testIterator_FailsAtEnd() {
		final Ref master = newRef("refs/heads/master", ID_ONE);
		loose = toList(master);

		RefMap map = new RefMap("", packed, loose, resolved);
		Iterator<Ref> itr = map.values().iterator();
		assertTrue(itr.hasNext());
		assertSame(master, itr.next());
		try {
			itr.next();
			fail("iterator allowed next");
		} catch (NoSuchElementException err) {
			// expected
		}
	}

	@Test
	public void testIterator_MissingUnresolvedSymbolicRefIsBug() {
		final Ref master = newRef("refs/heads/master", ID_ONE);
		final Ref headR = newRef("HEAD", master);

		loose = toList(master);
		// loose should have added newRef("HEAD", "refs/heads/master")
		resolved = toList(headR);

		RefMap map = new RefMap("", packed, loose, resolved);
		Iterator<Ref> itr = map.values().iterator();
		try {
			itr.hasNext();
			fail("iterator did not catch bad input");
		} catch (IllegalStateException err) {
			// expected
		}
	}

	@Test
	public void testMerge_HeadMaster() {
		final Ref master = newRef("refs/heads/master", ID_ONE);
		final Ref headU = newRef("HEAD", "refs/heads/master");
		final Ref headR = newRef("HEAD", master);

		loose = toList(headU, master);
		resolved = toList(headR);

		RefMap map = new RefMap("", packed, loose, resolved);
		assertEquals(2, map.size());
		assertFalse(map.isEmpty());
		assertTrue(map.containsKey("refs/heads/master"));
		assertSame(master, map.get("refs/heads/master"));

		// resolved overrides loose given same name
		assertSame(headR, map.get("HEAD"));

		Iterator<Ref> itr = map.values().iterator();
		assertTrue(itr.hasNext());
		assertSame(headR, itr.next());
		assertTrue(itr.hasNext());
		assertSame(master, itr.next());
		assertFalse(itr.hasNext());
	}

	@Test
	public void testMerge_PackedLooseLoose() {
		final Ref refA = newRef("A", ID_ONE);
		final Ref refB_ONE = newRef("B", ID_ONE);
		final Ref refB_TWO = newRef("B", ID_TWO);
		final Ref refc = newRef("c", ID_ONE);

		packed = toList(refA, refB_ONE);
		loose = toList(refB_TWO, refc);

		RefMap map = new RefMap("", packed, loose, resolved);
		assertEquals(3, map.size());
		assertFalse(map.isEmpty());
		assertTrue(map.containsKey(refA.getName()));
		assertSame(refA, map.get(refA.getName()));

		// loose overrides packed given same name
		assertSame(refB_TWO, map.get(refB_ONE.getName()));

		Iterator<Ref> itr = map.values().iterator();
		assertTrue(itr.hasNext());
		assertSame(refA, itr.next());
		assertTrue(itr.hasNext());
		assertSame(refB_TWO, itr.next());
		assertTrue(itr.hasNext());
		assertSame(refc, itr.next());
		assertFalse(itr.hasNext());
	}

	@Test
	public void testMerge_WithPrefix() {
		final Ref a = newRef("refs/heads/A", ID_ONE);
		final Ref b = newRef("refs/heads/foo/bar/B", ID_TWO);
		final Ref c = newRef("refs/heads/foo/rab/C", ID_TWO);
		final Ref g = newRef("refs/heads/g", ID_ONE);
		packed = toList(a, b, c, g);

		RefMap map = new RefMap("refs/heads/foo/", packed, loose, resolved);
		assertEquals(2, map.size());

		assertSame(b, map.get("bar/B"));
		assertSame(c, map.get("rab/C"));
		assertNull(map.get("refs/heads/foo/bar/B"));
		assertNull(map.get("refs/heads/A"));

		assertTrue(map.containsKey("bar/B"));
		assertTrue(map.containsKey("rab/C"));
		assertFalse(map.containsKey("refs/heads/foo/bar/B"));
		assertFalse(map.containsKey("refs/heads/A"));

		Iterator<Map.Entry<String, Ref>> itr = map.entrySet().iterator();
		Map.Entry<String, Ref> ent;
		assertTrue(itr.hasNext());
		ent = itr.next();
		assertEquals("bar/B", ent.getKey());
		assertSame(b, ent.getValue());
		assertTrue(itr.hasNext());
		ent = itr.next();
		assertEquals("rab/C", ent.getKey());
		assertSame(c, ent.getValue());
		assertFalse(itr.hasNext());
	}

	@Test
	public void testPut_KeyMustMatchName_NoPrefix() {
		final Ref refA = newRef("refs/heads/A", ID_ONE);
		RefMap map = new RefMap("", packed, loose, resolved);
		try {
			map.put("FOO", refA);
			fail("map accepted invalid key/value pair");
		} catch (IllegalArgumentException err) {
			// expected
		}
	}

	@Test
	public void testPut_KeyMustMatchName_WithPrefix() {
		final Ref refA = newRef("refs/heads/A", ID_ONE);
		RefMap map = new RefMap("refs/heads/", packed, loose, resolved);
		try {
			map.put("FOO", refA);
			fail("map accepted invalid key/value pair");
		} catch (IllegalArgumentException err) {
			// expected
		}
	}

	@Test
	public void testPut_NoPrefix() {
		final Ref refA_one = newRef("refs/heads/A", ID_ONE);
		final Ref refA_two = newRef("refs/heads/A", ID_TWO);

		packed = toList(refA_one);

		RefMap map = new RefMap("", packed, loose, resolved);
		assertSame(refA_one, map.get(refA_one.getName()));
		assertSame(refA_one, map.put(refA_one.getName(), refA_two));

		// map changed, but packed, loose did not
		assertSame(refA_two, map.get(refA_one.getName()));
		assertSame(refA_one, packed.get(0));
		assertEquals(0, loose.size());

		assertSame(refA_two, map.put(refA_one.getName(), refA_one));
		assertSame(refA_one, map.get(refA_one.getName()));
	}

	@Test
	public void testPut_WithPrefix() {
		final Ref refA_one = newRef("refs/heads/A", ID_ONE);
		final Ref refA_two = newRef("refs/heads/A", ID_TWO);

		packed = toList(refA_one);

		RefMap map = new RefMap("refs/heads/", packed, loose, resolved);
		assertSame(refA_one, map.get("A"));
		assertSame(refA_one, map.put("A", refA_two));

		// map changed, but packed, loose did not
		assertSame(refA_two, map.get("A"));
		assertSame(refA_one, packed.get(0));
		assertEquals(0, loose.size());

		assertSame(refA_two, map.put("A", refA_one));
		assertSame(refA_one, map.get("A"));
	}

	@Test
	public void testPut_CollapseResolved() {
		final Ref master = newRef("refs/heads/master", ID_ONE);
		final Ref headU = newRef("HEAD", "refs/heads/master");
		final Ref headR = newRef("HEAD", master);
		final Ref a = newRef("refs/heads/A", ID_ONE);

		loose = toList(headU, master);
		resolved = toList(headR);

		RefMap map = new RefMap("", packed, loose, resolved);
		assertNull(map.put(a.getName(), a));
		assertSame(a, map.get(a.getName()));
		assertSame(headR, map.get("HEAD"));
	}

	@Test
	public void testRemove() {
		final Ref master = newRef("refs/heads/master", ID_ONE);
		final Ref headU = newRef("HEAD", "refs/heads/master");
		final Ref headR = newRef("HEAD", master);

		packed = toList(master);
		loose = toList(headU, master);
		resolved = toList(headR);

		RefMap map = new RefMap("", packed, loose, resolved);
		assertNull(map.remove("not.a.reference"));

		assertSame(master, map.remove("refs/heads/master"));
		assertNull(map.get("refs/heads/master"));

		assertSame(headR, map.remove("HEAD"));
		assertNull(map.get("HEAD"));

		assertTrue(map.isEmpty());
	}

	@Test
	public void testToString_NoPrefix() {
		final Ref a = newRef("refs/heads/A", ID_ONE);
		final Ref b = newRef("refs/heads/B", ID_TWO);

		packed = toList(a, b);

		StringBuilder exp = new StringBuilder();
		exp.append("[");
		exp.append(a.toString());
		exp.append(", ");
		exp.append(b.toString());
		exp.append("]");

		RefMap map = new RefMap("", packed, loose, resolved);
		assertEquals(exp.toString(), map.toString());
	}

	@Test
	public void testToString_WithPrefix() {
		final Ref a = newRef("refs/heads/A", ID_ONE);
		final Ref b = newRef("refs/heads/foo/B", ID_TWO);
		final Ref c = newRef("refs/heads/foo/C", ID_TWO);
		final Ref g = newRef("refs/heads/g", ID_ONE);

		packed = toList(a, b, c, g);

		StringBuilder exp = new StringBuilder();
		exp.append("[");
		exp.append(b.toString());
		exp.append(", ");
		exp.append(c.toString());
		exp.append("]");

		RefMap map = new RefMap("refs/heads/foo/", packed, loose, resolved);
		assertEquals(exp.toString(), map.toString());
	}

	@Test
	public void testEntryType() {
		final Ref a = newRef("refs/heads/A", ID_ONE);
		final Ref b = newRef("refs/heads/B", ID_TWO);

		packed = toList(a, b);

		RefMap map = new RefMap("refs/heads/", packed, loose, resolved);
		Iterator<Map.Entry<String, Ref>> itr = map.entrySet().iterator();
		Map.Entry<String, Ref> ent_a = itr.next();
		Map.Entry<String, Ref> ent_b = itr.next();

		assertEquals(ent_a.hashCode(), "A".hashCode());
		assertEquals(ent_a, ent_a);
		assertFalse(ent_a.equals(ent_b));

		assertEquals(a.toString(), ent_a.toString());
	}

	@Test
	public void testEntryTypeSet() {
		final Ref refA_one = newRef("refs/heads/A", ID_ONE);
		final Ref refA_two = newRef("refs/heads/A", ID_TWO);

		packed = toList(refA_one);

		RefMap map = new RefMap("refs/heads/", packed, loose, resolved);
		assertSame(refA_one, map.get("A"));

		Map.Entry<String, Ref> ent = map.entrySet().iterator().next();
		assertEquals("A", ent.getKey());
		assertSame(refA_one, ent.getValue());

		assertSame(refA_one, ent.setValue(refA_two));
		assertSame(refA_two, ent.getValue());
		assertSame(refA_two, map.get("A"));
		assertEquals(1, map.size());
	}

	private static RefList<Ref> toList(Ref... refs) {
		RefList.Builder<Ref> b = new RefList.Builder<>(refs.length);
		b.addAll(refs, 0, refs.length);
		return b.toRefList();
	}

	private static Ref newRef(String name, String dst) {
		return newRef(name,
				new ObjectIdRef.Unpeeled(Ref.Storage.NEW, dst, null));
	}

	private static Ref newRef(String name, Ref dst) {
		return new SymbolicRef(name, dst);
	}

	private static Ref newRef(String name, ObjectId id) {
		return new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, name, id);
	}
}

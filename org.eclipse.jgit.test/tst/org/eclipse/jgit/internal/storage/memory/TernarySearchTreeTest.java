/*
 * Copyright (C) 2021, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.memory;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.junit.Before;
import org.junit.Test;

public class TernarySearchTreeTest {

	private TernarySearchTree<String> tree;

	@Before
	public void setup() {
		tree = new TernarySearchTree<>();
		tree.insert("foo", "1");
		tree.insert("bar", "2");
		tree.insert("foobar", "3");
		tree.insert("foobaz", "4");
		tree.insert("johndoe", "5");
	}

	@Test
	public void testInsert() {
		tree.insert("foobarbaz", "6");
		assertEquals(6, tree.size());
		assertEquals("6", tree.get("foobarbaz"));
	}

	@Test
	public void testBatchInsert() {
		Map<String, String> m = new HashMap<>();
		m.put("refs/heads/master", "master");
		m.put("refs/heads/stable-1.0", "stable-1.0");
		m.put("refs/heads/stable-2.1", "stable-2.1");
		m.put("refs/heads/stable-2.0", "stable-2.0");
		m.put("refs/heads/stable-1.1", "stable-1.1");
		m.put("refs/heads/stable-2.2", "stable-2.2");
		m.put("aaa", "aaa");
		m.put("refs/tags/xyz", "xyz");
		m.put("refs/tags/v1.1", "v1.1");
		m.put("refs/tags/v2.2", "v2.2");
		m.put("refs/tags/v1.0", "v1.0");
		m.put("refs/tags/v2.1", "v2.1");
		m.put("refs/tags/v2.0", "v2.0");
		tree.insert(m);
		assertArrayEquals(
				new String[] { "refs/heads/master", "refs/heads/stable-1.0",
						"refs/heads/stable-1.1", "refs/heads/stable-2.0",
						"refs/heads/stable-2.1", "refs/heads/stable-2.2" },
				toArray(tree.getKeysWithPrefix("refs/heads/")));
		assertArrayEquals(
				new String[] { "refs/tags/v1.0", "refs/tags/v1.1",
						"refs/tags/v2.0", "refs/tags/v2.1", "refs/tags/v2.2",
						"refs/tags/xyz" },
				toArray(tree.getKeysWithPrefix("refs/tags/")));
		assertEquals("aaa", tree.get("aaa"));
	}

	@Test
	public void testInsertWithNullKey() {
		Exception exception = assertThrows(IllegalArgumentException.class,
				() -> {
					tree.insert(null, "42");
				});
		assertTrue(exception.getMessage()
				.contains("TernarySearchTree key must not be null or empty"));
	}

	@Test
	public void testOverwriteValue() {
		tree.insert("foo", "overwritten");
		assertEquals(5, tree.size());
		assertEquals("overwritten", tree.get("foo"));
	}

	@Test
	public void testInsertNullValue() {
		Exception exception = assertThrows(IllegalArgumentException.class,
				() -> {
					tree.insert("xxx", null);
				});
		assertTrue(exception.getMessage()
				.contains("cannot insert null value into TernarySearchTree"));
	}

	@Test
	public void testReplace() {
		Map<String, String> map = new HashMap<>();
		map.put("foo", "foo-value");
		map.put("bar", "bar-value");
		tree.reload(map.entrySet());
		assertArrayEquals(new String[] { "bar", "foo" },
				toArray(tree.getKeys()));
	}

	@Test
	public void testContains() {
		assertTrue(tree.contains("foo"));
		assertTrue(tree.contains("foobaz"));
		assertFalse(tree.contains("ship"));
	}

	@Test
	public void testContainsWithNullKey() {
		Exception exception = assertThrows(IllegalArgumentException.class,
				() -> {
					tree.contains(null);
				});
		assertTrue(exception.getMessage()
				.contains("TernarySearchTree key must not be null or empty"));
	}

	@Test
	public void testGet() {
		assertEquals("1", tree.get("foo"));
		assertEquals("5", tree.get("johndoe"));
		assertNull(tree.get("ship"));
	}

	@Test
	public void testGetWithNullKey() {
		Exception exception = assertThrows(IllegalArgumentException.class,
				() -> {
					tree.get(null);
				});
		assertTrue(exception.getMessage()
				.contains("TernarySearchTree key must not be null or empty"));
	}

	@Test
	public void testDelete() {
		tree.delete("foo");
		assertNull(tree.get("foo"));
		assertEquals(4, tree.size());
		tree.delete("cake");
		assertEquals(4, tree.size());
	}

	@Test
	public void testDeleteWithNullKey() {
		Exception exception = assertThrows(IllegalArgumentException.class,
				() -> {
					tree.delete(null);
				});
		assertTrue(exception.getMessage()
				.contains("TernarySearchTree key must not be null or empty"));
	}

	@Test
	public void testClear() {
		assertEquals(5, tree.size());
		tree.clear();
		assertEquals(0, tree.size());
		tree.getKeys().forEach(new Consumer<String>() {

			@Override
			public void accept(String t) {
				throw new IllegalStateException("should find no key");
			}
		});
	}

	@Test
	public void testKeyLongestPrefix() {
		assertEquals("foobar", tree.keyLongestPrefixOf("foobari"));
		assertEquals("foo", tree.keyLongestPrefixOf("foocake"));
		assertEquals("", tree.keyLongestPrefixOf("faabar"));
		assertEquals("johndoe", tree.keyLongestPrefixOf("johndoea"));
		assertEquals("", tree.keyLongestPrefixOf("xyz"));
		assertNull(tree.keyLongestPrefixOf(""));
		assertNull(tree.keyLongestPrefixOf(null));
	}

	@Test
	public void testGetKeys() {
		assertArrayEquals(
				new String[] { "bar", "foo", "foobar", "foobaz", "johndoe" },
				toArray(tree.getKeys()));
	}

	@Test
	public void testGetKeysWithPrefix() {
		assertArrayEquals(new String[] { "foo", "foobar", "foobaz" },
				toArray(tree.getKeysWithPrefix("foo")));
		assertArrayEquals(new String[] { "johndoe" },
				toArray(tree.getKeysWithPrefix("john")));
		assertArrayEquals(new String[0],
				toArray(tree.getKeysWithPrefix("cake")));
		assertArrayEquals(
				new String[] { "bar", "foo", "foobar", "foobaz", "johndoe" },
				toArray(tree.getKeysWithPrefix("")));
		assertArrayEquals(new String[0], toArray(tree.getKeysWithPrefix(null)));
	}

	@Test
	public void testGetKeysMatching() {
		assertArrayEquals(new String[] { "foobar" },
				toArray(tree.getKeysMatching("fo?bar")));
		assertArrayEquals(new String[] { "foobar", "foobaz" },
				toArray(tree.getKeysMatching("fooba?")));
		assertArrayEquals(new String[] { "foobar", "foobaz" },
				toArray(tree.getKeysMatching("?o?ba?")));
		assertArrayEquals(new String[0], toArray(tree.getKeysMatching("")));
		assertArrayEquals(new String[0], toArray(tree.getKeysMatching(null)));
	}

	private static String[] toArray(Iterable<String> iter) {
		List<String> keys = StreamSupport.stream(iter.spliterator(), false)
				.collect(Collectors.toList());
		return keys.toArray(new String[0]);
	}
}

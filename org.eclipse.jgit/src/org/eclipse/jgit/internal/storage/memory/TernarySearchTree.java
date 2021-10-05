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

import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.StringUtils;

/**
 * A ternary search tree with String keys and generic values.
 *
 * TernarySearchTree is a type of trie (sometimes called a prefix tree) where
 * nodes are arranged in a manner similar to a binary search tree, but with up
 * to three children rather than the binary tree's limit of two. Like other
 * prefix trees, a ternary search tree can be used as an associative map
 * structure with the ability for incremental string search. However, ternary
 * search trees are more space efficient compared to standard prefix trees, at
 * the cost of speed.
 *
 * Keys must not be null or empty. Values cannot be null.
 *
 * This class is thread safe.
 *
 * @param <Value>
 *            type of values in this tree
 * @since 6.0
 */
public final class TernarySearchTree<Value> {

	private static final char WILDCARD = '?';

	private static class Node<Value> {
		final char c;

		Node<Value> lo, eq, hi;

		Value val;

		Node(char c) {
			this.c = c;
		}

		boolean hasValue() {
			return val != null;
		}
	}

	/**
	 * Loader to load key-value pairs to be cached in the tree
	 *
	 * @param <Value>
	 *            type of values
	 */
	public static interface Loader<Value> {
		/**
		 * Load map of all key value pairs
		 *
		 * @return map of all key value pairs to cache in the tree
		 */
		Map<String, Value> loadAll();
	}

	private static void validateKey(String key) {
		if (StringUtils.isEmptyOrNull(key)) {
			throw new IllegalArgumentException(
					JGitText.get().illegalTernarySearchTreeKey);
		}
	}

	private static <V> void validateValue(V value) {
		if (value == null) {
			throw new IllegalArgumentException(
					JGitText.get().illegalTernarySearchTreeValue);
		}
	}

	private final ReadWriteLock lock;

	private final Lock readLock;

	private final Lock writeLock;

	private int size;

	private Node<Value> root;

	/**
	 * Construct a new ternary search tree
	 */
	public TernarySearchTree() {
		lock = new ReentrantReadWriteLock();
		readLock = lock.readLock();
		writeLock = lock.writeLock();
	}

	/**
	 * Reload this tree.
	 *
	 * @param loader
	 *            loader to load key-value pairs
	 */
	public void reload(Loader<Value> loader) {
		writeLock.lock();
		try {
			clear();
			insert(loader.loadAll());
		} finally {
			writeLock.unlock();
		}

	}

	/**
	 * Get the number of key value pairs in this trie
	 *
	 * @return number of key value pairs in this trie
	 */
	public int size() {
		readLock.lock();
		try {
			return size;
		} finally {
			readLock.unlock();
		}
	}

	/**
	 * Get the value associated to a key or {@code null}.
	 *
	 * @param key
	 *            the key
	 * @return the value associated to this key
	 */
	@Nullable
	public Value get(String key) {
		validateKey(key);
		readLock.lock();
		try {
			Node<Value> node = get(root, key, 0);
			if (node == null) {
				return null;
			}
			return node.val;
		} finally {
			readLock.unlock();
		}
	}

	/**
	 * Check whether this tree contains the given key.
	 *
	 * @param key
	 *            a key
	 * @return whether this tree contains this key
	 */
	public boolean contains(String key) {
		return get(key) != null;
	}

	/**
	 * Insert a key-value pair. If the key already exists the old value is
	 * overwritten.
	 *
	 * @param key
	 *            the key
	 * @param value
	 *            the value
	 */
	public void insert(String key, Value value) {
		writeLock.lock();
		try {
			insertImpl(key, value);
		} finally {
			writeLock.unlock();
		}
	}

	/**
	 * Insert map of key-value pairs. Values of existing keys are overwritten.
	 * Use this method to insert multiple key-value pairs.
	 *
	 * @param map
	 *            map of key-value pairs to insert
	 */
	public void insert(Map<String, Value> map) {
		writeLock.lock();
		try {
			for (Entry<String, Value> e : map.entrySet()) {
				insertImpl(e.getKey(), e.getValue());
			}
		} finally {
			writeLock.unlock();
		}
	}

	private void insertImpl(String key, Value value) {
		validateValue(value);
		if (!contains(key)) {
			size++;
		}
		root = insert(root, key, value, 0);
	}

	/**
	 * Delete a key-value pair. Does nothing if the key doesn't exist.
	 *
	 * @param key
	 *            the key
	 */
	public void delete(String key) {
		writeLock.lock();
		try {
			if (contains(key)) {
				size--;
				root = insert(root, key, null, 0);
			}
		} finally {
			writeLock.unlock();
		}
	}

	/**
	 * Remove all key value pairs from this tree
	 */
	public void clear() {
		writeLock.lock();
		try {
			root = null;
			size = 0;
		} finally {
			writeLock.unlock();
		}
	}

	/**
	 * Find the key which is the longest prefix of the given query string.
	 *
	 * @param query
	 * @return the key which is the longest prefix of the given query string or
	 *         {@code null} if none exists.
	 */
	@Nullable
	public String keyLongestPrefixOf(String query) {
		if (StringUtils.isEmptyOrNull(query)) {
			return null;
		}
		readLock.lock();
		try {
			int length = 0;
			Node<Value> node = root;
			int i = 0;
			while (node != null && i < query.length()) {
				char c = query.charAt(i);
				if (node.c > c) {
					node = node.lo;
				} else if (node.c < c) {
					node = node.hi;
				} else {
					i++;
					if (node.hasValue()) {
						length = i;
					}
					node = node.eq;
				}
			}
			return query.substring(0, length);
		} finally {
			readLock.unlock();
		}
	}

	/**
	 * Get all keys.
	 *
	 * @return all keys
	 */
	public Iterable<String> getKeys() {
		Queue<String> queue = new LinkedList<>();
		readLock.lock();
		try {
			findKeysWithPrefix(root, new StringBuilder(), queue);
			return queue;
		} finally {
			readLock.unlock();
		}
	}

	/**
	 * Get keys starting with given prefix
	 *
	 * @param prefix
	 *            key prefix
	 * @return keys starting with given prefix
	 */
	public Iterable<String> getKeysWithPrefix(String prefix) {
		Queue<String> keys = new LinkedList<>();
		if (StringUtils.isEmptyOrNull(prefix)) {
			return keys;
		}
		readLock.lock();
		try {
			validateKey(prefix);
			Node<Value> node = get(root, prefix, 0);
			if (node == null) {
				return keys;
			}
			if (node.hasValue()) {
				keys.add(prefix);
			}
			findKeysWithPrefix(node.eq, new StringBuilder(prefix), keys);
			return keys;
		} finally {
			readLock.unlock();
		}
	}

	/**
	 * Get keys matching given pattern using '?' as wildcard character.
	 *
	 * @param pattern
	 *            search pattern
	 * @return keys matching given pattern.
	 */
	public Iterable<String> getKeysMatching(String pattern) {
		Queue<String> keys = new LinkedList<>();
		readLock.lock();
		try {
			findKeysWithPrefix(root, new StringBuilder(), 0, pattern, keys);
			return keys;
		} finally {
			readLock.unlock();
		}
	}

	private Node<Value> get(Node<Value> node, String key, int depth) {
		if (node == null) {
			return null;
		}
		char c = key.charAt(depth);
		if (node.c > c) {
			return get(node.lo, key, depth);
		} else if (node.c < c) {
			return get(node.hi, key, depth);
		} else if (depth < key.length() - 1) {
			return get(node.eq, key, depth + 1);
		} else {
			return node;
		}
	}

	private Node<Value> insert(Node<Value> node, String key, Value val,
			int depth) {
		char c = key.charAt(depth);
		if (node == null) {
			node = new Node<>(c);
		}
		if (node.c > c) {
			node.lo = insert(node.lo, key, val, depth);
		} else if (node.c < c) {
			node.hi = insert(node.hi, key, val, depth);
		} else if (depth < key.length() - 1) {
			node.eq = insert(node.eq, key, val, depth + 1);
		} else {
			node.val = val;
		}
		return node;
	}

	private void findKeysWithPrefix(Node<Value> node, StringBuilder prefix,
			Queue<String> keys) {
		if (node == null) {
			return;
		}
		findKeysWithPrefix(node.lo, prefix, keys);
		if (node.hasValue()) {
			keys.add(prefix.toString() + node.c);
		}
		findKeysWithPrefix(node.eq, prefix.append(node.c), keys);
		prefix.deleteCharAt(prefix.length() - 1);
		findKeysWithPrefix(node.hi, prefix, keys);
	}

	private void findKeysWithPrefix(Node<Value> node, StringBuilder prefix,
			int i, String pattern, Queue<String> keys) {
		if (node == null || StringUtils.isEmptyOrNull(pattern)) {
			return;
		}
		char c = pattern.charAt(i);
		if (c == WILDCARD || node.c > c) {
			findKeysWithPrefix(node.lo, prefix, i, pattern, keys);
		}
		if (c == WILDCARD || node.c == c) {
			if (i == pattern.length() - 1 && node.hasValue()) {
				keys.add(prefix.toString() + node.c);
			}
			if (i < pattern.length() - 1) {
				findKeysWithPrefix(node.eq, prefix.append(node.c), i + 1,
						pattern, keys);
				prefix.deleteCharAt(prefix.length() - 1);
			}
		}
		if (c == WILDCARD || node.c < c) {
			findKeysWithPrefix(node.hi, prefix, i, pattern, keys);
		}
	}
}

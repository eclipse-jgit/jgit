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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
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
 * @since 6.5
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

	private final AtomicInteger size = new AtomicInteger(0);

	private Node<Value> root;

	/**
	 * Construct a new ternary search tree
	 */
	public TernarySearchTree() {
		lock = new ReentrantReadWriteLock();
	}

	/**
	 * Get the lock guarding read and write access to the cache.
	 *
	 * @return lock guarding read and write access to the cache
	 */
	public ReadWriteLock getLock() {
		return lock;
	}

	/**
	 * Replace the tree with a new tree containing all entries provided by an
	 * iterable
	 *
	 * @param loader
	 *            iterable providing key-value pairs to load
	 * @return number of key-value pairs after replacing finished
	 */
	public int replace(Iterable<Entry<String, Value>> loader) {
		lock.writeLock().lock();
		try {
			clear();
			for (Entry<String, Value> e : loader) {
				insertImpl(e.getKey(), e.getValue());
			}
		} finally {
			lock.writeLock().unlock();
		}
		return size.get();
	}

	/**
	 * Reload the tree entries provided by loader
	 *
	 * @param loader
	 *            iterable providing key-value pairs to load
	 * @return number of key-value pairs
	 */
	public int reload(Iterable<Entry<String, Value>> loader) {
		lock.writeLock().lock();
		try {
			for (Entry<String, Value> e : loader) {
				insertImpl(e.getKey(), e.getValue());
			}
		} finally {
			lock.writeLock().unlock();
		}
		return size.get();
	}

	/**
	 * Delete entries
	 *
	 * @param delete
	 *            iterable providing keys of entries to be deleted
	 * @return number of key-value pairs
	 */
	public int delete(Iterable<String> delete) {
		lock.writeLock().lock();
		try {
			for (String s : delete) {
				delete(s);
			}
		} finally {
			lock.writeLock().unlock();
		}
		return size.get();
	}

	/**
	 * Get the number of key value pairs in this trie
	 *
	 * @return number of key value pairs in this trie
	 */
	public int size() {
		return size.get();
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
		lock.readLock().lock();
		try {
			Node<Value> node = get(root, key, 0);
			if (node == null) {
				return null;
			}
			return node.val;
		} finally {
			lock.readLock().unlock();
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
	 * @return number of key-value pairs after adding the entry
	 */
	public int insert(String key, Value value) {
		lock.writeLock().lock();
		try {
			insertImpl(key, value);
			return size.get();
		} finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * Insert map of key-value pairs. Values of existing keys are overwritten.
	 * Use this method to insert multiple key-value pairs.
	 *
	 * @param map
	 *            map of key-value pairs to insert
	 * @return number of key-value pairs after adding entries
	 */
	public int insert(Map<String, Value> map) {
		lock.writeLock().lock();
		try {
			for (Entry<String, Value> e : map.entrySet()) {
				insertImpl(e.getKey(), e.getValue());
			}
			return size.get();
		} finally {
			lock.writeLock().unlock();
		}
	}

	private void insertImpl(String key, Value value) {
		validateValue(value);
		if (!contains(key)) {
			size.addAndGet(1);
		}
		root = insert(root, key, value, 0);
	}

	/**
	 * Delete a key-value pair. Does nothing if the key doesn't exist.
	 *
	 * @param key
	 *            the key
	 * @return number of key-value pairs after the deletion
	 */
	public int delete(String key) {
		lock.writeLock().lock();
		try {
			if (contains(key)) {
				size.addAndGet(-1);
				root = insert(root, key, null, 0);
			}
			return size.get();
		} finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * Remove all key value pairs from this tree
	 */
	public void clear() {
		lock.writeLock().lock();
		try {
			size.set(0);
			root = null;
		} finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * Find the key which is the longest prefix of the given query string.
	 *
	 * @param query
	 *            the query string
	 * @return the key which is the longest prefix of the given query string or
	 *         {@code null} if none exists.
	 */
	@Nullable
	public String keyLongestPrefixOf(String query) {
		if (StringUtils.isEmptyOrNull(query)) {
			return null;
		}
		lock.readLock().lock();
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
			lock.readLock().unlock();
		}
	}

	/**
	 * Get all keys.
	 *
	 * @return all keys
	 */
	public Iterable<String> getKeys() {
		Queue<String> queue = new ArrayDeque<>();
		lock.readLock().lock();
		try {
			findKeysWithPrefix(root, new StringBuilder(), queue);
			return queue;
		} finally {
			lock.readLock().unlock();
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
		Queue<String> keys = new ArrayDeque<>();
		if (prefix == null) {
			return keys;
		}
		if (prefix.isEmpty()) {
			return getKeys();
		}
		lock.readLock().lock();
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
			lock.readLock().unlock();
		}
	}

	/**
	 * Get all entries.
	 *
	 * @return all entries
	 */
	public Map<String, Value> getAll() {
		Map<String, Value> entries = new HashMap<>();
		lock.readLock().lock();
		try {
			findWithPrefix(root, new StringBuilder(), entries);
			return entries;
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Get all values.
	 *
	 * @return all values
	 */
	public List<Value> getAllValues() {
		List<Value> values = new ArrayList<>();
		lock.readLock().lock();
		try {
			findValuesWithPrefix(root, new StringBuilder(), values);
			return values;
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Get all entries with given prefix
	 *
	 * @param prefix
	 *            key prefix
	 * @return entries with given prefix
	 */
	public Map<String, Value> getWithPrefix(String prefix) {
		Map<String, Value> entries = new HashMap<>();
		if (prefix == null) {
			return entries;
		}
		if (prefix.isEmpty()) {
			return getAll();
		}
		lock.readLock().lock();
		try {
			validateKey(prefix);
			Node<Value> node = get(root, prefix, 0);
			if (node == null) {
				return entries;
			}
			if (node.hasValue()) {
				entries.put(prefix, node.val);
			}
			findWithPrefix(node.eq, new StringBuilder(prefix), entries);
			return entries;
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Get all values with given key prefix
	 *
	 * @param prefix
	 *            key prefix
	 * @return entries with given prefix
	 */
	public List<Value> getValuesWithPrefix(String prefix) {
		List<Value> values = new ArrayList<>();
		if (prefix == null) {
			return values;
		}
		if (prefix.isEmpty()) {
			return getAllValues();
		}
		lock.readLock().lock();
		try {
			validateKey(prefix);
			Node<Value> node = get(root, prefix, 0);
			if (node == null) {
				return values;
			}
			if (node.hasValue()) {
				values.add(node.val);
			}
			findValuesWithPrefix(node.eq, new StringBuilder(prefix), values);
			return values;
		} finally {
			lock.readLock().unlock();
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
		Queue<String> keys = new ArrayDeque<>();
		lock.readLock().lock();
		try {
			findKeysWithPrefix(root, new StringBuilder(), 0, pattern, keys);
			return keys;
		} finally {
			lock.readLock().unlock();
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

	private void findWithPrefix(Node<Value> node, StringBuilder prefix,
			Map<String, Value> entries) {
		if (node == null) {
			return;
		}
		findWithPrefix(node.lo, prefix, entries);
		if (node.hasValue()) {
			entries.put(prefix.toString() + node.c, node.val);
		}
		findWithPrefix(node.eq, prefix.append(node.c), entries);
		prefix.deleteCharAt(prefix.length() - 1);
		findWithPrefix(node.hi, prefix, entries);
	}

	private void findValuesWithPrefix(Node<Value> node, StringBuilder prefix,
			List<Value> values) {
		if (node == null) {
			return;
		}
		findValuesWithPrefix(node.lo, prefix, values);
		if (node.hasValue()) {
			values.add(node.val);
		}
		findValuesWithPrefix(node.eq, prefix.append(node.c), values);
		prefix.deleteCharAt(prefix.length() - 1);
		findValuesWithPrefix(node.hi, prefix, values);
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

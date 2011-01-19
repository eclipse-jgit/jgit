/*
 * Copyright (C) 2011, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.storage.dht.spi.memory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.storage.dht.spi.util.ColumnMatcher;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.SystemReader;

/**
 * Tiny in-memory NoSQL style table.
 * <p>
 * This table is thread-safe, but not very efficient. It uses a single lock to
 * protect its internal data structure from concurrent access, and stores all
 * data as byte arrays. To reduce memory usage, the arrays passed by the caller
 * during put or compareAndSet are used as-is in the internal data structure,
 * and may be returned later. Callers should not modify byte arrays once they
 * are stored in the table, or when obtained from the table.
 */
public class MemTable {
	private final Map<Key, Map<Key, Cell>> map;

	private final Object lock;

	/** Initialize an empty table. */
	public MemTable() {
		map = new HashMap<Key, Map<Key, Cell>>();
		lock = new Object();
	}

	/**
	 * Put a value into a cell.
	 *
	 * @param row
	 * @param col
	 * @param val
	 */
	public void put(byte[] row, byte[] col, byte[] val) {
		synchronized (lock) {
			Key rowKey = new Key(row);
			Map<Key, Cell> r = map.get(rowKey);
			if (r == null) {
				r = new HashMap<Key, Cell>(4);
				map.put(rowKey, r);
			}
			r.put(new Key(col), new Cell(row, col, val));
		}
	}

	/**
	 * Delete an entire row.
	 *
	 * @param row
	 */
	public void deleteRow(byte[] row) {
		synchronized (lock) {
			map.remove(new Key(row));
		}
	}

	/**
	 * Delete a cell.
	 *
	 * @param row
	 * @param col
	 */
	public void delete(byte[] row, byte[] col) {
		synchronized (lock) {
			Key rowKey = new Key(row);
			Map<Key, Cell> r = map.get(rowKey);
			if (r == null)
				return;

			r.remove(new Key(col));
			if (r.isEmpty())
				map.remove(rowKey);
		}
	}

	/**
	 * Compare and put or delete a cell.
	 * <p>
	 * This method performs an atomic compare-and-swap operation on the named
	 * cell. If the cell does not yet exist, it will be created. If the cell
	 * exists, it will be replaced, and if {@code newVal} is null, the cell will
	 * be deleted.
	 *
	 * @param row
	 * @param col
	 * @param oldVal
	 *            if null, the cell must not exist, otherwise the cell's current
	 *            value must exactly equal this value for the update to occur.
	 * @param newVal
	 *            if null, the cell will be removed, otherwise the cell will be
	 *            created or updated to this value.
	 * @return true if successful, false if {@code oldVal} does not match.
	 */
	public boolean compareAndSet(byte[] row, byte[] col, byte[] oldVal,
			byte[] newVal) {
		synchronized (lock) {
			Key rowKey = new Key(row);
			Key colKey = new Key(col);

			Map<Key, Cell> r = map.get(rowKey);
			if (r == null) {
				r = new HashMap<Key, Cell>(4);
				map.put(rowKey, r);
			}

			Cell oldCell = r.get(colKey);
			if (!same(oldCell, oldVal)) {
				if (r.isEmpty())
					map.remove(rowKey);
				return false;
			}

			if (newVal != null) {
				r.put(colKey, new Cell(row, col, newVal));
				return true;
			}

			r.remove(colKey);
			if (r.isEmpty())
				map.remove(rowKey);
			return true;
		}
	}

	private static boolean same(Cell oldCell, byte[] expVal) {
		if (oldCell == null)
			return expVal == null;

		if (expVal == null)
			return false;

		return Arrays.equals(oldCell.value, expVal);
	}

	/**
	 * Get a single cell, or null.
	 *
	 * @param row
	 * @param col
	 * @return the cell, or null.
	 */
	public Cell get(byte[] row, byte[] col) {
		synchronized (lock) {
			Map<Key, Cell> r = map.get(new Key(row));
			return r != null ? r.get(new Key(col)) : null;
		}
	}

	/**
	 * Scan all cells in a row.
	 *
	 * @param row
	 *            the row to scan.
	 * @param family
	 *            if not null, the family to filter and return.
	 * @return iterator for the cells. Cells may appear in any order, including
	 *         random. Never null.
	 */
	public Iterable<Cell> scanFamily(byte[] row, ColumnMatcher family) {
		synchronized (lock) {
			Map<Key, Cell> r = map.get(new Key(row));
			if (r == null)
				return Collections.emptyList();

			if (family == null)
				return new ArrayList<Cell>(r.values());

			ArrayList<Cell> out = new ArrayList<Cell>(4);
			for (Cell cell : r.values()) {
				if (family.sameFamily(cell.getName()))
					out.add(cell);
			}
			return out;
		}
	}

	private static class Key {
		final byte[] key;

		Key(byte[] key) {
			this.key = key;
		}

		@Override
		public int hashCode() {
			int hash = 5381;
			for (int ptr = 0; ptr < key.length; ptr++)
				hash = ((hash << 5) + hash) + (key[ptr] & 0xff);
			return hash;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other)
				return true;
			if (other instanceof Key)
				return Arrays.equals(key, ((Key) other).key);
			return false;
		}

		@Override
		public String toString() {
			return RawParseUtils.decode(key);
		}
	}

	/** A cell value in a column. */
	public static class Cell {
		final byte[] row;

		final byte[] name;

		final byte[] value;

		final long timestamp;

		Cell(byte[] row, byte[] name, byte[] value) {
			this.row = row;
			this.name = name;
			this.value = value;
			this.timestamp = SystemReader.getInstance().getCurrentTime();
		}

		/** @return key of the row holding the cell. */
		public byte[] getRow() {
			return row;
		}

		/** @return name of the cell's column. */
		public byte[] getName() {
			return name;
		}

		/** @return the cell's value. */
		public byte[] getValue() {
			return value;
		}

		/** @return system clock time of last modification. */
		public long getTimestamp() {
			return timestamp;
		}

		@Override
		public String toString() {
			return RawParseUtils.decode(name);
		}
	}
}

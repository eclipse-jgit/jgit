/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.internal.storage.reftable.ReftableReader;

/**
 * Tracks multiple open
 * {@link org.eclipse.jgit.internal.storage.reftable.ReftableReader} instances.
 */
public class DfsReftableStack implements AutoCloseable {
	/**
	 * Opens a stack of tables for reading.
	 *
	 * @param ctx
	 *            context to read the tables with. This {@code ctx} will be
	 *            retained by the stack and each of the table readers.
	 * @param files
	 *            the tables to open.
	 * @return stack reference to close the tables.
	 * @throws java.io.IOException
	 *             a table could not be opened
	 */
	public static DfsReftableStack open(DfsReader ctx, List<DfsReftable> files)
			throws IOException {
		DfsReftableStack stack = new DfsReftableStack(files.size());
		boolean close = true;
		try {
			for (DfsReftable t : files) {
				stack.files.add(t);
				stack.tables.add(t.open(ctx));
			}
			close = false;
			return stack;
		} finally {
			if (close) {
				stack.close();
			}
		}
	}

	private final List<DfsReftable> files;
	private final List<ReftableReader> tables;

	private DfsReftableStack(int tableCnt) {
		this.files = new ArrayList<>(tableCnt);
		this.tables = new ArrayList<>(tableCnt);
	}

	/**
	 * Get unmodifiable list of DfsRefatble files
	 *
	 * @return unmodifiable list of DfsRefatble files, in the same order the
	 *         files were passed to {@link #open(DfsReader, List)}.
	 */
	public List<DfsReftable> files() {
		return Collections.unmodifiableList(files);
	}

	/**
	 * Get unmodifiable list of tables
	 *
	 * @return unmodifiable list of tables, in the same order the files were
	 *         passed to {@link #open(DfsReader, List)}.
	 */
	public List<ReftableReader> readers() {
		return Collections.unmodifiableList(tables);
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		for (ReftableReader t : tables) {
			try {
				t.close();
			} catch (IOException e) {
				// Ignore close failures.
			}
		}
	}
}

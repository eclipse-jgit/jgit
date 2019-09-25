/*
 * Copyright (C) 2017, Google Inc.
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

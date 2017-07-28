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

import org.eclipse.jgit.internal.storage.reftable.Reftable;

/** Tracks multiple open {@link Reftable} instances. */
public class ReftableStack implements AutoCloseable {
	/**
	 * Opens a stack of tables for reading.
	 *
	 * @param ctx
	 *            context to read the tables with. This {@code ctx} will be
	 *            retained by the stack and each of the table readers.
	 * @param tables
	 *            the tables to open.
	 * @return stack reference to close the tables.
	 * @throws IOException
	 *             a table could not be opened
	 */
	public static ReftableStack open(DfsReader ctx, List<DfsReftable> tables)
			throws IOException {
		ReftableStack stack = new ReftableStack(tables.size());
		boolean close = true;
		try {
			for (DfsReftable t : tables) {
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

	private final List<Reftable> tables;

	private ReftableStack(int tableCnt) {
		this.tables = new ArrayList<>(tableCnt);
	}

	/**
	 * @return unmodifiable list of tables, in the same order the files were
	 *         passed to {@link #open(DfsReader, List)}.
	 */
	public List<Reftable> readers() {
		return Collections.unmodifiableList(tables);
	}

	@Override
	public void close() {
		for (Reftable t : tables) {
			try {
				t.close();
			} catch (IOException e) {
				// Ignore close failures.
			}
		}
	}
}

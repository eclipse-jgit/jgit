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

package org.eclipse.jgit.internal.storage.reftable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Merges reftables and compacts them into a single output.
 */
public class ReftableCompactor {
	private final ReftableWriter writer = new ReftableWriter();
	private final ArrayDeque<RefCursor> tables = new ArrayDeque<>();

	private long compactBytesLimit;
	private long bytesToCompact;
	private boolean includeDeletes;

	/**
	 * @param bytes
	 *            limit on number of bytes from source tables to compact.
	 * @return {@code this}
	 */
	public ReftableCompactor setCompactBytesLimit(long bytes) {
		compactBytesLimit = bytes;
		return this;
	}

	/**
	 * @param szBytes
	 *            desired output block size in bytes.
	 * @return {@code this}
	 */
	public ReftableCompactor setBlockSize(int szBytes) {
		writer.setBlockSize(szBytes);
		return this;
	}

	/**
	 * @param interval
	 *            number of references between binary search markers. If
	 *            {@code interval} is 0 (default), the writer will select a
	 *            default value based on the block size.
	 * @return {@code this}
	 */
	public ReftableCompactor setRestartInterval(int interval) {
		writer.setRestartInterval(interval);
		return this;
	}

	/**
	 * @param deletes
	 *            {@code true} to include deletions in the output, which may be
	 *            necessary for partial compactions.
	 * @return {@code this}
	 */
	public ReftableCompactor setIncludeDeletes(boolean deletes) {
		includeDeletes = deletes;
		return this;
	}

	/**
	 * Add all of the tables, in the specified order.
	 * <p>
	 * Unconditionally adds all tables, ignoring the
	 * {@link #setCompactBytesLimit(long)}.
	 *
	 * @param readers
	 *            tables to compact. Tables should be ordered oldest first/most
	 *            recent last so that the more recent tables can shadow the
	 *            older results. Caller is responsible for closing the readers.
	 */
	public void addAll(List<RefCursor> readers) {
		tables.addAll(readers);
	}

	/**
	 * Unconditionally add a table at the bottom of the stack.
	 *
	 * @param table
	 *            the table to read.
	 */
	public void addFirst(RefCursor table) {
		tables.addFirst(table);
	}

	/**
	 * Try to add this reader at the bottom of the stack.
	 * <p>
	 * A reader may be rejected by returning {@code false} if the compactor is
	 * already rewriting its {@link #setCompactBytesLimit(long)}. When this
	 * happens the caller should stop trying to add tables, and execute the
	 * compaction.
	 *
	 * @param reader
	 *            the reader to insert at the bottom of the stack. Caller is
	 *            responsible for closing the reader.
	 * @return {@code true} if the compactor accepted this table; {@code false}
	 *         if the compactor has reached its limit.
	 * @throws IOException
	 *             size of {@code reader} cannot be read.
	 */
	public boolean tryAddFirst(ReftableReader reader) throws IOException {
		long sz = reader.size();
		if (compactBytesLimit > 0 && bytesToCompact + sz > compactBytesLimit) {
			return false;
		}
		bytesToCompact += sz;
		tables.addFirst(reader);
		return true;
	}

	/**
	 * Write a compaction to {@code out}.
	 *
	 * @param out
	 *            stream to write the compacted tables to. Caller is responsible
	 *            for closing {@code out}.
	 * @throws IOException
	 *             tables cannot be read, or cannot be written.
	 */
	public void compact(OutputStream out) throws IOException {
		@SuppressWarnings("resource")
		MergedReftable mr = new MergedReftable(new ArrayList<>(tables));
		mr.setIncludeDeletes(includeDeletes);
		mr.seekToFirstRef();

		writer.begin(out);
		while (mr.next()) {
			writer.write(mr.getRef());
		}
		writer.finish();
	}
}

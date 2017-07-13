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

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ReflogEntry;

/**
 * Merges reftables and compacts them into a single output.
 * <p>
 * For a partial compaction callers should {@link #setIncludeDeletes(boolean)}
 * to {@code true} to ensure the new reftable continues to use a delete marker
 * to shadow any lower reftable that may have the reference present.
 * <p>
 * By default all log entries are copied, even if no references in the output
 * file match the log records. Callers may truncate the log to a more recent
 * time horizon with {@link #setOldestReflogTimeUsec(long)}, or disable the log
 * altogether with {@code setOldestReflogTimeUsec(Long.MAX_VALUE)}.
 */
public class ReftableCompactor {
	private final ReftableWriter writer = new ReftableWriter();
	private final ArrayDeque<RefCursor> tables = new ArrayDeque<>();
	private boolean includeDeletes;
	private long oldestReflogTimeUsec;

	/**
	 * @param szBytes
	 *            desired output block size for references, in bytes.
	 * @return {@code this}
	 */
	public ReftableCompactor setRefBlockSize(int szBytes) {
		writer.setRefBlockSize(szBytes);
		return this;
	}

	/**
	 * @param szBytes
	 *            desired output block size for log entries, in bytes.
	 * @return {@code this}
	 */
	public ReftableCompactor setLogBlockSize(int szBytes) {
		writer.setLogBlockSize(szBytes);
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
	 *            necessary for partial compaction.
	 * @return {@code this}
	 */
	public ReftableCompactor setIncludeDeletes(boolean deletes) {
		includeDeletes = deletes;
		return this;
	}

	/**
	 * @param timeUsec
	 *            oldest log time to preserve. Entries whose timestamps are
	 *            {@code >= timeUsec} will be copied into the output file. Log
	 *            entries that predate {@code timeUsec} will be discarded.
	 *            Specified in microseconds since the epoch.
	 * @return {@code this}
	 */
	public ReftableCompactor setOldestReflogTimeUsec(long timeUsec) {
		oldestReflogTimeUsec = timeUsec;
		return this;
	}

	/**
	 * Add all of the tables, in the specified order.
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
	 * Write a compaction to {@code out}.
	 *
	 * @param out
	 *            stream to write the compacted tables to. Caller is responsible
	 *            for closing {@code out}.
	 * @throws IOException
	 *             tables cannot be read, or cannot be written.
	 */
	public void compact(OutputStream out) throws IOException {
		MergedReftable mr = new MergedReftable(new ArrayList<>(tables));
		mr.setIncludeDeletes(includeDeletes);

		writer.begin(out);
		mergeRefs(mr);
		mergeLogs(mr);
		writer.finish();
	}

	private void mergeRefs(MergedReftable mr) throws IOException {
		mr.seekToFirstRef();
		while (mr.next()) {
			writer.writeRef(mr.getRef());
		}
	}

	private void mergeLogs(MergedReftable mr) throws IOException {
		mr.seekToFirstLog();
		while (mr.next()) {
			long timeUsec = mr.getReflogTimeUsec();
			ReflogEntry log = mr.getReflogEntry();
			PersonIdent who = log.getWho();
			if (timeUsec >= oldestReflogTimeUsec) {
				writer.writeLog(
						mr.getRefName(),
						timeUsec,
						who,
						log.getOldId(),
						log.getNewId(),
						log.getComment());
			}
		}
	}
}

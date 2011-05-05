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

package org.eclipse.jgit.storage.hbase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Row;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.DhtTimeoutException;
import org.eclipse.jgit.storage.dht.spi.util.AbstractWriteBuffer;

/** Buffers write operations to HBase to create larger batches. */
final class HBaseBuffer extends AbstractWriteBuffer {
	private Map<HTableInterface, TableBuffer> queued;

	HBaseBuffer(HBaseDatabase db, int bufferSize) {
		super(db.getExecutorService(), bufferSize);
	}

	void write(HTableInterface table, Put row) throws DhtException {
		int sz = (int) row.heapSize();
		if (add(sz)) {
			queue(table, row, sz);
		} else {
			TableBuffer b = new TableBuffer(table);
			b.add(row, sz);
			b.startInBackground();
		}
	}

	void write(HTableInterface table, Delete row) throws DhtException {
		int sz = (int) row.getRow().length;
		add(sz);
		queue(table, row, sz);
	}

	private void queue(HTableInterface table, Row row, int sz)
			throws DhtException {
		if (queued == null)
			queued = new HashMap<HTableInterface, TableBuffer>();
		TableBuffer b = queued.get(table);
		if (b == null) {
			b = new TableBuffer(table);
			queued.put(table, b);
		}
		b.add(row, sz);
		queued(sz);
	}

	@Override
	protected void startQueuedOperations(int bufferedByteCount)
			throws DhtException {
		for (TableBuffer b : queued.values())
			b.startInBackground();
		queued = null;
	}

	@Override
	public void abort() throws DhtException {
		queued = null;
		super.abort();
	}

	private class TableBuffer {
		final HTableInterface table;

		final List<Row> actions;

		int size;

		TableBuffer(HTableInterface table) {
			this.table = table;
			this.actions = new ArrayList<Row>();
		}

		void add(Row row, int sz) {
			actions.add(row);
			size += sz;
		}

		void startInBackground() throws DhtException {
			start(new Callable<Void>() {
				public Void call() throws Exception {
					try {
						table.batch(actions);
						return null;
					} catch (IOException err) {
						throw new DhtException(err);
					} catch (InterruptedException err) {
						throw new DhtTimeoutException(err);
					}
				}
			}, size);
		}
	}
}

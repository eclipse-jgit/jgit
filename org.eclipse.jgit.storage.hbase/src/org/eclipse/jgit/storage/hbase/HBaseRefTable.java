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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.RefData;
import org.eclipse.jgit.storage.dht.RefKey;
import org.eclipse.jgit.storage.dht.RepositoryKey;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.RefTable;

final class HBaseRefTable implements RefTable {
	private static final byte[] QUAL = {};

	private final HTableInterface table;

	private byte[] colTarget;

	HBaseRefTable(HBaseDatabase db) throws DhtException {
		this.table = db.openTable("REF");
		this.colTarget = Bytes.toBytes("target");
	}

	public Map<RefKey, RefData> getAll(Context options, RepositoryKey repository)
			throws DhtException {
		Scan scan = new Scan();
		scan.setStartRow(RefKey.create(repository, "").asBytes());
		scan.setStopRow(RefKey.create(repository, "~").asBytes());
		scan.addColumn(colTarget, QUAL);

		Map<RefKey, RefData> refs = new HashMap<RefKey, RefData>();
		try {
			ResultScanner scanner = table.getScanner(scan);
			try {
				Result row;
				while ((row = scanner.next()) != null) {
					RefKey key = RefKey.fromBytes(row.getRow());
					RefData data = RefData.fromBytes(row.value());
					refs.put(key, data);
				}
			} finally {
				scanner.close();
			}
		} catch (IOException err) {
			throw new DhtException(err);
		}
		return refs;
	}

	public boolean compareAndPut(RefKey refKey, RefData oldData, RefData newData)
			throws DhtException, TimeoutException {
		byte[] row = refKey.asBytes();
		byte[] old = oldData != RefData.NONE ? oldData.asBytes() : null;
		Put put = new Put(row);
		put.add(colTarget, QUAL, newData.asBytes());
		try {
			return table.checkAndPut(row, colTarget, QUAL, old, put);
		} catch (IOException err) {
			throw new DhtException(err);
		}
	}

	public boolean compareAndRemove(RefKey refKey, RefData oldData)
			throws DhtException, TimeoutException {
		byte[] row = refKey.asBytes();
		byte[] old = oldData.asBytes();
		Delete del = new Delete(row);
		try {
			return table.checkAndDelete(row, colTarget, QUAL, old, del);
		} catch (IOException err) {
			throw new DhtException(err);
		}
	}
}

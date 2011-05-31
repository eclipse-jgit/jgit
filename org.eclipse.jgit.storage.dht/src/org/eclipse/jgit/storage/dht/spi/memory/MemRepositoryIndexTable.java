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

import java.text.MessageFormat;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.DhtText;
import org.eclipse.jgit.storage.dht.RepositoryKey;
import org.eclipse.jgit.storage.dht.RepositoryName;
import org.eclipse.jgit.storage.dht.spi.RepositoryIndexTable;
import org.eclipse.jgit.storage.dht.spi.memory.MemTable.Cell;
import org.eclipse.jgit.storage.dht.spi.util.ColumnMatcher;

final class MemRepositoryIndexTable implements RepositoryIndexTable {
	private final MemTable table = new MemTable();

	private final ColumnMatcher colId = new ColumnMatcher("id");

	public RepositoryKey get(RepositoryName name) throws DhtException,
			TimeoutException {
		Cell cell = table.get(name.asBytes(), colId.name());
		if (cell == null)
			return null;
		return RepositoryKey.fromBytes(cell.getValue());
	}

	public void putUnique(RepositoryName name, RepositoryKey key)
			throws DhtException, TimeoutException {
		boolean ok = table.compareAndSet( //
				name.asBytes(), //
				colId.name(), //
				null, //
				key.asBytes());
		if (!ok)
			throw new DhtException(MessageFormat.format(
					DhtText.get().repositoryAlreadyExists, name.asString()));
	}

	public void remove(RepositoryName name, RepositoryKey key)
			throws DhtException, TimeoutException {
		boolean ok = table.compareAndSet(
				name.asBytes(),
				colId.name(),
				key.asBytes(),
				null);
		if (!ok)
			throw new DhtException(MessageFormat.format(
					DhtText.get().repositoryAlreadyExists, name.asString()));
	}
}

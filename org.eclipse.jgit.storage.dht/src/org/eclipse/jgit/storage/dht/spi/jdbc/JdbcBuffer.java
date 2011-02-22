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

package org.eclipse.jgit.storage.dht.spi.jdbc;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.spi.util.AbstractWriteBuffer;

final class JdbcBuffer extends AbstractWriteBuffer {
	private final ClientPool pool;

	private LinkedHashMap<Task<?>, List<?>> pending;

	JdbcBuffer(ExecutorService executor, int bufferSize, ClientPool pool) {
		super(executor, bufferSize);
		this.pool = pool;
	}

	@SuppressWarnings("unchecked")
	<T> void submit(Task<T> task, T data) throws DhtException {
		int sz = task.size(data);
		if (add(sz)) {
			if (pending == null)
				pending = new LinkedHashMap<Task<?>, List<?>>();

			List<T> list = (List<T>) pending.get(task);
			if (list == null) {
				list = new ArrayList<T>();
				pending.put(task, list);
			}
			list.add(data);
			queued(sz);
		} else {
			List<T> one = Collections.singletonList(data);
			Map map = Collections.singletonMap(task, one);
			start(new TaskRunner(map), sz);
		}
	}

	@Override
	protected void startQueuedOperations(int bufferedByteCount)
			throws DhtException {
		final LinkedHashMap<Task<?>, List<?>> taskList = pending;
		pending = null;
		start(new TaskRunner(taskList), bufferedByteCount);
	}

	@Override
	public void abort() throws DhtException {
		pending = null;
		super.abort();
	}

	static abstract class Task<T> {
		abstract int size(T data);

		abstract void run(Client conn, List<T> data) throws SQLException;
	}

	private final class TaskRunner implements Callable<Void> {
		private final Map<Task<?>, List<?>> taskList;

		TaskRunner(Map<Task<?>, List<?>> taskList) {
			this.taskList = taskList;
		}

		@SuppressWarnings("unchecked")
		public Void call() throws Exception {
			Client conn = pool.get();
			try {
				for (Map.Entry<Task<?>, List<?>> ent : taskList.entrySet()) {
					Task task = ent.getKey();
					List data = ent.getValue();
					run(conn, task, data);
				}
			} finally {
				pool.release(conn);
			}
			return null;
		}

		private <T> void run(Client conn, Task<T> task, List<T> data)
				throws SQLException {
			task.run(conn, data);
		}
	}
}

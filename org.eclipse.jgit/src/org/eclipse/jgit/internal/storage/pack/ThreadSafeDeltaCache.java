/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.pack;

import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.storage.pack.PackConfig;

class ThreadSafeDeltaCache extends DeltaCache {
	private final ReentrantLock lock;

	ThreadSafeDeltaCache(PackConfig pc) {
		super(pc);
		lock = new ReentrantLock();
	}

	@Override
	boolean canCache(int length, ObjectToPack src, ObjectToPack res) {
		lock.lock();
		try {
			return super.canCache(length, src, res);
		} finally {
			lock.unlock();
		}
	}

	@Override
	void credit(int reservedSize) {
		lock.lock();
		try {
			super.credit(reservedSize);
		} finally {
			lock.unlock();
		}
	}

	@Override
	Ref cache(byte[] data, int actLen, int reservedSize) {
		data = resize(data, actLen);
		lock.lock();
		try {
			return super.cache(data, actLen, reservedSize);
		} finally {
			lock.unlock();
		}
	}
}

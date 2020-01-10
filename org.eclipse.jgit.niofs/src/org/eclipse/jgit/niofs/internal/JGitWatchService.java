/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class JGitWatchService implements WatchService {

	private boolean wsClose = false;

	private final Queue<WatchKey> events = new ConcurrentLinkedQueue<>();
	private final String fsName;
	private Consumer<JGitWatchService> notifyClose;

	public JGitWatchService(String fsName, Consumer<JGitWatchService> notifyClose) {

		this.fsName = fsName;
		this.notifyClose = notifyClose;
	}

	@Override
	public WatchKey poll() throws ClosedWatchServiceException {
		return events.poll();
	}

	@Override
	public WatchKey poll(long timeout, TimeUnit unit) throws ClosedWatchServiceException, InterruptedException {
		return events.poll();
	}

	@Override
	public synchronized WatchKey take() throws ClosedWatchServiceException, InterruptedException {
		while (true) {
			if (wsClose) {
				throw new ClosedWatchServiceException();
			} else if (events.size() > 0) {
				return events.poll();
			} else {
				try {
					this.wait();
				} catch (final java.lang.InterruptedException e) {
				}
			}
		}
	}

	public boolean isClose() {
		return wsClose;
	}

	@Override
	public synchronized void close() throws IOException {
		wsClose = true;
		notifyAll();
		notifyClose.accept(this);
	}

	synchronized void closeWithoutNotifyParent() {
		wsClose = true;
		notifyAll();
	}

	@Override
	public String toString() {
		return "WatchService{" + "FileSystem=" + fsName + '}';
	}

	public void publish(WatchKey wk) {
		events.add(wk);
	}
}
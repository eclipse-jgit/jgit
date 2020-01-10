/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.daemon.git;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.niofs.internal.ExecutorServiceProducer;
import org.junit.Test;

public class DaemonTest {

	private final ExecutorService executorService = new ExecutorServiceProducer().produceUnmanagedExecutorService();

	@Test
	public void testShutdownByStop() throws Exception {
		ExecutorService executor = Executors.newCachedThreadPool();
		Daemon d = new Daemon(null, executor, executorService);
		d.start();
		assertTrue(d.isRunning());

		d.stop();

		assertFalse(d.isRunning());
	}

	@Test
	public void testShutdownByThreadPoolTermination() throws Exception {
		ExecutorService executor = Executors.newCachedThreadPool();
		Daemon d = new Daemon(null, executor, executorService);
		d.start();
		assertTrue(d.isRunning());

		executor.shutdownNow();
		executor.awaitTermination(10, TimeUnit.SECONDS);

		assertFalse(d.isRunning());
	}
}
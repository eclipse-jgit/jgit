/*
 * Copyright (C) 2017 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;

/**
 * Daemon tests.
 */
public class DaemonTest {

	@Test
	void testDaemonStop() throws Exception {
		Daemon d = new Daemon();
		d.start();
		InetSocketAddress address = d.getAddress();
		assertTrue(address.getPort() > 0, "Port should be allocated");
		assertTrue(d.isRunning(), "Daemon should be running");
		Thread.sleep(1000); // Give it time to enter accept()
		d.stopAndWait();
		// Try to start a new Daemon again on the same port
		d = new Daemon(address);
		d.start();
		InetSocketAddress newAddress = d.getAddress();
		assertEquals(address,
				newAddress,
				"New daemon should run on the same port");
		assertTrue(d.isRunning(), "Daemon should be running");
		Thread.sleep(1000);
		d.stopAndWait();
	}

	@Test
	void testDaemonRestart() throws Exception {
		Daemon d = new Daemon();
		d.start();
		InetSocketAddress address = d.getAddress();
		assertTrue(address.getPort() > 0, "Port should be allocated");
		assertTrue(d.isRunning(), "Daemon should be running");
		Thread.sleep(1000);
		d.stopAndWait();
		// Re-start the same daemon
		d.start();
		InetSocketAddress newAddress = d.getAddress();
		assertEquals(address,
				newAddress,
				"Daemon should again run on the same port");
		assertTrue(d.isRunning(), "Daemon should be running");
		Thread.sleep(1000);
		d.stopAndWait();
	}
}

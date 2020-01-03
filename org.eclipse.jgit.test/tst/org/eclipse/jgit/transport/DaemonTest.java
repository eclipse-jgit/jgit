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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;

import org.junit.Test;

/**
 * Daemon tests.
 */
public class DaemonTest {

	@Test
	public void testDaemonStop() throws Exception {
		Daemon d = new Daemon();
		d.start();
		InetSocketAddress address = d.getAddress();
		assertTrue("Port should be allocated", address.getPort() > 0);
		assertTrue("Daemon should be running", d.isRunning());
		Thread.sleep(1000); // Give it time to enter accept()
		d.stopAndWait();
		// Try to start a new Daemon again on the same port
		d = new Daemon(address);
		d.start();
		InetSocketAddress newAddress = d.getAddress();
		assertEquals("New daemon should run on the same port", address,
				newAddress);
		assertTrue("Daemon should be running", d.isRunning());
		Thread.sleep(1000);
		d.stopAndWait();
	}

	@Test
	public void testDaemonRestart() throws Exception {
		Daemon d = new Daemon();
		d.start();
		InetSocketAddress address = d.getAddress();
		assertTrue("Port should be allocated", address.getPort() > 0);
		assertTrue("Daemon should be running", d.isRunning());
		Thread.sleep(1000);
		d.stopAndWait();
		// Re-start the same daemon
		d.start();
		InetSocketAddress newAddress = d.getAddress();
		assertEquals("Daemon should again run on the same port", address,
				newAddress);
		assertTrue("Daemon should be running", d.isRunning());
		Thread.sleep(1000);
		d.stopAndWait();
	}
}

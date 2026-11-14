/*
 * Copyright (C) 2026, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd.proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.junit.Test;

/**
 * Basic tests for accumulating the SOCKS connection reply.
 */
public class Socks5ProxyConnectorTest {

	@Test
	public void splitConnectionReply() throws Exception {
		byte[] chunk1 = new byte[] { 5 }; // Socks version
		byte[] chunk2 = new byte[] { 0 /* Success */, 0 };
		byte[] chunk3 = new byte[] { 1 /* IPv4 */, 1, 2, 3 };
		byte[] chunk4 = new byte[] { 4, 0, 100, (byte) 'S', (byte) 'S',
				(byte) 'H', (byte) '-', (byte) '2', (byte) '.', (byte) '0',
				(byte) '-', (byte) 'X', (byte) '\n' };
		InetSocketAddress unused = new InetSocketAddress(
				InetAddress.getLocalHost(), 2000);
		AtomicReference<Buffer> rest = new AtomicReference<>();
		AtomicBoolean isDone = new AtomicBoolean();
		Socks5ClientConnector connector = new Socks5ClientConnector(unused,
				unused) {

			@Override
			protected void setDone(boolean success, Buffer lastData)
					throws Exception {
				isDone.set(success);
				rest.set(lastData);
			}
		};
		connector.receiveConnectReply(new ByteArrayBuffer(chunk1));
		assertFalse(isDone.get());
		assertNull(rest.get());
		connector.receiveConnectReply(new ByteArrayBuffer(chunk2));
		assertFalse(isDone.get());
		assertNull(rest.get());
		connector.receiveConnectReply(new ByteArrayBuffer(chunk3));
		assertFalse(isDone.get());
		assertNull(rest.get());
		connector.receiveConnectReply(new ByteArrayBuffer(chunk4));
		assertTrue(isDone.get());
		Buffer data = rest.get();
		assertNotNull(data);
		String ident = new String(data.array(), data.rpos(), data.available(),
				StandardCharsets.US_ASCII);
		assertEquals("SSH-2.0-X\n", ident);
	}

}

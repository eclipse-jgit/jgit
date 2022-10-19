/*
 * Copyright (C) 2022 Thomas Wolf <thomas.wolf@paranor.ch> and others
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SideBandInputStreamTest {

	private StringWriter messages;

	private SideBandInputStream sideband;

	@BeforeEach
	public void setup() {
		messages = new StringWriter();
	}

	@Test
	void progressSingleCR() throws IOException {
		init(packet("message\r"));
		assertTrue(sideband.read() < 0);
		assertEquals("message\r", messages.toString());
	}

	@Test
	void progressSingleLF() throws IOException {
		init(packet("message\n"));
		assertTrue(sideband.read() < 0);
		assertEquals("message\n", messages.toString());
	}

	@Test
	void progressSingleCRLF() throws IOException {
		init(packet("message\r\n"));
		assertTrue(sideband.read() < 0);
		assertEquals("message\r\n", messages.toString());
	}

	@Test
	void progressMultiCR() throws IOException {
		init(packet("message   0%\rmessage 100%\r"));
		assertTrue(sideband.read() < 0);
		assertEquals("message   0%\rmessage 100%\r", messages.toString());
	}

	@Test
	void progressMultiLF() throws IOException {
		init(packet("message   0%\nmessage 100%\n"));
		assertTrue(sideband.read() < 0);
		assertEquals("message   0%\nmessage 100%\n", messages.toString());
	}

	@Test
	void progressMultiCRLF() throws IOException {
		init(packet("message   0%\r\nmessage 100%\r\n"));
		assertTrue(sideband.read() < 0);
		assertEquals("message   0%\r\nmessage 100%\r\n", messages.toString());
	}

	@Test
	void progressPartial() throws IOException {
		init(packet("message"));
		assertTrue(sideband.read() < 0);
		assertEquals("", messages.toString());
		sideband.drainMessages();
		assertEquals("message\n", messages.toString());
	}

	@Test
	void progressPartialTwoCR() throws IOException {
		init(packet("message") + packet("message\r"));
		assertTrue(sideband.read() < 0);
		assertEquals("messagemessage\r", messages.toString());
	}

	@Test
	void progressPartialTwoLF() throws IOException {
		init(packet("message") + packet("message\n"));
		assertTrue(sideband.read() < 0);
		assertEquals("messagemessage\n", messages.toString());
	}

	@Test
	void progressPartialTwoCRLF() throws IOException {
		init(packet("message") + packet("message\r\n"));
		assertTrue(sideband.read() < 0);
		assertEquals("messagemessage\r\n", messages.toString());
	}

	@Test
	void progressPartialThreeCR() throws IOException {
		init(packet("message") + packet("message") + packet("message\r"));
		assertTrue(sideband.read() < 0);
		assertEquals("messagemessagemessage\r", messages.toString());
	}

	@Test
	void progressPartialThreeLF() throws IOException {
		init(packet("message") + packet("message") + packet("message\n"));
		assertTrue(sideband.read() < 0);
		assertEquals("messagemessagemessage\n", messages.toString());
	}

	@Test
	void progressPartialThreeCRLF() throws IOException {
		init(packet("message") + packet("message") + packet("message\r\n"));
		assertTrue(sideband.read() < 0);
		assertEquals("messagemessagemessage\r\n", messages.toString());
	}

	@Test
	void progressPartialCR() throws IOException {
		init(packet("message   0%\rmessage 100%"));
		assertTrue(sideband.read() < 0);
		assertEquals("message   0%\r", messages.toString());
		sideband.drainMessages();
		assertEquals("message   0%\rmessage 100%\n", messages.toString());
	}

	@Test
	void progressPartialLF() throws IOException {
		init(packet("message   0%\nmessage 100%"));
		assertTrue(sideband.read() < 0);
		assertEquals("message   0%\n", messages.toString());
		sideband.drainMessages();
		assertEquals("message   0%\nmessage 100%\n", messages.toString());
	}

	@Test
	void progressPartialCRLF() throws IOException {
		init(packet("message   0%\r\nmessage 100%"));
		assertTrue(sideband.read() < 0);
		assertEquals("message   0%\r\n", messages.toString());
		sideband.drainMessages();
		assertEquals("message   0%\r\nmessage 100%\n", messages.toString());
	}

	@Test
	void progressPartialSplitCR() throws IOException {
		init(packet("message") + "0006\001a" + packet("   0%\rmessa")
				+ packet("ge 100%"));
		assertEquals('a', sideband.read());
		assertEquals("", messages.toString());
		assertTrue(sideband.read() < 0);
		assertEquals("message   0%\r", messages.toString());
		sideband.drainMessages();
		assertEquals("message   0%\rmessage 100%\n", messages.toString());
	}

	@Test
	void progressPartialSplitLF() throws IOException {
		init(packet("message") + "0006\001a" + packet("   0%\nmessa")
				+ packet("ge 100%"));
		assertEquals('a', sideband.read());
		assertEquals("", messages.toString());
		assertTrue(sideband.read() < 0);
		assertEquals("message   0%\n", messages.toString());
		sideband.drainMessages();
		assertEquals("message   0%\nmessage 100%\n", messages.toString());
	}

	@Test
	void progressPartialSplitCRLF() throws IOException {
		init(packet("message") + "0006\001a" + packet("   0%\r\nmessa")
				+ packet("ge 100%"));
		assertEquals('a', sideband.read());
		assertEquals("", messages.toString());
		assertTrue(sideband.read() < 0);
		assertEquals("message   0%\r\n", messages.toString());
		sideband.drainMessages();
		assertEquals("message   0%\r\nmessage 100%\n", messages.toString());
	}

	@Test
	void progressInterleaved() throws IOException {
		init(packet("message   0%\r") + "0006\001a" + packet("message  10%")
				+ "0006\001b" + packet("\rmessage 100%\n"));
		assertEquals('a', sideband.read());
		assertEquals("message   0%\r", messages.toString());
		assertEquals('b', sideband.read());
		assertEquals("message   0%\r", messages.toString());
		assertTrue(sideband.read() < 0);
		assertEquals("message   0%\rmessage  10%\rmessage 100%\n",
				messages.toString());
	}

	@Test
	void progressInterleavedPartial() throws IOException {
		init(packet("message   0%\r") + "0006\001a" + packet("message  10%")
				+ "0006\001b" + packet("\rmessage 100%"));
		assertEquals('a', sideband.read());
		assertEquals("message   0%\r", messages.toString());
		assertEquals('b', sideband.read());
		assertEquals("message   0%\r", messages.toString());
		assertTrue(sideband.read() < 0);
		assertEquals("message   0%\rmessage  10%\r", messages.toString());
		sideband.drainMessages();
		assertEquals("message   0%\rmessage  10%\rmessage 100%\n",
				messages.toString());
	}

	private String packet(String data) {
		return String.format("%04x\002%s", Integer.valueOf(data.length() + 5),
				data);
	}

	private void init(String packets) {
		InputStream rawIn = new ByteArrayInputStream(
				(packets + "0000").getBytes(StandardCharsets.UTF_8));
		sideband = new SideBandInputStream(rawIn, null, messages, null);
	}
}

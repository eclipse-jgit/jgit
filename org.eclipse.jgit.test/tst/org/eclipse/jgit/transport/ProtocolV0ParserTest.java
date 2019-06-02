/*
 * Copyright (C) 2018, Google LLC.
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
package org.eclipse.jgit.transport;

import static org.eclipse.jgit.transport.ObjectIdMatcher.hasOnlyObjectIds;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class ProtocolV0ParserTest {
	/*
	 * Convert the input lines to the PacketLine that the parser reads.
	 */
	private static PacketLineIn formatAsPacketLine(String... inputLines)
			throws IOException {
		ByteArrayOutputStream send = new ByteArrayOutputStream();
		PacketLineOut pckOut = new PacketLineOut(send);
		for (String line : inputLines) {
			if (PacketLineIn.isEnd(line)) {
				pckOut.end();
			} else if (PacketLineIn.isDelimiter(line)) {
				pckOut.writeDelim();
			} else {
				pckOut.writeString(line);
			}
		}

		return new PacketLineIn(new ByteArrayInputStream(send.toByteArray()));
	}

	private static TransferConfig defaultConfig() {
		Config rc = new Config();
		rc.setBoolean("uploadpack", null, "allowfilter", true);
		return new TransferConfig(rc);
	}

	@Test
	public void testRecvWantsWithCapabilities()
			throws PackProtocolException, IOException {
		PacketLineIn pckIn = formatAsPacketLine(
				String.join(" ", "want",
						"4624442d68ee402a94364191085b77137618633e", "thin-pack",
						"no-progress", "include-tag", "ofs-delta", "\n"),
				"want f900c8326a43303685c46b279b9f70411bff1a4b\n",
				PacketLineIn.end());
		ProtocolV0Parser parser = new ProtocolV0Parser(defaultConfig());
		FetchV0Request request = parser.recvWants(pckIn);
		assertTrue(request.getClientCapabilities()
				.contains(GitProtocolConstants.OPTION_THIN_PACK));
		assertTrue(request.getClientCapabilities()
				.contains(GitProtocolConstants.OPTION_NO_PROGRESS));
		assertTrue(request.getClientCapabilities()
				.contains(GitProtocolConstants.OPTION_INCLUDE_TAG));
		assertTrue(request.getClientCapabilities()
				.contains(GitProtocolConstants.CAPABILITY_OFS_DELTA));
		assertThat(request.getWantIds(),
				hasOnlyObjectIds("4624442d68ee402a94364191085b77137618633e",
						"f900c8326a43303685c46b279b9f70411bff1a4b"));
	}

	@Test
	public void testRecvWantsWithAgent()
			throws PackProtocolException, IOException {
		PacketLineIn pckIn = formatAsPacketLine(
				String.join(" ", "want",
						"4624442d68ee402a94364191085b77137618633e", "thin-pack",
						"agent=JGit.test/0.0.1", "\n"),
				"want f900c8326a43303685c46b279b9f70411bff1a4b\n",
				PacketLineIn.end());
		ProtocolV0Parser parser = new ProtocolV0Parser(defaultConfig());
		FetchV0Request request = parser.recvWants(pckIn);
		assertTrue(request.getClientCapabilities()
				.contains(GitProtocolConstants.OPTION_THIN_PACK));
		assertEquals(1, request.getClientCapabilities().size());
		assertEquals("JGit.test/0.0.1", request.getAgent());
		assertThat(request.getWantIds(),
				hasOnlyObjectIds("4624442d68ee402a94364191085b77137618633e",
						"f900c8326a43303685c46b279b9f70411bff1a4b"));
	}

	/*
	 * First round of protocol v0 negotiation. Client send wants, no
	 * capabilities.
	 */
	@Test
	public void testRecvWantsWithoutCapabilities()
			throws PackProtocolException, IOException {
		PacketLineIn pckIn = formatAsPacketLine(
				"want 4624442d68ee402a94364191085b77137618633e\n",
				"want f900c8326a43303685c46b279b9f70411bff1a4b\n",
				PacketLineIn.end());
		ProtocolV0Parser parser = new ProtocolV0Parser(defaultConfig());
		FetchV0Request request = parser.recvWants(pckIn);
		assertTrue(request.getClientCapabilities().isEmpty());
		assertThat(request.getWantIds(),
				hasOnlyObjectIds("4624442d68ee402a94364191085b77137618633e",
						"f900c8326a43303685c46b279b9f70411bff1a4b"));
	}

	@Test
	public void testRecvWantsDeepen()
			throws PackProtocolException, IOException {
		PacketLineIn pckIn = formatAsPacketLine(
				"want 4624442d68ee402a94364191085b77137618633e\n",
				"want f900c8326a43303685c46b279b9f70411bff1a4b\n", "deepen 3\n",
				PacketLineIn.end());
		ProtocolV0Parser parser = new ProtocolV0Parser(defaultConfig());
		FetchV0Request request = parser.recvWants(pckIn);
		assertTrue(request.getClientCapabilities().isEmpty());
		assertEquals(3, request.getDepth());
		assertThat(request.getWantIds(),
				hasOnlyObjectIds("4624442d68ee402a94364191085b77137618633e",
						"f900c8326a43303685c46b279b9f70411bff1a4b"));
	}

	@Test
	public void testRecvWantsShallow()
			throws PackProtocolException, IOException {
		PacketLineIn pckIn = formatAsPacketLine(
				"want 4624442d68ee402a94364191085b77137618633e\n",
				"want f900c8326a43303685c46b279b9f70411bff1a4b\n",
				"shallow 4b643d0ef739a1b494e7d6926d8d8ed80d35edf4\n",
				PacketLineIn.end());
		ProtocolV0Parser parser = new ProtocolV0Parser(defaultConfig());
		FetchV0Request request = parser.recvWants(pckIn);
		assertTrue(request.getClientCapabilities().isEmpty());
		assertThat(request.getWantIds(),
				hasOnlyObjectIds("4624442d68ee402a94364191085b77137618633e",
						"f900c8326a43303685c46b279b9f70411bff1a4b"));
		assertThat(request.getClientShallowCommits(),
				hasOnlyObjectIds("4b643d0ef739a1b494e7d6926d8d8ed80d35edf4"));
	}

	@Test
	public void testRecvWantsFilter()
			throws PackProtocolException, IOException {
		PacketLineIn pckIn = formatAsPacketLine(
				"want 4624442d68ee402a94364191085b77137618633e\n",
				"want f900c8326a43303685c46b279b9f70411bff1a4b\n",
				"filter blob:limit=13000\n",
				PacketLineIn.end());
		ProtocolV0Parser parser = new ProtocolV0Parser(defaultConfig());
		FetchV0Request request = parser.recvWants(pckIn);
		assertTrue(request.getClientCapabilities().isEmpty());
		assertThat(request.getWantIds(),
				hasOnlyObjectIds("4624442d68ee402a94364191085b77137618633e",
						"f900c8326a43303685c46b279b9f70411bff1a4b"));
		assertEquals(13000, request.getFilterSpec().getBlobLimit());
		assertEquals(-1, request.getFilterSpec().getTreeDepthLimit());
	}

}

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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.lib.Sets;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Test invocation of the advertiseRefsHooks in combinations of protocol (v0/v1
 * or v2) and transports (bidirectional or not)
 */
public class UploadPackAdvertiseRefsTest {
	@Rule
	public ExpectedException thrown = ExpectedException.none();
	private InMemoryRepository server;

	private static ByteArrayInputStream linesAsInputStream(String... inputLines)
			throws IOException {
		try (ByteArrayOutputStream send = new ByteArrayOutputStream()) {
			PacketLineOut pckOut = new PacketLineOut(send);
			for (String line : inputLines) {
				if (line == PacketLineIn.END) {
					pckOut.end();
				} else if (line == PacketLineIn.DELIM) {
					pckOut.writeDelim();
				} else {
					pckOut.writeString(line);
				}
			}
			return new ByteArrayInputStream(send.toByteArray());
		}
	}

	@Before
	public void setUp() throws Exception {
		server = newRepo("server");
	}

	private static InMemoryRepository newRepo(String name) {
		return new InMemoryRepository(new DfsRepositoryDescription(name));
	}


	class TestAdvertisedRefsHook implements AdvertiseRefsHook {

		public boolean invoked;

		private final Map<String, Ref> advertisedRefs = new HashMap<>();

		public TestAdvertisedRefsHook(String... refNames) {
			for (String refName : refNames) {
				this.advertisedRefs.put(refName,
						new ObjectIdRef.PeeledNonTag(Storage.LOOSE, refName, ObjectId.zeroId()));
			}
		}

		@Override
		public void advertiseRefs(UploadPack uploadPack)
				throws ServiceMayNotContinueException {
			assertFalse(invoked);
			invoked = true;
			uploadPack.setAdvertisedRefs(advertisedRefs);

		}

		@Override
		public void advertiseRefs(BaseReceivePack receivePack)
				throws ServiceMayNotContinueException {
			// TODO Auto-generated method stub

		}
	}

	@Test
	public void advertiseRefs_Onedi_V0() throws IOException {
		// HTTP v0
		UploadPack up = new UploadPack(server);
		up.setBiDirectionalPipe(false);

		TestAdvertisedRefsHook hook = new TestAdvertisedRefsHook("refs/heads/master", "refs/heads/change1");
		up.setAdvertiseRefsHook(hook);

		ByteArrayInputStream send = linesAsInputStream(PacketLineIn.END);
		ByteArrayOutputStream recv = new ByteArrayOutputStream();
		up.upload(send, recv, null);

		assertTrue(hook.invoked);
	}

	@Test
	public void advertiseRefs_Bidi_V0() throws IOException {
		// HTTP v0
		UploadPack up = new UploadPack(server);
		up.setBiDirectionalPipe(true);

		TestAdvertisedRefsHook hook = new TestAdvertisedRefsHook(
				"refs/heads/master", "refs/heads/change1");
		up.setAdvertiseRefsHook(hook);

		ByteArrayInputStream send = linesAsInputStream(PacketLineIn.END);
		ByteArrayOutputStream recv = new ByteArrayOutputStream();
		up.upload(send, recv, null);

		assertTrue(hook.invoked);
	}

	// TODO(ifrade): Fix invocations for V2!
	@Test
	public void advertiseRefs_Onedi_V2() throws IOException {
		// HTTP v0
		server.getConfig().setString("protocol", null, "version", "2");
		UploadPack up = new UploadPack(server);
		up.setBiDirectionalPipe(false);
		up.setExtraParameters(Sets.of("version=2"));

		TestAdvertisedRefsHook hook = new TestAdvertisedRefsHook(
				"refs/heads/master", "refs/heads/change1");
		up.setAdvertiseRefsHook(hook);

		ByteArrayInputStream send = linesAsInputStream(PacketLineIn.END);
		ByteArrayOutputStream recv = new ByteArrayOutputStream();
		up.upload(send, recv, null);

		assertFalse(hook.invoked);
	}

	// TODO(ifrade): Fix invocations for V2!
	@Test
	public void advertiseRefs_Bidi_V2() throws IOException {
		// HTTP v0
		server.getConfig().setString("protocol", null, "version", "2");
		UploadPack up = new UploadPack(server);
		up.setBiDirectionalPipe(false);
		up.setExtraParameters(Sets.of("version=2"));

		TestAdvertisedRefsHook hook = new TestAdvertisedRefsHook(
				"refs/heads/master", "refs/heads/change1");
		up.setAdvertiseRefsHook(hook);

		ByteArrayInputStream send = linesAsInputStream(PacketLineIn.END);
		ByteArrayOutputStream recv = new ByteArrayOutputStream();
		up.upload(send, recv, null);

		assertFalse(hook.invoked);
	}

}

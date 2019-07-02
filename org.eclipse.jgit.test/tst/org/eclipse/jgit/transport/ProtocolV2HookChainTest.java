/*
 * Copyright (C) 2019, Google LLC.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProtocolV2HookChainTest {

	@Test
	public void testDefaultIfEmpty() {
		ProtocolV2Hook[] noHooks = {};
		ProtocolV2Hook newChain = ProtocolV2HookChain
				.newChain(Arrays.asList(noHooks));
		assertEquals(newChain, ProtocolV2Hook.DEFAULT);
	}

	@Test
	public void testFlattenChainIfOnlyOne() {
		FakeProtocolV2Hook hook1 = new FakeProtocolV2Hook();
		ProtocolV2Hook newChain = ProtocolV2HookChain
				.newChain(Arrays.asList(ProtocolV2Hook.DEFAULT, hook1));
		assertEquals(newChain, hook1);
	}

	@Test
	public void testMultipleHooks() throws ServiceMayNotContinueException {
		FakeProtocolV2Hook hook1 = new FakeProtocolV2Hook();
		FakeProtocolV2Hook hook2 = new FakeProtocolV2Hook();

		ProtocolV2Hook chained = ProtocolV2HookChain
				.newChain(Arrays.asList(hook1, hook2));
		chained.onLsRefs(LsRefsV2Request.builder().build());

		assertTrue(hook1.wasInvoked());
		assertTrue(hook2.wasInvoked());
	}

	private static final class FakeProtocolV2Hook implements ProtocolV2Hook {
		boolean invoked;

		@Override
		public void onLsRefs(LsRefsV2Request req)
				throws ServiceMayNotContinueException {
			invoked = true;
		}

		@Override
		public void onCapabilities(CapabilitiesV2Request req)
				throws ServiceMayNotContinueException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void onFetch(FetchV2Request req)
				throws ServiceMayNotContinueException {
			throw new UnsupportedOperationException();
		}

		public boolean wasInvoked() {
			return invoked;
		}
	}
}

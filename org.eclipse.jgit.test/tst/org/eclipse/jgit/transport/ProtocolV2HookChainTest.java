/*
 * Copyright (C) 2019, Google LLC. and others
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

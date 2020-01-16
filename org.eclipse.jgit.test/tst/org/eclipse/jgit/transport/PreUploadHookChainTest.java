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
import java.util.Collection;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PreUploadHookChainTest {

	@Test
	public void testDefaultIfEmpty() {
		PreUploadHook[] noHooks = {};
		PreUploadHook newChain = PreUploadHookChain
				.newChain(Arrays.asList(noHooks));
		assertEquals(newChain, PreUploadHook.NULL);
	}

	@Test
	public void testFlattenChainIfOnlyOne() {
		FakePreUploadHook hook1 = new FakePreUploadHook();
		PreUploadHook newChain = PreUploadHookChain
				.newChain(Arrays.asList(PreUploadHook.NULL, hook1));
		assertEquals(newChain, hook1);
	}

	@Test
	public void testMultipleHooks() throws ServiceMayNotContinueException {
		FakePreUploadHook hook1 = new FakePreUploadHook();
		FakePreUploadHook hook2 = new FakePreUploadHook();

		PreUploadHook chained = PreUploadHookChain
				.newChain(Arrays.asList(hook1, hook2));
		chained.onBeginNegotiateRound(null, null, 0);

		assertTrue(hook1.wasInvoked());
		assertTrue(hook2.wasInvoked());
	}

	private static final class FakePreUploadHook implements PreUploadHook {
		boolean invoked;

		@Override
		public void onBeginNegotiateRound(UploadPack up,
				Collection<? extends ObjectId> wants, int cntOffered)
				throws ServiceMayNotContinueException {
			invoked = true;
		}

		@Override
		public void onEndNegotiateRound(UploadPack up,
				Collection<? extends ObjectId> wants, int cntCommon,
				int cntNotFound, boolean ready)
				throws ServiceMayNotContinueException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void onSendPack(UploadPack up,
				Collection<? extends ObjectId> wants,
				Collection<? extends ObjectId> haves)
				throws ServiceMayNotContinueException {
			throw new UnsupportedOperationException();
		}

		public boolean wasInvoked() {
			return invoked;
		}
	}
}
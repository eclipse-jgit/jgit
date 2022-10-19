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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.eclipse.jgit.storage.pack.PackStatistics;
import org.junit.jupiter.api.Test;

public class PostUploadHookChainTest {

	@Test
	void testDefaultIfEmpty() {
		PostUploadHook[] noHooks = {};
		PostUploadHook newChain = PostUploadHookChain
				.newChain(Arrays.asList(noHooks));
		assertEquals(newChain, PostUploadHook.NULL);
	}

	@Test
	void testFlattenChainIfOnlyOne() {
		FakePostUploadHook hook1 = new FakePostUploadHook();
		PostUploadHook newChain = PostUploadHookChain
				.newChain(Arrays.asList(PostUploadHook.NULL, hook1));
		assertEquals(newChain, hook1);
	}

	@Test
	void testMultipleHooks() {
		FakePostUploadHook hook1 = new FakePostUploadHook();
		FakePostUploadHook hook2 = new FakePostUploadHook();

		PostUploadHook chained = PostUploadHookChain
				.newChain(Arrays.asList(hook1, hook2));
		chained.onPostUpload(null);

		assertTrue(hook1.wasInvoked());
		assertTrue(hook2.wasInvoked());
	}

	private static final class FakePostUploadHook implements PostUploadHook {
		boolean invoked;

		public boolean wasInvoked() {
			return invoked;
		}

		@Override
		public void onPostUpload(PackStatistics stats) {
			invoked = true;
		}
	}
}
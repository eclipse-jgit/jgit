/*
 * Copyright (C) 2009, Google Inc.
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

package org.eclipse.jgit.storage.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.Test;

public class WindowCacheReconfigureTest extends RepositoryTestCase {
	@Test
	public void testConfigureCache_PackedGitLimit_0() {
		try {
			final WindowCacheConfig cfg = new WindowCacheConfig();
			cfg.setPackedGitLimit(0);
			WindowCache.reconfigure(cfg);
			fail("incorrectly permitted PackedGitLimit = 0");
		} catch (IllegalArgumentException e) {
			//
		}
	}

	@Test
	public void testConfigureCache_PackedGitWindowSize_0() {
		try {
			final WindowCacheConfig cfg = new WindowCacheConfig();
			cfg.setPackedGitWindowSize(0);
			WindowCache.reconfigure(cfg);
			fail("incorrectly permitted PackedGitWindowSize = 0");
		} catch (IllegalArgumentException e) {
			assertEquals("Invalid window size", e.getMessage());
		}
	}

	@Test
	public void testConfigureCache_PackedGitWindowSize_512() {
		try {
			final WindowCacheConfig cfg = new WindowCacheConfig();
			cfg.setPackedGitWindowSize(512);
			WindowCache.reconfigure(cfg);
			fail("incorrectly permitted PackedGitWindowSize = 512");
		} catch (IllegalArgumentException e) {
			assertEquals("Invalid window size", e.getMessage());
		}
	}

	@Test
	public void testConfigureCache_PackedGitWindowSize_4097() {
		try {
			final WindowCacheConfig cfg = new WindowCacheConfig();
			cfg.setPackedGitWindowSize(4097);
			WindowCache.reconfigure(cfg);
			fail("incorrectly permitted PackedGitWindowSize = 4097");
		} catch (IllegalArgumentException e) {
			assertEquals("Window size must be power of 2", e.getMessage());
		}
	}

	@Test
	public void testConfigureCache_PackedGitOpenFiles_0() {
		try {
			final WindowCacheConfig cfg = new WindowCacheConfig();
			cfg.setPackedGitOpenFiles(0);
			WindowCache.reconfigure(cfg);
			fail("incorrectly permitted PackedGitOpenFiles = 0");
		} catch (IllegalArgumentException e) {
			assertEquals("Open files must be >= 1", e.getMessage());
		}
	}

	@Test
	public void testConfigureCache_PackedGitWindowSizeAbovePackedGitLimit() {
		try {
			final WindowCacheConfig cfg = new WindowCacheConfig();
			cfg.setPackedGitLimit(1024);
			cfg.setPackedGitWindowSize(8192);
			WindowCache.reconfigure(cfg);
			fail("incorrectly permitted PackedGitWindowSize > PackedGitLimit");
		} catch (IllegalArgumentException e) {
			assertEquals("Window size must be < limit", e.getMessage());
		}
	}

	@Test
	public void testConfigureCache_Limits1() {
		// This test is just to force coverage over some lower bounds for
		// the table. We don't want the table to wind up with too small
		// of a size. This is highly dependent upon the table allocation
		// algorithm actually implemented in WindowCache.
		//
		final WindowCacheConfig cfg = new WindowCacheConfig();
		cfg.setPackedGitLimit(6 * 4096 / 5);
		cfg.setPackedGitWindowSize(4096);
		WindowCache.reconfigure(cfg);
	}
}

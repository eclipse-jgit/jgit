/*
 * Copyright (C) 2016 Ericsson
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

package org.eclipse.jgit.lib;

import static org.eclipse.jgit.lib.RepositoryCacheConfig.AUTO_CLEANUP_DELAY;
import static org.eclipse.jgit.lib.RepositoryCacheConfig.NO_CLEANUP;
import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.Before;
import org.junit.Test;

public class RepositoryCacheConfigTest {

	private RepositoryCacheConfig config;

	@Before
	public void setUp() {
		config = new RepositoryCacheConfig();
	}

	@Test
	public void testDefaultValues() {
		assertEquals(TimeUnit.HOURS.toMillis(1), config.getExpireAfter());
		assertEquals(config.getExpireAfter() / 10, config.getCleanupDelay());
	}

	@Test
	public void testCleanupDelay() {
		config.setCleanupDelay(TimeUnit.HOURS.toMillis(1));
		assertEquals(TimeUnit.HOURS.toMillis(1), config.getCleanupDelay());
	}

	@Test
	public void testAutoCleanupDelay() {
		config.setExpireAfter(TimeUnit.MINUTES.toMillis(20));
		config.setCleanupDelay(AUTO_CLEANUP_DELAY);
		assertEquals(TimeUnit.MINUTES.toMillis(20), config.getExpireAfter());
		assertEquals(config.getExpireAfter() / 10, config.getCleanupDelay());
	}

	@Test
	public void testAutoCleanupDelayShouldBeMax10minutes() {
		config.setExpireAfter(TimeUnit.HOURS.toMillis(10));
		assertEquals(TimeUnit.HOURS.toMillis(10), config.getExpireAfter());
		assertEquals(TimeUnit.MINUTES.toMillis(10), config.getCleanupDelay());
	}

	@Test
	public void testDisabledCleanupDelay() {
		config.setCleanupDelay(NO_CLEANUP);
		assertEquals(NO_CLEANUP, config.getCleanupDelay());
	}

	@Test
	public void testFromConfig() throws ConfigInvalidException {
		Config otherConfig = new Config();
		otherConfig.fromText("[core]\nrepositoryCacheExpireAfter=1000\n"
				+ "repositoryCacheCleanupDelay=500");
		config.fromConfig(otherConfig);
		assertEquals(1000, config.getExpireAfter());
		assertEquals(500, config.getCleanupDelay());
	}
}

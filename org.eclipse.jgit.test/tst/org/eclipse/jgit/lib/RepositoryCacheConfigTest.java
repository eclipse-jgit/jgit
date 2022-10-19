/*
 * Copyright (C) 2016 Ericsson and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static org.eclipse.jgit.lib.RepositoryCacheConfig.AUTO_CLEANUP_DELAY;
import static org.eclipse.jgit.lib.RepositoryCacheConfig.NO_CLEANUP;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RepositoryCacheConfigTest {

	private RepositoryCacheConfig config;

	@BeforeEach
	public void setUp() {
		config = new RepositoryCacheConfig();
	}

	@Test
	void testDefaultValues() {
		assertEquals(TimeUnit.HOURS.toMillis(1), config.getExpireAfter());
		assertEquals(config.getExpireAfter() / 10, config.getCleanupDelay());
	}

	@Test
	void testCleanupDelay() {
		config.setCleanupDelay(TimeUnit.HOURS.toMillis(1));
		assertEquals(TimeUnit.HOURS.toMillis(1), config.getCleanupDelay());
	}

	@Test
	void testAutoCleanupDelay() {
		config.setExpireAfter(TimeUnit.MINUTES.toMillis(20));
		config.setCleanupDelay(AUTO_CLEANUP_DELAY);
		assertEquals(TimeUnit.MINUTES.toMillis(20), config.getExpireAfter());
		assertEquals(config.getExpireAfter() / 10, config.getCleanupDelay());
	}

	@Test
	void testAutoCleanupDelayShouldBeMax10minutes() {
		config.setExpireAfter(TimeUnit.HOURS.toMillis(10));
		assertEquals(TimeUnit.HOURS.toMillis(10), config.getExpireAfter());
		assertEquals(TimeUnit.MINUTES.toMillis(10), config.getCleanupDelay());
	}

	@Test
	void testDisabledCleanupDelay() {
		config.setCleanupDelay(NO_CLEANUP);
		assertEquals(NO_CLEANUP, config.getCleanupDelay());
	}

	@Test
	void testFromConfig() throws ConfigInvalidException {
		Config otherConfig = new Config();
		otherConfig.fromText("[core]\nrepositoryCacheExpireAfter=1000\n"
				+ "repositoryCacheCleanupDelay=500");
		config.fromConfig(otherConfig);
		assertEquals(1000, config.getExpireAfter());
		assertEquals(500, config.getCleanupDelay());
	}
}

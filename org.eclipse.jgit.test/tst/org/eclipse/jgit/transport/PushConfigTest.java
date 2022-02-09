/*
 * Copyright (C) 2017, 2022 David Pursehouse <david.pursehouse@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.PushConfig.PushDefault;
import org.eclipse.jgit.transport.PushConfig.PushRecurseSubmodulesMode;
import org.junit.Test;

public class PushConfigTest {

	@Test
	public void pushRecurseSubmoduleMatch() throws Exception {
		assertTrue(PushRecurseSubmodulesMode.CHECK.matchConfigValue("check"));
		assertTrue(PushRecurseSubmodulesMode.CHECK.matchConfigValue("CHECK"));

		assertTrue(PushRecurseSubmodulesMode.ON_DEMAND
				.matchConfigValue("on-demand"));
		assertTrue(PushRecurseSubmodulesMode.ON_DEMAND
				.matchConfigValue("ON-DEMAND"));
		assertTrue(PushRecurseSubmodulesMode.ON_DEMAND
				.matchConfigValue("on_demand"));
		assertTrue(PushRecurseSubmodulesMode.ON_DEMAND
				.matchConfigValue("ON_DEMAND"));

		assertTrue(PushRecurseSubmodulesMode.NO.matchConfigValue("no"));
		assertTrue(PushRecurseSubmodulesMode.NO.matchConfigValue("NO"));
		assertTrue(PushRecurseSubmodulesMode.NO.matchConfigValue("false"));
		assertTrue(PushRecurseSubmodulesMode.NO.matchConfigValue("FALSE"));
	}

	@Test
	public void pushRecurseSubmoduleNoMatch() throws Exception {
		assertFalse(PushRecurseSubmodulesMode.NO.matchConfigValue("N"));
		assertFalse(PushRecurseSubmodulesMode.ON_DEMAND
				.matchConfigValue("ONDEMAND"));
	}

	@Test
	public void pushRecurseSubmoduleToConfigValue() {
		assertEquals("on-demand",
				PushRecurseSubmodulesMode.ON_DEMAND.toConfigValue());
		assertEquals("check", PushRecurseSubmodulesMode.CHECK.toConfigValue());
		assertEquals("false", PushRecurseSubmodulesMode.NO.toConfigValue());
	}

	@Test
	public void pushDefaultMatch() throws Exception {
		assertTrue(PushDefault.NOTHING.matchConfigValue("nothing"));
		assertTrue(PushDefault.NOTHING.matchConfigValue("NOTHING"));
		assertTrue(PushDefault.CURRENT.matchConfigValue("current"));
		assertTrue(PushDefault.CURRENT.matchConfigValue("CURRENT"));
		assertTrue(PushDefault.UPSTREAM.matchConfigValue("upstream"));
		assertTrue(PushDefault.UPSTREAM.matchConfigValue("UPSTREAM"));
		assertTrue(PushDefault.UPSTREAM.matchConfigValue("tracking"));
		assertTrue(PushDefault.UPSTREAM.matchConfigValue("TRACKING"));
		assertTrue(PushDefault.SIMPLE.matchConfigValue("simple"));
		assertTrue(PushDefault.SIMPLE.matchConfigValue("SIMPLE"));
		assertTrue(PushDefault.MATCHING.matchConfigValue("matching"));
		assertTrue(PushDefault.MATCHING.matchConfigValue("MATCHING"));
	}

	@Test
	public void pushDefaultNoMatch() throws Exception {
		assertFalse(PushDefault.NOTHING.matchConfigValue("n"));
		assertFalse(PushDefault.CURRENT.matchConfigValue(""));
		assertFalse(PushDefault.UPSTREAM.matchConfigValue("track"));
	}

	@Test
	public void pushDefaultToConfigValue() throws Exception {
		assertEquals("nothing", PushDefault.NOTHING.toConfigValue());
		assertEquals("current", PushDefault.CURRENT.toConfigValue());
		assertEquals("upstream", PushDefault.UPSTREAM.toConfigValue());
		assertEquals("simple", PushDefault.SIMPLE.toConfigValue());
		assertEquals("matching", PushDefault.MATCHING.toConfigValue());
	}

	@Test
	public void testEmptyConfig() throws Exception {
		PushConfig cfg = parse("");
		assertEquals(PushRecurseSubmodulesMode.NO, cfg.getRecurseSubmodules());
		assertEquals(PushDefault.SIMPLE, cfg.getPushDefault());
	}

	@Test
	public void testConfig() throws Exception {
		PushConfig cfg = parse(
				"[push]\n\tdefault = tracking\n\trecurseSubmodules = on-demand\n");
		assertEquals(PushRecurseSubmodulesMode.ON_DEMAND,
				cfg.getRecurseSubmodules());
		assertEquals(PushDefault.UPSTREAM, cfg.getPushDefault());
	}

	private static PushConfig parse(String content) throws Exception {
		Config c = new Config();
		c.fromText(content);
		return c.get(PushConfig::new);
	}

}

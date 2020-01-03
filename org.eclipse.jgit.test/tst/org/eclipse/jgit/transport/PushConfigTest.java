/*
 * Copyright (C) 2017, David Pursehouse <david.pursehouse@gmail.com> and others
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
}

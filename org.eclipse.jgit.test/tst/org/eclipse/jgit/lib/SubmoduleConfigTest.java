/*
 * Copyright (C) 2017, David Pursehouse <david.pursehouse@gmail.com>
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.lib.SubmoduleConfig.FetchRecurseSubmodulesMode;
import org.junit.Test;

public class SubmoduleConfigTest {
	@Test
	public void fetchRecurseMatch() throws Exception {
		assertTrue(FetchRecurseSubmodulesMode.YES.matchConfigValue("yes"));
		assertTrue(FetchRecurseSubmodulesMode.YES.matchConfigValue("YES"));
		assertTrue(FetchRecurseSubmodulesMode.YES.matchConfigValue("true"));
		assertTrue(FetchRecurseSubmodulesMode.YES.matchConfigValue("TRUE"));

		assertTrue(FetchRecurseSubmodulesMode.ON_DEMAND
				.matchConfigValue("on-demand"));
		assertTrue(FetchRecurseSubmodulesMode.ON_DEMAND
				.matchConfigValue("ON-DEMAND"));
		assertTrue(FetchRecurseSubmodulesMode.ON_DEMAND
				.matchConfigValue("on_demand"));
		assertTrue(FetchRecurseSubmodulesMode.ON_DEMAND
				.matchConfigValue("ON_DEMAND"));

		assertTrue(FetchRecurseSubmodulesMode.NO.matchConfigValue("no"));
		assertTrue(FetchRecurseSubmodulesMode.NO.matchConfigValue("NO"));
		assertTrue(FetchRecurseSubmodulesMode.NO.matchConfigValue("false"));
		assertTrue(FetchRecurseSubmodulesMode.NO.matchConfigValue("FALSE"));
	}

	@Test
	public void fetchRecurseNoMatch() throws Exception {
		assertFalse(FetchRecurseSubmodulesMode.YES.matchConfigValue("Y"));
		assertFalse(FetchRecurseSubmodulesMode.NO.matchConfigValue("N"));
		assertFalse(FetchRecurseSubmodulesMode.ON_DEMAND
				.matchConfigValue("ONDEMAND"));
		assertFalse(FetchRecurseSubmodulesMode.YES.matchConfigValue(""));
		assertFalse(FetchRecurseSubmodulesMode.YES.matchConfigValue(null));
	}

	@Test
	public void fetchRecurseToConfigValue() {
		assertEquals("on-demand",
				FetchRecurseSubmodulesMode.ON_DEMAND.toConfigValue());
	}
}

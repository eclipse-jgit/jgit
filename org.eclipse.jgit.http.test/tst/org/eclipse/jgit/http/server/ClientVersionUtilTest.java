/*
 * Copyright (C) 2012, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server;

import static org.eclipse.jgit.http.server.ClientVersionUtil.invalidVersion;
import static org.eclipse.jgit.http.server.ClientVersionUtil.parseVersion;

import org.junit.Assert;
import org.junit.Test;

public class ClientVersionUtilTest {
	@Test
	public void testParse() {
		assertEquals("1.6.5", parseVersion("git/1.6.6-rc0"));
		assertEquals("1.6.6", parseVersion("git/1.6.6"));
		assertEquals("1.7.5", parseVersion("git/1.7.5.GIT"));
		assertEquals("1.7.6.1", parseVersion("git/1.7.6.1.45.gbe0cc"));

		assertEquals("1.5.4.3", parseVersion("git/1.5.4.3,gzip(proxy)"));
		assertEquals("1.7.0.2", parseVersion("git/1.7.0.2.msysgit.0.14.g956d7,gzip"));
		assertEquals("1.7.10.2", parseVersion("git/1.7.10.2 (Apple Git-33)"));

		assertEquals(ClientVersionUtil.toString(invalidVersion()), parseVersion("foo"));
	}

	private static void assertEquals(String exp, int[] act) {
		Assert.assertEquals(exp, ClientVersionUtil.toString(act));
	}
}

/*
 * Copyright (C) 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jgit.lib.Config;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TransferConfig} parsing.
 */
public class TransferConfigTest {

	@Test
	void testParseProtocolV0() {
		Config rc = new Config();
		rc.setInt("protocol", null, "version", 0);
		TransferConfig tc = new TransferConfig(rc);
		assertEquals(TransferConfig.ProtocolVersion.V0, tc.protocolVersion);
	}

	@Test
	void testParseProtocolV1() {
		Config rc = new Config();
		rc.setInt("protocol", null, "version", 1);
		TransferConfig tc = new TransferConfig(rc);
		assertEquals(TransferConfig.ProtocolVersion.V0, tc.protocolVersion);
	}

	@Test
	void testParseProtocolV2() {
		Config rc = new Config();
		rc.setInt("protocol", null, "version", 2);
		TransferConfig tc = new TransferConfig(rc);
		assertEquals(TransferConfig.ProtocolVersion.V2, tc.protocolVersion);
	}

	@Test
	void testParseProtocolNotSet() {
		Config rc = new Config();
		TransferConfig tc = new TransferConfig(rc);
		assertNull(tc.protocolVersion);
	}

	@Test
	void testParseProtocolUnknown() {
		Config rc = new Config();
		rc.setInt("protocol", null, "version", 3);
		TransferConfig tc = new TransferConfig(rc);
		assertNull(tc.protocolVersion);
	}

	@Test
	void testParseProtocolInvalid() {
		Config rc = new Config();
		rc.setString("protocol", null, "version", "foo");
		TransferConfig tc = new TransferConfig(rc);
		assertNull(tc.protocolVersion);
	}

	@Test
	public void testParseAdvertiseSIDDefault() {
		Config rc = new Config();
		TransferConfig tc = new TransferConfig(rc);
		assertFalse(tc.isAllowReceiveClientSID());
	}

	@Test
	public void testParseAdvertiseSIDSet() {
		Config rc = new Config();
		rc.setBoolean("transfer", null, "advertiseSID", true);
		TransferConfig tc = new TransferConfig(rc);
		assertTrue(tc.isAllowReceiveClientSID());
	}
}

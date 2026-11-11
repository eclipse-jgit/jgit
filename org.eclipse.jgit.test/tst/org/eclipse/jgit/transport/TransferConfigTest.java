/*
 * Copyright (C) 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries.
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.eclipse.jgit.lib.Config;
import org.junit.Test;

/**
 * Tests for {@link TransferConfig} parsing.
 */
public class TransferConfigTest {

	@Test
	public void testParseProtocolV0() {
		Config rc = new Config();
		rc.setInt("protocol", null, "version", 0);
		TransferConfig tc = new TransferConfig(rc);
		assertEquals(TransferConfig.ProtocolVersion.V0, tc.protocolVersion);
	}

	@Test
	public void testParseProtocolV1() {
		Config rc = new Config();
		rc.setInt("protocol", null, "version", 1);
		TransferConfig tc = new TransferConfig(rc);
		assertEquals(TransferConfig.ProtocolVersion.V0, tc.protocolVersion);
	}

	@Test
	public void testParseProtocolV2() {
		Config rc = new Config();
		rc.setInt("protocol", null, "version", 2);
		TransferConfig tc = new TransferConfig(rc);
		assertEquals(TransferConfig.ProtocolVersion.V2, tc.protocolVersion);
	}

	@Test
	public void testParseProtocolNotSet() {
		Config rc = new Config();
		TransferConfig tc = new TransferConfig(rc);
		assertNull(tc.protocolVersion);
	}

	@Test
	public void testParseProtocolUnknown() {
		Config rc = new Config();
		rc.setInt("protocol", null, "version", 3);
		TransferConfig tc = new TransferConfig(rc);
		assertNull(tc.protocolVersion);
	}

	@Test
	public void testParseProtocolInvalid() {
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

	@Test
	public void testPromisorAdvertisementConfigurations() throws Exception {
		String remote = "[remote \"origin\"]\n"
				+ "url = https://example.com/objects\n";
		String enabled = "[promisor]\nadvertise = true\n";
		String[][] cases = {
				{ remote + "promisor = true\n", null },
				{ "[promisor]\nadvertise = false\n" + remote
						+ "promisor = true\n", null },
				{ enabled, null },
				{ enabled + remote, null },
				{ enabled + remote + "promisor = false\n", null },
				{ enabled + remote + "promisor = true\n",
						"name=origin,url=https%3A%2F%2Fexample.com%2Fobjects" },
				{ enabled + remote + "partialCloneFilter = blob:none\n",
						"name=origin,url=https%3A%2F%2Fexample.com%2Fobjects" },
				{ enabled + remote
						+ "promisor = false\npartialCloneFilter = blob:none\n",
						"name=origin,url=https%3A%2F%2Fexample.com%2Fobjects" },
				{ enabled + remote + "partialCloneFilter = \"\"\n",
						"name=origin,url=https%3A%2F%2Fexample.com%2Fobjects" },
				{ enabled + "[remote \"origin\"]\npromisor = true\n",
						null },
				{ enabled + "[remote \"origin\"]\n"
						+ "promisor = true\nurl = \"\"\n",
						null },
				{ enabled + remote + "promisor = true\nurl = ssh://last/repo\n",
						"name=origin,url=ssh%3A%2F%2Flast%2Frepo" } };
		for (String[] testCase : cases) {
			Config rc = new Config();
			rc.fromText(testCase[0]);
			PromisorRemoteConfig promisors = new TransferConfig(rc)
					.getPromisorRemotes();
			assertEquals(testCase[0], testCase[1], promisors.getAdvertisement());
			if (testCase[1] == null) {
				assertTrue(promisors.getAdvertisedRemotes().isEmpty());
			}
		}
	}

	@Test
	public void testPromisorAdvertisementSkipsUnusableNames() {
		Config rc = new Config();
		rc.setBoolean("promisor", null, "advertise", true);
		for (String name : List.of("", "/absolute", "null\0name")) {
			rc.setBoolean("remote", name, "promisor", true);
			rc.setString("remote", name, "url", "https://example.com/objects");
		}
		assertNull(new TransferConfig(rc).getPromisorRemotes().getAdvertisement());
	}

	@Test
	public void testPromisorAdvertisementEscapesMultipleRemotes() {
		Config rc = new Config();
		rc.setBoolean("promisor", null, "advertise", true);
		rc.setString("promisor", null, "sendFields", "partialCloneFilter,token");
		rc.setBoolean("remote", "résumé,+;%=x", "promisor", true);
		rc.setString("remote", "résumé,+;%=x", "url",
				"https://example.com/a b/é?x=1+2,y;z=%25#frag");
		rc.setString("remote", "résumé,+;%=x", "token", "secret");
		rc.setString("remote", "filtered", "partialCloneFilter", "blob:none");
		rc.setString("remote", "filtered", "url", "ssh://example.com/repo+pack");
		assertEquals("name=r%C3%A9sum%C3%A9%2C%2B%3B%25%3Dx,"
				+ "url=https%3A%2F%2Fexample.com%2Fa%20b%2F%C3%A9"
				+ "%3Fx%3D1%2B2%2Cy%3Bz%3D%2525%23frag;"
				+ "name=filtered,url=ssh%3A%2F%2Fexample.com%2Frepo%2Bpack",
				new TransferConfig(rc).getPromisorRemotes().getAdvertisement());
	}

	@Test
	public void testPromisorAdvertisementEscapesControlCharacters() {
		Config rc = new Config();
		rc.setBoolean("promisor", null, "advertise", true);
		rc.setBoolean("remote", "objects", "promisor", true);
		rc.setString("remote", "objects", "url", "ssh://example/a\tb\nc\rd\u007f");
		assertEquals("name=objects,url=ssh%3A%2F%2Fexample%2Fa%09b%0Ac%0Dd%7F",
				new TransferConfig(rc).getPromisorRemotes().getAdvertisement());
	}

	@Test
	public void testPromisorAdvertisementInheritedConfiguration() {
		Config global = new Config();
		global.setBoolean("promisor", null, "advertise", true);
		global.setString("remote", "global", "partialCloneFilter", "blob:none");
		global.setString("remote", "global", "url", "https://global/objects");
		global.setBoolean("remote", "shared", "promisor", true);
		global.setString("remote", "shared", "url", "https://global/shared");
		Config repository = new Config(global);
		assertEquals("name=global,url=https%3A%2F%2Fglobal%2Fobjects;"
				+ "name=shared,url=https%3A%2F%2Fglobal%2Fshared",
				new TransferConfig(repository).getPromisorRemotes().getAdvertisement());

		repository.setString("remote", "shared", "url", "https://repo/shared");
		assertEquals("name=shared,url=https%3A%2F%2Frepo%2Fshared;"
				+ "name=global,url=https%3A%2F%2Fglobal%2Fobjects",
				new TransferConfig(repository).getPromisorRemotes().getAdvertisement());

		repository.setBoolean("remote", "shared", "promisor", false);
		assertEquals("name=global,url=https%3A%2F%2Fglobal%2Fobjects",
				new TransferConfig(repository).getPromisorRemotes().getAdvertisement());

		repository.setBoolean("promisor", null, "advertise", false);
		assertNull(new TransferConfig(repository).getPromisorRemotes().getAdvertisement());
	}

	@Test
	public void testPromisorAdvertisementSnapshotIsImmutable() {
		Config global = new Config();
		global.setBoolean("promisor", null, "advertise", true);
		global.setBoolean("remote", "objects", "promisor", true);
		global.setString("remote", "objects", "url", "https://example/objects");
		Config repository = new Config(global);
		TransferConfig tc = new TransferConfig(repository);

		global.setString("remote", "objects", "url", "https://changed/objects");
		repository.setBoolean("remote", "other", "promisor", true);
		repository.setString("remote", "other", "url", "https://example/other");
		repository.setBoolean("promisor", null, "advertise", false);

		assertEquals("name=objects,url=https%3A%2F%2Fexample%2Fobjects",
				tc.getPromisorRemotes().getAdvertisement());
		assertEquals(List.of(new PromisorRemoteConfig.Remote("objects",
				"https://example/objects")),
				tc.getPromisorRemotes().getAdvertisedRemotes());
		assertThrows(UnsupportedOperationException.class,
				() -> tc.getPromisorRemotes().getAdvertisedRemotes().clear());
	}
}

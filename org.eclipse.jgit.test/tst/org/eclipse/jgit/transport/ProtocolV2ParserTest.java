/*
 * Copyright (C) 2018, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.eclipse.jgit.transport.ObjectIdMatcher.hasOnlyObjectIds;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ProtocolV2ParserTest {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	private TestRepository<InMemoryRepository> testRepo;

	@Before
	public void setUp() throws Exception {
		testRepo = new TestRepository<>(newRepo("protocol-v2-parser-test"));
	}

	private static InMemoryRepository newRepo(String name) {
		return new InMemoryRepository(new DfsRepositoryDescription(name));
	}

	private static class ConfigBuilder {

		private boolean allowRefInWant;

		private boolean allowFilter;

		private ConfigBuilder() {
		}

		static ConfigBuilder start() {
			return new ConfigBuilder();
		}

		static TransferConfig getDefault() {
			return start().done();
		}

		ConfigBuilder allowRefInWant() {
			allowRefInWant = true;
			return this;
		}

		ConfigBuilder allowFilter() {
			allowFilter = true;
			return this;
		}

		TransferConfig done() {
			Config rc = new Config();
			rc.setBoolean("uploadpack", null, "allowrefinwant", allowRefInWant);
			rc.setBoolean("uploadpack", null, "allowfilter", allowFilter);
			return new TransferConfig(rc);
		}
	}

	/*
	 * Convert the input lines to the PacketLine that the parser reads.
	 */
	private static PacketLineIn formatAsPacketLine(String... inputLines)
			throws IOException {
		ByteArrayOutputStream send = new ByteArrayOutputStream();
		PacketLineOut pckOut = new PacketLineOut(send);
		for (String line : inputLines) {
			if (PacketLineIn.isEnd(line)) {
				pckOut.end();
			} else if (PacketLineIn.isDelimiter(line)) {
				pckOut.writeDelim();
			} else {
				pckOut.writeString(line);
			}
		}

		return new PacketLineIn(new ByteArrayInputStream(send.toByteArray()));
	}

	/*
	 * Successful fetch with the basic core commands of the protocol.
	 */
	@Test
	public void testFetchBasicArguments()
			throws PackProtocolException, IOException {
		PacketLineIn pckIn = formatAsPacketLine(
				PacketLineIn.delimiter(),
				"thin-pack", "no-progress", "include-tag", "ofs-delta",
				"want 4624442d68ee402a94364191085b77137618633e",
				"want f900c8326a43303685c46b279b9f70411bff1a4b",
				"have 554f6e41067b9e3e565b6988a8294fac1cb78f4b",
				"have abc760ab9ad72f08209943251b36cb886a578f87", "done",
				PacketLineIn.end());
		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.getDefault());
		FetchV2Request request = parser.parseFetchRequest(pckIn);
		assertTrue(request.getClientCapabilities()
				.contains(GitProtocolConstants.OPTION_THIN_PACK));
		assertTrue(request.getClientCapabilities()
				.contains(GitProtocolConstants.OPTION_NO_PROGRESS));
		assertTrue(request.getClientCapabilities()
				.contains(GitProtocolConstants.OPTION_INCLUDE_TAG));
		assertTrue(request.getClientCapabilities()
				.contains(GitProtocolConstants.CAPABILITY_OFS_DELTA));
		assertThat(request.getWantIds(),
				hasOnlyObjectIds("4624442d68ee402a94364191085b77137618633e",
						"f900c8326a43303685c46b279b9f70411bff1a4b"));
		assertThat(request.getPeerHas(),
				hasOnlyObjectIds("554f6e41067b9e3e565b6988a8294fac1cb78f4b",
						"abc760ab9ad72f08209943251b36cb886a578f87"));
		assertTrue(request.getWantedRefs().isEmpty());
		assertTrue(request.wasDoneReceived());
	}

	@Test
	public void testFetchWithShallow_deepen() throws IOException {
		PacketLineIn pckIn = formatAsPacketLine(
				PacketLineIn.delimiter(),
				"deepen 15",
				"deepen-relative",
				"shallow 28274d02c489f4c7e68153056e9061a46f62d7a0",
				"shallow 145e683b229dcab9d0e2ccb01b386f9ecc17d29d",
				PacketLineIn.end());
		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.getDefault());
		FetchV2Request request = parser.parseFetchRequest(pckIn);
		assertThat(request.getClientShallowCommits(),
				hasOnlyObjectIds("28274d02c489f4c7e68153056e9061a46f62d7a0",
						"145e683b229dcab9d0e2ccb01b386f9ecc17d29d"));
		assertTrue(request.getDeepenNotRefs().isEmpty());
		assertEquals(15, request.getDepth());
		assertTrue(request.getClientCapabilities()
				.contains(GitProtocolConstants.OPTION_DEEPEN_RELATIVE));
	}

	@Test
	public void testFetchWithShallow_deepenNot() throws IOException {
		PacketLineIn pckIn = formatAsPacketLine(PacketLineIn.delimiter(),
				"shallow 28274d02c489f4c7e68153056e9061a46f62d7a0",
				"shallow 145e683b229dcab9d0e2ccb01b386f9ecc17d29d",
				"deepen-not a08595f76159b09d57553e37a5123f1091bb13e7",
				PacketLineIn.end());
		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.getDefault());
		FetchV2Request request = parser.parseFetchRequest(pckIn);
		assertThat(request.getClientShallowCommits(),
				hasOnlyObjectIds("28274d02c489f4c7e68153056e9061a46f62d7a0",
						"145e683b229dcab9d0e2ccb01b386f9ecc17d29d"));
		assertThat(request.getDeepenNotRefs(),
				hasItems("a08595f76159b09d57553e37a5123f1091bb13e7"));
	}

	@Test
	public void testFetchWithShallow_deepenSince() throws IOException {
		PacketLineIn pckIn = formatAsPacketLine(PacketLineIn.delimiter(),
				"shallow 28274d02c489f4c7e68153056e9061a46f62d7a0",
				"shallow 145e683b229dcab9d0e2ccb01b386f9ecc17d29d",
				"deepen-since 123123123",
				PacketLineIn.end());
		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.getDefault());
		FetchV2Request request = parser.parseFetchRequest(pckIn);
		assertThat(request.getClientShallowCommits(),
				hasOnlyObjectIds("28274d02c489f4c7e68153056e9061a46f62d7a0",
						"145e683b229dcab9d0e2ccb01b386f9ecc17d29d"));
		assertEquals(123123123, request.getDeepenSince());
	}

	@Test
	public void testFetchWithNoneFilter() throws IOException {
		PacketLineIn pckIn = formatAsPacketLine(PacketLineIn.delimiter(),
				"filter blob:none",
				PacketLineIn.end());
		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.start().allowFilter().done());
		FetchV2Request request = parser.parseFetchRequest(pckIn);
		assertEquals(0, request.getFilterSpec().getBlobLimit());
		assertEquals(-1, request.getFilterSpec().getTreeDepthLimit());
	}

	@Test
	public void testFetchWithBlobSizeFilter() throws IOException {
		PacketLineIn pckIn = formatAsPacketLine(PacketLineIn.delimiter(),
				"filter blob:limit=15",
				PacketLineIn.end());
		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.start().allowFilter().done());
		FetchV2Request request = parser.parseFetchRequest(pckIn);
		assertEquals(15, request.getFilterSpec().getBlobLimit());
		assertEquals(-1, request.getFilterSpec().getTreeDepthLimit());
	}

	@Test
	public void testFetchWithTreeDepthFilter() throws IOException {
		PacketLineIn pckIn = formatAsPacketLine(PacketLineIn.delimiter(),
				"filter tree:3",
				PacketLineIn.end());
		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.start().allowFilter().done());
		FetchV2Request request = parser.parseFetchRequest(pckIn);
		assertEquals(-1, request.getFilterSpec().getBlobLimit());
		assertEquals(3, request.getFilterSpec().getTreeDepthLimit());
	}

	@Test
	public void testFetchMustNotHaveMultipleFilters() throws IOException {
		PacketLineIn pckIn = formatAsPacketLine(PacketLineIn.delimiter(),
				"filter blob:none",
				"filter blob:limit=12",
				PacketLineIn.end());
		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.start().allowFilter().done());

		thrown.expect(PackProtocolException.class);
		parser.parseFetchRequest(pckIn);
	}

	@Test
	public void testFetchFilterWithoutAllowFilter() throws IOException {
		PacketLineIn pckIn = formatAsPacketLine(PacketLineIn.delimiter(),
				"filter blob:limit=12", PacketLineIn.end());
		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.getDefault());

		thrown.expect(PackProtocolException.class);
		parser.parseFetchRequest(pckIn);
	}

	@Test
	public void testFetchWithRefInWant() throws Exception {
		RevCommit one = testRepo.commit().message("1").create();
		RevCommit two = testRepo.commit().message("2").create();
		testRepo.update("branchA", one);
		testRepo.update("branchB", two);

		PacketLineIn pckIn = formatAsPacketLine(PacketLineIn.delimiter(),
				"want e4980cdc48cfa1301493ca94eb70523f6788b819",
				"want-ref refs/heads/branchA",
				PacketLineIn.end());
		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.start().allowRefInWant().done());

		FetchV2Request request = parser.parseFetchRequest(pckIn);
		assertEquals(1, request.getWantedRefs().size());
		assertThat(request.getWantedRefs(),
				hasItems("refs/heads/branchA"));
		assertEquals(1, request.getWantIds().size());
		assertThat(request.getWantIds(), hasOnlyObjectIds(
				"e4980cdc48cfa1301493ca94eb70523f6788b819"));
	}

	@Test
	public void testFetchWithRefInWantUnknownRef() throws Exception {
		PacketLineIn pckIn = formatAsPacketLine(PacketLineIn.delimiter(),
				"want e4980cdc48cfa1301493ca94eb70523f6788b819",
				"want-ref refs/heads/branchC",
				PacketLineIn.end());
		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.start().allowRefInWant().done());

		RevCommit one = testRepo.commit().message("1").create();
		RevCommit two = testRepo.commit().message("2").create();
		testRepo.update("branchA", one);
		testRepo.update("branchB", two);

		FetchV2Request request = parser.parseFetchRequest(pckIn);
		assertEquals(1, request.getWantedRefs().size());
		assertThat(request.getWantedRefs(), hasItems("refs/heads/branchC"));
	}

	@Test
	public void testLsRefsMinimalReq() throws IOException {
		PacketLineIn pckIn = formatAsPacketLine(PacketLineIn.delimiter(),
				PacketLineIn.end());

		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.getDefault());
		LsRefsV2Request req = parser.parseLsRefsRequest(pckIn);
		assertFalse(req.getPeel());
		assertFalse(req.getSymrefs());
		assertEquals(0, req.getRefPrefixes().size());
	}

	@Test
	public void testLsRefsSymrefs() throws IOException {
		PacketLineIn pckIn = formatAsPacketLine(PacketLineIn.delimiter(), "symrefs",
				PacketLineIn.end());

		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.getDefault());
		LsRefsV2Request req = parser.parseLsRefsRequest(pckIn);
		assertFalse(req.getPeel());
		assertTrue(req.getSymrefs());
		assertEquals(0, req.getRefPrefixes().size());

	}

	@Test
	public void testLsRefsPeel() throws IOException {
		PacketLineIn pckIn = formatAsPacketLine(
				PacketLineIn.delimiter(),
				"peel",
				PacketLineIn.end());

		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.getDefault());
		LsRefsV2Request req = parser.parseLsRefsRequest(pckIn);
		assertTrue(req.getPeel());
		assertFalse(req.getSymrefs());
		assertEquals(0, req.getRefPrefixes().size());
	}

	@Test
	public void testLsRefsRefPrefixes() throws IOException {
		PacketLineIn pckIn = formatAsPacketLine(PacketLineIn.delimiter(),
				"ref-prefix refs/for", "ref-prefix refs/heads",
				PacketLineIn.end());

		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.getDefault());
		LsRefsV2Request req = parser.parseLsRefsRequest(pckIn);
		assertFalse(req.getPeel());
		assertFalse(req.getSymrefs());
		assertEquals(2, req.getRefPrefixes().size());
		assertThat(req.getRefPrefixes(), hasItems("refs/for", "refs/heads"));
	}
}

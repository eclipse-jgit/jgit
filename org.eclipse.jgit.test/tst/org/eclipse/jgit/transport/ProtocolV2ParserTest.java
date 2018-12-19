/*
 * Copyright (C) 2018, Google LLC.
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
package org.eclipse.jgit.transport;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
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
			if (line == PacketLineIn.END) {
				pckOut.end();
			} else if (line == PacketLineIn.DELIM) {
				pckOut.writeDelim();
			} else {
				pckOut.writeString(line);
			}
		}

		return new PacketLineIn(new ByteArrayInputStream(send.toByteArray()));
	}

	private static List<String> objIdsAsStrings(Collection<ObjectId> objIds) {
		// TODO(ifrade) Translate this to a matcher, so it would read as
		// assertThat(req.wantsIds(), hasObjectIds("...", "..."))
		return objIds.stream().map(ObjectId::name).collect(Collectors.toList());
	}

	/*
	 * Succesful fetch with the basic core commands of the protocol.
	 */
	@Test
	public void testFetchBasicArguments()
			throws PackProtocolException, IOException {
		PacketLineIn pckIn = formatAsPacketLine(
				PacketLineIn.DELIM,
				"thin-pack", "no-progress", "include-tag", "ofs-delta",
				"want 4624442d68ee402a94364191085b77137618633e",
				"want f900c8326a43303685c46b279b9f70411bff1a4b",
				"have 554f6e41067b9e3e565b6988a8294fac1cb78f4b",
				"have abc760ab9ad72f08209943251b36cb886a578f87", "done",
				PacketLineIn.END);
		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.getDefault());
		FetchV2Request request = parser.parseFetchRequest(pckIn);
		assertTrue(request.getOptions()
				.contains(GitProtocolConstants.OPTION_THIN_PACK));
		assertTrue(request.getOptions()
				.contains(GitProtocolConstants.OPTION_NO_PROGRESS));
		assertTrue(request.getOptions()
				.contains(GitProtocolConstants.OPTION_INCLUDE_TAG));
		assertTrue(request.getOptions()
				.contains(GitProtocolConstants.CAPABILITY_OFS_DELTA));
		assertThat(objIdsAsStrings(request.getWantsIds()),
				hasItems("4624442d68ee402a94364191085b77137618633e",
						"f900c8326a43303685c46b279b9f70411bff1a4b"));
		assertThat(objIdsAsStrings(request.getPeerHas()),
				hasItems("554f6e41067b9e3e565b6988a8294fac1cb78f4b",
						"abc760ab9ad72f08209943251b36cb886a578f87"));
		assertTrue(request.getWantedRefs().isEmpty());
		assertTrue(request.wasDoneReceived());
	}

	@Test
	public void testFetchWithShallow_deepen() throws IOException {
		PacketLineIn pckIn = formatAsPacketLine(
				PacketLineIn.DELIM,
				"deepen 15",
				"deepen-relative",
				"shallow 28274d02c489f4c7e68153056e9061a46f62d7a0",
				"shallow 145e683b229dcab9d0e2ccb01b386f9ecc17d29d",
				PacketLineIn.END);
		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.getDefault());
		FetchV2Request request = parser.parseFetchRequest(pckIn);
		assertThat(objIdsAsStrings(request.getClientShallowCommits()),
				hasItems("28274d02c489f4c7e68153056e9061a46f62d7a0",
						"145e683b229dcab9d0e2ccb01b386f9ecc17d29d"));
		assertTrue(request.getDeepenNotRefs().isEmpty());
		assertEquals(15, request.getDepth());
		assertTrue(request.getOptions()
				.contains(GitProtocolConstants.OPTION_DEEPEN_RELATIVE));
	}

	@Test
	public void testFetchWithShallow_deepenNot() throws IOException {
		PacketLineIn pckIn = formatAsPacketLine(PacketLineIn.DELIM,
				"shallow 28274d02c489f4c7e68153056e9061a46f62d7a0",
				"shallow 145e683b229dcab9d0e2ccb01b386f9ecc17d29d",
				"deepen-not a08595f76159b09d57553e37a5123f1091bb13e7",
				PacketLineIn.END);
		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.getDefault());
		FetchV2Request request = parser.parseFetchRequest(pckIn);
		assertThat(objIdsAsStrings(request.getClientShallowCommits()),
				hasItems("28274d02c489f4c7e68153056e9061a46f62d7a0",
						"145e683b229dcab9d0e2ccb01b386f9ecc17d29d"));
		assertThat(request.getDeepenNotRefs(),
				hasItems("a08595f76159b09d57553e37a5123f1091bb13e7"));
	}

	@Test
	public void testFetchWithShallow_deepenSince() throws IOException {
		PacketLineIn pckIn = formatAsPacketLine(PacketLineIn.DELIM,
				"shallow 28274d02c489f4c7e68153056e9061a46f62d7a0",
				"shallow 145e683b229dcab9d0e2ccb01b386f9ecc17d29d",
				"deepen-since 123123123",
				PacketLineIn.END);
		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.getDefault());
		FetchV2Request request = parser.parseFetchRequest(pckIn);
		assertThat(objIdsAsStrings(request.getClientShallowCommits()),
				hasItems("28274d02c489f4c7e68153056e9061a46f62d7a0",
						"145e683b229dcab9d0e2ccb01b386f9ecc17d29d"));
		assertEquals(123123123, request.getDeepenSince());
	}

	@Test
	public void testFetchWithNoneFilter() throws IOException {
		PacketLineIn pckIn = formatAsPacketLine(PacketLineIn.DELIM,
				"filter blob:none",
				PacketLineIn.END);
		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.start().allowFilter().done());
		FetchV2Request request = parser.parseFetchRequest(pckIn);
		assertEquals(0, request.getFilterBlobLimit());
	}

	@Test
	public void testFetchWithBlobSizeFilter() throws IOException {
		PacketLineIn pckIn = formatAsPacketLine(PacketLineIn.DELIM,
				"filter blob:limit=15",
				PacketLineIn.END);
		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.start().allowFilter().done());
		FetchV2Request request = parser.parseFetchRequest(pckIn);
		assertEquals(15, request.getFilterBlobLimit());
	}

	@Test
	public void testFetchMustNotHaveMultipleFilters() throws IOException {
		thrown.expect(PackProtocolException.class);
		PacketLineIn pckIn = formatAsPacketLine(PacketLineIn.DELIM,
				"filter blob:none",
				"filter blob:limit=12",
				PacketLineIn.END);
		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.start().allowFilter().done());
		FetchV2Request request = parser.parseFetchRequest(pckIn);
		assertEquals(0, request.getFilterBlobLimit());
	}

	@Test
	public void testFetchFilterWithoutAllowFilter() throws IOException {
		thrown.expect(PackProtocolException.class);
		PacketLineIn pckIn = formatAsPacketLine(PacketLineIn.DELIM,
				"filter blob:limit=12", PacketLineIn.END);
		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.getDefault());
		parser.parseFetchRequest(pckIn);
	}

	@Test
	public void testFetchWithRefInWant() throws Exception {
		RevCommit one = testRepo.commit().message("1").create();
		RevCommit two = testRepo.commit().message("2").create();
		testRepo.update("branchA", one);
		testRepo.update("branchB", two);

		PacketLineIn pckIn = formatAsPacketLine(PacketLineIn.DELIM,
				"want e4980cdc48cfa1301493ca94eb70523f6788b819",
				"want-ref refs/heads/branchA",
				PacketLineIn.END);
		ProtocolV2Parser parser = new ProtocolV2Parser(
				ConfigBuilder.start().allowRefInWant().done());

		FetchV2Request request = parser.parseFetchRequest(pckIn);
		assertEquals(1, request.getWantedRefs().size());
		assertThat(request.getWantedRefs(), hasItems("refs/heads/branchA"));
		assertEquals(1, request.getWantsIds().size());
		assertThat(objIdsAsStrings(request.getWantsIds()),
				hasItems("e4980cdc48cfa1301493ca94eb70523f6788b819"));
	}

	@Test
	public void testFetchWithRefInWantUnknownRef() throws Exception {
		PacketLineIn pckIn = formatAsPacketLine(PacketLineIn.DELIM,
				"want e4980cdc48cfa1301493ca94eb70523f6788b819",
				"want-ref refs/heads/branchC",
				PacketLineIn.END);
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

}

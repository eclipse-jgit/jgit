/*
 * Copyright (C) 2021, Saša Živkov <sasa.zivkov@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Sets;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.junit.Before;
import org.junit.Test;

// TODO: refactor UploadPackTest to run against both DfsRepository and FileRepository
public class UploadPackLsRefsFileRepositoryTest
		extends LocalDiskRepositoryTestCase {

	private FileRepository server;

	private TestRepository<FileRepository> remote;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		server = createWorkRepository();
		remote = new TestRepository<>(server);
	}

	@Test
	public void testV2LsRefsPeel() throws Exception {
		RevCommit tip = remote.commit().message("message").create();
		remote.update("master", tip);
		server.updateRef("HEAD").link("refs/heads/master");
		RevTag tag = remote.tag("tag", tip);
		remote.update("refs/tags/tag", tag);

		ByteArrayInputStream recvStream = uploadPackV2("command=ls-refs\n",
				PacketLineIn.delimiter(), "peel", PacketLineIn.end());
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		assertThat(pckIn.readString(),
				is(tip.toObjectId().getName() + " HEAD"));
		assertThat(pckIn.readString(),
				is(tip.toObjectId().getName() + " refs/heads/master"));
		assertThat(pckIn.readString(), is(tag.toObjectId().getName()
				+ " refs/tags/tag peeled:" + tip.toObjectId().getName()));
		assertTrue(PacketLineIn.isEnd(pckIn.readString()));
	}

	private ByteArrayInputStream uploadPackV2(String... inputLines)
			throws Exception {
		return uploadPackV2(null, inputLines);
	}

	private ByteArrayInputStream uploadPackV2(
			Consumer<UploadPack> postConstructionSetup, String... inputLines)
			throws Exception {
		ByteArrayInputStream recvStream = uploadPackV2Setup(
				postConstructionSetup, inputLines);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		// drain capabilities
		while (!PacketLineIn.isEnd(pckIn.readString())) {
			// do nothing
		}
		return recvStream;
	}

	private ByteArrayInputStream uploadPackV2Setup(
			Consumer<UploadPack> postConstructionSetup, String... inputLines)
			throws Exception {

		ByteArrayInputStream send = linesAsInputStream(inputLines);

		server.getConfig().setString("protocol", null, "version", "2");
		UploadPack up = new UploadPack(server);
		if (postConstructionSetup != null) {
			postConstructionSetup.accept(up);
		}
		up.setExtraParameters(Sets.of("version=2"));

		ByteArrayOutputStream recv = new ByteArrayOutputStream();
		up.upload(send, recv, null);

		return new ByteArrayInputStream(recv.toByteArray());
	}

	private static ByteArrayInputStream linesAsInputStream(String... inputLines)
			throws IOException {
		try (ByteArrayOutputStream send = new ByteArrayOutputStream()) {
			PacketLineOut pckOut = new PacketLineOut(send);
			for (String line : inputLines) {
				Objects.requireNonNull(line);
				if (PacketLineIn.isEnd(line)) {
					pckOut.end();
				} else if (PacketLineIn.isDelimiter(line)) {
					pckOut.writeDelim();
				} else {
					pckOut.writeString(line);
				}
			}
			return new ByteArrayInputStream(send.toByteArray());
		}
	}
}

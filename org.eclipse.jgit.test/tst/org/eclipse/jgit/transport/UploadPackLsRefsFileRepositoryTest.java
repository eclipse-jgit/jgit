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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.RefUpdate;
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

	@Test
	public void testV2LsRefsRejectsPathTraversalRefPrefix()
			throws Exception {
		try (FileRepository source = createBareRepository();
				FileRepository victim = createBareRepository()) {
			ObjectId admin = writeLooseRef(victim, "refs/private/admin",
					"private object in victim repository");
			ObjectId deploy = writeLooseRef(victim, "refs/private/deploy",
					"second private object in victim repository");

			assertNull(source.exactRef("refs/private/admin"));
			assertNull(source.exactRef("refs/private/deploy"));
			assertTrue(!source.getObjectDatabase().has(admin));
			assertTrue(!source.getObjectDatabase().has(deploy));

			String prefix = "refs/heads/../../../"
					+ victim.getDirectory().getName() + "/refs/private/";
			UploadPackInternalServerErrorException e = assertThrows(
					UploadPackInternalServerErrorException.class,
					() -> uploadPackV2(source, "command=ls-refs\n",
							PacketLineIn.delimiter(), "ref-prefix " + prefix,
							PacketLineIn.end()));

			assertThat(e.getCause().getMessage(),
					containsString("Invalid ref name: " + prefix));
		}
	}

	private ByteArrayInputStream uploadPackV2(String... inputLines)
			throws Exception {
		return uploadPackV2(server, inputLines);
	}

	private ByteArrayInputStream uploadPackV2(FileRepository repository,
			String... inputLines) throws Exception {
		ByteArrayInputStream recvStream = uploadPackV2Setup(repository,
				inputLines);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		// drain capabilities
		while (!PacketLineIn.isEnd(pckIn.readString())) {
			// do nothing
		}
		return recvStream;
	}

	private ByteArrayInputStream uploadPackV2Setup(
			FileRepository repository, String... inputLines)
			throws Exception {

		ByteArrayInputStream send = linesAsInputStream(inputLines);

		repository.getConfig().setString("protocol", null, "version", "2");
		UploadPack up = new UploadPack(repository);
		up.setExtraParameters(Sets.of("version=2"));

		ByteArrayOutputStream recv = new ByteArrayOutputStream();
		up.upload(send, recv, null);

		return new ByteArrayInputStream(recv.toByteArray());
	}

	private static ObjectId writeLooseRef(FileRepository repository,
			String name, String contents) throws IOException {
		ObjectId oid;
		try (ObjectInserter inserter = repository.newObjectInserter()) {
			oid = inserter.insert(Constants.OBJ_BLOB,
					contents.getBytes(StandardCharsets.UTF_8));
			inserter.flush();
		}

		RefUpdate update = repository.updateRef(name);
		update.setNewObjectId(oid);
		update.setForceUpdate(true);
		RefUpdate.Result result = update.update();
		assertTrue(result == RefUpdate.Result.NEW
				|| result == RefUpdate.Result.FORCED
				|| result == RefUpdate.Result.NO_CHANGE);
		return oid;
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

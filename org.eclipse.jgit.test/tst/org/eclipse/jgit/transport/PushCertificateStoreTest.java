/*
 * Copyright (C) 2015, Google Inc.
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

import static org.eclipse.jgit.lib.ObjectId.zeroId;
import static org.eclipse.jgit.lib.RefUpdate.Result.FAST_FORWARD;
import static org.eclipse.jgit.lib.RefUpdate.Result.LOCK_FAILURE;
import static org.eclipse.jgit.lib.RefUpdate.Result.NEW;
import static org.eclipse.jgit.lib.RefUpdate.Result.NO_CHANGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

public class PushCertificateStoreTest {
	private static final ObjectId ID1 =
		ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");

	private static final ObjectId ID2 =
		ObjectId.fromString("badc0ffebadc0ffebadc0ffebadc0ffebadc0ffe");

	private static PushCertificate newCert(String... updateLines) {
		StringBuilder cert = new StringBuilder(
				"certificate version 0.1\n"
				+ "pusher Dave Borowitz <dborowitz@google.com> 1433954361 -0700\n"
				+ "pushee git://localhost/repo.git\n"
				+ "nonce 1433954361-bde756572d665bba81d8\n"
				+ "\n");
		for (String updateLine : updateLines) {
			cert.append(updateLine).append('\n');
		}
		cert.append(
				"-----BEGIN PGP SIGNATURE-----\n"
				+ "DUMMY/SIGNATURE\n"
				+ "-----END PGP SIGNATURE-----\n");
		try {
			return PushCertificateParser.fromReader(new InputStreamReader(
					new ByteArrayInputStream(Constants.encode(cert.toString()))));
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private static String command(ObjectId oldId, ObjectId newId, String ref) {
		return oldId.name() + " " + newId.name() + " " + ref;
	}

	private AtomicInteger ts = new AtomicInteger(1433954361);
	private InMemoryRepository repo;
	private PushCertificateStore store;

	@Before
	public void setUp() throws Exception {
		repo = new InMemoryRepository(new DfsRepositoryDescription("repo"));
		store = newStore();
	}

	@Test
	public void missingRef() throws Exception {
		assertCerts("refs/heads/master");
	}

	@Test
	public void saveNoChange() throws Exception {
		assertEquals(NO_CHANGE, store.save());
	}

	@Test
	public void saveOneCertOnOneRef() throws Exception {
		PersonIdent ident = newIdent();
		PushCertificate addMaster = newCert(
				command(zeroId(), ID1, "refs/heads/master"));
		store.put(addMaster, ident);
		assertEquals(NEW, store.save());
		assertCerts("refs/heads/master", addMaster);
		assertCerts("refs/heads/branch");

		try (RevWalk rw = new RevWalk(repo)) {
			RevCommit c = rw.parseCommit(repo.resolve(PushCertificateStore.REF_NAME));
			rw.parseBody(c);
			assertEquals("Store push certificate for refs/heads/master\n",
					c.getFullMessage());
			assertEquals(ident, c.getAuthorIdent());
			assertEquals(ident, c.getCommitterIdent());
		}
	}

	@Test
	public void saveTwoCertsOnSameRefInTwoUpdates() throws Exception {
		PushCertificate addMaster = newCert(
				command(zeroId(), ID1, "refs/heads/master"));
		store.put(addMaster, newIdent());
		assertEquals(NEW, store.save());
		PushCertificate updateMaster = newCert(
				command(ID1, ID2, "refs/heads/master"));
		store.put(updateMaster, newIdent());
		assertEquals(FAST_FORWARD, store.save());
		assertCerts("refs/heads/master", updateMaster, addMaster);
	}

	@Test
	public void saveTwoCertsOnSameRefInOneUpdate() throws Exception {
		PersonIdent ident1 = newIdent();
		PersonIdent ident2 = newIdent();
		PushCertificate updateMaster = newCert(
				command(ID1, ID2, "refs/heads/master"));
		store.put(updateMaster, ident2);
		PushCertificate addMaster = newCert(
				command(zeroId(), ID1, "refs/heads/master"));
		store.put(addMaster, ident1);
		assertEquals(NEW, store.save());
		assertCerts("refs/heads/master", updateMaster, addMaster);
	}

	@Test
	public void saveTwoCertsOnDifferentRefsInOneUpdate() throws Exception {
		PersonIdent ident1 = newIdent();
		PersonIdent ident3 = newIdent();
		PushCertificate addBranch = newCert(
				command(zeroId(), ID1, "refs/heads/branch"));
		store.put(addBranch, ident3);
		PushCertificate addMaster = newCert(
				command(zeroId(), ID1, "refs/heads/master"));
		store.put(addMaster, ident1);
		assertEquals(NEW, store.save());
		assertCerts("refs/heads/master", addMaster);
		assertCerts("refs/heads/branch", addBranch);
	}

	@Test
	public void saveTwoCertsOnDifferentRefsInTwoUpdates() throws Exception {
		PushCertificate addMaster = newCert(
				command(zeroId(), ID1, "refs/heads/master"));
		store.put(addMaster, newIdent());
		assertEquals(NEW, store.save());
		PushCertificate addBranch = newCert(
				command(zeroId(), ID1, "refs/heads/branch"));
		store.put(addBranch, newIdent());
		assertEquals(FAST_FORWARD, store.save());
		assertCerts("refs/heads/master", addMaster);
		assertCerts("refs/heads/branch", addBranch);
	}

	@Test
	public void saveOneCertOnMultipleRefs() throws Exception {
		PersonIdent ident = newIdent();
		PushCertificate addMasterAndBranch = newCert(
				command(zeroId(), ID1, "refs/heads/branch"),
				command(zeroId(), ID2, "refs/heads/master"));
		store.put(addMasterAndBranch, ident);
		assertEquals(NEW, store.save());
		assertCerts("refs/heads/master", addMasterAndBranch);
		assertCerts("refs/heads/branch", addMasterAndBranch);

		try (RevWalk rw = new RevWalk(repo)) {
			RevCommit c = rw.parseCommit(repo.resolve(PushCertificateStore.REF_NAME));
			rw.parseBody(c);
			assertEquals("Store push certificate for 2 refs\n", c.getFullMessage());
			assertEquals(ident, c.getAuthorIdent());
			assertEquals(ident, c.getCommitterIdent());
		}
	}

	@Test
	public void changeRefFileToDirectory() throws Exception {
		PushCertificate deleteRefsHeads = newCert(
				command(ID1, zeroId(), "refs/heads"));
		store.put(deleteRefsHeads, newIdent());
		PushCertificate addMaster = newCert(
				command(zeroId(), ID1, "refs/heads/master"));
		store.put(addMaster, newIdent());
		assertEquals(NEW, store.save());
		assertCerts("refs/heads", deleteRefsHeads);
		assertCerts("refs/heads/master", addMaster);
	}

	@Test
	public void getBeforeSaveDoesNotIncludePending() throws Exception {
		PushCertificate addMaster = newCert(
				command(zeroId(), ID1, "refs/heads/master"));
		store.put(addMaster, newIdent());
		assertEquals(NEW, store.save());

		PushCertificate updateMaster = newCert(
				command(ID1, ID2, "refs/heads/master"));
		store.put(updateMaster, newIdent());

		assertCerts("refs/heads/master", addMaster);
		assertEquals(FAST_FORWARD, store.save());
		assertCerts("refs/heads/master", updateMaster, addMaster);
	}

	@Test
	public void lockFailure() throws Exception {
		PushCertificateStore store1 = store;
		PushCertificateStore store2 = newStore();
		store2.get("refs/heads/master");

		PushCertificate addMaster = newCert(
				command(zeroId(), ID1, "refs/heads/master"));
		store1.put(addMaster, newIdent());
		assertEquals(NEW, store1.save());

		PushCertificate addBranch = newCert(
				command(zeroId(), ID2, "refs/heads/branch"));
		store2.put(addBranch, newIdent());

		assertEquals(LOCK_FAILURE, store2.save());
		// Reread ref after lock failure.
		assertCerts(store2, "refs/heads/master", addMaster);
		assertCerts(store2, "refs/heads/branch");

		assertEquals(FAST_FORWARD, store2.save());
		assertCerts(store2, "refs/heads/master", addMaster);
		assertCerts(store2, "refs/heads/branch", addBranch);
	}

	@Test
	public void saveInBatch() throws Exception {
		BatchRefUpdate batch = repo.getRefDatabase().newBatchUpdate();
		assertFalse(store.save(batch));
		assertEquals(0, batch.getCommands().size());
		PushCertificate addMaster = newCert(
				command(zeroId(), ID1, "refs/heads/master"));
		store.put(addMaster, newIdent());
		assertTrue(store.save(batch));

		List<ReceiveCommand> commands = batch.getCommands();
		assertEquals(1, commands.size());
		ReceiveCommand cmd = commands.get(0);
		assertEquals("refs/meta/push-certs", cmd.getRefName());
		assertEquals(ReceiveCommand.Result.NOT_ATTEMPTED, cmd.getResult());

		try (RevWalk rw = new RevWalk(repo)) {
			batch.execute(rw, NullProgressMonitor.INSTANCE);
			assertEquals(ReceiveCommand.Result.OK, cmd.getResult());
		}
	}

	@Test
	public void putMatchingWithNoMatchingRefs() throws Exception {
		PushCertificate addMaster = newCert(
				command(zeroId(), ID1, "refs/heads/master"),
				command(zeroId(), ID2, "refs/heads/branch"));
		store.put(addMaster, newIdent(), Collections.<ReceiveCommand> emptyList());
		assertEquals(NO_CHANGE, store.save());
	}

	@Test
	public void putMatchingWithNoMatchingRefsInBatchOnEmptyRef()
			throws Exception {
		PushCertificate addMaster = newCert(
				command(zeroId(), ID1, "refs/heads/master"),
				command(zeroId(), ID2, "refs/heads/branch"));
		store.put(addMaster, newIdent(), Collections.<ReceiveCommand> emptyList());
		BatchRefUpdate batch = repo.getRefDatabase().newBatchUpdate();
		assertFalse(store.save(batch));
		assertEquals(0, batch.getCommands().size());
	}

	@Test
	public void putMatchingWithNoMatchingRefsInBatchOnNonEmptyRef()
			throws Exception {
		PushCertificate addMaster = newCert(
				command(zeroId(), ID1, "refs/heads/master"));
		store.put(addMaster, newIdent());
		assertEquals(NEW, store.save());

		PushCertificate addBranch = newCert(
				command(zeroId(), ID2, "refs/heads/branch"));
		store.put(addBranch, newIdent(), Collections.<ReceiveCommand> emptyList());
		BatchRefUpdate batch = repo.getRefDatabase().newBatchUpdate();
		assertFalse(store.save(batch));
		assertEquals(0, batch.getCommands().size());
	}

	@Test
	public void putMatchingWithSomeMatchingRefs() throws Exception {
		PushCertificate addMasterAndBranch = newCert(
				command(zeroId(), ID1, "refs/heads/master"),
				command(zeroId(), ID2, "refs/heads/branch"));
		store.put(addMasterAndBranch, newIdent(),
				Collections.singleton(addMasterAndBranch.getCommands().get(0)));
		assertEquals(NEW, store.save());
		assertCerts("refs/heads/master", addMasterAndBranch);
		assertCerts("refs/heads/branch");
	}

	private PersonIdent newIdent() {
		return new PersonIdent(
				"A U. Thor", "author@example.com", ts.getAndIncrement(), 0);
	}

	private PushCertificateStore newStore() {
		return new PushCertificateStore(repo);
	}

	private void assertCerts(String refName, PushCertificate... expected)
			throws Exception {
		assertCerts(store, refName, expected);
		assertCerts(newStore(), refName, expected);
	}

	private static void assertCerts(PushCertificateStore store, String refName,
			PushCertificate... expected) throws Exception {
		List<PushCertificate> ex = Arrays.asList(expected);
		PushCertificate first = !ex.isEmpty() ? ex.get(0) : null;
		assertEquals(first, store.get(refName));
		assertEquals(ex, toList(store.getAll(refName)));
	}

	private static <T> List<T> toList(Iterable<T> it) {
		List<T> list = new ArrayList<>();
		for (T t : it) {
			list.add(t);
		}
		return list;
	}
}

/*
 * Copyright (C) 2012, Christian Halstrick <christian.halstrick@sap.com>
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

package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

import org.eclipse.jgit.junit.TestRepository.BranchBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class GcBasicPackingTest extends GcTestCase {
	@Test
	public void repackEmptyRepo_noPackCreated() throws IOException {
		gc.repack();
		assertEquals(0, repo.getObjectDatabase().getPacks().size());
	}

	@Test
	public void testPackRepoWithNoRefs() throws Exception {
		tr.commit().add("A", "A").add("B", "B").create();
		stats = gc.getStatistics();
		assertEquals(4, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(4, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		assertEquals(0, stats.numberOfPackFiles);
	}

	@Test
	public void testPack2Commits() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();

		stats = gc.getStatistics();
		assertEquals(8, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(8, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
	}

	@Test
	public void testPackAllObjectsInOnePack() throws Exception {
		tr.branch("refs/heads/master").commit().add("A", "A").add("B", "B")
				.create();
		stats = gc.getStatistics();
		assertEquals(4, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(4, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);

		// Do the gc again and check that it hasn't changed anything
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(4, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
	}

	@Test
	public void testPackCommitsAndLooseOne() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		RevCommit first = bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();
		tr.update("refs/heads/master", first);

		stats = gc.getStatistics();
		assertEquals(8, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(8, stats.numberOfPackedObjects);
		assertEquals(2, stats.numberOfPackFiles);
	}

	@Test
	public void testNotPackTwice() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		RevCommit first = bb.commit().message("M").add("M", "M").create();
		bb.commit().message("B").add("B", "Q").create();
		bb.commit().message("A").add("A", "A").create();
		RevCommit second = tr.commit().parent(first).message("R").add("R", "Q")
				.create();
		tr.update("refs/tags/t1", second);

		Collection<PackFile> oldPacks = tr.getRepository().getObjectDatabase()
				.getPacks();
		assertEquals(0, oldPacks.size());
		stats = gc.getStatistics();
		assertEquals(11, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);

		gc.setExpireAgeMillis(0);
		fsTick();
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);

		Iterator<PackFile> pIt = repo.getObjectDatabase().getPacks().iterator();
		long c = pIt.next().getObjectCount();
		if (c == 9)
			assertEquals(2, pIt.next().getObjectCount());
		else {
			assertEquals(2, c);
			assertEquals(9, pIt.next().getObjectCount());
		}
	}
}

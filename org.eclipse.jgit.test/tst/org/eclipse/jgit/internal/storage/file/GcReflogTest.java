/*
 * Copyright (C) 2012, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Collections;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.TestRepository.BranchBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Test;

public class GcReflogTest extends GcTestCase {
	@Test
	public void testPruneNone() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();
		new File(repo.getDirectory(), Constants.LOGS + "/refs/heads/master")
				.delete();
		stats = gc.getStatistics();
		assertEquals(8, stats.numberOfLooseObjects);
		gc.setExpireAgeMillis(0);
		fsTick();
		gc.prune(Collections.<ObjectId> emptySet());
		stats = gc.getStatistics();
		assertEquals(8, stats.numberOfLooseObjects);
		tr.blob("x");
		stats = gc.getStatistics();
		assertEquals(9, stats.numberOfLooseObjects);
		fsTick();
		gc.prune(Collections.<ObjectId> emptySet());
		stats = gc.getStatistics();
		assertEquals(8, stats.numberOfLooseObjects);
	}

	@Test
	public void testPackRepoWithCorruptReflog() throws Exception {
		// create a reflog entry "0000... 0000... foobar" by doing an initial
		// refupdate for HEAD which points to a non-existing ref. The
		// All-Projects repo of gerrit instances had such entries
		RefUpdate ru = repo.updateRef(Constants.HEAD);
		ru.link("refs/to/garbage");
		tr.branch("refs/heads/master").commit().add("A", "A").add("B", "B")
				.create();
		// make sure HEAD exists
		Git.wrap(repo).checkout().setName("refs/heads/master").call();
		gc.gc();
	}

	@Test
	public void testPackCommitsAndLooseOneNoReflog() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		RevCommit first = bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();
		tr.update("refs/heads/master", first);

		stats = gc.getStatistics();
		assertEquals(8, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);

		FileUtils.delete(new File(repo.getDirectory(), "logs/HEAD"),
				FileUtils.RETRY | FileUtils.SKIP_MISSING);
		FileUtils.delete(
				new File(repo.getDirectory(), "logs/refs/heads/master"),
				FileUtils.RETRY | FileUtils.SKIP_MISSING);
		gc.gc();

		stats = gc.getStatistics();
		assertEquals(4, stats.numberOfLooseObjects);
		assertEquals(4, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
	}

	@Test
	public void testPackCommitsAndLooseOneWithPruneNowNoReflog()
			throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		RevCommit first = bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();
		tr.update("refs/heads/master", first);

		stats = gc.getStatistics();
		assertEquals(8, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);

		FileUtils.delete(new File(repo.getDirectory(), "logs/HEAD"),
				FileUtils.RETRY | FileUtils.SKIP_MISSING);
		FileUtils.delete(
				new File(repo.getDirectory(), "logs/refs/heads/master"),
				FileUtils.RETRY | FileUtils.SKIP_MISSING);
		gc.setExpireAgeMillis(0);
		gc.gc();

		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(4, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
	}
}

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
package org.eclipse.jgit.storage.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRepository.BranchBuilder;
import org.eclipse.jgit.junit.TestRepository.CommitBuilder;
import org.eclipse.jgit.lib.EmptyProgressMonitor;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.storage.file.PackIndex.MutableEntry;
import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GCTest extends LocalDiskRepositoryTestCase {
	private TestRepository<FileRepository> tr;

	private FileRepository repo;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		repo = createWorkRepository();
		tr = new TestRepository<FileRepository>((repo));
	}

	@After
	public void tearDown() throws Exception {
		super.tearDown();
	}

	// GC.packRefs tests

	@Test
	public void packRefs_looseRefPacked() throws Exception {
		RevBlob a = tr.blob("a");
		tr.lightweightTag("t", a);

		GC.packRefs(NullProgressMonitor.INSTANCE, repo);
		assertSame(repo.getRef("t").getStorage(), Storage.PACKED);
	}

	@Test
	public void concurrentPackRefs_onlyOneWritesPackedRefs() throws Exception {
		RevBlob a = tr.blob("a");
		tr.lightweightTag("t", a);

		final CyclicBarrier syncPoint = new CyclicBarrier(2);

		Callable<Integer> packRefs = new Callable<Integer>() {

			/** @return 0 for success, 1 in case of error when writing pack */
			public Integer call() throws Exception {
				syncPoint.await();
				try {
					GC.packRefs(NullProgressMonitor.INSTANCE, repo);
					return 0;
				} catch (IOException e) {
					return 1;
				}
			}
		};
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<Integer> p1 = pool.submit(packRefs);
			Future<Integer> p2 = pool.submit(packRefs);
			assertTrue(p1.get() + p2.get() == 1);
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
		}
	}

	@Test
	public void packRefsWhileRefLocked_refNotPackedNoError()
			throws Exception {
		RevBlob a = tr.blob("a");
		tr.lightweightTag("t1", a);
		tr.lightweightTag("t2", a);
		LockFile refLock = new LockFile(new File(repo.getDirectory(),
				"refs/tags/t1"), repo.getFS());
		try {
			refLock.lock();
			GC.packRefs(NullProgressMonitor.INSTANCE, repo);
		} finally {
			refLock.unlock();
		}

		assertSame(repo.getRef("refs/tags/t1").getStorage(), Storage.LOOSE);
		assertSame(repo.getRef("refs/tags/t2").getStorage(), Storage.PACKED);
	}

	@Test
	public void packRefsWhileRefUpdated_refUpdateSucceeds()
			throws Exception {
		RevBlob a = tr.blob("a");
		tr.lightweightTag("t", a);
		final RevBlob b = tr.blob("b");

		final CyclicBarrier refUpdateLockedRef = new CyclicBarrier(2);
		final CyclicBarrier packRefsDone = new CyclicBarrier(2);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<Result> result = pool.submit(new Callable<Result>() {

				public Result call() throws Exception {
					RefUpdate update = new RefDirectoryUpdate(
							(RefDirectory) repo.getRefDatabase(),
							repo.getRef("refs/tags/t")) {
						@Override
						public boolean isForceUpdate() {
							try {
								refUpdateLockedRef.await();
								packRefsDone.await();
							} catch (InterruptedException e) {
								Thread.currentThread().interrupt();
							} catch (BrokenBarrierException e) {
								Thread.currentThread().interrupt();
							}
							return super.isForceUpdate();
						}
					};
					update.setForceUpdate(true);
					update.setNewObjectId(b);
					return update.update();
				}
			});

			pool.submit(new Callable<Void>() {
				public Void call() throws Exception {
					refUpdateLockedRef.await();
					GC.packRefs(NullProgressMonitor.INSTANCE, repo);
					packRefsDone.await();
					return null;
				}
			});

			assertSame(result.get(), Result.FORCED);

		} finally {
			pool.shutdownNow();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
		}

		assertEquals(repo.getRef("refs/tags/t").getObjectId(), b);
	}

	// GC.repack tests

	@Test
	public void repackEmptyRepo_noPackCreated() throws IOException {
		GC.repack(null, repo);
		assertEquals(0, repo.getObjectDatabase().getPacks().size());
	}

	@Test
	public void concurrentRepack_onlyOneWritesPack() throws Exception {
		final CyclicBarrier syncPoint = new CyclicBarrier(2);

		class DoRepack extends EmptyProgressMonitor implements
				Callable<Integer> {

			public void beginTask(String title, int totalWork) {
				if (title.equals(JGitText.get().writingObjects)) {
					try {
						syncPoint.await();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					} catch (BrokenBarrierException e) {
						Thread.currentThread().interrupt();
					}
				}
			}

			/** @return 0 for success, 1 in case of error when writing pack */
			public Integer call() throws Exception {
				try {
					GC.repack(this, repo);
					return 0;
				} catch (IOException e) {
					// leave the syncPoint in broken state so any awaiting
					// threads and any threads that call await in the future get
					// the BrokenBarrierException
					Thread.currentThread().interrupt();
					try {
						syncPoint.await();
					} catch (InterruptedException ignored) {
						//
					}
					return 1;
				}
			}
		}

		RevBlob a = tr.blob("a");
		tr.lightweightTag("t", a);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			DoRepack repack1 = new DoRepack();
			DoRepack repack2 = new DoRepack();
			Future<Integer> result1 = pool.submit(repack1);
			Future<Integer> result2 = pool.submit(repack2);
			assertTrue(result1.get() + result2.get() == 1);
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
		}
	}

	// GC.prune tests

	@Test
	public void nonReferencedNonExpiredObject_notPruned() throws Exception {
		RevBlob a = tr.blob("a");
		GC.prune(null, repo, Collections.<ObjectId> emptySet(), 1);
		assertTrue(repo.hasObject(a));
	}

	@Test
	public void nonReferencedExiredObject_pruned() throws Exception {
		RevBlob a = tr.blob("a");
		GC.prune(null, repo, Collections.<ObjectId> emptySet(), 0);
		assertFalse(repo.hasObject(a));
	}

	@Test
	public void nonReferencedExpiredObjectTree_pruned() throws Exception {
		RevBlob a = tr.blob("a");
		RevTree t = tr.tree(tr.file("a", a));
		GC.prune(null, repo, Collections.<ObjectId> emptySet(), 0);
		assertFalse(repo.hasObject(t));
		assertFalse(repo.hasObject(a));
	}

	@Test
	public void lightweightTag_objectNotPruned() throws Exception {
		RevBlob a = tr.blob("a");
		tr.lightweightTag("t", a);
		GC.prune(null, repo, Collections.<ObjectId> emptySet(), 0);
		assertTrue(repo.hasObject(a));
	}

	@Test
	public void annotatedTag_objectNotPruned() throws Exception {
		RevBlob a = tr.blob("a");
		RevTag t = tr.tag("t", a); // this doesn't create the refs/tags/t ref
		tr.lightweightTag("t", t);

		GC.prune(null, repo, Collections.<ObjectId> emptySet(), 0);
		assertTrue(repo.hasObject(t));
		assertTrue(repo.hasObject(a));
	}

	@Test
	public void branch_historyNotPruned() throws Exception {
		RevCommit tip = commitChain(10);
		tr.branch("b").update(tip);
		GC.prune(null, repo, Collections.<ObjectId> emptySet(), 0);
		do {
			assertTrue(repo.hasObject(tip));
			tr.parseBody(tip);
			RevTree t = tip.getTree();
			assertTrue(repo.hasObject(t));
			assertTrue(repo.hasObject(tr.get(t, "a")));
			tip = tip.getParentCount() > 0 ? tip.getParent(0) : null;
		} while (tip != null);
	}

	@Test
	public void deleteBranch_historyPruned() throws Exception {
		RevCommit tip = commitChain(10);
		tr.branch("b").update(tip);
		RefUpdate update = repo.updateRef("refs/heads/b");
		update.setForceUpdate(true);
		update.delete();
		// TODO: expire reflogs
		GC.prune(null, repo, Collections.<ObjectId> emptySet(), 0);
		assertEquals(0, looseObjectIDs().size());
	}

	@Test
	public void deleteMergedBranch_historyNotPruned() throws Exception {
		RevCommit parent = tr.commit().create();
		RevCommit b1Tip = tr.branch("b1").commit().parent(parent).add("x", "x")
				.create();
		RevCommit b2Tip = tr.branch("b2").commit().parent(parent).add("y", "y")
				.create();

		// merge b1Tip and b2Tip and update refs/heads/b1 to the merge commit
		Merger merger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(repo);
		merger.merge(b1Tip, b2Tip);
		CommitBuilder cb = tr.commit();
		cb.parent(b1Tip).parent(b2Tip);
		cb.setTopLevelTree(merger.getResultTreeId());
		RevCommit mergeCommit = cb.create();
		RefUpdate u = repo.updateRef("refs/heads/b1");
		u.setNewObjectId(mergeCommit);
		u.update();

		RefUpdate update = repo.updateRef("refs/heads/b2");
		update.setForceUpdate(true);
		update.delete();

		GC.prune(null, repo, Collections.<ObjectId> emptySet(), 0);
		assertTrue(repo.hasObject(b2Tip));
	}

	// GC.gc tests

	// //


	@Test
	public void testPackAllObjectsInOnePack() throws Exception {
		tr.branch("refs/heads/master").commit().add("A", "A").add("B", "B")
				.create();
		assertEquals(4, looseObjectIDs().size());
		assertEquals(0, packedObjectIDs().size());
		GC.gc(null, repo, 14);
		assertEquals(0, looseObjectIDs().size());
		assertEquals(4, packedObjectIDs().size());
		assertEquals(1, repo.getObjectDatabase().getPacks().size());
	}

	@Test
	public void testPackRepoWithNoRefs() throws Exception {
		tr.commit().add("A", "A").add("B", "B").create();
		assertEquals(4, looseObjectIDs().size());
		assertEquals(0, packedObjectIDs().size());
		GC.gc(null, repo, 14);
		assertEquals(4, looseObjectIDs().size());
		assertEquals(0, packedObjectIDs().size());
		assertEquals(0, repo.getObjectDatabase().getPacks().size());
	}

	@Test
	public void testPack2Commits() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();

		assertEquals(8, looseObjectIDs().size());
		assertEquals(0, packedObjectIDs().size());
		GC.gc(null, repo, 14);
		assertEquals(0, looseObjectIDs().size());
		assertEquals(8, packedObjectIDs().size());
		assertEquals(1, repo.getObjectDatabase().getPacks().size());
	}

	@Test
	public void testPackCommitsAndLooseOne() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		RevCommit first = bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();
		tr.update("refs/heads/master", first);

		assertEquals(8, looseObjectIDs().size());
		assertEquals(0, packedObjectIDs().size());
		GC.gc(null, repo, 14);
		assertEquals(0, looseObjectIDs().size());
		assertEquals(8, packedObjectIDs().size());
		assertEquals(2, repo.getObjectDatabase().getPacks().size());
	}

	@Test
	public void testPackCommitsAndLooseOneNoReflog() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		RevCommit first = bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();
		tr.update("refs/heads/master", first);

		assertEquals(8, looseObjectIDs().size());
		assertEquals(0, packedObjectIDs().size());

		FileUtils.delete(new File(repo.getDirectory(), "logs/HEAD"),
				FileUtils.RETRY | FileUtils.SKIP_MISSING);
		FileUtils.delete(
				new File(repo.getDirectory(), "logs/refs/heads/master"),
				FileUtils.RETRY | FileUtils.SKIP_MISSING);
		GC.gc(null, repo, 14);

		assertEquals(4, looseObjectIDs().size());
		assertEquals(4, packedObjectIDs().size());
		assertEquals(1, repo.getObjectDatabase().getPacks().size());
	}

	@Test
	public void testPackCommitsAndLooseOneWithPruneNow() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		RevCommit first = bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();
		tr.update("refs/heads/master", first);

		assertEquals(8, looseObjectIDs().size());
		assertEquals(0, packedObjectIDs().size());
		GC.gc(null, repo, 0);
		assertEquals(0, looseObjectIDs().size());
		assertEquals(8, packedObjectIDs().size());
		assertEquals(2, repo.getObjectDatabase().getPacks().size());
	}

	@Test
	public void testPackCommitsAndLooseOneWithPruneNowNoReflog()
			throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		RevCommit first = bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();
		tr.update("refs/heads/master", first);

		assertEquals(8, looseObjectIDs().size());
		assertEquals(0, packedObjectIDs().size());

		FileUtils.delete(new File(repo.getDirectory(), "logs/HEAD"),
				FileUtils.RETRY | FileUtils.SKIP_MISSING);
		FileUtils.delete(
				new File(repo.getDirectory(), "logs/refs/heads/master"),
				FileUtils.RETRY | FileUtils.SKIP_MISSING);
		GC.gc(null, repo, 0);

		assertEquals(0, looseObjectIDs().size());
		assertEquals(4, packedObjectIDs().size());
		assertEquals(1, repo.getObjectDatabase().getPacks().size());
	}

	@Test
	public void testIndexSavesObjects() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();
		bb.commit().add("A", "A3"); // this new content in index should survive
		assertEquals(9, looseObjectIDs().size());
		assertEquals(0, packedObjectIDs().size());
		GC.gc(null, repo, 14);
		assertEquals(1, looseObjectIDs().size());
		assertEquals(8, packedObjectIDs().size());
		assertEquals(1, repo.getObjectDatabase().getPacks().size());
	}

	@Test
	public void testIndexSavesObjectsWithPruneNow() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();
		bb.commit().add("A", "A3"); // this new content in index should survive
		assertEquals(9, looseObjectIDs().size());
		assertEquals(0, packedObjectIDs().size());
		GC.gc(null, repo, 0);
		assertEquals(0, looseObjectIDs().size());
		assertEquals(8, packedObjectIDs().size());
		assertEquals(1, repo.getObjectDatabase().getPacks().size());
	}

	public Set<String> looseObjectIDs() {
		Set<String> ret = new HashSet<String>();
		for (File parent : repo.getObjectsDirectory().listFiles())
			if (parent.getName().matches("[0-9a-fA-F]{2}"))
				for (File obj : parent.listFiles())
					if (obj.getName().matches("[0-9a-fA-F]{38}"))
						ret.add(parent.getName() + obj.getName());
		return ret;
	}

	/**
	 * Create a chain of commits of given depth.
	 * <p>
	 * Each commit contains one file named "a" containing the index of the
	 * commit in the chain as its content. The created commit chain is
	 * referenced from any ref.
	 * <p>
	 * A chain of depth = N will create 3*N objects in Gits object database. For
	 * each depth level three objects are created: the commit object, the
	 * top-level tree object and a blob for the content of the file "a".
	 *
	 * @param depth
	 *            the depth of the commit chain.
	 * @return the commit that is the tip of the commit chain
	 * @throws Exception
	 */
	private RevCommit commitChain(int depth) throws Exception {
		if (depth <= 0)
			throw new IllegalArgumentException("Chain depth must be > 0");
		CommitBuilder cb = tr.commit();
		RevCommit tip;
		do {
			--depth;
			tip = cb.add("a", "" + depth).message("" + depth).create();
			cb = cb.child();
		} while (depth > 0);
		return tip;
	}

	public Set<String> packedObjectIDs() throws IOException {
		Set<String> ret = new HashSet<String>();
		repo.scanForRepoChanges();
		Iterator<PackFile> pi = repo.getObjectDatabase().getPacks().iterator();
		while (pi.hasNext()) {
			PackFile pf = pi.next();
			Iterator<MutableEntry> ei = pf.iterator();
			while (ei.hasNext()) {
				MutableEntry enty = ei.next();
				ret.add(enty.name());
			}
			pf.close();
		}
		return ret;
	}
}

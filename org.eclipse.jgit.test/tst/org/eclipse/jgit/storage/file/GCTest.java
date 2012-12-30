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

import static java.lang.Integer.valueOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collection;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRepository.BranchBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.junit.TestRepository.CommitBuilder;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.EmptyProgressMonitor;
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
import org.eclipse.jgit.storage.file.GC.RepoStatistics;
import org.eclipse.jgit.storage.file.PackIndex.MutableEntry;
import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GCTest extends LocalDiskRepositoryTestCase {
	private TestRepository<FileRepository> tr;

	private FileRepository repo;

	private GC gc;

	private RepoStatistics stats;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		repo = createWorkRepository();
		tr = new TestRepository<FileRepository>((repo));
		gc = new GC(repo);
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

		gc.packRefs();
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
					gc.packRefs();
					return valueOf(0);
				} catch (IOException e) {
					return valueOf(1);
				}
			}
		};
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<Integer> p1 = pool.submit(packRefs);
			Future<Integer> p2 = pool.submit(packRefs);
			assertEquals(1, p1.get().intValue() + p2.get().intValue());
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
			gc.packRefs();
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
					gc.packRefs();
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
		gc.repack();
		assertEquals(0, repo.getObjectDatabase().getPacks().size());
	}

	@Test
	public void concurrentRepack() throws Exception {
		final CyclicBarrier syncPoint = new CyclicBarrier(2);

		class DoRepack extends EmptyProgressMonitor implements
				Callable<Integer> {

			public void beginTask(String title, int totalWork) {
				if (title.equals(JGitText.get().writingObjects)) {
					try {
						syncPoint.await();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					} catch (BrokenBarrierException ignored) {
						//
					}
				}
			}

			/** @return 0 for success, 1 in case of error when writing pack */
			public Integer call() throws Exception {
				try {
					gc.setProgressMonitor(this);
					gc.repack();
					return valueOf(0);
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
					return valueOf(1);
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
			assertEquals(0, result1.get().intValue() + result2.get().intValue());
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
		}
	}

	// GC.prune tests

	@Test
	public void nonReferencedNonExpiredObject_notPruned() throws Exception {
		RevBlob a = tr.blob("a");
		gc.setExpire(new Date(lastModified(a)));
		gc.prune(Collections.<ObjectId> emptySet());
		assertTrue(repo.hasObject(a));
	}

	@Test
	public void nonReferencedExpiredObject_pruned() throws Exception {
		RevBlob a = tr.blob("a");
		gc.setExpireAgeMillis(0);
		gc.prune(Collections.<ObjectId> emptySet());
		assertFalse(repo.hasObject(a));
	}

	@Test
	public void nonReferencedExpiredObjectTree_pruned() throws Exception {
		RevBlob a = tr.blob("a");
		RevTree t = tr.tree(tr.file("a", a));
		gc.setExpireAgeMillis(0);
		gc.prune(Collections.<ObjectId> emptySet());
		assertFalse(repo.hasObject(t));
		assertFalse(repo.hasObject(a));
	}

	@Test
	public void nonReferencedObjects_onlyExpiredPruned() throws Exception {
		RevBlob a = tr.blob("a");
		gc.setExpire(new Date(lastModified(a) + 1));

		fsTick();
		RevBlob b = tr.blob("b");

		gc.prune(Collections.<ObjectId> emptySet());
		assertFalse(repo.hasObject(a));
		assertTrue(repo.hasObject(b));
	}

	@Test
	public void lightweightTag_objectNotPruned() throws Exception {
		RevBlob a = tr.blob("a");
		tr.lightweightTag("t", a);
		gc.setExpireAgeMillis(0);
		gc.prune(Collections.<ObjectId> emptySet());
		assertTrue(repo.hasObject(a));
	}

	@Test
	public void annotatedTag_objectNotPruned() throws Exception {
		RevBlob a = tr.blob("a");
		RevTag t = tr.tag("t", a); // this doesn't create the refs/tags/t ref
		tr.lightweightTag("t", t);

		gc.setExpireAgeMillis(0);
		gc.prune(Collections.<ObjectId> emptySet());
		assertTrue(repo.hasObject(t));
		assertTrue(repo.hasObject(a));
	}

	@Test
	public void branch_historyNotPruned() throws Exception {
		RevCommit tip = commitChain(10);
		tr.branch("b").update(tip);
		gc.setExpireAgeMillis(0);
		gc.prune(Collections.<ObjectId> emptySet());
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
		gc.setExpireAgeMillis(0);
		gc.prune(Collections.<ObjectId> emptySet());
		assertTrue(gc.getStatistics().numberOfLooseObjects == 0);
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

		gc.setExpireAgeMillis(0);
		gc.prune(Collections.<ObjectId> emptySet());
		assertTrue(repo.hasObject(b2Tip));
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
	}

	@Test
	public void testKeepFiles() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().add("A", "A").add("B", "B").create();
		stats = gc.getStatistics();
		assertEquals(4, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		assertEquals(0, stats.numberOfPackFiles);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(4, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);

		Iterator<PackFile> packIt = repo.getObjectDatabase().getPacks()
				.iterator();
		PackFile singlePack = packIt.next();
		assertFalse(packIt.hasNext());
		File keepFile = new File(singlePack.getPackFile().getPath() + ".keep");
		assertFalse(keepFile.exists());
		assertTrue(keepFile.createNewFile());
		bb.commit().add("A", "A2").add("B", "B2").create();
		stats = gc.getStatistics();
		assertEquals(4, stats.numberOfLooseObjects);
		assertEquals(4, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(8, stats.numberOfPackedObjects);
		assertEquals(2, stats.numberOfPackFiles);

		// check that no object is packed twice
		Iterator<PackFile> packs = repo.getObjectDatabase().getPacks()
				.iterator();
		PackIndex ind1 = packs.next().getIndex();
		assertEquals(4, ind1.getObjectCount());
		PackIndex ind2 = packs.next().getIndex();
		assertEquals(4, ind2.getObjectCount());
		for (MutableEntry e: ind1)
			if (ind2.hasObject(e.toObjectId()))
			assertFalse(
					"the following object is in both packfiles: "
							+ e.toObjectId(), ind2.hasObject(e.toObjectId()));
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
	public void testPackCommitsAndLooseOneWithPruneNow() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		RevCommit first = bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();
		tr.update("refs/heads/master", first);

		stats = gc.getStatistics();
		assertEquals(8, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		gc.setExpireAgeMillis(0);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(8, stats.numberOfPackedObjects);
		assertEquals(2, stats.numberOfPackFiles);
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

	@Test
	public void testIndexSavesObjects() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();
		bb.commit().add("A", "A3"); // this new content in index should survive
		stats = gc.getStatistics();
		assertEquals(9, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(1, stats.numberOfLooseObjects);
		assertEquals(8, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
	}

	@Test
	public void testIndexSavesObjectsWithPruneNow() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();
		bb.commit().add("A", "A3"); // this new content in index should survive
		stats = gc.getStatistics();
		assertEquals(9, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		gc.setExpireAgeMillis(0);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(8, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
	}

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
		gc.prune(Collections.<ObjectId> emptySet());
		stats = gc.getStatistics();
		assertEquals(8, stats.numberOfLooseObjects);
		tr.blob("x");
		stats = gc.getStatistics();
		assertEquals(9, stats.numberOfLooseObjects);
		gc.prune(Collections.<ObjectId> emptySet());
		stats = gc.getStatistics();
		assertEquals(8, stats.numberOfLooseObjects);
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

	private long lastModified(AnyObjectId objectId) {
		return repo.getObjectDatabase().fileFor(objectId).lastModified();
	}

	private static void fsTick() throws InterruptedException, IOException {
		RepositoryTestCase.fsTick(null);
	}
}

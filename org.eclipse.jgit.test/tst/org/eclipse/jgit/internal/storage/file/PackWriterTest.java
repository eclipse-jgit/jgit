/*
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
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

import static org.eclipse.jgit.internal.storage.pack.PackWriter.NONE;
import static org.eclipse.jgit.lib.Constants.INFO_ALTERNATES;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.file.PackIndex.MutableEntry;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRepository.BranchBuilder;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdSet;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.Sets;
import org.eclipse.jgit.revwalk.DepthWalk;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.storage.pack.PackStatistics;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.eclipse.jgit.transport.PackParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PackWriterTest extends SampleDataRepositoryTestCase {

	private static final List<RevObject> EMPTY_LIST_REVS = Collections
			.<RevObject> emptyList();

	private static final Set<ObjectIdSet> EMPTY_ID_SET = Collections
			.<ObjectIdSet> emptySet();

	private PackConfig config;

	private PackWriter writer;

	private ByteArrayOutputStream os;

	private PackFile pack;

	private ObjectInserter inserter;

	private FileRepository dst;

	private RevBlob contentA;

	private RevBlob contentB;

	private RevBlob contentC;

	private RevBlob contentD;

	private RevBlob contentE;

	private RevCommit c1;

	private RevCommit c2;

	private RevCommit c3;

	private RevCommit c4;

	private RevCommit c5;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		os = new ByteArrayOutputStream();
		config = new PackConfig(db);

		dst = createBareRepository();
		File alt = new File(dst.getObjectDatabase().getDirectory(), INFO_ALTERNATES);
		alt.getParentFile().mkdirs();
		write(alt, db.getObjectDatabase().getDirectory().getAbsolutePath() + "\n");
	}

	@Override
	@After
	public void tearDown() throws Exception {
		if (writer != null) {
			writer.close();
			writer = null;
		}
		if (inserter != null) {
			inserter.close();
			inserter = null;
		}
		super.tearDown();
	}

	/**
	 * Test constructor for exceptions, default settings, initialization.
	 *
	 * @throws IOException
	 */
	@Test
	public void testContructor() throws IOException {
		writer = new PackWriter(config, db.newObjectReader());
		assertFalse(writer.isDeltaBaseAsOffset());
		assertTrue(config.isReuseDeltas());
		assertTrue(config.isReuseObjects());
		assertEquals(0, writer.getObjectCount());
	}

	/**
	 * Change default settings and verify them.
	 */
	@Test
	public void testModifySettings() {
		config.setReuseDeltas(false);
		config.setReuseObjects(false);
		config.setDeltaBaseAsOffset(false);
		assertFalse(config.isReuseDeltas());
		assertFalse(config.isReuseObjects());
		assertFalse(config.isDeltaBaseAsOffset());

		writer = new PackWriter(config, db.newObjectReader());
		writer.setDeltaBaseAsOffset(true);
		assertTrue(writer.isDeltaBaseAsOffset());
		assertFalse(config.isDeltaBaseAsOffset());
	}

	/**
	 * Write empty pack by providing empty sets of interesting/uninteresting
	 * objects and check for correct format.
	 *
	 * @throws IOException
	 */
	@Test
	public void testWriteEmptyPack1() throws IOException {
		createVerifyOpenPack(NONE, NONE, false, false);

		assertEquals(0, writer.getObjectCount());
		assertEquals(0, pack.getObjectCount());
		assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", writer
				.computeName().name());
	}

	/**
	 * Write empty pack by providing empty iterator of objects to write and
	 * check for correct format.
	 *
	 * @throws IOException
	 */
	@Test
	public void testWriteEmptyPack2() throws IOException {
		createVerifyOpenPack(EMPTY_LIST_REVS);

		assertEquals(0, writer.getObjectCount());
		assertEquals(0, pack.getObjectCount());
	}

	/**
	 * Try to pass non-existing object as uninteresting, with non-ignoring
	 * setting.
	 *
	 * @throws IOException
	 */
	@Test
	public void testNotIgnoreNonExistingObjects() throws IOException {
		final ObjectId nonExisting = ObjectId
				.fromString("0000000000000000000000000000000000000001");
		try {
			createVerifyOpenPack(NONE, haves(nonExisting), false, false);
			fail("Should have thrown MissingObjectException");
		} catch (MissingObjectException x) {
			// expected
		}
	}

	/**
	 * Try to pass non-existing object as uninteresting, with ignoring setting.
	 *
	 * @throws IOException
	 */
	@Test
	public void testIgnoreNonExistingObjects() throws IOException {
		final ObjectId nonExisting = ObjectId
				.fromString("0000000000000000000000000000000000000001");
		createVerifyOpenPack(NONE, haves(nonExisting), false, true);
		// shouldn't throw anything
	}

	/**
	 * Try to pass non-existing object as uninteresting, with ignoring setting.
	 * Use a repo with bitmap indexes because then PackWriter will use
	 * PackWriterBitmapWalker which had problems with this situation.
	 *
	 * @throws IOException
	 * @throws ParseException
	 */
	@Test
	public void testIgnoreNonExistingObjectsWithBitmaps() throws IOException,
			ParseException {
		final ObjectId nonExisting = ObjectId
				.fromString("0000000000000000000000000000000000000001");
		new GC(db).gc();
		createVerifyOpenPack(NONE, haves(nonExisting), false, true, true);
		// shouldn't throw anything
	}

	/**
	 * Create pack basing on only interesting objects, then precisely verify
	 * content. No delta reuse here.
	 *
	 * @throws IOException
	 */
	@Test
	public void testWritePack1() throws IOException {
		config.setReuseDeltas(false);
		writeVerifyPack1();
	}

	/**
	 * Test writing pack without object reuse. Pack content/preparation as in
	 * {@link #testWritePack1()}.
	 *
	 * @throws IOException
	 */
	@Test
	public void testWritePack1NoObjectReuse() throws IOException {
		config.setReuseDeltas(false);
		config.setReuseObjects(false);
		writeVerifyPack1();
	}

	/**
	 * Create pack basing on both interesting and uninteresting objects, then
	 * precisely verify content. No delta reuse here.
	 *
	 * @throws IOException
	 */
	@Test
	public void testWritePack2() throws IOException {
		writeVerifyPack2(false);
	}

	/**
	 * Test pack writing with deltas reuse, delta-base first rule. Pack
	 * content/preparation as in {@link #testWritePack2()}.
	 *
	 * @throws IOException
	 */
	@Test
	public void testWritePack2DeltasReuseRefs() throws IOException {
		writeVerifyPack2(true);
	}

	/**
	 * Test pack writing with delta reuse. Delta bases referred as offsets. Pack
	 * configuration as in {@link #testWritePack2DeltasReuseRefs()}.
	 *
	 * @throws IOException
	 */
	@Test
	public void testWritePack2DeltasReuseOffsets() throws IOException {
		config.setDeltaBaseAsOffset(true);
		writeVerifyPack2(true);
	}

	/**
	 * Test pack writing with delta reuse. Raw-data copy (reuse) is made on a
	 * pack with CRC32 index. Pack configuration as in
	 * {@link #testWritePack2DeltasReuseRefs()}.
	 *
	 * @throws IOException
	 */
	@Test
	public void testWritePack2DeltasCRC32Copy() throws IOException {
		final File packDir = db.getObjectDatabase().getPackDirectory();
		final File crc32Pack = new File(packDir,
				"pack-34be9032ac282b11fa9babdc2b2a93ca996c9c2f.pack");
		final File crc32Idx = new File(packDir,
				"pack-34be9032ac282b11fa9babdc2b2a93ca996c9c2f.idx");
		copyFile(JGitTestUtil.getTestResourceFile(
				"pack-34be9032ac282b11fa9babdc2b2a93ca996c9c2f.idxV2"),
				crc32Idx);
		db.openPack(crc32Pack);

		writeVerifyPack2(true);
	}

	/**
	 * Create pack basing on fixed objects list, then precisely verify content.
	 * No delta reuse here.
	 *
	 * @throws IOException
	 * @throws MissingObjectException
	 *
	 */
	@Test
	public void testWritePack3() throws MissingObjectException, IOException {
		config.setReuseDeltas(false);
		final ObjectId forcedOrder[] = new ObjectId[] {
				ObjectId.fromString("82c6b885ff600be425b4ea96dee75dca255b69e7"),
				ObjectId.fromString("c59759f143fb1fe21c197981df75a7ee00290799"),
				ObjectId.fromString("aabf2ffaec9b497f0950352b3e582d73035c2035"),
				ObjectId.fromString("902d5476fa249b7abc9d84c611577a81381f0327"),
				ObjectId.fromString("6ff87c4664981e4397625791c8ea3bbb5f2279a3") ,
				ObjectId.fromString("5b6e7c66c276e7610d4a73c70ec1a1f7c1003259") };
		try (RevWalk parser = new RevWalk(db)) {
			final RevObject forcedOrderRevs[] = new RevObject[forcedOrder.length];
			for (int i = 0; i < forcedOrder.length; i++)
				forcedOrderRevs[i] = parser.parseAny(forcedOrder[i]);

			createVerifyOpenPack(Arrays.asList(forcedOrderRevs));
		}

		assertEquals(forcedOrder.length, writer.getObjectCount());
		verifyObjectsOrder(forcedOrder);
		assertEquals("ed3f96b8327c7c66b0f8f70056129f0769323d86", writer
				.computeName().name());
	}

	/**
	 * Another pack creation: basing on both interesting and uninteresting
	 * objects. No delta reuse possible here, as this is a specific case when we
	 * write only 1 commit, associated with 1 tree, 1 blob.
	 *
	 * @throws IOException
	 */
	@Test
	public void testWritePack4() throws IOException {
		writeVerifyPack4(false);
	}

	/**
	 * Test thin pack writing: 1 blob delta base is on objects edge. Pack
	 * configuration as in {@link #testWritePack4()}.
	 *
	 * @throws IOException
	 */
	@Test
	public void testWritePack4ThinPack() throws IOException {
		writeVerifyPack4(true);
	}

	/**
	 * Compare sizes of packs created using {@link #testWritePack2()} and
	 * {@link #testWritePack2DeltasReuseRefs()}. The pack using deltas should
	 * be smaller.
	 *
	 * @throws Exception
	 */
	@Test
	public void testWritePack2SizeDeltasVsNoDeltas() throws Exception {
		config.setReuseDeltas(false);
		config.setDeltaCompress(false);
		testWritePack2();
		final long sizePack2NoDeltas = os.size();
		tearDown();
		setUp();
		testWritePack2DeltasReuseRefs();
		final long sizePack2DeltasRefs = os.size();

		assertTrue(sizePack2NoDeltas > sizePack2DeltasRefs);
	}

	/**
	 * Compare sizes of packs created using
	 * {@link #testWritePack2DeltasReuseRefs()} and
	 * {@link #testWritePack2DeltasReuseOffsets()}. The pack with delta bases
	 * written as offsets should be smaller.
	 *
	 * @throws Exception
	 */
	@Test
	public void testWritePack2SizeOffsetsVsRefs() throws Exception {
		testWritePack2DeltasReuseRefs();
		final long sizePack2DeltasRefs = os.size();
		tearDown();
		setUp();
		testWritePack2DeltasReuseOffsets();
		final long sizePack2DeltasOffsets = os.size();

		assertTrue(sizePack2DeltasRefs > sizePack2DeltasOffsets);
	}

	/**
	 * Compare sizes of packs created using {@link #testWritePack4()} and
	 * {@link #testWritePack4ThinPack()}. Obviously, the thin pack should be
	 * smaller.
	 *
	 * @throws Exception
	 */
	@Test
	public void testWritePack4SizeThinVsNoThin() throws Exception {
		testWritePack4();
		final long sizePack4 = os.size();
		tearDown();
		setUp();
		testWritePack4ThinPack();
		final long sizePack4Thin = os.size();

		assertTrue(sizePack4 > sizePack4Thin);
	}

	@Test
	public void testDeltaStatistics() throws Exception {
		config.setDeltaCompress(true);
		FileRepository repo = createBareRepository();
		ArrayList<RevObject> blobs = new ArrayList<>();
		try (TestRepository<FileRepository> testRepo = new TestRepository<>(
				repo)) {
			blobs.add(testRepo.blob(genDeltableData(1000)));
			blobs.add(testRepo.blob(genDeltableData(1005)));
		}

		try (PackWriter pw = new PackWriter(repo)) {
			NullProgressMonitor m = NullProgressMonitor.INSTANCE;
			pw.preparePack(blobs.iterator());
			pw.writePack(m, m, os);
			PackStatistics stats = pw.getStatistics();
			assertEquals(1, stats.getTotalDeltas());
			assertTrue("Delta bytes not set.",
					stats.byObjectType(OBJ_BLOB).getDeltaBytes() > 0);
		}
	}

	// Generate consistent junk data for building files that delta well
	private String genDeltableData(int length) {
		assertTrue("Generated data must have a length > 0", length > 0);
		char[] data = {'a', 'b', 'c', '\n'};
		StringBuilder builder = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			builder.append(data[i % 4]);
		}
		return builder.toString();
	}


	@Test
	public void testWriteIndex() throws Exception {
		config.setIndexVersion(2);
		writeVerifyPack4(false);

		File packFile = pack.getPackFile();
		String name = packFile.getName();
		String base = name.substring(0, name.lastIndexOf('.'));
		File indexFile = new File(packFile.getParentFile(), base + ".idx");

		// Validate that IndexPack came up with the right CRC32 value.
		final PackIndex idx1 = PackIndex.open(indexFile);
		assertTrue(idx1 instanceof PackIndexV2);
		assertEquals(0x4743F1E4L, idx1.findCRC32(ObjectId
				.fromString("82c6b885ff600be425b4ea96dee75dca255b69e7")));

		// Validate that an index written by PackWriter is the same.
		final File idx2File = new File(indexFile.getAbsolutePath() + ".2");
		try (FileOutputStream is = new FileOutputStream(idx2File)) {
			writer.writeIndex(is);
		}
		final PackIndex idx2 = PackIndex.open(idx2File);
		assertTrue(idx2 instanceof PackIndexV2);
		assertEquals(idx1.getObjectCount(), idx2.getObjectCount());
		assertEquals(idx1.getOffset64Count(), idx2.getOffset64Count());

		for (int i = 0; i < idx1.getObjectCount(); i++) {
			final ObjectId id = idx1.getObjectId(i);
			assertEquals(id, idx2.getObjectId(i));
			assertEquals(idx1.findOffset(id), idx2.findOffset(id));
			assertEquals(idx1.findCRC32(id), idx2.findCRC32(id));
		}
	}

	@Test
	public void testExclude() throws Exception {
		FileRepository repo = createBareRepository();

		try (TestRepository<FileRepository> testRepo = new TestRepository<>(
				repo)) {
			BranchBuilder bb = testRepo.branch("refs/heads/master");
			contentA = testRepo.blob("A");
			c1 = bb.commit().add("f", contentA).create();
			testRepo.getRevWalk().parseHeaders(c1);
			PackIndex pf1 = writePack(repo, wants(c1), EMPTY_ID_SET);
			assertContent(pf1, Arrays.asList(c1.getId(), c1.getTree().getId(),
					contentA.getId()));
			contentB = testRepo.blob("B");
			c2 = bb.commit().add("f", contentB).create();
			testRepo.getRevWalk().parseHeaders(c2);
			PackIndex pf2 = writePack(repo, wants(c2),
					Sets.of((ObjectIdSet) pf1));
			assertContent(pf2, Arrays.asList(c2.getId(), c2.getTree().getId(),
					contentB.getId()));
		}
	}

	private static void assertContent(PackIndex pi, List<ObjectId> expected) {
		assertEquals("Pack index has wrong size.", expected.size(),
				pi.getObjectCount());
		for (int i = 0; i < pi.getObjectCount(); i++)
			assertTrue(
					"Pack index didn't contain the expected id "
							+ pi.getObjectId(i),
					expected.contains(pi.getObjectId(i)));
	}

	@Test
	public void testShallowIsMinimalDepth1() throws Exception {
		FileRepository repo = setupRepoForShallowFetch();

		PackIndex idx = writeShallowPack(repo, 1, wants(c2), NONE, NONE);
		assertContent(idx, Arrays.asList(c2.getId(), c2.getTree().getId(),
				contentA.getId(), contentB.getId()));

		// Client already has blobs A and B, verify those are not packed.
		idx = writeShallowPack(repo, 1, wants(c5), haves(c2), shallows(c2));
		assertContent(idx, Arrays.asList(c5.getId(), c5.getTree().getId(),
				contentC.getId(), contentD.getId(), contentE.getId()));
	}

	@Test
	public void testShallowIsMinimalDepth2() throws Exception {
		FileRepository repo = setupRepoForShallowFetch();

		PackIndex idx = writeShallowPack(repo, 2, wants(c2), NONE, NONE);
		assertContent(idx,
				Arrays.asList(c1.getId(), c2.getId(), c1.getTree().getId(),
						c2.getTree().getId(), contentA.getId(),
						contentB.getId()));

		// Client already has blobs A and B, verify those are not packed.
		idx = writeShallowPack(repo, 2, wants(c5), haves(c1, c2), shallows(c1));
		assertContent(idx,
				Arrays.asList(c4.getId(), c5.getId(), c4.getTree().getId(),
						c5.getTree().getId(), contentC.getId(),
						contentD.getId(), contentE.getId()));
	}

	@Test
	public void testShallowFetchShallowParentDepth1() throws Exception {
		FileRepository repo = setupRepoForShallowFetch();

		PackIndex idx = writeShallowPack(repo, 1, wants(c5), NONE, NONE);
		assertContent(idx,
				Arrays.asList(c5.getId(), c5.getTree().getId(),
						contentA.getId(), contentB.getId(), contentC.getId(),
						contentD.getId(), contentE.getId()));

		idx = writeShallowPack(repo, 1, wants(c4), haves(c5), shallows(c5));
		assertContent(idx, Arrays.asList(c4.getId(), c4.getTree().getId()));
	}

	@Test
	public void testShallowFetchShallowParentDepth2() throws Exception {
		FileRepository repo = setupRepoForShallowFetch();

		PackIndex idx = writeShallowPack(repo, 2, wants(c5), NONE, NONE);
		assertContent(idx,
				Arrays.asList(c4.getId(), c5.getId(), c4.getTree().getId(),
						c5.getTree().getId(), contentA.getId(),
						contentB.getId(), contentC.getId(), contentD.getId(),
						contentE.getId()));

		idx = writeShallowPack(repo, 2, wants(c3), haves(c4, c5), shallows(c4));
		assertContent(idx, Arrays.asList(c2.getId(), c3.getId(),
				c2.getTree().getId(), c3.getTree().getId()));
	}

	@Test
	public void testShallowFetchShallowAncestorDepth1() throws Exception {
		FileRepository repo = setupRepoForShallowFetch();

		PackIndex idx = writeShallowPack(repo, 1, wants(c5), NONE, NONE);
		assertContent(idx,
				Arrays.asList(c5.getId(), c5.getTree().getId(),
						contentA.getId(), contentB.getId(), contentC.getId(),
						contentD.getId(), contentE.getId()));

		idx = writeShallowPack(repo, 1, wants(c3), haves(c5), shallows(c5));
		assertContent(idx, Arrays.asList(c3.getId(), c3.getTree().getId()));
	}

	@Test
	public void testShallowFetchShallowAncestorDepth2() throws Exception {
		FileRepository repo = setupRepoForShallowFetch();

		PackIndex idx = writeShallowPack(repo, 2, wants(c5), NONE, NONE);
		assertContent(idx,
				Arrays.asList(c4.getId(), c5.getId(), c4.getTree().getId(),
						c5.getTree().getId(), contentA.getId(),
						contentB.getId(), contentC.getId(), contentD.getId(),
						contentE.getId()));

		idx = writeShallowPack(repo, 2, wants(c2), haves(c4, c5), shallows(c4));
		assertContent(idx, Arrays.asList(c1.getId(), c2.getId(),
				c1.getTree().getId(), c2.getTree().getId()));
	}

	private FileRepository setupRepoForShallowFetch() throws Exception {
		FileRepository repo = createBareRepository();
		try (TestRepository<Repository> r = new TestRepository<>(repo)) {
			BranchBuilder bb = r.branch("refs/heads/master");
			contentA = r.blob("A");
			contentB = r.blob("B");
			contentC = r.blob("C");
			contentD = r.blob("D");
			contentE = r.blob("E");
			c1 = bb.commit().add("a", contentA).create();
			c2 = bb.commit().add("b", contentB).create();
			c3 = bb.commit().add("c", contentC).create();
			c4 = bb.commit().add("d", contentD).create();
			c5 = bb.commit().add("e", contentE).create();
			r.getRevWalk().parseHeaders(c5); // fully initialize the tip RevCommit
			return repo;
		}
	}

	private static PackIndex writePack(FileRepository repo,
			Set<? extends ObjectId> want, Set<ObjectIdSet> excludeObjects)
					throws IOException {
		RevWalk walk = new RevWalk(repo);
		return writePack(repo, walk, 0, want, NONE, excludeObjects);
	}

	private static PackIndex writeShallowPack(FileRepository repo, int depth,
			Set<? extends ObjectId> want, Set<? extends ObjectId> have,
			Set<? extends ObjectId> shallow) throws IOException {
		// During negotiation, UploadPack would have set up a DepthWalk and
		// marked the client's "shallow" commits. Emulate that here.
		DepthWalk.RevWalk walk = new DepthWalk.RevWalk(repo, depth - 1);
		walk.assumeShallow(shallow);
		return writePack(repo, walk, depth, want, have, EMPTY_ID_SET);
	}

	private static PackIndex writePack(FileRepository repo, RevWalk walk,
			int depth, Set<? extends ObjectId> want,
			Set<? extends ObjectId> have, Set<ObjectIdSet> excludeObjects)
					throws IOException {
		try (PackWriter pw = new PackWriter(repo)) {
			pw.setDeltaBaseAsOffset(true);
			pw.setReuseDeltaCommits(false);
			for (ObjectIdSet idx : excludeObjects) {
				pw.excludeObjects(idx);
			}
			if (depth > 0) {
				pw.setShallowPack(depth, null);
			}
			ObjectWalk ow = walk.toObjectWalkWithSameObjects();

			pw.preparePack(NullProgressMonitor.INSTANCE, ow, want, have, NONE);
			String id = pw.computeName().getName();
			File packdir = repo.getObjectDatabase().getPackDirectory();
			File packFile = new File(packdir, "pack-" + id + ".pack");
			try (FileOutputStream packOS = new FileOutputStream(packFile)) {
				pw.writePack(NullProgressMonitor.INSTANCE,
						NullProgressMonitor.INSTANCE, packOS);
			}
			File idxFile = new File(packdir, "pack-" + id + ".idx");
			try (FileOutputStream idxOS = new FileOutputStream(idxFile)) {
				pw.writeIndex(idxOS);
			}
			return PackIndex.open(idxFile);
		}
	}

	// TODO: testWritePackDeltasCycle()
	// TODO: testWritePackDeltasDepth()

	private void writeVerifyPack1() throws IOException {
		final HashSet<ObjectId> interestings = new HashSet<>();
		interestings.add(ObjectId
				.fromString("82c6b885ff600be425b4ea96dee75dca255b69e7"));
		createVerifyOpenPack(interestings, NONE, false, false);

		final ObjectId expectedOrder[] = new ObjectId[] {
				ObjectId.fromString("82c6b885ff600be425b4ea96dee75dca255b69e7"),
				ObjectId.fromString("c59759f143fb1fe21c197981df75a7ee00290799"),
				ObjectId.fromString("540a36d136cf413e4b064c2b0e0a4db60f77feab"),
				ObjectId.fromString("aabf2ffaec9b497f0950352b3e582d73035c2035"),
				ObjectId.fromString("902d5476fa249b7abc9d84c611577a81381f0327"),
				ObjectId.fromString("4b825dc642cb6eb9a060e54bf8d69288fbee4904"),
				ObjectId.fromString("6ff87c4664981e4397625791c8ea3bbb5f2279a3"),
				ObjectId.fromString("5b6e7c66c276e7610d4a73c70ec1a1f7c1003259") };

		assertEquals(expectedOrder.length, writer.getObjectCount());
		verifyObjectsOrder(expectedOrder);
		assertEquals("34be9032ac282b11fa9babdc2b2a93ca996c9c2f", writer
				.computeName().name());
	}

	private void writeVerifyPack2(boolean deltaReuse) throws IOException {
		config.setReuseDeltas(deltaReuse);
		final HashSet<ObjectId> interestings = new HashSet<>();
		interestings.add(ObjectId
				.fromString("82c6b885ff600be425b4ea96dee75dca255b69e7"));
		final HashSet<ObjectId> uninterestings = new HashSet<>();
		uninterestings.add(ObjectId
				.fromString("540a36d136cf413e4b064c2b0e0a4db60f77feab"));
		createVerifyOpenPack(interestings, uninterestings, false, false);

		final ObjectId expectedOrder[] = new ObjectId[] {
				ObjectId.fromString("82c6b885ff600be425b4ea96dee75dca255b69e7"),
				ObjectId.fromString("c59759f143fb1fe21c197981df75a7ee00290799"),
				ObjectId.fromString("aabf2ffaec9b497f0950352b3e582d73035c2035"),
				ObjectId.fromString("902d5476fa249b7abc9d84c611577a81381f0327"),
				ObjectId.fromString("6ff87c4664981e4397625791c8ea3bbb5f2279a3") ,
				ObjectId.fromString("5b6e7c66c276e7610d4a73c70ec1a1f7c1003259") };
		if (!config.isReuseDeltas() && !config.isDeltaCompress()) {
			// If no deltas are in the file the final two entries swap places.
			swap(expectedOrder, 4, 5);
		}
		assertEquals(expectedOrder.length, writer.getObjectCount());
		verifyObjectsOrder(expectedOrder);
		assertEquals("ed3f96b8327c7c66b0f8f70056129f0769323d86", writer
				.computeName().name());
	}

	private static void swap(ObjectId[] arr, int a, int b) {
		ObjectId tmp = arr[a];
		arr[a] = arr[b];
		arr[b] = tmp;
	}

	private void writeVerifyPack4(final boolean thin) throws IOException {
		final HashSet<ObjectId> interestings = new HashSet<>();
		interestings.add(ObjectId
				.fromString("82c6b885ff600be425b4ea96dee75dca255b69e7"));
		final HashSet<ObjectId> uninterestings = new HashSet<>();
		uninterestings.add(ObjectId
				.fromString("c59759f143fb1fe21c197981df75a7ee00290799"));
		createVerifyOpenPack(interestings, uninterestings, thin, false);

		final ObjectId writtenObjects[] = new ObjectId[] {
				ObjectId.fromString("82c6b885ff600be425b4ea96dee75dca255b69e7"),
				ObjectId.fromString("aabf2ffaec9b497f0950352b3e582d73035c2035"),
				ObjectId.fromString("5b6e7c66c276e7610d4a73c70ec1a1f7c1003259") };
		assertEquals(writtenObjects.length, writer.getObjectCount());
		ObjectId expectedObjects[];
		if (thin) {
			expectedObjects = new ObjectId[4];
			System.arraycopy(writtenObjects, 0, expectedObjects, 0,
					writtenObjects.length);
			expectedObjects[3] = ObjectId
					.fromString("6ff87c4664981e4397625791c8ea3bbb5f2279a3");

		} else {
			expectedObjects = writtenObjects;
		}
		verifyObjectsOrder(expectedObjects);
		assertEquals("cded4b74176b4456afa456768b2b5aafb41c44fc", writer
				.computeName().name());
	}

	private void createVerifyOpenPack(final Set<ObjectId> interestings,
			final Set<ObjectId> uninterestings, final boolean thin,
			final boolean ignoreMissingUninteresting)
			throws MissingObjectException, IOException {
		createVerifyOpenPack(interestings, uninterestings, thin,
				ignoreMissingUninteresting, false);
	}

	private void createVerifyOpenPack(final Set<ObjectId> interestings,
			final Set<ObjectId> uninterestings, final boolean thin,
			final boolean ignoreMissingUninteresting, boolean useBitmaps)
			throws MissingObjectException, IOException {
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		writer = new PackWriter(config, db.newObjectReader());
		writer.setUseBitmaps(useBitmaps);
		writer.setThin(thin);
		writer.setIgnoreMissingUninteresting(ignoreMissingUninteresting);
		writer.preparePack(m, interestings, uninterestings);
		writer.writePack(m, m, os);
		writer.close();
		verifyOpenPack(thin);
	}

	private void createVerifyOpenPack(List<RevObject> objectSource)
			throws MissingObjectException, IOException {
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		writer = new PackWriter(config, db.newObjectReader());
		writer.preparePack(objectSource.iterator());
		assertEquals(objectSource.size(), writer.getObjectCount());
		writer.writePack(m, m, os);
		writer.close();
		verifyOpenPack(false);
	}

	private void verifyOpenPack(boolean thin) throws IOException {
		final byte[] packData = os.toByteArray();

		if (thin) {
			PackParser p = index(packData);
			try {
				p.parse(NullProgressMonitor.INSTANCE);
				fail("indexer should grumble about missing object");
			} catch (IOException x) {
				// expected
			}
		}

		ObjectDirectoryPackParser p = (ObjectDirectoryPackParser) index(packData);
		p.setKeepEmpty(true);
		p.setAllowThin(thin);
		p.setIndexVersion(2);
		p.parse(NullProgressMonitor.INSTANCE);
		pack = p.getPackFile();
		assertNotNull("have PackFile after parsing", pack);
	}

	private PackParser index(byte[] packData) throws IOException {
		if (inserter == null)
			inserter = dst.newObjectInserter();
		return inserter.newPackParser(new ByteArrayInputStream(packData));
	}

	private void verifyObjectsOrder(ObjectId objectsOrder[]) {
		final List<PackIndex.MutableEntry> entries = new ArrayList<>();

		for (MutableEntry me : pack) {
			entries.add(me.cloneEntry());
		}
		Collections.sort(entries, (MutableEntry o1, MutableEntry o2) -> Long
				.signum(o1.getOffset() - o2.getOffset()));

		int i = 0;
		for (MutableEntry me : entries) {
			assertEquals(objectsOrder[i++].toObjectId(), me.toObjectId());
		}
	}

	private static Set<ObjectId> haves(ObjectId... objects) {
		return Sets.of(objects);
	}

	private static Set<ObjectId> wants(ObjectId... objects) {
		return Sets.of(objects);
	}

	private static Set<ObjectId> shallows(ObjectId... objects) {
		return Sets.of(objects);
	}
}

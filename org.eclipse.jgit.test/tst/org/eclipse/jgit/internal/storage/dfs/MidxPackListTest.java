package org.eclipse.jgit.internal.storage.dfs;

import static java.util.Arrays.asList;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Deflater;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

public class MidxPackListTest {

	InMemoryRepository db;

	@Before
	public void setUp() {
		db = new InMemoryRepository(new DfsRepositoryDescription("test"));
		db.getObjectDatabase().setUseMultipackIndex(true);
	}

	@Test
	public void getAllPlainPacks_onlyPlain() throws IOException {
		setupThreePacks();
		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		MidxPackList packList = MidxPackList.create(packs);
		assertEquals(3, packList.getAllPlainPacks().size());
	}

	@Test
	public void getAllPlainPacks_onlyMidx() throws IOException {
		setupThreePacksAndMidx();

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(1, packs.length);
		MidxPackList packList = MidxPackList.create(packs);
		assertEquals(3, packList.getAllPlainPacks().size());
	}

	@Test
	public void getAllPlainPacks_midxPlusOne() throws IOException {
		setupThreePacksAndMidx();
		writePackWithRandomBlob(60);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(2, packs.length);
		MidxPackList packList = MidxPackList.create(packs);
		assertEquals(4, packList.getAllPlainPacks().size());
	}

	@Test
	public void getAllPlainPacks_nestedMidx() throws IOException {
		setupSixPacksThreeMidx();

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(1, packs.length);
		MidxPackList packList = MidxPackList.create(packs);
		assertEquals(6, packList.getAllPlainPacks().size());
	}

	@Test
	public void getAllMidxPacks_onlyPlain() throws IOException {
		setupThreePacks();

		MidxPackList packList = MidxPackList
				.create(db.getObjectDatabase().getPacks());
		assertEquals(0, packList.getAllMidxPacks().size());
	}

	@Test
	public void getAllMidxPacks_onlyMidx() throws IOException {
		setupThreePacksAndMidx();

		MidxPackList packList = MidxPackList
				.create(db.getObjectDatabase().getPacks());
		assertEquals(1, packList.getAllMidxPacks().size());
	}

	@Test
	public void getAllMidxPacks_midxPlusOne() throws IOException {
		setupThreePacksAndMidx();
		writePackWithRandomBlob(60);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(2, packs.length);
		MidxPackList packList = MidxPackList.create(packs);
		assertEquals(1, packList.getAllMidxPacks().size());
	}

	@Test
	public void getAllMidxPacks_nestedMidx() throws IOException {
		setupSixPacksThreeMidx();

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(1, packs.length);
		MidxPackList packList = MidxPackList.create(packs);
		assertEquals(3, packList.getAllMidxPacks().size());
	}

	@Test
	public void findAllImpactedMidx_onlyPacks() throws IOException {
		setupThreePacks();

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		MidxPackList packList = MidxPackList
				.create(db.getObjectDatabase().getPacks());
		assertEquals(0, packList.findAllImpactedMidxs(asList(packs)).size());
	}

	@Test
	public void findAllImpactedMidx_onlyMidx() throws IOException {
		setupThreePacksAndMidx();

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		MidxPackList packList = MidxPackList.create(packs);
		List<DfsPackFile> covered = ((DfsPackFileMidx) packs[0])
				.getAllCoveredPacks();
		assertEquals(1, packList.findAllImpactedMidxs(covered.get(0)).size());
		assertEquals(1, packList.findAllImpactedMidxs(covered.get(1)).size());
		assertEquals(1, packList.findAllImpactedMidxs(covered.get(2)).size());

		assertEquals("multiple packs covered", 1,
				packList.findAllImpactedMidxs(covered.subList(0, 2)).size());
	}

	@Test
	public void findAllImpactedMidx_midxPlusOne() throws IOException {
		setupThreePacksAndMidx();
		writePackWithRandomBlob(60);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(2, packs.length);

		DfsPackFile uncoveredPack = packs[0];
		List<DfsPackFile> coveredPacks = ((DfsPackFileMidx) packs[1])
				.getAllCoveredPacks();
		assertEquals(3, coveredPacks.size());

		MidxPackList packList = MidxPackList.create(packs);
		assertEquals("one non covered", 0,
				packList.findAllImpactedMidxs(uncoveredPack).size());
		assertEquals("one and covered", 1,
				packList.findAllImpactedMidxs(coveredPacks.get(1)).size());
		assertEquals(
				"two, only one covered", 1, packList
						.findAllImpactedMidxs(
								List.of(uncoveredPack, coveredPacks.get(2)))
						.size());
	}

	@Test
	public void findAllImpactedMidxs_nestedMidx() throws IOException {
		setupSixPacksThreeMidx();

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(1, packs.length);
		MidxPackList packList = MidxPackList.create(packs);
		List<DfsPackFile> coveredPacks = ((DfsPackFileMidx) packs[0])
				.getAllCoveredPacks();
		assertEquals(6, coveredPacks.size());

		assertEquals("one covered tip midx", 1,
				packList.findAllImpactedMidxs(coveredPacks.get(0)).size());
		assertEquals("one covered middle midx", 2,
				packList.findAllImpactedMidxs(coveredPacks.get(2)).size());
		assertEquals("one covered base midx", 3,
				packList.findAllImpactedMidxs(coveredPacks.get(4)).size());
		assertEquals(
				"multiple covered in chain", 3, packList
						.findAllImpactedMidxs(List.of(coveredPacks.get(1),
								coveredPacks.get(2), coveredPacks.get(5)))
						.size());
	}



	private void setupThreePacks() throws IOException {
		writePackWithRandomBlob(100);
		writePackWithRandomBlob(300);
		writePackWithRandomBlob(50);
	}

	private void setupThreePacksAndMidx() throws IOException {
		writePackWithRandomBlob(100);
		writePackWithRandomBlob(300);
		writePackWithRandomBlob(50);
		writeMultipackIndex();
	}

	private void setupSixPacksThreeMidx() throws IOException {
		writePackWithRandomBlob(100);
		writePackWithRandomBlob(300);
		writePackWithRandomBlob(50);
		writePackWithRandomBlob(400);
		writePackWithRandomBlob(130);
		writePackWithRandomBlob(500);
		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(6, packs.length);
		DfsPackFileMidx midxBase = writeMultipackIndex(
				Arrays.copyOfRange(packs, 4, 6), null);
		DfsPackFileMidx midxMid = writeMultipackIndex(
				Arrays.copyOfRange(packs, 2, 4), midxBase);
		writeMultipackIndex(Arrays.copyOfRange(packs, 0, 2), midxMid);
		assertEquals("only top midx", 1,
				db.getObjectDatabase().getPacks().length);
	}

	private void writeMultipackIndex() throws IOException {
		writeMultipackIndex(db.getObjectDatabase().getPacks(), null);
	}

	private DfsPackFileMidx writeMultipackIndex(DfsPackFile[] packs,
			DfsPackFileMidx base) throws IOException {
		List<DfsPackFile> packfiles = asList(packs);
		DfsPackDescription desc = DfsMidxWriter.writeMidx(
				NullProgressMonitor.INSTANCE, db.getObjectDatabase(), packfiles,
				base != null ? base.getPackDescription() : null);
		db.getObjectDatabase().commitPack(List.of(desc), null);
		return DfsPackFileMidx.create(DfsBlockCache.getInstance(), desc,
				packfiles, base);
	}

	private ObjectId writePackWithBlob(byte[] data) throws IOException {
		DfsInserter ins = (DfsInserter) db.newObjectInserter();
		ins.setCompressionLevel(Deflater.NO_COMPRESSION);
		ObjectId blobId = ins.insert(OBJ_BLOB, data);
		ins.flush();
		return blobId;
	}

	// Do not use the size twice into the same test (it gives the same blob!)
	private ObjectId writePackWithRandomBlob(int size) throws IOException {
		byte[] data = new TestRng(JGitTestUtil.getName()).nextBytes(size);
		return writePackWithBlob(data);
	}
}

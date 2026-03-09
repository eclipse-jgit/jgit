package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.junit.Before;
import org.junit.Test;

public class PackDirectoryTest extends LocalDiskRepositoryTestCase {
	private int streamThreshold = 16 * 1024;

//	private TestRng rng;

	private FileRepository repo;

	private TestRepository<FileRepository> tr;

	// private WindowCursor wc;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		WindowCacheConfig cfg = new WindowCacheConfig();
		cfg.setStreamFileThreshold(streamThreshold);
		cfg.install();

		mockSystemReader.getSystemConfig().setBoolean(
				ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_MULTIPACKINDEX, true);

		repo = createBareRepository();
		tr = new TestRepository<>(repo);
		// wc = (WindowCursor) repo.newObjectReader();
	}

	@Test
	public void getPacks_midxReplacesCoveredPacks() throws Exception {
		createThreePacks();
		Collection<Pack> packs = repo.getObjectDatabase().getPacks();
		assertEquals(3, packs.size());
		writeMidxOverAllPacks();

		// Reading now should return the midx
		packs = repo.getObjectDatabase().getPacks();
		assertEquals(1, packs.size());

		tr.branch("refs/heads/main").commit().add("more.txt", "booooo")
				.create();
		tr.packAndPrune();

		packs = repo.getObjectDatabase().getPacks();
		assertEquals(2, packs.size());
	}

	private TestRepoObjects createThreePacks() throws Exception {
		TestRepository<FileRepository>.BranchBuilder branch = tr
				.branch("refs/heads/main");
		RevBlob contentsOfA = tr.blob("contents of a");
		RevCommit commitA = branch.commit().add("a.txt", contentsOfA).create();
		tr.packAndPrune();

		RevBlob contentsOfB = tr.blob("contents of b");
		RevCommit commitB = branch.commit().add("b.txt", contentsOfB).create();
		tr.packAndPrune();

		RevCommit commitC = branch.commit().add("c.txt", "contents of c commit")
				.create();
		tr.packAndPrune();

		Map<RevObject, Integer> midxOffsets = Map.of(commitA, 163, commitB, 12,
				commitC, 694, contentsOfA, 399, contentsOfB, 421);

		return new TestRepoObjects(commitA, commitB, commitC, contentsOfA,
				contentsOfB, midxOffsets);
	}

	private record TestRepoObjects(RevCommit commitA, RevCommit commitB,
			RevCommit commitC, RevBlob contentsOfA, RevBlob contentsOfB,
			Map<RevObject, Integer> midxOffsets) {
	}

	private void writeMidxOverAllPacks() throws IOException {
		Collection<Pack> packs = tr.getRepository().getObjectDatabase()
				.getPacks();
		assertEquals(3, packs.size());

		File midxDest = new File(repo.getObjectDatabase().getPackDirectory(),
				"multi-pack-index");
		MidxWriter.writeMidx(new TextProgressMonitor(), repo, packs, midxDest,
				new PackConfig(repo));
	}
}

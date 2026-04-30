package org.eclipse.jgit.internal.storage.file;

import static org.eclipse.jgit.internal.storage.pack.PackExt.BITMAP_INDEX;
import static org.eclipse.jgit.lib.Constants.MIDX_FILE;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import org.eclipse.jgit.internal.storage.midx.MultiPackIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndexLoader;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.junit.Before;
import org.junit.Test;

public class MidxWriterTest extends LocalDiskRepositoryTestCase {
	private int streamThreshold = 16 * 1024;

	private final static ObjectId UNKNOWN_OBJ = ObjectId
			.fromString("678668bdd3a609c6e1d7fea516e9fc1ed4f50ad2");

	private FileRepository repo;

	private TestRepository<Repository> tr;

//	private WindowCursor wc;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		WindowCacheConfig cfg = new WindowCacheConfig();
		cfg.setStreamFileThreshold(streamThreshold);
		cfg.install();

		repo = createBareRepository();
		tr = new TestRepository<>(repo);
//		wc = (WindowCursor) repo.newObjectReader();
	}

	@Test
	public void midx_write() throws Exception {
		TestRepository<Repository>.BranchBuilder branch = tr
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

		File midxFile = new File(repo.getObjectDatabase().getPackDirectory(),
				MIDX_FILE);
		assertFalse(midxFile.exists());
		MidxWriter.writeMidx(
				new TextProgressMonitor(new OutputStreamWriter(System.out,
						StandardCharsets.UTF_8)),
				repo, repo.getObjectDatabase().getPacks(), midxFile,
				new PackConfig());
		assertTrue(midxFile.exists());

		MultiPackIndex midx = MultiPackIndexLoader.open(midxFile);
		assertTrue(midx.hasObject(commitA));
		assertTrue(midx.hasObject(commitB));
		assertTrue(midx.hasObject(commitC));
		assertFalse(midx.hasObject(UNKNOWN_OBJ));

		File midxBitmaps = new File(repo.getObjectDatabase().getPackDirectory(),
				String.format("%s-%s.%s", MIDX_FILE,
						ObjectId.fromRaw(midx.getChecksum()).name(),
						BITMAP_INDEX.getExtension()));
		assertTrue(midxBitmaps.exists());

	}
}

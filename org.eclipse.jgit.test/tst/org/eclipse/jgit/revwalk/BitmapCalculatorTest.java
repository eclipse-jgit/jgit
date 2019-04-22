package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.junit.Before;
import org.junit.Test;

public class BitmapCalculatorTest extends LocalDiskRepositoryTestCase {
	TestRepository<FileRepository> repo;

	/** {@inheritDoc} */
	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		FileRepository db = createWorkRepository();
		repo = new TestRepository<>(db);
	}

	@Test
	public void walkCommits() throws Exception {
		RevBlob abBlob = repo.blob("a_b_content");
		RevCommit root = repo.commit().add("a/b", abBlob).create();
		repo.update("refs/heads/master", root);

		// GC creates bitmap index with ALL objects
		GC gc = new GC(repo.getRepository());
		gc.setAuto(false);
		gc.gc();

		// These objects are not in the bitmap index.
		RevBlob acBlob = repo.blob("a_c_content");
		RevCommit head = repo.commit().parent(root).add("a/c", acBlob).create();
		repo.update("refs/heads/master", head);


		BitmapCalculator bitmapWalker = new BitmapCalculator(
				repo.getRevWalk(),
				repo.getRevWalk().getObjectReader().getBitmapIndex(),
				NullProgressMonitor.INSTANCE);
		BitmapBuilder bitmap = bitmapWalker
				.getBitmapFor(Arrays.asList(head.getId()), null, false);

		assertTrue(bitmap.contains(root.getId()));
		assertTrue(bitmap.contains(root.getTree().getId()));
		assertTrue(bitmap.contains(abBlob.getId()));

		// BitmapCalculator added only the commit, no other objects.
		assertTrue(bitmap.contains(head.getId()));
		assertFalse(bitmap.contains(head.getTree().getId()));
		assertFalse(bitmap.contains(acBlob.getId()));
	}
}

package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.junit.Before;
import org.junit.Test;

public class BitmapWalkerTest extends LocalDiskRepositoryTestCase {
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
	public void walkObjects() throws Exception {
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


		BitmapWalker bitmapWalker = new BitmapWalker(
				new ObjectWalk(repo.getRevWalk().getObjectReader()),
				repo.getRevWalk().getObjectReader().getBitmapIndex(),
				NullProgressMonitor.INSTANCE);
		BitmapBuilder bitmap = bitmapWalker
				.findObjects(Arrays.asList(head.getId()), null, false);

		assertTrue(bitmap.contains(root.getId()));
		assertTrue(bitmap.contains(root.getTree().getId()));
		assertTrue(bitmap.contains(abBlob.getId()));

		// BitmapWalker added commit and objects
		assertTrue(bitmap.contains(head.getId()));
		assertTrue(bitmap.contains(head.getTree().getId()));
		assertTrue(bitmap.contains(acBlob.getId()));
	}
}

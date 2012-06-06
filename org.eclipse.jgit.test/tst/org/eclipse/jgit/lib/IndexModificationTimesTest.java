package org.eclipse.jgit.lib;

import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.junit.Test;

public class IndexModificationTimesTest extends RepositoryTestCase {

	@Test
	public void testLastModifiedTimes() throws Exception {
		Git git = new Git(db);
		String path = "file";
		writeTrashFile(path, "content");
		String path2 = "file2";
		writeTrashFile(path2, "content2");

		git.add().addFilepattern(path).call();
		git.add().addFilepattern(path2).call();
		git.commit().setMessage("commit").call();

		DirCache dc = db.readDirCache();
		DirCacheEntry entry = dc.getEntry(path);
		DirCacheEntry entry2 = dc.getEntry(path);

		assertTrue("last modified shall not be zero!",
				entry.getLastModified() != 0);

		assertTrue("last modified shall not be zero!",
				entry2.getLastModified() != 0);

		writeTrashFile(path, "new content");
		git.add().addFilepattern(path).call();
		git.commit().setMessage("commit2").call();

		dc = db.readDirCache();
		entry = dc.getEntry(path);
		entry2 = dc.getEntry(path);

		assertTrue("last modified shall not be zero!",
				entry.getLastModified() != 0);

		assertTrue("last modified shall not be zero!",
				entry2.getLastModified() != 0);
	}

	@Test
	public void testModify() throws Exception {
		Git git = new Git(db);
		String path = "file";
		writeTrashFile(path, "content");

		git.add().addFilepattern(path).call();
		git.commit().setMessage("commit").call();

		DirCache dc = db.readDirCache();
		DirCacheEntry entry = dc.getEntry(path);

		long masterLastMod = entry.getLastModified();

		git.checkout().setCreateBranch(true).setName("side").call();

		Thread.sleep(10);
		String path2 = "file2";
		writeTrashFile(path2, "side content");
		git.add().addFilepattern(path2).call();
		git.commit().setMessage("commit").call();

		dc = db.readDirCache();
		entry = dc.getEntry(path);

		long sideLastMode = entry.getLastModified();

		Thread.sleep(2000);

		writeTrashFile(path, "uncommitted content");
		git.checkout().setName("master").call();

		dc = db.readDirCache();
		entry = dc.getEntry(path);

		assertTrue("shall have equal mod time!", masterLastMod == sideLastMode);
		assertTrue("shall not equal master timestamp!",
				entry.getLastModified() == masterLastMod);

	}

}

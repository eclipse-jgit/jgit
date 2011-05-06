package org.eclipse.jgit.diff;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.pgm.Diff;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.junit.Test;

public class DiffTest extends RepositoryTestCase {

	@Test
	public void testDiff() throws Exception {
		assertFalse(db.isBare());

		write(new File(db.getDirectory().getParent(), "test.txt"), "test");
		File folder = new File(db.getDirectory().getParent(), "folder");
		folder.mkdir();
		write(new File(folder, "folder.txt"), "folder");
		Git git = new Git(db);
		git.add().addFilepattern(".").call();
		git.commit().setMessage("Initial commit").call();
		write(new File(folder, "folder.txt"), "folder change");

		Diff diff = new org.eclipse.jgit.pgm.Diff();
		diff.db = db;
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		diff.diffFmt = new DiffFormatter(new BufferedOutputStream(os));
		diff.pathFilter = PathFilter.create("folder");
		diff.run();

		String actual = os.toString();
		StringBuilder expected = new StringBuilder();
		expected.append("diff --git a/folder/folder.txt b/folder/folder.txt")
				.append("\n");
		expected.append("index 0119635..95c4c65 100644").append("\n");
		expected.append("--- a/folder/folder.txt").append("\n");
		expected.append("+++ b/folder/folder.txt").append("\n");
		expected.append("@@ -1 +1 @@").append("\n");
		expected.append("-folder").append("\n");
		expected.append("\\ No newline at end of file").append("\n");
		expected.append("+folder change").append("\n");
		expected.append("\\ No newline at end of file").append("\n");

		assertEquals(expected.toString(), actual);
	}

}

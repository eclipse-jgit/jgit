package org.eclipse.jgit.java7;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FSTest {

	private final File trash = new File(new File("target"), "trash");

	@Before
	public void setUp() throws Exception {
		FileUtils.delete(trash, FileUtils.RECURSIVE | FileUtils.RETRY | FileUtils.SKIP_MISSING);
		assertTrue(trash.mkdirs());
	}

	@After
	public void tearDown() throws Exception {
		FileUtils.delete(trash, FileUtils.RECURSIVE | FileUtils.RETRY);
	}

	/**
	 * The old File methods traverses symbolic links and look at the targets. With
	 * symbolic links we usually want to modify/look at the link. For some reason
	 * the executable attribute seems to always look at the target, but for the
	 * other attributes like lastModified, hidden and exists we must differ between
	 * the link and the target.
	 *
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Test
	public void testSymlinkAttributes() throws IOException, InterruptedException {
		FS  fs = FS.DETECTED;
		File link = new File(trash, "x");
		fs.createSymLink(link, "y");
		assertTrue(fs.exists(link));
		String target = fs.readSymLink(link);
		assertEquals("y", target);
		assertTrue(fs.lastModified(link) > 0);
		assertTrue(fs.exists(link));
		assertFalse(fs.canExecute(link));
		assertEquals(1, fs.length(link));

		RepositoryTestCase.fsTick(link);
		// Now create the link target
		File targetFile = new File(trash, "y");
		FileUtils.createNewFile(targetFile);
		assertTrue(fs.exists(link));
		assertTrue(fs.lastModified(link) > 0);
		assertTrue(fs.lastModified(targetFile) > fs.lastModified(link));
		assertFalse(fs.canExecute(link));
		fs.setExecute(targetFile, true);
		assertTrue(fs.canExecute(link));
	}

	@Test
	public void testSymlinkDirectory() throws IOException, InterruptedException {
		// TODO: On windows we need to distinguish between directory and file
		// symlinks
	}
}

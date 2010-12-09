package org.eclipse.jgit.util;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

public class FileUtilTest extends TestCase {
	public void testDeleteFile() throws IOException {
		File f = new File("test");
		assertTrue(f.createNewFile());
		FileUtils.delete(f);
		assertFalse(f.exists());

		try {
			FileUtils.delete(f);
			fail("deletion of non-existing file must fail");
		} catch (IOException e) {
			// expected
		}

		try {
			FileUtils.delete(f, FileUtils.SKIP_MISSING);
		} catch (IOException e) {
			fail("deletion of non-existing file must not fail with option SKIP_MISSING");
		}
	}

	public void testDeleteRecursive() throws IOException {
		File f1 = new File("test/test/a");
		f1.mkdirs();
		f1.createNewFile();
		File f2 = new File("test/test/b");
		f2.createNewFile();
		File d = new File("test");
		FileUtils.delete(d, FileUtils.RECURSIVE);
		assertFalse(d.exists());

		try {
			FileUtils.delete(d, FileUtils.RECURSIVE);
			fail("recursive deletion of non-existing directory must fail");
		} catch (IOException e) {
			// expected
		}

		try {
			FileUtils.delete(d, FileUtils.RECURSIVE | FileUtils.SKIP_MISSING);
		} catch (IOException e) {
			fail("recursive deletion of non-existing directory must not fail with option SKIP_MISSING");
		}
	}
}

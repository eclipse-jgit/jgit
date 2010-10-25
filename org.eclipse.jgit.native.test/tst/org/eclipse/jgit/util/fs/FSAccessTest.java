package org.eclipse.jgit.util.fs;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

public class FSAccessTest extends TestCase {
	public void test_lstat() throws ClassNotFoundException {
		File test = new File("test.txt");
		try {
			test.createNewFile();
			LStat stat = FSAccess.lstat(test
					.getAbsolutePath());
			System.out.println(stat.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

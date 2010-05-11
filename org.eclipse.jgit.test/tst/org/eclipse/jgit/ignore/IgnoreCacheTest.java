/*
 * Copyright (C) 2010, Red Hat Inc.
 * Copyright (C) 2010, Charley Wang <charley.wang@gmail.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.ignore;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import junit.framework.TestCase;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.JGitTestUtil;

/**
 * Tests for the ignore cache
 */
public class IgnoreCacheTest extends TestCase {

	private File ignoreTestDir = JGitTestUtil.getTestResourceFile("excludeTest");
	private Repository repo;
	private SimpleIgnoreCache cache;
	private final ArrayList<File> toDelete = new ArrayList<File>();

	//TODO: Do not use OS dependent strings to encode file paths

	public void tearDown() {
		deleteIgnoreFiles();
		cache.clear();
		toDelete.clear();
	}

	public void setUp() {
		ignoreTestDir = JGitTestUtil.getTestResourceFile("excludeTest");
		assertTrue("Test resource directory is not a directory",ignoreTestDir.isDirectory());

		try {
			repo = new Repository(ignoreTestDir);
		} catch (IOException e) {
			fail("IOException when creating repository at" + ignoreTestDir);
		}
		cache = new SimpleIgnoreCache(repo);
	}

	public void testInitialization() {
		File test = new File(repo.getDirectory() + "/new/a/b1/test.stp");
		assertTrue("Missing file " + test.getAbsolutePath(), test.exists());
		initCache(test);
		initCache(test);

		/*
		 * Every folder along the path has a .gitignore file. Therefore every
		 * folder should have been added and initialized
		 */
		boolean result = isIgnored(getRelativePath(test));
		assertFalse("Unexpected match for " + test.toString(), result);

		/*
		 * Check that every .gitignore along the path has been initialized
		 */
		File folder = test.getParentFile();
		IgnoreNode rules = null;
		String fp = folder.getAbsolutePath();
		while (!folder.equals(repo.getDirectory()) && fp.length() > 0) {
			rules = cache.getRules(getRelativePath(fp));
			assertNotNull("Ignore file not initialized for " + fp, rules);
			if (fp.equals(repo.getDirectory() + "/new/a"))
				//The /new/a directory has an empty ignore file
				assertEquals("Ignore file not initialized for " + fp, 0, rules.getRules().size());
			else {
				assertEquals("Ignore file not initialized for " + fp, 1, rules.getRules().size());
			}
			folder = folder.getParentFile();
			fp = folder.getAbsolutePath();
		}
		if (rules != null)
			assertEquals(1, rules.getRules().size());
		else
			fail("Base directory not initialized");

		test = new File("/tmp/not/part/of/repo/path");
		initCache(test);
	}

	public void testRules() {
		ignoreTestDir = JGitTestUtil.getTestResourceFile("excludeTest");
		assertTrue("Test resource directory is not a directory", ignoreTestDir.isDirectory());
		createExcludeFile();

		File test = new File(repo.getDirectory(), "test.stp");
		String path = test.getAbsolutePath();
		assertTrue("Could not find test file " + path, test.exists());
		initCache(test);

		IgnoreNode baseRules = cache.getRules("");
		assertNotNull("Could not find base rules", baseRules);


		/*
		 * .git/info/excludes:
		 * /test.stp
		 * /notignored
		 *
		 * new/.gitignore:
		 * notarealfile
		 *
		 * new/a/.gitignore:
		 * <empty>
		 *
		 * new/a/b2/.gitignore:
		 * <does not exist>
		 *
		 * new/a/b1/.gitignore:
		 * /c
		 *
		 * .gitignore:
		 * !/notignored
		 * /commentNotIgnored.tx#t
		 * /commentIgnored.txt#comment
		 * /commentIgnored.txt #comment
		 */
		boolean result = isIgnored(getRelativePath(path));
		assertEquals(3, baseRules.getRules().size());
		assertEquals(repo.getDirectory().getAbsolutePath(), baseRules.getBaseDir());
		//Test basic exclude file
		assertTrue("Did not match file " + test.toString(), result);
		//Test exclude file priority
		assertNotMatched("notignored");
		//Test that /src/test.stp is not matched by /test.stp in exclude file (Do not reinitialize)
		assertNotMatched("/src/test.stp");
		//Test file that is not mentioned -- should just return unmatched
		assertNotMatched("not/mentioned/file.txt");


		//Test adding nonexistent node
		test = new File(repo.getDirectory(), "new/a/b2/d/test.stp");
		initCache(test);
		assertNotMatched("new/a/b2/d/test.stp");
		assertNotMatched("new/a/b2/d/");
		assertNotMatched("new/a/b2/d");

		//Test folder
		test = new File(repo.getDirectory(), "new/a/b1/c");
		initCache(test);
		assertMatched("new/a/b1/c");
		assertMatched("new/a/b1/c/anything.c");
		assertMatched("new/a/b1/c/and.o");
		assertMatched("new/a/b1/c/everything.d");

		//Test name-only (use non-existent folders)
		assertNotMatched("notarealfile");
		assertNotMatched("/notarealfile");
		assertMatched("new/notarealfile");
		assertMatched("new/notarealfile/fake");
		assertMatched("new/a/notarealfile");
		assertMatched("new/a/b1/notarealfile");

		//Test clearing node -- create empty .gitignore
		createIgnoreFile(repo.getDirectory() + "/new/a/b2/.gitignore", new String[0]);
		test = new File(repo.getDirectory(), "new/a/b2/c");
		initCache(test);
		baseRules = cache.getRules("/new/a/b2");
		assertNotNull(baseRules);
		baseRules.clear();
		assertEquals(baseRules.getRules().size(), 0);
		try {
			assertFalse("Node not properly cleared", baseRules.isIgnored(getRelativePath(test)));
		} catch (IOException e) {
			e.printStackTrace();
			fail("IO exception when testing base rules");
		}

		//Test clearing entire cache, and isEmpty
		assertNotNull(cache.getRules(""));
		assertFalse(cache.isEmpty());
		cache.clear();
		assertNull(cache.getRules(""));
		assertTrue(cache.isEmpty());
		assertNotMatched("/anything");
		assertNotMatched("/new/anything");
		assertNotMatched("/src/anything");
	}

	public void testPriorities() {
		ignoreTestDir = JGitTestUtil.getTestResourceFile("excludeTest");
		assertTrue("Test resource directory is not a directory",ignoreTestDir.isDirectory());
		createExcludeFile();

		File test = new File(repo.getDirectory(), "/src/test.stp");
		assertTrue("Resource file " + test.getName() + " is missing", test.exists());
		initCache(test);

		//Test basic exclude file
		IgnoreNode node = cache.getRules("/src");
		assertNotNull("Excludes file was not initialized", node);

		/*
		 * src/.gitignore:
		 * /*.st?
		 * !/test.stp
		 * !/a.c
		 * /a.c
		 *
		 * ./.gitignore:
		 * !/notignored
		 *
		 * .git/info/exclude:
		 * /test.stp
		 * /notignored
		 */
		assertMatched("src/a.c");
		assertMatched("test.stp");
		assertMatched("src/blank.stp");
		assertNotMatched("notignored");
		assertNotMatched("src/test.stp");

		assertEquals(4, node.getRules().size());

		/*
		 * new/.gitignore:
		 * notarealfile
		 *
		 * new/a/.gitignore:
		 * <empty>
		 *
		 * new/a/b2/.gitignore:
		 * <does not exist>
		 *
		 * new/a/b2/c/.gitignore:
		 * /notarealfile2
		 */
		initCache(new File(repo.getDirectory(), "new/a/b2/c/notarealfile"));
		assertMatched("new/a/b2/c/notarealfile2");
		assertMatched("new/notarealfile");
		assertMatched("new/a/notarealfile");
		assertNotMatched("new/a/b2/c/test.stp");
		assertNotMatched("new/a/b2/c");
		assertNotMatched("new/a/b2/nonexistent");

	}

	/**
	 * Check if a file is not matched
	 * @param relativePath
	 * 			  Path to file, relative to repo.getDirectory. Use "/" as a separator,
	 * 			  this method will replace all instances of "/" with File.separator
	 */
	private void assertNotMatched(String relativePath) {
		File test = new File(repo.getDirectory(), relativePath);
		assertFalse("Should not match " + test.toString(), isIgnored(getRelativePath(test)));
	}

	/**
	 * Check if a file is matched
	 * @param relativePath
	 * 			  Path to file, relative to repo.getDirectory. Use "/" as a separator,
	 * 			  this method will replace all instances of "/" with File.separator.
	 */
	private void assertMatched(String relativePath) {
		File test = new File(repo.getDirectory(), relativePath);
		assertTrue("Failed to match " + test.toString(), isIgnored(getRelativePath(test)));
	}

	/**
	 * Attempt to write an ignore file at the given location
	 * @param path
	 * 			  Will create file at this path
	 * @param contents
	 * 			  Each entry in contents will be entered on its own line
	 */
	private void createIgnoreFile(String path, String[] contents) {
		File ignoreFile = new File(path);
		ignoreFile.delete();
		ignoreFile.deleteOnExit();	//Hope to catch in the event of crash
		toDelete.add(ignoreFile);	//For teardown purposes

		//Jump through some hoops to create the exclude file
		try {
			if (!ignoreFile.createNewFile()) {
				fail("Could not create ignore file" + ignoreFile.getAbsolutePath());
			}

			BufferedWriter bw = new BufferedWriter(new FileWriter (ignoreFile));
			for (String s : contents)
				bw.write(s + System.getProperty("line.separator"));
			bw.flush();
			bw.close();
		} catch (IOException e1) {
			e1.printStackTrace();
			fail("Could not create exclude file");
		}
	}

	private void createExcludeFile() {
		String[] content = new String[2];
		content[0] = "/test.stp";
		content[1] = "/notignored";

		//We can do this because we explicitly delete parent directories later in deleteIgnoreFiles.
		File parent= new File(repo.getDirectory(), ".git/info");
		if (!parent.exists())
			parent.mkdirs();

		createIgnoreFile(repo.getDirectory() + "/.git/info/exclude", content);
	}

	private void deleteIgnoreFiles() {
		for (File f : toDelete)
			f.delete();

		//Systematically delete exclude parent dirs
		File f = new File(ignoreTestDir.getAbsoluteFile(), ".git/info");
		f.delete();
		f = new File(ignoreTestDir.getAbsoluteFile(), ".git");
		f.delete();
	}

	/**
	 * @param path
	 * 			  Filepath relative to the git directory
	 * @return
	 * 			  Results of cache.isIgnored(path) -- true if ignored, false if
	 * 			  a negation is encountered or if no rules apply
	 */
	private boolean isIgnored(String path) {
		try {
			return cache.isIgnored(path);
		} catch (IOException e) {
			fail("IOException when attempting to check ignored status");
		}
		return false;
	}

	/**
	 * @param path
	 * 			  Absolute path.
	 * @return
	 * 			  Returns a path relative to repo.getDirectory().
	 */
	private String getRelativePath(String path) {
		String retVal = path.replaceFirst(repo.getDirectory().getAbsolutePath(), "");
		if (retVal.length() == path.length())
			fail("Not a child of the git directory");
		return retVal;
	}

	private String getRelativePath(File file) {
		String retVal = file.getAbsolutePath().replaceFirst(repo.getDirectory().getAbsolutePath(), "");
		if (retVal.length() == file.getAbsolutePath().length())
			fail("Not a child of the git directory");
		return retVal;
	}

	private void initCache(File test) {
		try {
			cache.initialize();
		} catch (IOException e) {
			e.printStackTrace();
			fail("Could not initialize cache");
		}
	}

}

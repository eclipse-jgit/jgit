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

import org.eclipse.jgit.util.JGitTestUtil;

/**
 *
 * @author Charley Wang
 *
 */
public class IgnoreCacheTest extends TestCase {

	private File ignoreTestDir = JGitTestUtil.getTestResourceFile("excludeTest");
	private final ArrayList<File> toDelete = new ArrayList<File>();

	public void tearDown() {
		deleteIgnoreFiles();
	}

	public void setUp() {
		toDelete.clear();
		ignoreTestDir = JGitTestUtil.getTestResourceFile("excludeTest");
		assertTrue("Test resource directory is not a directory",ignoreTestDir.isDirectory());
		createExcludeFile();
	}


	public void testInitialization() {
		SimpleIgnoreCache cache = new SimpleIgnoreCache(ignoreTestDir);
		File test = new File(ignoreTestDir.getAbsolutePath() + "/new/a/b1/test.stp");
		assertTrue("Missing file " + test.getAbsolutePath(), test.exists());

		try {
			cache.partiallyInitialize(test);
		} catch (IOException e) {
			e.printStackTrace();
			fail("IOException when attempting to lazy initialize");
		}

		/*
		 * Every folder along the path has a .gitignore file. Therefore every
		 * folder should have been added and initialized
		 */
		boolean result = cache.isIgnored(test);
		assertFalse("Unexpected match for " + test.toString(), result);

		File folder = test.getParentFile();

		IgnoreNode rules = null;
		while (!folder.getAbsolutePath().equals(ignoreTestDir.getAbsolutePath()) && folder.getAbsolutePath().length() > 0) {
			rules = cache.getRules(folder.getAbsolutePath());
			assertNotNull("Ignore file was not initialized for " + folder.getAbsolutePath(), rules);
			assertEquals(1, rules.getRules().size());
			folder = folder.getParentFile();
		}
		if (rules != null) {
			assertEquals(1, rules.getRules().size());
		}
		else {
			fail("Ignore nodes not initialized");
		}
	}

	public void testRules() {
		ignoreTestDir = JGitTestUtil.getTestResourceFile("excludeTest");
		assertTrue("Test resource directory is not a directory",ignoreTestDir.isDirectory());
		createExcludeFile();


		SimpleIgnoreCache cache = new SimpleIgnoreCache(ignoreTestDir);
		File test = new File(ignoreTestDir.getAbsolutePath() + "/test.stp");
		assertTrue("Missing file " + test.getAbsolutePath(), test.exists());

		try {
			cache.partiallyInitialize(test);
		} catch (IOException e) {
			e.printStackTrace();
			fail("IOException when attempting to lazy initialize");
		}

		IgnoreNode baseRules = cache.getRules(ignoreTestDir.getAbsolutePath());
		assertNotNull("Excludes file was not initialized", baseRules);

		/*
		 * .git/info/excludes:
		 * /test.stp
		 * /notignored
		 *
		 * .gitignore:
		 * !/notignored
		 */
		boolean result = cache.isIgnored(test);
		assertEquals(3, baseRules.getRules().size());
		assertEquals(ignoreTestDir.getAbsolutePath(), baseRules.getBaseDir());

		//Test basic exclude file
		assertTrue("Did not match file " + test.toString(), result);

		//Test exclude file priority
		test = new File(ignoreTestDir.getAbsolutePath() + "/notignored");
		assertFalse("Abnormal priority for " + test.toString(), cache.isIgnored(test));


		//Test that /src/test.stp is not matched by /test.stp in exclude file
		test = new File(ignoreTestDir.getAbsolutePath() + "/src/test.stp");
		assertTrue("Missing file " + test.getAbsolutePath(), test.exists());
		assertFalse("Unexpected match for " + test.toString(), cache.isIgnored(test));

		test = new File("/not/a/valid/path/at/all.txt");
		assertFalse("Cache matched non-existent file", cache.isIgnored(test));
		try {
			assertFalse("Node matched non-existent file", baseRules.isIgnored(test));
		} catch (IOException e) {
			e.printStackTrace();
			fail("IO exception when testing base rules");
		}

		//Test adding nonexistent node
		test = new File(ignoreTestDir.getAbsolutePath() + "/new/a/b2/c/test.stp");
		try {
			cache.partiallyInitialize(test);
		} catch (IOException e1) {
			e1.printStackTrace();
			fail("IOException when attempting to lazy initialize");
		}

		//Test clearing node
		createIgnoreFile(ignoreTestDir.getAbsolutePath() + "/new/a/b2/.gitignore", new String[0]);
		try {
			cache.partiallyInitialize(test);
		} catch (IOException e1) {
			e1.printStackTrace();
			fail("IOException when attempting to lazy initialize");
		}

		baseRules = cache.getRules(ignoreTestDir.getAbsolutePath() + "/new/a/b2");
		assertNotNull(baseRules);
		baseRules.clear();
		assertEquals(baseRules.getRules().size(), 0);
		try {
			assertFalse("Node not properly cleared", baseRules.isIgnored(test));
		} catch (IOException e) {
			e.printStackTrace();
			fail("IO exception when testing base rules");
		}

		//Initialize again, then delete node

		//Test clearing entire cache, and isEmpty
		assertNotNull(cache.getRules(ignoreTestDir.getAbsolutePath()));
		assertFalse(cache.isEmpty());
		cache.clear();
		assertNull(cache.getRules(ignoreTestDir.getAbsolutePath()));
		assertTrue(cache.isEmpty());
	}


	public void testPriorities() {
		ignoreTestDir = JGitTestUtil.getTestResourceFile("excludeTest");
		assertTrue("Test resource directory is not a directory",ignoreTestDir.isDirectory());
		createExcludeFile();

		SimpleIgnoreCache cache = new SimpleIgnoreCache(ignoreTestDir);
		File test = new File(ignoreTestDir.getAbsolutePath() + "/src/test.stp");
		assertTrue("Resource file " + test.getName() + " is missing", test.exists());
		try {
			cache.partiallyInitialize(test);
		} catch (IOException e) {
			e.printStackTrace();
			fail("IOException when attempting to lazy initialize");
		}
		IgnoreNode gitignore = cache.getRules(ignoreTestDir.getAbsolutePath() + "/src");
		assertNotNull("Excludes file was not initialized", gitignore);


		//Test basic exclude file
		boolean result = cache.isIgnored(test);

		/*
		 * src/.gitignore:
		 * /*.st?
		 * !/test.stp
		 * !/a.c
		 * /a.c
		 *
		 */
		assertEquals(4, gitignore.getRules().size());
		assertFalse("Unexpected match for " + test.toString(), result);

		test = new File(ignoreTestDir.getAbsolutePath() + "/src/a.c");
		assertTrue("Failed to match " + test.toString(), cache.isIgnored(test));

		/*
		 * new/.gitignore:
		 * /notarealfile
		 *
		 * new/a/.gitignore:
		 * /notarealfile
		 *
		 * new/a/b2/.gitignore:
		 * <does not exist>
		 *
		 * new/a/b2/c/.gitignore:
		 * /notarealfile
		 */
		test = new File(ignoreTestDir.getAbsolutePath() + "/new/a/b2/c/test.stp");
		assertFalse("Failed to match " + test.toString(), cache.isIgnored(test));
	}


	/**
	 * Attempt to write an exclude file
	 * @param path
	 * @param contents
	 */
	private void createIgnoreFile(String path, String[] contents) {
		assertTrue("Test resource directory is not a directory",ignoreTestDir.isDirectory());
		File ignoreFile = new File(path);
		ignoreFile.delete();
		//Jump through some hoops to create the exclude file
		toDelete.add(ignoreFile);

		try {
			if (!ignoreFile.createNewFile()) {
				fail("Could not create ignore file" + ignoreFile.getAbsolutePath());
			}

			BufferedWriter bw = new BufferedWriter(new FileWriter (ignoreFile));
			for (String s : contents) {
				bw.write(s + "\n");
			}
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
		File parent= new File(ignoreTestDir.getAbsolutePath() + "/.git/info");
		if (!parent.exists())
			parent.mkdirs();

		createIgnoreFile(ignoreTestDir.getAbsolutePath() + "/.git/info/exclude", content);
	}

	private void deleteIgnoreFiles() {
		for (File f : toDelete) {
			f.delete();
		}

		//Systematically delete exclude parent dirs
		File f = new File(ignoreTestDir.getAbsoluteFile() + "/.git/info");
		f.delete();
		f = new File(ignoreTestDir.getAbsoluteFile() + "/.git");
		f.delete();
	}




}


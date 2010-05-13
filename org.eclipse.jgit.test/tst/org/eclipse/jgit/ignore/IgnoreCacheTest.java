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

import junit.framework.TestCase;

import org.eclipse.jgit.util.JGitTestUtil;

/**
 *
 * @author Charley Wang
 *
 */
public class IgnoreCacheTest extends TestCase {


	public void testInitialization() {
		File ignoreTestDir = JGitTestUtil.getTestResourceFile("excludeTest");
		assertTrue("Test resource directory is not a directory",ignoreTestDir.isDirectory());
		SimpleIgnoreCache cache = new SimpleIgnoreCache(ignoreTestDir);
		File test = new File(ignoreTestDir.getAbsolutePath() + "/new/a/b1/test.stp");
		assertTrue("Missing file " + test.getAbsolutePath(), test.exists());

		try {
			cache.partiallyInitialize(test);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			fail("IOException when attempting to lazy initialize");
			e.printStackTrace();
		}

		/*
		 * Every folder along the path has a .gitignore file. Therefore every
		 * folder should have been added and initialized
		 */
		boolean result = cache.isIgnored(test);
		assertFalse("Unexpected match for " + test.toString(), result);

		File folder = test;

		while (!folder.getAbsolutePath().equals(ignoreTestDir.getAbsolutePath()) && folder.length() > 0) {
			folder = folder.getParentFile();
			IgnoreNode rules = cache.getRules(folder.getAbsolutePath());
			assertNotNull("Ignore file was not initialized for " + folder.getAbsolutePath(), rules);
			assertEquals(1, rules.getRules().size());
		}
	}

	public void testExclude() {
		File ignoreTestDir = JGitTestUtil.getTestResourceFile("excludeTest");
		assertTrue("Test resource directory is not a directory",ignoreTestDir.isDirectory());
		File excludeFile = new File(ignoreTestDir.getAbsolutePath() + "/.git/info/exclude");

		//Jump through some hoops to create the exclude file
		if (!excludeFile.exists()) {

			File exclude = new File(ignoreTestDir.getAbsolutePath() + "/.git/info");
			if (!exclude.exists() && !exclude.mkdirs()) {
				fail("Could not create .git/info directory");
			}

			try {
				if (!excludeFile.createNewFile()) {
					fail("Could not create exclude file");
				}

				BufferedWriter bw = new BufferedWriter(new FileWriter (excludeFile));
				bw.write("/test.stp\n");
				bw.write("/notignored");
				bw.flush();
				bw.close();

			} catch (IOException e1) {
				fail("Could not create exclude file");
				e1.printStackTrace();
			}
		}

		SimpleIgnoreCache cache = new SimpleIgnoreCache(ignoreTestDir);
		File test = new File(ignoreTestDir.getAbsolutePath() + "/test.stp");
		assertTrue("Missing file " + test.getAbsolutePath(), test.exists());

		try {
			cache.partiallyInitialize(test);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			fail("IOException when attempting to lazy initialize");
			e.printStackTrace();
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

		//Test basic exclude file
		assertTrue("Did not match file " + test.toString(), result);

		//Test exclude file priority
		test = new File(ignoreTestDir.getAbsolutePath() + "/notignored");
		assertFalse("Abnormal priority for " + test.toString(), cache.isIgnored(test));


		//Test that /src/test.stp is not matched by /test.stp in exclude file
		test = new File(ignoreTestDir.getAbsolutePath() + "/src/test.stp");
		assertTrue("Missing file " + test.getAbsolutePath(), test.exists());
		assertFalse("Unexpected match for " + test.toString(), cache.isIgnored(test));
	}

	public void testPriorities() {
		File ignoreTestDir = JGitTestUtil.getTestResourceFile("excludeTest");
		assertTrue("Test resource directory is missing",ignoreTestDir.exists());
		assertTrue("Test resource directory is not a directory",ignoreTestDir.isDirectory());
		SimpleIgnoreCache cache = new SimpleIgnoreCache(ignoreTestDir);
		File test = new File(ignoreTestDir.getAbsolutePath() + "/src/test.stp");
		assertTrue("Resource file " + test.getName() + " is missing", test.exists());
		try {
			cache.partiallyInitialize(test);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			fail("IOException when attempting to lazy initialize");
			e.printStackTrace();
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
		result = cache.isIgnored(test);
		assertTrue("Failed to match " + test.toString(), cache.isIgnored(test));
	}



}


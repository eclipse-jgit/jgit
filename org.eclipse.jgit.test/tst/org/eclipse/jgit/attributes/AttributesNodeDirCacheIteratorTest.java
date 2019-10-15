/*
 * Copyright (C) 2010, Red Hat Inc.
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
package org.eclipse.jgit.attributes;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.attributes.Attribute.State;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests attributes node behavior on the index.
 */
public class AttributesNodeDirCacheIteratorTest extends RepositoryTestCase {

	private static final FileMode D = FileMode.TREE;

	private static final FileMode F = FileMode.REGULAR_FILE;

	private static Attribute EOL_LF = new Attribute("eol", "lf");

	private static Attribute DELTA_UNSET = new Attribute("delta", State.UNSET);

	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);

	}

	@Test
	public void testRules() throws Exception {
		writeAttributesFile(".git/info/attributes", "windows* eol=crlf");

		writeAttributesFile(".gitattributes", "*.txt eol=lf");
		writeTrashFile("windows.file", "");
		writeTrashFile("windows.txt", "");
		writeTrashFile("readme.txt", "");

		writeAttributesFile("src/config/.gitattributes", "*.txt -delta");
		writeTrashFile("src/config/readme.txt", "");
		writeTrashFile("src/config/windows.file", "");
		writeTrashFile("src/config/windows.txt", "");

		// Adds file to index
		git.add().addFilepattern(".").call();

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, ".gitattributes");
			assertIteration(walk, F, "readme.txt", asList(EOL_LF));

			assertIteration(walk, D, "src");

			assertIteration(walk, D, "src/config");
			assertIteration(walk, F, "src/config/.gitattributes");
			assertIteration(walk, F, "src/config/readme.txt",
					asList(DELTA_UNSET));
			assertIteration(walk, F, "src/config/windows.file", null);
			assertIteration(walk, F, "src/config/windows.txt",
					asList(DELTA_UNSET));

			assertIteration(walk, F, "windows.file", null);
			assertIteration(walk, F, "windows.txt", asList(EOL_LF));

			assertFalse("Not all files tested", walk.next());
		}
	}

	/**
	 * Checks that if there is no .gitattributes file in the repository
	 * everything still work fine.
	 *
	 * @throws Exception
	 */
	@Test
	public void testNoAttributes() throws Exception {
		writeTrashFile("l0.txt", "");
		writeTrashFile("level1/l1.txt", "");
		writeTrashFile("level1/level2/l2.txt", "");

		// Adds file to index
		git.add().addFilepattern(".").call();

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, "l0.txt");

			assertIteration(walk, D, "level1");
			assertIteration(walk, F, "level1/l1.txt");

			assertIteration(walk, D, "level1/level2");
			assertIteration(walk, F, "level1/level2/l2.txt");

			assertFalse("Not all files tested", walk.next());
		}
	}

	/**
	 * Checks that empty .gitattribute files do not return incorrect value.
	 *
	 * @throws Exception
	 */
	@Test
	public void testEmptyGitAttributeFile() throws Exception {
		writeAttributesFile(".git/info/attributes", "");
		writeTrashFile("l0.txt", "");
		writeAttributesFile(".gitattributes", "");
		writeTrashFile("level1/l1.txt", "");
		writeTrashFile("level1/level2/l2.txt", "");

		// Adds file to index
		git.add().addFilepattern(".").call();

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, ".gitattributes");
			assertIteration(walk, F, "l0.txt");

			assertIteration(walk, D, "level1");
			assertIteration(walk, F, "level1/l1.txt");

			assertIteration(walk, D, "level1/level2");
			assertIteration(walk, F, "level1/level2/l2.txt");

			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testNoMatchingAttributes() throws Exception {
		writeAttributesFile(".git/info/attributes", "*.java delta");
		writeAttributesFile(".gitattributes", "*.java -delta");
		writeAttributesFile("levelA/.gitattributes", "*.java eol=lf");
		writeAttributesFile("levelB/.gitattributes", "*.txt eol=lf");

		writeTrashFile("levelA/lA.txt", "");

		// Adds file to index
		git.add().addFilepattern(".").call();

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, ".gitattributes");

			assertIteration(walk, D, "levelA");
			assertIteration(walk, F, "levelA/.gitattributes");
			assertIteration(walk, F, "levelA/lA.txt");

			assertIteration(walk, D, "levelB");
			assertIteration(walk, F, "levelB/.gitattributes");

			assertFalse("Not all files tested", walk.next());
		}
	}

	@Test
	public void testIncorrectAttributeFileName() throws Exception {
		writeAttributesFile("levelA/file.gitattributes", "*.txt -delta");
		writeAttributesFile("gitattributes", "*.txt eol=lf");

		writeTrashFile("l0.txt", "");
		writeTrashFile("levelA/lA.txt", "");

		// Adds file to index
		git.add().addFilepattern(".").call();

		try (TreeWalk walk = beginWalk()) {
			assertIteration(walk, F, "gitattributes");

			assertIteration(walk, F, "l0.txt");

			assertIteration(walk, D, "levelA");
			assertIteration(walk, F, "levelA/file.gitattributes");
			assertIteration(walk, F, "levelA/lA.txt");

			assertFalse("Not all files tested", walk.next());
		}
	}

	private void assertIteration(TreeWalk walk, FileMode type, String pathName)
			throws IOException {
		assertIteration(walk, type, pathName,
				Collections.<Attribute> emptyList());
	}

	private void assertIteration(TreeWalk walk, FileMode type, String pathName,
			List<Attribute> nodeAttrs) throws IOException {
		assertTrue("walk has entry", walk.next());
		assertEquals(pathName, walk.getPathString());
		assertEquals(type, walk.getFileMode(0));
		DirCacheIterator itr = walk.getTree(0, DirCacheIterator.class);
		assertNotNull("has tree", itr);

		AttributesNode attributesNode = itr.getEntryAttributesNode(db
				.newObjectReader());
		assertAttributesNode(walk, pathName, attributesNode, nodeAttrs);

		if (D.equals(type))
			walk.enterSubtree();

	}

	private void assertAttributesNode(TreeWalk walk, String pathName,
			AttributesNode attributesNode, List<Attribute> nodeAttrs)
					throws IOException {
		if (attributesNode == null)
			assertTrue(nodeAttrs == null || nodeAttrs.isEmpty());
		else {

			Attributes entryAttributes = new Attributes();
			new AttributesHandler(walk).mergeAttributes(attributesNode,
					pathName,
					false,
					entryAttributes);

			if (nodeAttrs != null && !nodeAttrs.isEmpty()) {
				for (Attribute attribute : nodeAttrs) {
					assertThat(entryAttributes.getAll(),
							hasItem(attribute));
				}
			} else {
				assertTrue(
						"The entry "
								+ pathName
								+ " should not have any attributes. Instead, the following attributes are applied to this file "
								+ entryAttributes.toString(),
						entryAttributes.isEmpty());
			}
		}
	}

	private void writeAttributesFile(String name, String... rules)
			throws IOException {
		StringBuilder data = new StringBuilder();
		for (String line : rules)
			data.append(line + "\n");
		writeTrashFile(name, data.toString());
	}

	private TreeWalk beginWalk() throws Exception {
		TreeWalk newWalk = new TreeWalk(db);
		newWalk.addTree(new DirCacheIterator(db.readDirCache()));
		return newWalk;
	}
}

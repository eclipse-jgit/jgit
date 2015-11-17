/*
 * Copyright (C) 2014, Obeo.
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

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.attributes.Attribute.State;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.junit.Test;

/**
 * Tests attributes node behavior on the local filesystem.
 */
public class AttributesNodeWorkingTreeIteratorTest extends RepositoryTestCase {

	private static final FileMode D = FileMode.TREE;

	private static final FileMode F = FileMode.REGULAR_FILE;

	private static Attribute EOL_LF = new Attribute("eol", "lf");

	private static Attribute DELTA_UNSET = new Attribute("delta", State.UNSET);

	private TreeWalk walk;

	@Test
	public void testRules() throws Exception {

		File customAttributeFile = File.createTempFile("tmp_",
				"customAttributeFile", null);
		customAttributeFile.deleteOnExit();

		JGitTestUtil.write(customAttributeFile, "*.txt custom=value");
		db.getConfig().setString("core", null, "attributesfile",
				customAttributeFile.getAbsolutePath());
		writeAttributesFile(".git/info/attributes", "windows* eol=crlf");

		writeAttributesFile(".gitattributes", "*.txt eol=lf");
		writeTrashFile("windows.file", "");
		writeTrashFile("windows.txt", "");
		writeTrashFile("global.txt", "");
		writeTrashFile("readme.txt", "");

		writeAttributesFile("src/config/.gitattributes", "*.txt -delta");
		writeTrashFile("src/config/readme.txt", "");
		writeTrashFile("src/config/windows.file", "");
		writeTrashFile("src/config/windows.txt", "");

		walk = beginWalk();

		assertIteration(F, ".gitattributes");
		assertIteration(F, "global.txt", asList(EOL_LF));
		assertIteration(F, "readme.txt", asList(EOL_LF));

		assertIteration(D, "src");

		assertIteration(D, "src/config");
		assertIteration(F, "src/config/.gitattributes");
		assertIteration(F, "src/config/readme.txt", asList(DELTA_UNSET));
		assertIteration(F, "src/config/windows.file", null);
		assertIteration(F, "src/config/windows.txt", asList(DELTA_UNSET));

		assertIteration(F, "windows.file", null);
		assertIteration(F, "windows.txt", asList(EOL_LF));

		endWalk();
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

		walk = beginWalk();

		assertIteration(F, "l0.txt");

		assertIteration(D, "level1");
		assertIteration(F, "level1/l1.txt");

		assertIteration(D, "level1/level2");
		assertIteration(F, "level1/level2/l2.txt");

		endWalk();
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

		walk = beginWalk();

		assertIteration(F, ".gitattributes");
		assertIteration(F, "l0.txt");

		assertIteration(D, "level1");
		assertIteration(F, "level1/l1.txt");

		assertIteration(D, "level1/level2");
		assertIteration(F, "level1/level2/l2.txt");

		endWalk();
	}

	@Test
	public void testNoMatchingAttributes() throws Exception {
		writeAttributesFile(".git/info/attributes", "*.java delta");
		writeAttributesFile(".gitattributes", "*.java -delta");
		writeAttributesFile("levelA/.gitattributes", "*.java eol=lf");
		writeAttributesFile("levelB/.gitattributes", "*.txt eol=lf");

		writeTrashFile("levelA/lA.txt", "");

		walk = beginWalk();

		assertIteration(F, ".gitattributes");

		assertIteration(D, "levelA");
		assertIteration(F, "levelA/.gitattributes");
		assertIteration(F, "levelA/lA.txt");

		assertIteration(D, "levelB");
		assertIteration(F, "levelB/.gitattributes");

		endWalk();
	}

	private void assertIteration(FileMode type, String pathName)
			throws IOException {
		assertIteration(type, pathName, Collections.<Attribute> emptyList());
	}

	private void assertIteration(FileMode type, String pathName,
			List<Attribute> nodeAttrs)
			throws IOException {
		assertTrue("walk has entry", walk.next());
		assertEquals(pathName, walk.getPathString());
		assertEquals(type, walk.getFileMode(0));
		WorkingTreeIterator itr = walk.getTree(0, WorkingTreeIterator.class);
		assertNotNull("has tree", itr);

		AttributesNode attributesNode = itr.getEntryAttributesNode();
		assertAttributesNode(pathName, attributesNode, nodeAttrs);
		if (D.equals(type))
			walk.enterSubtree();

	}

	private void assertAttributesNode(String pathName,
			AttributesNode attributesNode, List<Attribute> nodeAttrs)
					throws IOException {
		if (attributesNode == null)
			assertTrue(nodeAttrs == null || nodeAttrs.isEmpty());
		else {

			Attributes entryAttributes = new Attributes();
			new AttributesHandler(walk).mergeAttributes(attributesNode,
					pathName, false,
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

	private TreeWalk beginWalk() {
		TreeWalk newWalk = new TreeWalk(db);
		newWalk.addTree(new FileTreeIterator(db));
		return newWalk;
	}

	private void endWalk() throws IOException {
		assertFalse("Not all files tested", walk.next());
	}
}

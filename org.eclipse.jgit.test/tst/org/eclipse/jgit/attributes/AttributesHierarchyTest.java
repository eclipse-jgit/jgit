/*
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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.Test;

/**
 * Tests {@link AttributesHierarchy}
 */
public class AttributesHierarchyTest extends RepositoryTestCase {

	@Test
	public void testExpandNonMacro() throws Exception {
		AttributesHierarchy manager = setupAttributesHierarchy(null, null, null);

		assertListEquals(attrs("text"), manager.expandMacro(attr("text")));
		assertListEquals(attrs("-text"), manager.expandMacro(attr("-text")));
		assertListEquals(attrs("!text"), manager.expandMacro(attr("!text")));
		assertListEquals(attrs("text=auto"),
				manager.expandMacro(attr("text=auto")));
	}

	@Test
	public void testExpandBuiltInMacro() throws Exception {
		AttributesHierarchy manager = setupAttributesHierarchy(null, null, null);

		assertListEquals(attrs("binary -diff -merge -text"),
				manager.expandMacro(attr("binary")));
		assertListEquals(attrs("-binary diff merge text"),
				manager.expandMacro(attr("-binary")));
		assertListEquals(attrs("!binary !diff !merge !text"),
				manager.expandMacro(attr("!binary")));
	}

	@Test
	public void testCustomGlobalMacro() throws Exception {
		AttributesHierarchy manager = setupAttributesHierarchy(
				"[attr]foo a -b !c d=e", null, null);

		assertListEquals(attrs("foo a -b !c d=e"),
				manager.expandMacro(attr("foo")));
		assertListEquals(attrs("-foo -a b !c d=e"),
				manager.expandMacro(attr("-foo")));
		assertListEquals(attrs("!foo !a !b !c !d"),
				manager.expandMacro(attr("!foo")));
		assertListEquals(attrs("foo=bar a -b !c d=bar"),
				manager.expandMacro(attr("foo=bar")));
	}

	@Test
	public void testInfoOverridesGlobal() throws Exception {
		AttributesHierarchy manager = setupAttributesHierarchy("[attr]foo bar1",
				"[attr]foo bar2", null);

		assertListEquals(attrs("foo bar2"), manager.expandMacro(attr("foo")));
	}

	@Test
	public void testWorkDirRootOverridesGlobal() throws Exception {
		AttributesHierarchy manager = setupAttributesHierarchy("[attr]foo bar1", null,
				"[attr]foo bar3");

		assertListEquals(attrs("foo bar3"), manager.expandMacro(attr("foo")));
	}

	@Test
	public void testInfoOverridesWorkDirRoot() throws Exception {
		AttributesHierarchy manager = setupAttributesHierarchy("[attr]foo bar1",
				"[attr]foo bar2", "[attr]foo bar3");

		assertListEquals(attrs("foo bar2"), manager.expandMacro(attr("foo")));
	}

	@Test
	public void testRecursiveMacro() throws Exception {
		AttributesHierarchy manager = setupAttributesHierarchy("[attr]foo x bar -foo",
				null, null);

		assertListEquals(attrs("-foo -x -bar"),
				manager.expandMacro(attr("foo")));
	}

	@Test
	public void testCyclicMacros() throws Exception {
		AttributesHierarchy manager = setupAttributesHierarchy(
				"[attr]foo x -bar\n[attr]bar y -foo", null, null);

		assertListEquals(attrs("foo x -bar -y"),
				manager.expandMacro(attr("foo")));
	}

	private static Collection<Attribute> attrs(String s) {
		return new AttributesRule("*", s).getAttributes();
	}

	private static Attribute attr(String s) {
		return new AttributesRule("*", s).getAttributes().get(0);
	}

	private static <T> void assertListEquals(Collection<T> expected,
			Collection<T> actual) {
		assertEquals(new ArrayList<T>(expected), new ArrayList<T>(actual));
	}

	private AttributesHierarchy setupAttributesHierarchy(
			String globalAttributesContent,
			String infoAttributesContent, String workDirRootAttributesContent)
					throws Exception {
		FileBasedConfig config = db.getConfig();
		if (globalAttributesContent != null) {
			File f = new File(db.getDirectory(), "global/attributes");
			write(f, globalAttributesContent);
			config.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_ATTRIBUTESFILE,
					f.getAbsolutePath());

		}
		if (infoAttributesContent != null) {
			File f = new File(db.getDirectory(), Constants.INFO_ATTRIBUTES);
			write(f, infoAttributesContent);
		}
		config.save();

		if (workDirRootAttributesContent != null) {
			createFile(Constants.DOT_GIT_ATTRIBUTES,
					workDirRootAttributesContent);
		}
		return db.getAttributesHierarchy();
	}

	private void createFile(String path, String content)
			throws Exception {
		int pos = path.lastIndexOf('/');
		if (pos < 0) {
			writeTrashFile(path, content);
		} else {
			writeTrashFile(path.substring(0, pos), path.substring(pos + 1),
					content);
		}
	}
}

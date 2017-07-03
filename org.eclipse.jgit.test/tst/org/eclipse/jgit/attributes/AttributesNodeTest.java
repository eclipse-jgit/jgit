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

import static org.eclipse.jgit.attributes.Attribute.State.SET;
import static org.eclipse.jgit.attributes.Attribute.State.UNSET;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.After;
import org.junit.Test;

/**
 * Test {@link AttributesNode}
 */
public class AttributesNodeTest {
	private static final TreeWalk DUMMY_WALK = new TreeWalk(
			new InMemoryRepository(new DfsRepositoryDescription("FooBar")));

	private static final Attribute A_SET_ATTR = new Attribute("A", SET);

	private static final Attribute A_UNSET_ATTR = new Attribute("A", UNSET);

	private static final Attribute B_SET_ATTR = new Attribute("B", SET);

	private static final Attribute B_UNSET_ATTR = new Attribute("B", UNSET);

	private static final Attribute C_VALUE_ATTR = new Attribute("C", "value");

	private static final Attribute C_VALUE2_ATTR = new Attribute("C", "value2");

	private InputStream is;

	@After
	public void after() throws IOException {
		if (is != null)
			is.close();
	}

	@Test
	public void testBasic() throws IOException {
		String attributeFileContent = "*.type1 A -B C=value\n"
				+ "*.type2 -A B C=value2";

		is = new ByteArrayInputStream(attributeFileContent.getBytes());
		AttributesNode node = new AttributesNode();
		node.parse(is);
		assertAttribute("file.type1", node,
				asSet(A_SET_ATTR, B_UNSET_ATTR, C_VALUE_ATTR));
		assertAttribute("file.type2", node,
				asSet(A_UNSET_ATTR, B_SET_ATTR, C_VALUE2_ATTR));
	}

	@Test
	public void testNegativePattern() throws IOException {
		String attributeFileContent = "!*.type1 A -B C=value\n"
				+ "!*.type2 -A B C=value2";

		is = new ByteArrayInputStream(attributeFileContent.getBytes());
		AttributesNode node = new AttributesNode();
		node.parse(is);
		assertAttribute("file.type1", node, new Attributes());
		assertAttribute("file.type2", node, new Attributes());
	}

	@Test
	public void testEmptyNegativeAttributeKey() throws IOException {
		String attributeFileContent = "*.type1 - \n" //
				+ "*.type2 -   -A";
		is = new ByteArrayInputStream(attributeFileContent.getBytes());
		AttributesNode node = new AttributesNode();
		node.parse(is);
		assertAttribute("file.type1", node, new Attributes());
		assertAttribute("file.type2", node, asSet(A_UNSET_ATTR));
	}

	@Test
	public void testEmptyValueKey() throws IOException {
		String attributeFileContent = "*.type1 = \n" //
				+ "*.type2 =value\n"//
				+ "*.type3 attr=\n";
		is = new ByteArrayInputStream(attributeFileContent.getBytes());
		AttributesNode node = new AttributesNode();
		node.parse(is);
		assertAttribute("file.type1", node, new Attributes());
		assertAttribute("file.type2", node, new Attributes());
		assertAttribute("file.type3", node, asSet(new Attribute("attr", "")));
	}

	@Test
	public void testEmptyLine() throws IOException {
		String attributeFileContent = "*.type1 A -B C=value\n" //
				+ "\n" //
				+ "    \n" //
				+ "*.type2 -A B C=value2";

		is = new ByteArrayInputStream(attributeFileContent.getBytes());
		AttributesNode node = new AttributesNode();
		node.parse(is);
		assertAttribute("file.type1", node,
				asSet(A_SET_ATTR, B_UNSET_ATTR, C_VALUE_ATTR));
		assertAttribute("file.type2", node,
				asSet(A_UNSET_ATTR, B_SET_ATTR, C_VALUE2_ATTR));
	}

	@Test
	public void testTabSeparator() throws IOException {
		String attributeFileContent = "*.type1 \tA -B\tC=value\n"
				+ "*.type2\t -A\tB C=value2\n" //
				+ "*.type3  \t\t   B\n" //
				+ "*.type3\t-A";//

		is = new ByteArrayInputStream(attributeFileContent.getBytes());
		AttributesNode node = new AttributesNode();
		node.parse(is);
		assertAttribute("file.type1", node,
				asSet(A_SET_ATTR, B_UNSET_ATTR, C_VALUE_ATTR));
		assertAttribute("file.type2", node,
				asSet(A_UNSET_ATTR, B_SET_ATTR, C_VALUE2_ATTR));
		assertAttribute("file.type3", node, asSet(A_UNSET_ATTR, B_SET_ATTR));
	}

	@Test
	public void testDoubleAsteriskAtEnd() throws IOException {
		String attributeFileContent = "dir/** \tA -B\tC=value";

		is = new ByteArrayInputStream(attributeFileContent.getBytes());
		AttributesNode node = new AttributesNode();
		node.parse(is);
		assertAttribute("dir", node,
				asSet(new Attribute[]{}));
		assertAttribute("dir/", node,
				asSet(new Attribute[]{}));
		assertAttribute("dir/file.type1", node,
				asSet(A_SET_ATTR, B_UNSET_ATTR, C_VALUE_ATTR));
		assertAttribute("dir/sub/", node,
				asSet(A_SET_ATTR, B_UNSET_ATTR, C_VALUE_ATTR));
		assertAttribute("dir/sub/file.type1", node,
				asSet(A_SET_ATTR, B_UNSET_ATTR, C_VALUE_ATTR));
	}

	private void assertAttribute(String path, AttributesNode node,
			Attributes attrs) throws IOException {
		Attributes attributes = new Attributes();
		new AttributesHandler(DUMMY_WALK).mergeAttributes(node, path, false,
				attributes);
		assertEquals(attrs, attributes);
	}

	static Attributes asSet(Attribute... attrs) {
		return new Attributes(attrs);
	}

}

/*
 * Copyright (C) 2014, Obeo. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.attributes;

import static java.nio.charset.StandardCharsets.UTF_8;
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

		is = new ByteArrayInputStream(attributeFileContent.getBytes(UTF_8));
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

		is = new ByteArrayInputStream(attributeFileContent.getBytes(UTF_8));
		AttributesNode node = new AttributesNode();
		node.parse(is);
		assertAttribute("file.type1", node, new Attributes());
		assertAttribute("file.type2", node, new Attributes());
	}

	@Test
	public void testEmptyNegativeAttributeKey() throws IOException {
		String attributeFileContent = "*.type1 - \n" //
				+ "*.type2 -   -A";
		is = new ByteArrayInputStream(attributeFileContent.getBytes(UTF_8));
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
		is = new ByteArrayInputStream(attributeFileContent.getBytes(UTF_8));
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

		is = new ByteArrayInputStream(attributeFileContent.getBytes(UTF_8));
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

		is = new ByteArrayInputStream(attributeFileContent.getBytes(UTF_8));
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

		is = new ByteArrayInputStream(attributeFileContent.getBytes(UTF_8));
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

/*
 * Copyright (C) 2010, Marc Strapetz <marc.strapetz@syntevo.com>
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

import static org.junit.Assert.assertEquals;

import java.io.*;
import java.util.*;

import org.junit.Test;

public class AttributesQueryTest {

	private static final Attribute DELTA_SET = new Attribute("delta",
			AttributeValue.SET);

	private static final Attribute DELTA_UNSET = new Attribute("delta",
			AttributeValue.UNSET);

	private static final Attribute EOL_CRLF = new Attribute("eol",
			AttributeValue.createValue("crlf"));

	private static final Attribute EOL_LF = new Attribute("eol",
			AttributeValue.createValue("lf"));

	private static final Attribute EOL_SET = new Attribute("eol",
			AttributeValue.SET);

	private static final Attribute TEXT_AUTO = new Attribute("text",
			AttributeValue.createValue("auto"));

	private static final Attribute TEXT_UNSET = new Attribute("text",
			AttributeValue.UNSET);

	@Test
	public void test() throws IOException {
		final StringBuilder gitInfoAttributesBuilder = new StringBuilder();
		addLine(gitInfoAttributesBuilder, "windows* eol=crlf");
		addLine(gitInfoAttributesBuilder, "dir/subdir/windows-lf.txt eol=lf");
		final Attributes gitInfoAttributes = new Attributes();
		gitInfoAttributes.parse(new StringReader(gitInfoAttributesBuilder
				.toString()));

		final StringBuilder rootBuilder = new StringBuilder();
		addLine(rootBuilder, "*.txt eol=lf");
		addLine(rootBuilder, "dir/subdir/* delta");
		final Attributes rootAttributes = new Attributes();
		rootAttributes.parse(new StringReader(rootBuilder.toString()));
		final AttributesQuery rootQuery = new AttributesQuery(rootAttributes,
				"", null);

		final StringBuilder dirBuilder = new StringBuilder();
		addLine(dirBuilder, "*.txt -delta");
		addLine(dirBuilder, "subdir/*.txt -text");
		final Attributes dirAttributes = new Attributes();
		dirAttributes.parse(new StringReader(dirBuilder.toString()));
		final AttributesQuery dirQuery = new AttributesQuery(dirAttributes,
				"dir", rootQuery);

		final StringBuilder dirSubdirBuilder = new StringBuilder();
		addLine(dirSubdirBuilder, "*.txt text=auto !eol");
		final Attributes dirSubdirAttributes = new Attributes();
		dirSubdirAttributes
				.parse(new StringReader(dirSubdirBuilder.toString()));
		final AttributesQuery dirSubdirQuery = new AttributesQuery(
				dirSubdirAttributes, "dir/subdir", dirQuery);

		final StringBuilder dirSubdir2Builder = new StringBuilder();
		addLine(dirSubdir2Builder, "*.txt eol");
		final Attributes dirSubdir2Attributes = new Attributes();
		dirSubdir2Attributes.parse(new StringReader(dirSubdir2Builder
				.toString()));
		final AttributesQuery dirSubdir2Query = new AttributesQuery(
				dirSubdir2Attributes, "dir/subdir2", dirQuery);

		AttributesQuery query = new AttributesQuery(gitInfoAttributes, "",
				rootQuery);
		assertPathAttributes(query, "file.txt", EOL_LF);
		assertPathAttributes(query, "windows.txt", EOL_CRLF);
		assertPathAttributes(query, "dir/subdir/file.txt", DELTA_SET, EOL_LF);
		assertPathAttributes(query, "dir/subdir2/file.txt", EOL_LF);

		query = new AttributesQuery(gitInfoAttributes, "", dirQuery);
		assertPathAttributes(query, "file.txt", EOL_LF);
		assertPathAttributes(query, "dir/file.txt", DELTA_UNSET, EOL_LF);
		assertPathAttributes(query, "dir/subdir/file.txt", TEXT_UNSET,
				DELTA_UNSET, EOL_LF);

		query = new AttributesQuery(gitInfoAttributes, "", dirSubdirQuery);
		assertPathAttributes(query, "file.txt", EOL_LF);
		assertPathAttributes(query, "dir/file.txt", DELTA_UNSET, EOL_LF);
		assertPathAttributes(query, "dir/subdir/file.txt", TEXT_AUTO,
				DELTA_UNSET);
		assertPathAttributes(query, "dir/subdir/windows.txt", EOL_CRLF,
				TEXT_AUTO, DELTA_UNSET);
		assertPathAttributes(query, "dir/subdir/windows-lf.txt", EOL_LF,
				TEXT_AUTO, DELTA_UNSET);

		query = new AttributesQuery(gitInfoAttributes, "", dirSubdir2Query);
		assertPathAttributes(query, "dir/subdir2/file.txt", EOL_SET,
				DELTA_UNSET);
		assertPathAttributes(query, "dir/subdir2/windows.txt", EOL_CRLF,
				DELTA_UNSET);
		assertPathAttributes(query, "dir/subdir2/windows-lf.txt", EOL_CRLF,
				DELTA_UNSET);
	}

	public void testManPageExample() throws IOException {
		final StringBuilder gitInfoAttributesBuilder = new StringBuilder();
		addLine(gitInfoAttributesBuilder, "a*      foo !bar -baz");
		final Attributes gitInfoAttributes = new Attributes();
		gitInfoAttributes.parse(new StringReader(gitInfoAttributesBuilder
				.toString()));

		final StringBuilder rootBuilder = new StringBuilder();
		addLine(rootBuilder, "abc     foo bar baz");
		final Attributes rootAttributes = new Attributes();
		rootAttributes.parse(new StringReader(rootBuilder.toString()));
		final AttributesQuery rootQuery = new AttributesQuery(rootAttributes,
				"", null);

		final StringBuilder tBuilder = new StringBuilder();
		addLine(tBuilder, "ab*     merge=filfre");
		addLine(tBuilder, "abc     -foo -bar");
		addLine(tBuilder, "*.c     frotz");

		final Attributes tAttributes = new Attributes();
		tAttributes.parse(new StringReader(tBuilder.toString()));
		final AttributesQuery tQuery = new AttributesQuery(tAttributes, "t",
				rootQuery);

		final AttributesQuery query = new AttributesQuery(gitInfoAttributes,
				"", tQuery);
		assertPathAttributes(query, "t/abc", new Attribute("foo",
				AttributeValue.SET),
				new Attribute("baz", AttributeValue.UNSET), new Attribute(
						"merge", AttributeValue.createValue("filfre")));
	}

	private void addLine(StringBuilder builder, String str) {
		builder.append(str);
		builder.append("\n");
	}

	private static void assertPathAttributes(AttributesQuery query,
			String path, Attribute... expected) {
		final AttributesAllCollector allCollector = new AttributesAllCollector();
		query.collect(path, allCollector);
		assertEquals(Arrays.asList(expected), allCollector.getAttributes());

		for (Attribute attribute : expected) {
			final AttributeValueCollector valueCollector = new AttributeValueCollector(
					attribute.getKey());
			query.collect(path, valueCollector);
			assertEquals(attribute.getValue(), valueCollector.getValue());
		}
	}
}
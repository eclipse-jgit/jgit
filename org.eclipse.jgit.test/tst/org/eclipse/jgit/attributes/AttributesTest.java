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

import junit.framework.*;

import java.io.*;
import java.util.*;

public class AttributesTest extends TestCase {

	private static final Attribute DELTA_SET = new Attribute("delta",
			AttributeValue.SET);

	private static final Attribute EOL_CRLF = new Attribute("eol",
			AttributeValue.createValue("crlf"));

	private static final Attribute EOL_LF = new Attribute("eol",
			AttributeValue.createValue("lf"));

	private static final Attribute EOL_SET = new Attribute("eol",
			AttributeValue.SET);

	private static final Attribute TEXT_AUTO = new Attribute("text",
			AttributeValue.createValue("auto"));

	private static final Attribute TEXT_SET = new Attribute("text",
			AttributeValue.SET);

	private static final Attribute TEXT_UNSET = new Attribute("text",
			AttributeValue.UNSET);

	public void testParsing() throws IOException {
		final Attributes attributes = new Attributes();
		attributes.parse(new StringReader(createSampleAttributeContent()));

		final List<String> patterns = attributes.getPatterns();
		assertEquals(Arrays.asList("*.x", "file1.txt", "file2.txt",
				"file3.txt", "file4.txt", "file4.txt", "file5.txt"), patterns);
		assertPathAttributes(attributes, "*.x", TEXT_AUTO);
		assertPathAttributes(attributes, "file1.txt", TEXT_SET, EOL_LF);
		assertPathAttributes(attributes, "file2.txt", TEXT_UNSET);
		assertPathAttributes(attributes, "file3.txt");
		assertPathAttributes(attributes, "file4.txt", TEXT_SET, EOL_SET);
		assertPathAttributes(attributes, "file5.txt", new Attribute("foo",
				AttributeValue.createValue("oof")), new Attribute("bar",
				AttributeValue.SET), new Attribute("baz", AttributeValue.UNSET));
	}

	public void testDefinitions() throws IOException {
		final Attributes attributes = new Attributes();
		final StringBuilder builder = new StringBuilder();
		final String str = "*.txt text=auto";
		addLine(builder, str);
		addLine(builder, "file1.txt eol=lf");
		addLine(builder, "file1.* delta");
		addLine(builder, "file2.txt text");
		addLine(builder, "file3.txt text");
		addLine(builder, "file3.txt -text");
		addLine(builder, "windows4.txt text");
		addLine(builder, "windows*.txt eol=crlf");
		addLine(builder, "windows5.txt !text !eol");

		attributes.parse(new StringReader(builder.toString()));
		assertPathAttributes(attributes, "foo.txt", TEXT_AUTO);
		assertPathAttributes(attributes, "file1.txt", DELTA_SET, EOL_LF,
				TEXT_AUTO);
		assertPathAttributes(attributes, "file2.txt", TEXT_SET);
		assertPathAttributes(attributes, "file3.txt", TEXT_UNSET);
		assertPathAttributes(attributes, "windows4.txt", EOL_CRLF, TEXT_SET);
		assertPathAttributes(attributes, "windows5.txt");
	}

	public void testReadWrite() throws IOException {
		final Attributes attributes = new Attributes();
		final String content = createSampleAttributeContent();
		attributes.parse(new StringReader(content));
		assertEquals(content, attributes.toText());
	}

	private String createSampleAttributeContent() {
		final StringBuilder builder = new StringBuilder();
		addLine(builder, "*.x  text=auto\t");
		addLine(builder, "file1.txt\ttext eol=lf");
		addLine(builder, "file2.txt\t-text\t \t");
		addLine(builder, "file3.txt");
		addLine(builder, " # Comment\r");
		addLine(builder, "<<<<<<< HEAD");
		addLine(builder, "file4.txt eol");
		addLine(builder, "=======");
		addLine(builder, "file4.txt text");
		addLine(builder, ">>>>>>> 756039980aa1984e44c651c2559312e1a813a7fd");
		addLine(builder, "");
		addLine(builder, "file5.txt foo=oof bar -baz !biz");
		return builder.toString();
	}

	private void addLine(StringBuilder builder, String str) {
		builder.append(str);
		builder.append("\n");
	}

	private static void assertPathAttributes(Attributes attributes,
			String path, Attribute... expected) {
		final AttributesAllCollector allCollector = new AttributesAllCollector();
		attributes.collect(path, allCollector, null);
		assertEquals(Arrays.asList(expected), allCollector.getAttributes());

		for (Attribute attribute : expected) {
			final AttributeValueCollector valueCollector = new AttributeValueCollector(
					attribute.getKey());
			attributes.collect(path, valueCollector, null);
			assertEquals(attribute.getValue(), valueCollector.getValue());
		}
	}
}
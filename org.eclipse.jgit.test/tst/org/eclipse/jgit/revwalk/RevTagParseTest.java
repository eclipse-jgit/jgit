/*
 * Copyright (C) 2008-2010, Google Inc.
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

package org.eclipse.jgit.revwalk;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.ByteArrayOutputStream;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.TagBuilder;
import org.junit.Test;

public class RevTagParseTest extends RepositoryTestCase {
	@Test
	public void testTagBlob() throws Exception {
		testOneType(Constants.OBJ_BLOB);
	}

	@Test
	public void testTagTree() throws Exception {
		testOneType(Constants.OBJ_TREE);
	}

	@Test
	public void testTagCommit() throws Exception {
		testOneType(Constants.OBJ_COMMIT);
	}

	@Test
	public void testTagTag() throws Exception {
		testOneType(Constants.OBJ_TAG);
	}

	private void testOneType(int typeCode) throws Exception {
		final ObjectId id = id("9788669ad918b6fcce64af8882fc9a81cb6aba67");
		final StringBuilder b = new StringBuilder();
		b.append("object " + id.name() + "\n");
		b.append("type " + Constants.typeString(typeCode) + "\n");
		b.append("tag v1.2.3.4.5\n");
		b.append("tagger A U. Thor <a_u_thor@example.com> 1218123387 +0700\n");
		b.append("\n");

		final RevWalk rw = new RevWalk(db);
		final RevTag c;

		c = new RevTag(id("9473095c4cb2f12aefe1db8a355fe3fafba42f67"));
		assertNull(c.getObject());
		assertNull(c.getTagName());

		c.parseCanonical(rw, b.toString().getBytes(UTF_8));
		assertNotNull(c.getObject());
		assertEquals(id, c.getObject().getId());
		assertSame(rw.lookupAny(id, typeCode), c.getObject());
	}

	@Test
	public void testParseAllFields() throws Exception {
		final ObjectId treeId = id("9788669ad918b6fcce64af8882fc9a81cb6aba67");
		final String name = "v1.2.3.4.5";
		final String taggerName = "A U. Thor";
		final String taggerEmail = "a_u_thor@example.com";
		final int taggerTime = 1218123387;

		final StringBuilder body = new StringBuilder();

		body.append("object ");
		body.append(treeId.name());
		body.append("\n");

		body.append("type tree\n");

		body.append("tag ");
		body.append(name);
		body.append("\n");

		body.append("tagger ");
		body.append(taggerName);
		body.append(" <");
		body.append(taggerEmail);
		body.append("> ");
		body.append(taggerTime);
		body.append(" +0700\n");

		body.append("\n");

		final RevWalk rw = new RevWalk(db);
		final RevTag c;

		c = new RevTag(id("9473095c4cb2f12aefe1db8a355fe3fafba42f67"));
		assertNull(c.getObject());
		assertNull(c.getTagName());

		c.parseCanonical(rw, body.toString().getBytes(UTF_8));
		assertNotNull(c.getObject());
		assertEquals(treeId, c.getObject().getId());
		assertSame(rw.lookupTree(treeId), c.getObject());

		assertNotNull(c.getTagName());
		assertEquals(name, c.getTagName());
		assertEquals("", c.getFullMessage());

		final PersonIdent cTagger = c.getTaggerIdent();
		assertNotNull(cTagger);
		assertEquals(taggerName, cTagger.getName());
		assertEquals(taggerEmail, cTagger.getEmailAddress());
	}

	@Test
	public void testParseOldStyleNoTagger() throws Exception {
		final ObjectId treeId = id("9788669ad918b6fcce64af8882fc9a81cb6aba67");
		final String name = "v1.2.3.4.5";
		final String message = "test\n" //
				+ "\n" //
				+ "-----BEGIN PGP SIGNATURE-----\n" //
				+ "Version: GnuPG v1.4.1 (GNU/Linux)\n" //
				+ "\n" //
				+ "iD8DBQBC0b9oF3Y\n" //
				+ "-----END PGP SIGNATURE------n";

		final StringBuilder body = new StringBuilder();

		body.append("object ");
		body.append(treeId.name());
		body.append("\n");

		body.append("type tree\n");

		body.append("tag ");
		body.append(name);
		body.append("\n");
		body.append("\n");
		body.append(message);

		final RevWalk rw = new RevWalk(db);
		final RevTag c;

		c = new RevTag(id("9473095c4cb2f12aefe1db8a355fe3fafba42f67"));
		assertNull(c.getObject());
		assertNull(c.getTagName());

		c.parseCanonical(rw, body.toString().getBytes(UTF_8));
		assertNotNull(c.getObject());
		assertEquals(treeId, c.getObject().getId());
		assertSame(rw.lookupTree(treeId), c.getObject());

		assertNotNull(c.getTagName());
		assertEquals(name, c.getTagName());
		assertEquals("test", c.getShortMessage());
		assertEquals(message, c.getFullMessage());

		assertNull(c.getTaggerIdent());
	}

	private RevTag create(String msg) throws Exception {
		final StringBuilder b = new StringBuilder();
		b.append("object 9788669ad918b6fcce64af8882fc9a81cb6aba67\n");
		b.append("type tree\n");
		b.append("tag v1.2.3.4.5\n");
		b.append("tagger A U. Thor <a_u_thor@example.com> 1218123387 +0700\n");
		b.append("\n");
		b.append(msg);

		final RevTag c;
		c = new RevTag(id("9473095c4cb2f12aefe1db8a355fe3fafba42f67"));
		c.parseCanonical(new RevWalk(db), b.toString().getBytes(UTF_8));
		return c;
	}

	@Test
	public void testParse_implicit_UTF8_encoded() throws Exception {
		final ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write("object 9788669ad918b6fcce64af8882fc9a81cb6aba67\n"
				.getBytes(UTF_8));
		b.write("type tree\n".getBytes(UTF_8));
		b.write("tag v1.2.3.4.5\n".getBytes(UTF_8));

		b
				.write("tagger F\u00f6r fattare <a_u_thor@example.com> 1218123387 +0700\n"
						.getBytes(UTF_8));
		b.write("\n".getBytes(UTF_8));
		b.write("Sm\u00f6rg\u00e5sbord\n".getBytes(UTF_8));
		b.write("\n".getBytes(UTF_8));
		b.write("\u304d\u308c\u3044\n".getBytes(UTF_8));
		final RevTag c;
		c = new RevTag(id("9473095c4cb2f12aefe1db8a355fe3fafba42f67"));
		c.parseCanonical(new RevWalk(db), b.toByteArray());

		assertEquals("F\u00f6r fattare", c.getTaggerIdent().getName());
		assertEquals("Sm\u00f6rg\u00e5sbord", c.getShortMessage());
		assertEquals("Sm\u00f6rg\u00e5sbord\n\n\u304d\u308c\u3044\n", c
				.getFullMessage());
	}

	@Test
	public void testParse_implicit_mixed_encoded() throws Exception {
		final ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write("object 9788669ad918b6fcce64af8882fc9a81cb6aba67\n"
				.getBytes(UTF_8));
		b.write("type tree\n".getBytes(UTF_8));
		b.write("tag v1.2.3.4.5\n".getBytes(UTF_8));
		b.write("tagger F\u00f6r fattare <a_u_thor@example.com> 1218123387 +0700\n"
				.getBytes(ISO_8859_1));
		b.write("\n".getBytes(UTF_8));
		b.write("Sm\u00f6rg\u00e5sbord\n".getBytes(UTF_8));
		b.write("\n".getBytes(UTF_8));
		b.write("\u304d\u308c\u3044\n".getBytes(UTF_8));
		final RevTag c;
		c = new RevTag(id("9473095c4cb2f12aefe1db8a355fe3fafba42f67"));
		c.parseCanonical(new RevWalk(db), b.toByteArray());

		assertEquals("F\u00f6r fattare", c.getTaggerIdent().getName());
		assertEquals("Sm\u00f6rg\u00e5sbord", c.getShortMessage());
		assertEquals("Sm\u00f6rg\u00e5sbord\n\n\u304d\u308c\u3044\n", c
				.getFullMessage());
	}

	/**
	 * Test parsing of a commit whose encoding is given and works.
	 *
	 * @throws Exception
	 */
	@Test
	public void testParse_explicit_encoded() throws Exception {
		final ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write("object 9788669ad918b6fcce64af8882fc9a81cb6aba67\n"
				.getBytes("EUC-JP"));
		b.write("type tree\n".getBytes("EUC-JP"));
		b.write("tag v1.2.3.4.5\n".getBytes("EUC-JP"));
		b
				.write("tagger F\u00f6r fattare <a_u_thor@example.com> 1218123387 +0700\n"
						.getBytes("EUC-JP"));
		b.write("encoding euc_JP\n".getBytes("EUC-JP"));
		b.write("\n".getBytes("EUC-JP"));
		b.write("\u304d\u308c\u3044\n".getBytes("EUC-JP"));
		b.write("\n".getBytes("EUC-JP"));
		b.write("Hi\n".getBytes("EUC-JP"));
		final RevTag c;
		c = new RevTag(id("9473095c4cb2f12aefe1db8a355fe3fafba42f67"));
		c.parseCanonical(new RevWalk(db), b.toByteArray());

		assertEquals("F\u00f6r fattare", c.getTaggerIdent().getName());
		assertEquals("\u304d\u308c\u3044", c.getShortMessage());
		assertEquals("\u304d\u308c\u3044\n\nHi\n", c.getFullMessage());
	}

	/**
	 * This is a twisted case, but show what we expect here. We can revise the
	 * expectations provided this case is updated.
	 *
	 * What happens here is that an encoding us given, but data is not encoded
	 * that way (and we can detect it), so we try other encodings.
	 *
	 * @throws Exception
	 */
	@Test
	public void testParse_explicit_bad_encoded() throws Exception {
		final ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write("object 9788669ad918b6fcce64af8882fc9a81cb6aba67\n"
				.getBytes(UTF_8));
		b.write("type tree\n".getBytes(UTF_8));
		b.write("tag v1.2.3.4.5\n".getBytes(UTF_8));
		b
				.write("tagger F\u00f6r fattare <a_u_thor@example.com> 1218123387 +0700\n"
						.getBytes(ISO_8859_1));
		b.write("encoding EUC-JP\n".getBytes(UTF_8));
		b.write("\n".getBytes(UTF_8));
		b.write("\u304d\u308c\u3044\n".getBytes(UTF_8));
		b.write("\n".getBytes(UTF_8));
		b.write("Hi\n".getBytes(UTF_8));
		final RevTag c;
		c = new RevTag(id("9473095c4cb2f12aefe1db8a355fe3fafba42f67"));
		c.parseCanonical(new RevWalk(db), b.toByteArray());

		assertEquals("F\u00f6r fattare", c.getTaggerIdent().getName());
		assertEquals("\u304d\u308c\u3044", c.getShortMessage());
		assertEquals("\u304d\u308c\u3044\n\nHi\n", c.getFullMessage());
	}

	/**
	 * This is a twisted case too, but show what we expect here. We can revise
	 * the expectations provided this case is updated.
	 *
	 * What happens here is that an encoding us given, but data is not encoded
	 * that way (and we can detect it), so we try other encodings. Here data
	 * could actually be decoded in the stated encoding, but we override using
	 * UTF-8.
	 *
	 * @throws Exception
	 */
	@Test
	public void testParse_explicit_bad_encoded2() throws Exception {
		final ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write("object 9788669ad918b6fcce64af8882fc9a81cb6aba67\n"
				.getBytes(UTF_8));
		b.write("type tree\n".getBytes(UTF_8));
		b.write("tag v1.2.3.4.5\n".getBytes(UTF_8));
		b
				.write("tagger F\u00f6r fattare <a_u_thor@example.com> 1218123387 +0700\n"
						.getBytes(UTF_8));
		b.write("encoding ISO-8859-1\n".getBytes(UTF_8));
		b.write("\n".getBytes(UTF_8));
		b.write("\u304d\u308c\u3044\n".getBytes(UTF_8));
		b.write("\n".getBytes(UTF_8));
		b.write("Hi\n".getBytes(UTF_8));
		final RevTag c;
		c = new RevTag(id("9473095c4cb2f12aefe1db8a355fe3fafba42f67"));
		c.parseCanonical(new RevWalk(db), b.toByteArray());

		assertEquals("F\u00f6r fattare", c.getTaggerIdent().getName());
		assertEquals("\u304d\u308c\u3044", c.getShortMessage());
		assertEquals("\u304d\u308c\u3044\n\nHi\n", c.getFullMessage());
	}

	@Test
	public void testParse_illegalEncoding() throws Exception {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write("object 9788669ad918b6fcce64af8882fc9a81cb6aba67\n".getBytes(UTF_8));
		b.write("type tree\n".getBytes(UTF_8));
		b.write("tag v1.0\n".getBytes(UTF_8));
		b.write("tagger t <t@example.com> 1218123387 +0700\n".getBytes(UTF_8));
		b.write("encoding utf-8logoutputencoding=gbk\n".getBytes(UTF_8));
		b.write("\n".getBytes(UTF_8));
		b.write("message\n".getBytes(UTF_8));

		RevTag t = new RevTag(id("9473095c4cb2f12aefe1db8a355fe3fafba42f67"));
		t.parseCanonical(new RevWalk(db), b.toByteArray());

		assertEquals("t", t.getTaggerIdent().getName());
		assertEquals("message", t.getShortMessage());
		assertEquals("message\n", t.getFullMessage());
	}

	@Test
	public void testParse_unsupportedEncoding() throws Exception {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write("object 9788669ad918b6fcce64af8882fc9a81cb6aba67\n".getBytes(UTF_8));
		b.write("type tree\n".getBytes(UTF_8));
		b.write("tag v1.0\n".getBytes(UTF_8));
		b.write("tagger t <t@example.com> 1218123387 +0700\n".getBytes(UTF_8));
		b.write("encoding it_IT.UTF8\n".getBytes(UTF_8));
		b.write("\n".getBytes(UTF_8));
		b.write("message\n".getBytes(UTF_8));

		RevTag t = new RevTag(id("9473095c4cb2f12aefe1db8a355fe3fafba42f67"));
		t.parseCanonical(new RevWalk(db), b.toByteArray());

		assertEquals("t", t.getTaggerIdent().getName());
		assertEquals("message", t.getShortMessage());
		assertEquals("message\n", t.getFullMessage());
	}

	@Test
	public void testParse_NoMessage() throws Exception {
		final String msg = "";
		final RevTag c = create(msg);
		assertEquals(msg, c.getFullMessage());
		assertEquals(msg, c.getShortMessage());
	}

	@Test
	public void testParse_OnlyLFMessage() throws Exception {
		final RevTag c = create("\n");
		assertEquals("\n", c.getFullMessage());
		assertEquals("", c.getShortMessage());
	}

	@Test
	public void testParse_ShortLineOnlyNoLF() throws Exception {
		final String shortMsg = "This is a short message.";
		final RevTag c = create(shortMsg);
		assertEquals(shortMsg, c.getFullMessage());
		assertEquals(shortMsg, c.getShortMessage());
	}

	@Test
	public void testParse_ShortLineOnlyEndLF() throws Exception {
		final String shortMsg = "This is a short message.";
		final String fullMsg = shortMsg + "\n";
		final RevTag c = create(fullMsg);
		assertEquals(fullMsg, c.getFullMessage());
		assertEquals(shortMsg, c.getShortMessage());
	}

	@Test
	public void testParse_ShortLineOnlyEmbeddedLF() throws Exception {
		final String fullMsg = "This is a\nshort message.";
		final String shortMsg = fullMsg.replace('\n', ' ');
		final RevTag c = create(fullMsg);
		assertEquals(fullMsg, c.getFullMessage());
		assertEquals(shortMsg, c.getShortMessage());
	}

	@Test
	public void testParse_ShortLineOnlyEmbeddedAndEndingLF() throws Exception {
		final String fullMsg = "This is a\nshort message.\n";
		final String shortMsg = "This is a short message.";
		final RevTag c = create(fullMsg);
		assertEquals(fullMsg, c.getFullMessage());
		assertEquals(shortMsg, c.getShortMessage());
	}

	@Test
	public void testParse_GitStyleMessage() throws Exception {
		final String shortMsg = "This fixes a bug.";
		final String body = "We do it with magic and pixie dust and stuff.\n"
				+ "\n" + "Signed-off-by: A U. Thor <author@example.com>\n";
		final String fullMsg = shortMsg + "\n" + "\n" + body;
		final RevTag c = create(fullMsg);
		assertEquals(fullMsg, c.getFullMessage());
		assertEquals(shortMsg, c.getShortMessage());
	}

	@Test
	public void testParse_PublicParseMethod() throws CorruptObjectException {
		TagBuilder src = new TagBuilder();
		try (ObjectInserter.Formatter fmt = new ObjectInserter.Formatter()) {
			src.setObjectId(fmt.idFor(Constants.OBJ_TREE, new byte[] {}),
					Constants.OBJ_TREE);
		}
		src.setTagger(committer);
		src.setTag("a.test");
		src.setMessage("Test tag\n\nThis is a test.\n");

		RevTag p = RevTag.parse(src.build());
		assertEquals(src.getObjectId(), p.getObject());
		assertEquals(committer, p.getTaggerIdent());
		assertEquals("a.test", p.getTagName());
		assertEquals("Test tag", p.getShortMessage());
		assertEquals(src.getMessage(), p.getFullMessage());
	}

	private static ObjectId id(String str) {
		return ObjectId.fromString(str);
	}
}

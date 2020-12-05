/*
 * Copyright (C) 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Test;

public class TagBuilderTest {

	// @formatter:off
	private static final String SIGNATURE = "-----BEGIN PGP SIGNATURE-----\n" +
			"Version: BCPG v1.60\n" +
			"\n" +
			"iQEcBAABCAAGBQJb9cVhAAoJEKX+6Axg/6TZeFsH/0CY0WX/z7U8+7S5giFX4wH4\n" +
			"opvBwqyt6OX8lgNwTwBGHFNt8LdmDCCmKoq/XwkNi3ARVjLhe3gBcKXNoavvPk2Z\n" +
			"gIg5ChevGkU4afWCOMLVEYnkCBGw2+86XhrK1P7gTHEk1Rd+Yv1ZRDJBY+fFO7yz\n" +
			"uSBuF5RpEY2sJiIvp27Gub/rY3B5NTR/feO/z+b9oiP/fMUhpRwG5KuWUsn9NPjw\n" +
			"3tvbgawYpU/2UnS+xnavMY4t2fjRYjsoxndPLb2MUX8X7vC7FgWLBlmI/rquLZVM\n" +
			"IQEKkjnA+lhejjK1rv+ulq4kGZJFKGYWYYhRDwFg5PTkzhudhN2SGUq5Wxq1Eg4=\n" +
			"=b9OI\n" +
			"-----END PGP SIGNATURE-----";

	// @formatter:on

	private static final String TAGGER_LINE = "A U. Thor <a_u_thor@example.com> 1218123387 +0700";

	private static final PersonIdent TAGGER = RawParseUtils
			.parsePersonIdent(TAGGER_LINE);

	@Test
	public void testTagSimple() throws Exception {
		TagBuilder t = new TagBuilder();
		t.setTag("sometag");
		t.setObjectId(ObjectId.zeroId(), Constants.OBJ_COMMIT);
		t.setEncoding(US_ASCII);
		t.setMessage("Short message only");
		t.setTagger(TAGGER);
		String tag = new String(t.build(), UTF_8);
		String expected = "object 0000000000000000000000000000000000000000\n"
				+ "type commit\n" //
				+ "tag sometag\n" //
				+ "tagger " + TAGGER_LINE + '\n' //
				+ "encoding US-ASCII\n" //
				+ '\n' //
				+ "Short message only";
		assertEquals(expected, tag);
	}

	@Test
	public void testTagWithSignatureShortMessageEndsInLF() throws Exception {
		TagBuilder t = new TagBuilder();
		t.setTag("sometag");
		t.setObjectId(ObjectId.zeroId(), Constants.OBJ_COMMIT);
		t.setEncoding(US_ASCII);
		t.setMessage("Short message only\n");
		t.setTagger(TAGGER);
		t.setGpgSignature(new GpgSignature(SIGNATURE.getBytes(US_ASCII)));
		String tag = new String(t.build(), UTF_8);
		String expected = "object 0000000000000000000000000000000000000000\n"
				+ "type commit\n" //
				+ "tag sometag\n" //
				+ "tagger " + TAGGER_LINE + '\n' //
				+ "encoding US-ASCII\n" //
				+ '\n' //
				+ "Short message only\n" //
				+ SIGNATURE + '\n';
		assertEquals(expected, tag);
	}

	@Test
	public void testTagWithSignatureMessageNoLF() {
		TagBuilder t = new TagBuilder();
		t.setTag("sometag");
		t.setObjectId(ObjectId.zeroId(), Constants.OBJ_COMMIT);
		t.setEncoding(US_ASCII);
		t.setMessage("A message\n\nthat does not end in LF");
		t.setTagger(TAGGER);
		t.setGpgSignature(new GpgSignature(SIGNATURE.getBytes(US_ASCII)));
		Throwable ex = assertThrows(Throwable.class, t::build);
		assertEquals(JGitText.get().signedTagMessageNoLf, ex.getMessage());
	}

	@Test
	public void testTagWithSignatureNoParagraphsMessage() throws Exception {
		TagBuilder t = new TagBuilder();
		t.setTag("sometag");
		t.setObjectId(ObjectId.zeroId(), Constants.OBJ_COMMIT);
		t.setEncoding(US_ASCII);
		t.setMessage("A strange\ntag message\n");
		t.setTagger(TAGGER);
		t.setGpgSignature(new GpgSignature(SIGNATURE.getBytes(US_ASCII)));
		String tag = new String(t.build(), UTF_8);
		String expected = "object 0000000000000000000000000000000000000000\n"
				+ "type commit\n" //
				+ "tag sometag\n" //
				+ "tagger " + TAGGER_LINE + '\n' //
				+ "encoding US-ASCII\n" //
				+ '\n' //
				+ "A strange\ntag message\n" //
				+ SIGNATURE + '\n';
		assertEquals(expected, tag);
	}

	@Test
	public void testTagWithSignatureLongMessage() throws Exception {
		TagBuilder t = new TagBuilder();
		t.setTag("sometag");
		t.setObjectId(ObjectId.zeroId(), Constants.OBJ_COMMIT);
		t.setMessage("Short message\n\nFollowed by explanations.\n");
		t.setTagger(TAGGER);
		t.setGpgSignature(new GpgSignature(SIGNATURE.getBytes(US_ASCII)));
		String tag = new String(t.build(), UTF_8);
		String expected = "object 0000000000000000000000000000000000000000\n"
				+ "type commit\n" //
				+ "tag sometag\n" //
				+ "tagger " + TAGGER_LINE + '\n' //
				+ '\n' //
				+ "Short message\n\nFollowed by explanations.\n" //
				+ SIGNATURE + '\n';
		assertEquals(expected, tag);
	}

	@Test
	public void testTagWithSignatureEmptyMessage() throws Exception {
		TagBuilder t = new TagBuilder();
		t.setTag("sometag");
		t.setObjectId(ObjectId.zeroId(), Constants.OBJ_COMMIT);
		t.setTagger(TAGGER);
		t.setMessage("");
		String emptyMsg = new String(t.build(), UTF_8);
		t.setGpgSignature(new GpgSignature(SIGNATURE.getBytes(US_ASCII)));
		String tag = new String(t.build(), UTF_8);
		String expected = "object 0000000000000000000000000000000000000000\n"
				+ "type commit\n" //
				+ "tag sometag\n" //
				+ "tagger " + TAGGER_LINE + '\n' //
				+ '\n';
		assertEquals(expected, emptyMsg);
		assertEquals(expected + SIGNATURE + '\n', tag);
	}

	@Test
	public void testTagWithSignatureOnly() throws Exception {
		TagBuilder t = new TagBuilder();
		t.setTag("sometag");
		t.setObjectId(ObjectId.zeroId(), Constants.OBJ_COMMIT);
		t.setTagger(TAGGER);
		String emptyMsg = new String(t.build(), UTF_8);
		t.setGpgSignature(new GpgSignature(SIGNATURE.getBytes(US_ASCII)));
		String tag = new String(t.build(), UTF_8);
		String expected = "object 0000000000000000000000000000000000000000\n"
				+ "type commit\n" //
				+ "tag sometag\n" //
				+ "tagger " + TAGGER_LINE + '\n' //
				+ '\n';
		assertEquals(expected, emptyMsg);
		assertEquals(expected + SIGNATURE + '\n', tag);
	}

}

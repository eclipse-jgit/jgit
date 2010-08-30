/*
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.lib.objectcheckers;

import junit.framework.TestCase;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.lib.Constants;

public class TagCheckerTest extends TestCase {
	private TagChecker checker= new TagChecker();

	public void testValidTag() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");
		b.append("tag test-tag\n");
		b.append("tagger A. U. Thor <author@localhost> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		checker.check(data);
	}

	public void testInvalidTagNoObject1() {
		final StringBuilder b = new StringBuilder();

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no object header", e.getMessage());
		}
	}

	public void testInvalidTagNoObject2() {
		final StringBuilder b = new StringBuilder();

		b.append("object\t");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no object header", e.getMessage());
		}
	}

	public void testInvalidTagNoObject3() {
		final StringBuilder b = new StringBuilder();

		b.append("obejct ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no object header", e.getMessage());
		}
	}

	public void testInvalidTagNoObject4() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("zz9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("invalid object", e.getMessage());
		}
	}

	public void testInvalidTagNoObject5() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append(" \n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("invalid object", e.getMessage());
		}
	}

	public void testInvalidTagNoObject6() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("invalid object", e.getMessage());
		}
	}

	public void testInvalidTagNoType1() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no type header", e.getMessage());
		}
	}

	public void testInvalidTagNoType2() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type\tcommit\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no type header", e.getMessage());
		}
	}

	public void testInvalidTagNoType3() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("tpye commit\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no type header", e.getMessage());
		}
	}

	public void testInvalidTagNoType4() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no tag header", e.getMessage());
		}
	}

	public void testInvalidTagNoTagHeader1() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no tag header", e.getMessage());
		}
	}

	public void testInvalidTagNoTagHeader2() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");
		b.append("tag\tfoo\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no tag header", e.getMessage());
		}
	}

	public void testInvalidTagNoTagHeader3() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");
		b.append("tga foo\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no tag header", e.getMessage());
		}
	}

	public void testValidTagHasNoTaggerHeader() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");
		b.append("tag foo\n");

		checker.check(Constants.encodeASCII(b.toString()));
	}

	public void testInvalidTagInvalidTaggerHeader1() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");
		b.append("tag foo\n");
		b.append("tagger \n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("invalid tagger", e.getMessage());
		}
	}

	public void testInvalidTagInvalidTaggerHeader3() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");
		b.append("tag foo\n");
		b.append("tagger a < 1 +000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("invalid tagger", e.getMessage());
		}
	}

}

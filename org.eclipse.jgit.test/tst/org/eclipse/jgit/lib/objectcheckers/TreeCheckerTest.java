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

public class TreeCheckerTest extends TestCase {
	private TreeChecker checker= new TreeChecker();

	public void testValidEmptyTree() throws CorruptObjectException {
		checker.check(new byte[0]);
	}

	public void testValidTree1() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 regular-file");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.check(data);
	}

	public void testValidTree2() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100755 executable");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.check(data);
	}

	public void testValidTree3() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "40000 tree");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.check(data);
	}

	public void testValidTree4() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "120000 symlink");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.check(data);
	}

	public void testValidTree5() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "160000 git link");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.check(data);
	}

	public void testValidTree6() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 .a");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.check(data);
	}

	public void testValidTreeSorting1() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 fooaaa");
		entry(b, "100755 foobar");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.check(data);
	}

	public void testValidTreeSorting2() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100755 fooaaa");
		entry(b, "100644 foobar");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.check(data);
	}

	public void testValidTreeSorting3() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "40000 a");
		entry(b, "100644 b");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.check(data);
	}

	public void testValidTreeSorting4() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "40000 b");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.check(data);
	}

	public void testValidTreeSorting5() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a.c");
		entry(b, "40000 a");
		entry(b, "100644 a0c");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.check(data);
	}

	public void testValidTreeSorting6() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "40000 a");
		entry(b, "100644 apple");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.check(data);
	}

	public void testValidTreeSorting7() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "40000 an orang");
		entry(b, "40000 an orange");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.check(data);
	}

	public void testValidTreeSorting8() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "100644 a0c");
		entry(b, "100644 b");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.check(data);
	}

	public void testInvalidTreeModeStartsWithZero1() {
		final StringBuilder b = new StringBuilder();
		entry(b, "0 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("mode starts with '0'", e.getMessage());
		}
	}

	public void testInvalidTreeModeStartsWithZero2() {
		final StringBuilder b = new StringBuilder();
		entry(b, "0100644 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("mode starts with '0'", e.getMessage());
		}
	}

	public void testInvalidTreeModeStartsWithZero3() {
		final StringBuilder b = new StringBuilder();
		entry(b, "040000 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("mode starts with '0'", e.getMessage());
		}
	}

	public void testInvalidTreeModeNotOctal1() {
		final StringBuilder b = new StringBuilder();
		entry(b, "8 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid mode character", e.getMessage());
		}
	}

	public void testInvalidTreeModeNotOctal2() {
		final StringBuilder b = new StringBuilder();
		entry(b, "Z a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid mode character", e.getMessage());
		}
	}

	public void testInvalidTreeModeNotSupportedMode1() {
		final StringBuilder b = new StringBuilder();
		entry(b, "1 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid mode 1", e.getMessage());
		}
	}

	public void testInvalidTreeModeNotSupportedMode2() {
		final StringBuilder b = new StringBuilder();
		entry(b, "170000 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid mode " + 0170000, e.getMessage());
		}
	}

	public void testInvalidTreeModeMissingName() {
		final StringBuilder b = new StringBuilder();
		b.append("100644");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("truncated in mode", e.getMessage());
		}
	}

	public void testInvalidTreeNameContainsSlash() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a/b");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("name contains '/'", e.getMessage());
		}
	}

	public void testInvalidTreeNameIsEmpty() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 ");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("zero length name", e.getMessage());
		}
	}

	public void testInvalidTreeNameIsDot() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 .");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid name '.'", e.getMessage());
		}
	}

	public void testInvalidTreeNameIsDotDot() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 ..");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid name '..'", e.getMessage());
		}
	}

	public void testInvalidTreeTruncatedInName() {
		final StringBuilder b = new StringBuilder();
		b.append("100644 b");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("truncated in name", e.getMessage());
		}
	}

	public void testInvalidTreeTruncatedInObjectId() {
		final StringBuilder b = new StringBuilder();
		b.append("100644 b\0\1\2");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("truncated in object id", e.getMessage());
		}
	}

	public void testInvalidTreeBadSorting1() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 foobar");
		entry(b, "100644 fooaaa");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("incorrectly sorted", e.getMessage());
		}
	}

	public void testInvalidTreeBadSorting2() {
		final StringBuilder b = new StringBuilder();
		entry(b, "40000 a");
		entry(b, "100644 a.c");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("incorrectly sorted", e.getMessage());
		}
	}

	public void testInvalidTreeBadSorting3() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a0c");
		entry(b, "40000 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("incorrectly sorted", e.getMessage());
		}
	}

	public void testInvalidTreeDuplicateNames1() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "100644 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("duplicate entry names", e.getMessage());
		}
	}

	public void testInvalidTreeDuplicateNames2() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "100755 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("duplicate entry names", e.getMessage());
		}
	}

	public void testInvalidTreeDuplicateNames3() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "40000 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("duplicate entry names", e.getMessage());
		}
	}

	public void testInvalidTreeDuplicateNames4() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "100644 a.c");
		entry(b, "100644 a.d");
		entry(b, "100644 a.e");
		entry(b, "40000 a");
		entry(b, "100644 zoo");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("duplicate entry names", e.getMessage());
		}
	}

	private static void entry(final StringBuilder b, final String modeName) {
		b.append(modeName);
		b.append('\0');
		for (int i = 0; i < Constants.OBJECT_ID_LENGTH; i++)
			b.append((char) i);
	}
}

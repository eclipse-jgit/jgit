/*
 * Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com>
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

package org.eclipse.jgit.merge;

import org.eclipse.jgit.diff.RawText;

import junit.framework.TestCase;

public class MergeAlgorithmTest extends TestCase {
	String base="aaa\nbbbbb\nc\ndd\n";
	String oneSingleLineMod="aaa\nbbbbb\n00\ndd\n";
	String oneOtherSingleLineMod="11\nbbbbb\nc\ndd\n";
	String oneTwoLineMod="aaa\n00\n00\ndd\n";
	String oneThreeLineMod="aaa\n00\n00\n00\n";
	String twoSingleLineMods="aaa\n00\nc\n00\n";

	private RawText s2r(String txt) {
		return new RawText(txt.getBytes());
	}

	public void testTwoConflictingModifications() {
		// @todo test fails currently because last line is forgotten. Fix it
		assertEquals("aaa\n<<<<<<<\nbbbbb\n00\n=======\n00\n00\n>>>>>>>\ndd\n", MergeAlgorithm.merge(s2r(base), s2r(oneSingleLineMod), s2r(oneTwoLineMod)).toString());
	}

	public void testOneAgainstTwoConflictingModifications() {
		// @todo test fails currently because last line is forgotten. Fix it
		assertEquals("aaa\n<<<<<<<\n00\n00\n00\n=======\n00\nc\n00\n>>>>>>>\n", MergeAlgorithm.merge(s2r(base), s2r(oneThreeLineMod), s2r(twoSingleLineMods)).toString());
	}

	public void testNoAgainstOneModification() {
		// @todo test fails currently because last line is forgotten. Fix it
		assertEquals(twoSingleLineMods.toString(), MergeAlgorithm.merge(s2r(base), s2r(base), s2r(twoSingleLineMods)).toString());
	}

	public void testTwoNonConflictingModifications() {
		// @todo test fails currently because last line is forgotten. Fix it
		assertEquals("11\nbbbbb\n00\ndd\n", MergeAlgorithm.merge(s2r(base), s2r(oneSingleLineMod), s2r(oneOtherSingleLineMod)).toString());
	}

}

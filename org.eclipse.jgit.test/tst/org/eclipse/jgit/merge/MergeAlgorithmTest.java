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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import junit.framework.TestCase;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Constants;

public class MergeAlgorithmTest extends TestCase {
	MergeFormatter fmt=new MergeFormatter();

	// the texts which are used in this merge-tests are constructed by
	// concatenating fixed chunks of text defined by the String constants
	// A..Y. The common base text is always the text A+B+C+D+E+F+G+H+I+J.
	// The two texts being merged are constructed by deleting some chunks
	// or inserting new chunks. Some of the chunks are one-liners, others
	// contain more than one line.
	private static final String A = "aaa\n";
	private static final String B = "bbbbb\nbb\nbbb\n";
	private static final String C = "c\n";
	private static final String D = "dd\n";
	private static final String E = "ee\n";
	private static final String F = "fff\nff\n";
	private static final String G = "gg\n";
	private static final String H = "h\nhhh\nhh\n";
	private static final String I = "iiii\n";
	private static final String J = "jj\n";
	private static final String Z = "zzz\n";
	private static final String Y = "y\n";

	// constants which define how conflict-regions are expected to be reported.
	private static final String XXX_0 = "<<<<<<< O\n";
	private static final String XXX_1 = "=======\n";
	private static final String XXX_2 = ">>>>>>> T\n";

	// the common base from which all merges texts derive from
	String base=A+B+C+D+E+F+G+H+I+J;

	// the following constants define the merged texts. The name of the
	// constants describe how they are created out of the common base. E.g.
	// the constant named replace_XYZ_by_MNO stands for the text which is
	// created from common base by replacing first chunk X by chunk M, then
	// Y by N and then Z by O.
	String replace_C_by_Z=A+B+Z+D+E+F+G+H+I+J;
	String replace_A_by_Y=Y+B+C+D+E+F+G+H+I+J;
	String replace_A_by_Z=Z+B+C+D+E+F+G+H+I+J;
	String replace_J_by_Y=A+B+C+D+E+F+G+H+I+Y;
	String replace_J_by_Z=A+B+C+D+E+F+G+H+I+Z;
	String replace_BC_by_ZZ=A+Z+Z+D+E+F+G+H+I+J;
	String replace_BCD_by_ZZZ=A+Z+Z+Z+E+F+G+H+I+J;
	String replace_BD_by_ZZ=A+Z+C+Z+E+F+G+H+I+J;
	String replace_BCDEGI_by_ZZZZZZ=A+Z+Z+Z+Z+F+Z+H+Z+J;
	String replace_CEFGHJ_by_YYYYYY=A+B+Y+D+Y+Y+Y+Y+I+Y;
	String replace_BDE_by_ZZY=A+Z+C+Z+Y+F+G+H+I+J;

	/**
	 * Check for a conflict where the second text was changed similar to the
	 * first one, but the second texts modification covers one more line.
	 *
	 * @throws IOException
	 */
	public void testTwoConflictingModifications() throws IOException {
		assertEquals(A + XXX_0 + B + Z + XXX_1 + Z + Z + XXX_2 + D + E + F + G
				+ H + I + J,
				merge(base, replace_C_by_Z, replace_BC_by_ZZ));
	}

	/**
	 * Test a case where we have three consecutive chunks. The first text
	 * modifies all three chunks. The second text modifies the first and the
	 * last chunk. This should be reported as one conflicting region.
	 *
	 * @throws IOException
	 */
	public void testOneAgainstTwoConflictingModifications() throws IOException {
		assertEquals(A + XXX_0 + Z + Z + Z + XXX_1 + Z + C + Z + XXX_2 + E + F
				+ G + H + I + J,
				merge(base, replace_BCD_by_ZZZ, replace_BD_by_ZZ));
	}

	/**
	 * Test a merge where only the second text contains modifications. Expect as
	 * merge result the second text.
	 *
	 * @throws IOException
	 */
	public void testNoAgainstOneModification() throws IOException {
		assertEquals(replace_BD_by_ZZ.toString(),
				merge(base, base, replace_BD_by_ZZ));
	}

	/**
	 * Both texts contain modifications but not on the same chunks. Expect a
	 * non-conflict merge result.
	 *
	 * @throws IOException
	 */
	public void testTwoNonConflictingModifications() throws IOException {
		assertEquals(Y + B + Z + D + E + F + G + H + I + J,
			merge(base, replace_C_by_Z, replace_A_by_Y));
	}

	/**
	 * Merge two complicated modifications. The merge algorithm has to extend
	 * and combine conflicting regions to get to the expected merge result.
	 *
	 * @throws IOException
	 */
	public void testTwoComplicatedModifications() throws IOException {
		assertEquals(A + XXX_0 + Z + Z + Z + Z + F + Z + H + XXX_1 + B + Y + D
				+ Y + Y + Y + Y + XXX_2 + Z + Y,
				merge(base,
						replace_BCDEGI_by_ZZZZZZ,
						replace_CEFGHJ_by_YYYYYY));
	}

	/**
	 * Test a conflicting region at the very start of the text.
	 *
	 * @throws IOException
	 */
	public void testConflictAtStart() throws IOException {
		assertEquals(XXX_0 + Z + XXX_1 + Y + XXX_2 + B + C + D + E + F + G + H
				+ I + J, merge(base, replace_A_by_Z, replace_A_by_Y));
	}

	/**
	 * Test a conflicting region at the very end of the text.
	 *
	 * @throws IOException
	 */
	public void testConflictAtEnd() throws IOException {
		assertEquals(A+B+C+D+E+F+G+H+I+XXX_0+Z+XXX_1+Y+XXX_2, merge(base, replace_J_by_Z, replace_J_by_Y));
	}

	private String merge(String commonBase, String ours, String theirs) throws IOException {
		MergeResult r=MergeAlgorithm.merge(new RawText(Constants.encode(commonBase)), new RawText(Constants.encode(ours)), new RawText(Constants.encode(theirs)));
		ByteArrayOutputStream bo=new ByteArrayOutputStream(50);
		fmt.formatMerge(bo, r, "B", "O", "T", Constants.CHARACTER_ENCODING);
		return new String(bo.toByteArray(), Constants.CHARACTER_ENCODING);
	}
}

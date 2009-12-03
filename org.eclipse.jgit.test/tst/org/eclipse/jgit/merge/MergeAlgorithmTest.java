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

public class MergeAlgorithmTest extends TestCase {
	MergeFormatter fmt=new MergeFormatter();

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
	private static final String XXX_0 = "<<<<<<< O\n";
	private static final String XXX_1 = "=======\n";
	private static final String XXX_2 = ">>>>>>> T\n";

	String base=A+B+C+D+E+F+G+H+I+J;
	String mod_c_z=A+B+Z+D+E+F+G+H+I+J;
	String mod_a_y=Y+B+C+D+E+F+G+H+I+J;
	String mod_a_z=Z+B+C+D+E+F+G+H+I+J;
	String mod_j_y=A+B+C+D+E+F+G+H+I+Y;
	String mod_j_z=A+B+C+D+E+F+G+H+I+Z;
	String mod_bc_zz=A+Z+Z+D+E+F+G+H+I+J;
	String mod_bcd_zzz=A+Z+Z+Z+E+F+G+H+I+J;
	String mod_bd_zz=A+Z+C+Z+E+F+G+H+I+J;
	String mod_bcdegi_zzzzzz=A+Z+Z+Z+Z+F+Z+H+Z+J;
	String mod_cefghj_yyyyyy=A+B+Y+D+Y+Y+Y+Y+I+Y;
	String mod_bde_zzy=A+Z+C+Z+Y+F+G+H+I+J;

	private RawText s2r(String txt) {
		return new RawText(txt.getBytes());
	}

	public void testTwoConflictingModifications() throws IOException {
		assertEquals(A+XXX_0+B+Z+XXX_1+Z+Z+XXX_2+D+E+F+G+H+I+J, merge(base, mod_c_z, mod_bc_zz));
	}

	public void testOneAgainstTwoConflictingModifications() throws IOException {
		assertEquals(A+XXX_0+Z+Z+Z+XXX_1+Z+C+Z+XXX_2+E+F+G+H+I+J, merge(base, mod_bcd_zzz, mod_bd_zz));
	}

	public void testNoAgainstOneModification() throws IOException {
		assertEquals(mod_bd_zz.toString(), merge(base, base, mod_bd_zz));
	}

	public void testTwoNonConflictingModifications() throws IOException {
		assertEquals(Y+B+Z+D+E+F+G+H+I+J, merge(base, mod_c_z, mod_a_y));
	}

	public void testTwoComplicatedModifications() throws IOException {
		assertEquals(A+XXX_0+Z+Z+Z+Z+F+Z+H+XXX_1+B+Y+D+Y+Y+Y+Y+XXX_2+Z+Y, merge(base, mod_bcdegi_zzzzzz, mod_cefghj_yyyyyy));
	}

	public void testConflictAtStart() throws IOException {
		assertEquals(XXX_0+Z+XXX_1+Y+XXX_2+B+C+D+E+F+G+H+I+J, merge(base, mod_a_z, mod_a_y));
	}

	public void testConflictAtEnd() throws IOException {
		assertEquals(A+B+C+D+E+F+G+H+I+XXX_0+Z+XXX_1+Y+XXX_2, merge(base, mod_j_z, mod_j_y));
	}

	String merge(String base, String ours, String theirs) throws IOException {
		MergeResult r=MergeAlgorithm.merge(s2r(base), s2r(ours), s2r(theirs));
		ByteArrayOutputStream bo=new ByteArrayOutputStream(50);
		fmt.formatMerge(bo, r, "B", "O", "T");
		return new String(bo.toByteArray());
	}
}

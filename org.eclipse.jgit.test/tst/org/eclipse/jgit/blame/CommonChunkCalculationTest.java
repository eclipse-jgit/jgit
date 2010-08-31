/*
 * Copyright (C) 2010, Benjamin Muskalla <bmuskalla@eclipsesource.com>
 * Copyright (C) 2008, Manuel Woelker <manuel.woelker@gmail.com>
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
package org.eclipse.jgit.blame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

public class CommonChunkCalculationTest extends TestCase {
	public void testNoDifference() throws Exception {
		int length = 100;
		List<MockDifference> differences = new ArrayList<MockDifference>();
		List<CommonChunk> expected = Arrays
				.asList(new CommonChunk(0, 0, length));
		assertComputation(differences, expected, length, length);
	}

	public void testSimpleCommonPrefix() throws Exception {
		int length = 100;
		List<MockDifference> differences = Arrays.asList(new MockDifference(3,
				length, 3, length));
		List<CommonChunk> expected = Arrays.asList(new CommonChunk(0, 0, 3));
		assertComputation(differences, expected, length, length);
	}

	public void testCommonPrefix() throws Exception {
		List<MockDifference> differences = Arrays.asList(new MockDifference(3,
				10, 3, 20));
		List<CommonChunk> expected = Arrays.asList(new CommonChunk(0, 0, 3));
		assertComputation(differences, expected, 10, 20);
	}

	public void testSimpleCommonSuffix() throws Exception {
		List<MockDifference> differences = Arrays.asList(new MockDifference(0,
				10, 0, 20));
		List<CommonChunk> expected = Arrays.asList(new CommonChunk(11, 21, 29));
		assertComputation(differences, expected, 40, 50);
	}

	public void testSimpleInfix() throws Exception {
		List<MockDifference> differences = Arrays.asList(new MockDifference(0,
				10, 0, 10), new MockDifference(20, 30, 20, 40));
		List<CommonChunk> expected = Arrays.asList(new CommonChunk(11, 11, 9));
		assertComputation(differences, expected, 30, 40);
	}

	public void testSimpleAdditionFront() throws Exception {
		List<MockDifference> differences = Arrays.asList(new MockDifference(0,
				12, 0, -1));
		List<CommonChunk> expected = Arrays.asList(new CommonChunk(13, 0, 19));
		assertComputation(differences, expected, 32, 19);
	}

	public void testSimpleAdditionMiddle() throws Exception {
		List<MockDifference> differences = Arrays.asList(new MockDifference(10,
				12, 10, -1));
		List<CommonChunk> expected = Arrays.asList(new CommonChunk(0, 0, 10),
				new CommonChunk(13, 10, 20));
		assertComputation(differences, expected, 33, 30);
	}

	public void testSimpleAdditionEnd() throws Exception {
		List<MockDifference> differences = Arrays.asList(new MockDifference(10,
				12, 10, -1));
		List<CommonChunk> expected = Arrays.asList(new CommonChunk(0, 0, 10));
		assertComputation(differences, expected, 13, 10);
	}

	public void testSimpleAdditionTwoDifferences() throws Exception {
		List<MockDifference> differences = Arrays.asList(new MockDifference(3,
				-1, 3, 5), new MockDifference(6, -1, 9, 14));
		List<CommonChunk> expected = Arrays.asList(new CommonChunk(0, 0, 3),
				new CommonChunk(3, 6, 3));
		assertComputation(differences, expected, 6, 15);
	}

	public void testUnequalDiffLengths() throws Exception {
		List<MockDifference> differences = Arrays.asList(new MockDifference(0,
				1, 0, 0), new MockDifference(0, 5, 0, 0));
		try {
			CommonChunk.computeCommonChunks(differences, 0, 0);
			fail("Should fail for unequal diff lengths");
		} catch (RuntimeException e) {
			// expected
		}

		differences = Arrays.asList(new MockDifference(0, 1, 0, 3));
		try {
			CommonChunk.computeCommonChunks(differences, 0, 0);
			fail("Should fail for unequal diff lengths");
		} catch (RuntimeException e) {
			// expected
		}

		differences = Arrays.asList(new MockDifference(1, 0, 3, 0));
		try {
			CommonChunk.computeCommonChunks(differences, 0, 0);
			fail("Should fail for unequal diff lengths");
		} catch (RuntimeException e) {
			// expected
		}
	}

	private void assertComputation(List<MockDifference> differences,
			List<CommonChunk> expected, int lengthA, int lengthB) {
		{
			List<CommonChunk> commonChunks = CommonChunk.computeCommonChunks(
					differences, lengthA, lengthB);
			assertEquals(expected, commonChunks);
		}
		// inverse check
		{
			List<MockDifference> invertedDifferences = new ArrayList<MockDifference>();
			for (MockDifference difference : differences) {
				invertedDifferences.add(difference.inverted());
			}
			List<CommonChunk> invertedExpected = new ArrayList<CommonChunk>();
			for (CommonChunk commonChunk : expected) {
				invertedExpected.add(new CommonChunk(commonChunk.getBstart(),
						commonChunk.getAstart(), commonChunk.getLength()));
			}
			List<CommonChunk> commonChunks = CommonChunk.computeCommonChunks(
					invertedDifferences, lengthB, lengthA);
			assertEquals(invertedExpected, commonChunks);
		}
	}
}

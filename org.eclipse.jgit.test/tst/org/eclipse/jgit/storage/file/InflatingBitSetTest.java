/*
 * Copyright (C) 2012, Google Inc.
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

package org.eclipse.jgit.storage.file;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javaewah.EWAHCompressedBitmap;

import org.junit.Test;

public class InflatingBitSetTest {

	@Test
	public void testMaybeContains() {
		EWAHCompressedBitmap ecb = new EWAHCompressedBitmap();
		ecb.set(63);
		ecb.set(64);
		ecb.set(128);

		InflatingBitSet ibs = new InflatingBitSet(ecb);
		assertTrue(ibs.maybeContains(0));
		assertFalse(ibs.contains(0)); // Advance
		assertFalse(ibs.maybeContains(0));
		assertTrue(ibs.maybeContains(63));
		assertTrue(ibs.maybeContains(64));
		assertTrue(ibs.maybeContains(65));
		assertFalse(ibs.maybeContains(129));
	}

	@Test
	public void testContainsMany() {
		EWAHCompressedBitmap ecb = new EWAHCompressedBitmap();
		ecb.set(64);
		ecb.set(65);
		ecb.set(1024);

		InflatingBitSet ibs = new InflatingBitSet(ecb);
		assertFalse(ibs.contains(0));
		assertTrue(ibs.contains(64));
		assertTrue(ibs.contains(65));
		assertFalse(ibs.contains(66));
		assertTrue(ibs.contains(1024));
		assertFalse(ibs.contains(1025));
	}

	@Test
	public void testContainsOne() {
		EWAHCompressedBitmap ecb = new EWAHCompressedBitmap();
		ecb.set(64);

		InflatingBitSet ibs = new InflatingBitSet(ecb);
		assertTrue(ibs.contains(64));
		assertTrue(ibs.contains(64));
		assertFalse(ibs.contains(65));
		assertFalse(ibs.contains(63));
	}

	@Test
	public void testContainsEmpty() {
		InflatingBitSet ibs = new InflatingBitSet(new EWAHCompressedBitmap());
		assertFalse(ibs.maybeContains(0));
		assertFalse(ibs.contains(0));
	}
}

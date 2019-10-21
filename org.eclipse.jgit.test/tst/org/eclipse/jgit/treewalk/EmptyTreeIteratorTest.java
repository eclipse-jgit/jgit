/*
 * Copyright (C) 2008, Google Inc.
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

package org.eclipse.jgit.treewalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.junit.Test;

public class EmptyTreeIteratorTest extends RepositoryTestCase {
	@Test
	public void testAtEOF() throws Exception {
		final EmptyTreeIterator etp = new EmptyTreeIterator();
		assertTrue(etp.first());
		assertTrue(etp.eof());
	}

	@Test
	public void testCreateSubtreeIterator() throws Exception {
		final EmptyTreeIterator etp = new EmptyTreeIterator();
		try (ObjectReader reader = db.newObjectReader()) {
			final AbstractTreeIterator sub = etp.createSubtreeIterator(reader);
			assertNotNull(sub);
			assertTrue(sub.first());
			assertTrue(sub.eof());
			assertTrue(sub instanceof EmptyTreeIterator);
		}
	}

	@Test
	public void testEntryObjectId() throws Exception {
		final EmptyTreeIterator etp = new EmptyTreeIterator();
		assertSame(ObjectId.zeroId(), etp.getEntryObjectId());
		assertNotNull(etp.idBuffer());
		assertEquals(0, etp.idOffset());
		assertEquals(ObjectId.zeroId(), ObjectId.fromRaw(etp.idBuffer()));
	}

	@Test
	public void testNextDoesNothing() throws Exception {
		final EmptyTreeIterator etp = new EmptyTreeIterator();
		etp.next(1);
		assertTrue(etp.first());
		assertTrue(etp.eof());
		assertEquals(ObjectId.zeroId(), ObjectId.fromRaw(etp.idBuffer()));

		etp.next(1);
		assertTrue(etp.first());
		assertTrue(etp.eof());
		assertEquals(ObjectId.zeroId(), ObjectId.fromRaw(etp.idBuffer()));
	}

	@Test
	public void testBackDoesNothing() throws Exception {
		final EmptyTreeIterator etp = new EmptyTreeIterator();
		etp.back(1);
		assertTrue(etp.first());
		assertTrue(etp.eof());
		assertEquals(ObjectId.zeroId(), ObjectId.fromRaw(etp.idBuffer()));

		etp.back(1);
		assertTrue(etp.first());
		assertTrue(etp.eof());
		assertEquals(ObjectId.zeroId(), ObjectId.fromRaw(etp.idBuffer()));
	}

	@Test
	public void testStopWalkCallsParent() throws Exception {
		final boolean called[] = new boolean[1];
		assertFalse(called[0]);

		final EmptyTreeIterator parent = new EmptyTreeIterator() {
			@Override
			public void stopWalk() {
				called[0] = true;
			}
		};
		try (ObjectReader reader = db.newObjectReader()) {
			parent.createSubtreeIterator(reader).stopWalk();
		}
		assertTrue(called[0]);
	}
}

/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2006, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.lib;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.eclipse.jgit.errors.CheckoutConflictException;

public class WorkDirCheckoutTest extends RepositoryTestCase {
	public void testFindingConflicts() throws IOException {
		GitIndex index = new GitIndex(db);
		index.add(trash, writeTrashFile("bar", "bar"));
		index.add(trash, writeTrashFile("foo/bar/baz/qux", "foo/bar"));
		recursiveDelete(new File(trash, "bar"));
		recursiveDelete(new File(trash, "foo"));
		writeTrashFile("bar/baz/qux/foo", "another nasty one");
		writeTrashFile("foo", "troublesome little bugger");

		WorkDirCheckout workDirCheckout = new WorkDirCheckout(db, trash, index,
				index);
		workDirCheckout.prescanOneTree();
		ArrayList<String> conflictingEntries = workDirCheckout
				.getConflicts();
		ArrayList<String> removedEntries = workDirCheckout.getRemoved();
		assertEquals("bar/baz/qux/foo", conflictingEntries.get(0));
		assertEquals("foo", conflictingEntries.get(1));

		GitIndex index2 = new GitIndex(db);
		recursiveDelete(new File(trash, "bar"));
		recursiveDelete(new File(trash, "foo"));

		index2.add(trash, writeTrashFile("bar/baz/qux/foo", "bar"));
		index2.add(trash, writeTrashFile("foo", "lalala"));

		workDirCheckout = new WorkDirCheckout(db, trash, index2, index);
		workDirCheckout.prescanOneTree();

		conflictingEntries = workDirCheckout.getConflicts();
		removedEntries = workDirCheckout.getRemoved();
		assertTrue(conflictingEntries.isEmpty());
		assertTrue(removedEntries.contains("bar/baz/qux/foo"));
		assertTrue(removedEntries.contains("foo"));
	}

	public void testCheckingOutWithConflicts() throws IOException {
		GitIndex index = new GitIndex(db);
		index.add(trash, writeTrashFile("bar", "bar"));
		index.add(trash, writeTrashFile("foo/bar/baz/qux", "foo/bar"));
		recursiveDelete(new File(trash, "bar"));
		recursiveDelete(new File(trash, "foo"));
		writeTrashFile("bar/baz/qux/foo", "another nasty one");
		writeTrashFile("foo", "troublesome little bugger");

		try {
			WorkDirCheckout workDirCheckout = new WorkDirCheckout(db, trash,
					index, index);
			workDirCheckout.checkout();
			fail("Should have thrown exception");
		} catch (CheckoutConflictException e) {
			// all is well
		}

		WorkDirCheckout workDirCheckout = new WorkDirCheckout(db, trash, index,
				index);
		workDirCheckout.setFailOnConflict(false);
		workDirCheckout.checkout();

		assertTrue(new File(trash, "bar").isFile());
		assertTrue(new File(trash, "foo/bar/baz/qux").isFile());

		GitIndex index2 = new GitIndex(db);
		recursiveDelete(new File(trash, "bar"));
		recursiveDelete(new File(trash, "foo"));
		index2.add(trash, writeTrashFile("bar/baz/qux/foo", "bar"));
		writeTrashFile("bar/baz/qux/bar", "evil? I thought it said WEEVIL!");
		index2.add(trash, writeTrashFile("foo", "lalala"));

		workDirCheckout = new WorkDirCheckout(db, trash, index2, index);
		workDirCheckout.setFailOnConflict(false);
		workDirCheckout.checkout();

		assertTrue(new File(trash, "bar").isFile());
		assertTrue(new File(trash, "foo/bar/baz/qux").isFile());
		assertNotNull(index2.getEntry("bar"));
		assertNotNull(index2.getEntry("foo/bar/baz/qux"));
		assertNull(index2.getEntry("bar/baz/qux/foo"));
		assertNull(index2.getEntry("foo"));
	}
}

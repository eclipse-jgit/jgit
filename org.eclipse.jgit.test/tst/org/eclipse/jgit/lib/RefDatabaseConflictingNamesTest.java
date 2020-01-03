/*
 * Copyright (C) 2013, Robin Stocker <robin@nibor.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class RefDatabaseConflictingNamesTest {

	private RefDatabase refDatabase = new RefDatabase() {
		@Override
		public Map<String, Ref> getRefs(String prefix) throws IOException {
			if (ALL.equals(prefix)) {
				Map<String, Ref> existing = new HashMap<>();
				existing.put("refs/heads/a/b", null /* not used */);
				existing.put("refs/heads/q", null /* not used */);
				return existing;
			}
			return Collections.emptyMap();
		}

		@Override
		public Ref peel(Ref ref) throws IOException {
			return null;
		}

		@Override
		public RefUpdate newUpdate(String name, boolean detach)
				throws IOException {
			return null;
		}

		@Override
		public RefRename newRename(String fromName, String toName)
				throws IOException {
			return null;
		}

		@Override
		public boolean isNameConflicting(String name) throws IOException {
			return false;
		}

		@Override
		public Ref exactRef(String name) throws IOException {
			return null;
		}

		@Override
		public List<Ref> getAdditionalRefs() throws IOException {
			return null;
		}

		@Override
		public void create() throws IOException {
			// Not needed
		}

		@Override
		public void close() {
			// Not needed
		}
	};

	@Test
	public void testGetConflictingNames() throws IOException {
		// new references cannot replace an existing container
		assertConflictingNames("refs", "refs/heads/a/b", "refs/heads/q");
		assertConflictingNames("refs/heads", "refs/heads/a/b", "refs/heads/q");
		assertConflictingNames("refs/heads/a", "refs/heads/a/b");

		// existing reference is not conflicting
		assertNoConflictingNames("refs/heads/a/b");

		// new references are not conflicting
		assertNoConflictingNames("refs/heads/a/d");
		assertNoConflictingNames("refs/heads/master");

		// existing reference must not be used as a container
		assertConflictingNames("refs/heads/a/b/c", "refs/heads/a/b");
		assertConflictingNames("refs/heads/q/master", "refs/heads/q");
	}

	private void assertNoConflictingNames(String proposed) throws IOException {
		assertTrue("expected conflicting names to be empty", refDatabase
				.getConflictingNames(proposed).isEmpty());
	}

	private void assertConflictingNames(String proposed, String... conflicts)
			throws IOException {
		Set<String> expected = new HashSet<>(Arrays.asList(conflicts));
		assertEquals(expected,
				new HashSet<>(refDatabase.getConflictingNames(proposed)));
	}
}

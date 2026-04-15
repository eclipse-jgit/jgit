/*
 * Copyright (C) 2026, hanweiwei and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class RebaseResultTest {

	@Test
	public void toStringOk() {
		String s = RebaseResult.OK_RESULT.toString();
		assertNotNull(s);
		assertTrue(s.contains("status=OK"));
		assertTrue(s.startsWith("RebaseResult["));
		assertTrue(s.endsWith("]"));
	}

	@Test
	public void toStringUpToDate() {
		String s = RebaseResult.UP_TO_DATE_RESULT.toString();
		assertTrue(s.contains("status=UP_TO_DATE"));
	}

	@Test
	public void toStringFastForward() {
		String s = RebaseResult.FAST_FORWARD_RESULT.toString();
		assertTrue(s.contains("status=FAST_FORWARD"));
	}

	@Test
	public void toStringConflicts() {
		RebaseResult result = RebaseResult.conflicts(
				List.of("file1.txt", "file2.txt"));
		String s = result.toString();
		assertTrue(s.contains("status=CONFLICTS"));
		assertTrue(s.contains("file1.txt"));
		assertTrue(s.contains("file2.txt"));
	}

	@Test
	public void toStringUncommittedChanges() {
		RebaseResult result = RebaseResult.uncommittedChanges(
				List.of("dirty.java"));
		String s = result.toString();
		assertTrue(s.contains("status=UNCOMMITTED_CHANGES"));
		assertTrue(s.contains("dirty.java"));
	}

	@Test
	public void toStringAborted() {
		String s = RebaseResult.ABORTED_RESULT.toString();
		assertTrue(s.contains("status=ABORTED"));
	}
}

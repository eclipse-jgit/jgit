/*
 * Copyright (C) 2017, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.events;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A {@link WorkingTreeModifiedListener} that can be used in tests to check
 * expected events.
 */
public class ChangeRecorder implements WorkingTreeModifiedListener {

	public static final String[] EMPTY = new String[0];

	private Set<String> modified = new HashSet<>();

	private Set<String> deleted = new HashSet<>();

	private int eventCount;

	@Override
	public void onWorkingTreeModified(WorkingTreeModifiedEvent event) {
		eventCount++;
		modified.removeAll(event.getDeleted());
		deleted.removeAll(event.getModified());
		modified.addAll(event.getModified());
		deleted.addAll(event.getDeleted());
	}

	private String[] getModified() {
		return modified.toArray(new String[0]);
	}

	private String[] getDeleted() {
		return deleted.toArray(new String[0]);
	}

	private void reset() {
		eventCount = 0;
		modified.clear();
		deleted.clear();
	}

	public void assertNoEvent() {
		assertEquals("Unexpected WorkingTreeModifiedEvent ", 0, eventCount);
	}

	public void assertEvent(String[] expectedModified,
			String[] expectedDeleted) {
		String[] actuallyModified = getModified();
		String[] actuallyDeleted = getDeleted();
		Arrays.sort(actuallyModified);
		Arrays.sort(expectedModified);
		Arrays.sort(actuallyDeleted);
		Arrays.sort(expectedDeleted);
		assertEquals("Unexpected modifications reported",
				Arrays.toString(expectedModified),
				Arrays.toString(actuallyModified));
		assertEquals("Unexpected deletions reported",
				Arrays.toString(expectedDeleted),
				Arrays.toString(actuallyDeleted));
		reset();
	}
}

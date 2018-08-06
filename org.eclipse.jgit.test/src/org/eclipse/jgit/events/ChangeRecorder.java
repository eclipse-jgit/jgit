/*
 * Copyright (C) 2017, Thomas Wolf <thomas.wolf@paranor.ch>
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

package org.eclipse.jgit.events;

import static org.junit.Assert.assertArrayEquals;
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
		assertArrayEquals("Unexpected modifications reported", expectedModified,
				actuallyModified);
		assertArrayEquals("Unexpected deletions reported", expectedDeleted,
				actuallyDeleted);
		reset();
	}
}

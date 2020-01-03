/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.notes;

/** A note bucket that has been loaded into the process. */
abstract class InMemoryNoteBucket extends NoteBucket {
	/**
	 * Number of leading digits that leads to this bucket in the note path.
	 *
	 * This is counted in terms of hex digits, not raw bytes. Each bucket level
	 * is typically 2 higher than its parent, placing about 256 items in each
	 * level of the tree.
	 */
	final int prefixLen;

	/**
	 * Chain of non-note tree entries found at this path in the tree.
	 *
	 * During parsing of a note tree into the in-memory representation,
	 * {@link NoteParser} keeps track of all non-note tree entries and stores
	 * them here as a sorted linked list. That list can be merged back with the
	 * note data that is held by the subclass, allowing the tree to be
	 * recreated.
	 */
	NonNoteEntry nonNotes;

	InMemoryNoteBucket(int prefixLen) {
		this.prefixLen = prefixLen;
	}

	abstract InMemoryNoteBucket append(Note note);
}

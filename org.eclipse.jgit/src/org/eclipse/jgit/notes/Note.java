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

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

/**
 * In-memory representation of a single note attached to one object.
 */
public class Note extends ObjectId {
	private ObjectId data;

	/**
	 * A Git note about the object referenced by {@code noteOn}.
	 *
	 * @param noteOn
	 *            the object that has a note attached to it.
	 * @param noteData
	 *            the actual note data contained in this note
	 */
	public Note(AnyObjectId noteOn, ObjectId noteData) {
		super(noteOn);
		data = noteData;
	}

	/**
	 * Get the note content.
	 *
	 * @return the note content.
	 */
	public ObjectId getData() {
		return data;
	}

	void setData(ObjectId newData) {
		data = newData;
	}

	/** {@inheritDoc} */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return "Note[" + name() + " -> " + data.name() + "]";
	}
}

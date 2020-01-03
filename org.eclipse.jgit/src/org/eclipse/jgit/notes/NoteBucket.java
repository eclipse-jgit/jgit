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

import java.io.IOException;
import java.util.Iterator;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;

/**
 * A tree that stores note objects.
 *
 * @see FanoutBucket
 * @see LeafBucket
 */
abstract class NoteBucket {
	abstract Note getNote(AnyObjectId objId, ObjectReader reader)
			throws IOException;

	abstract Iterator<Note> iterator(AnyObjectId objId, ObjectReader reader)
			throws IOException;

	abstract int estimateSize(AnyObjectId noteOn, ObjectReader or)
			throws IOException;

	abstract InMemoryNoteBucket set(AnyObjectId noteOn, AnyObjectId noteData,
			ObjectReader reader) throws IOException;

	abstract ObjectId writeTree(ObjectInserter inserter) throws IOException;

	abstract ObjectId getTreeId();
}

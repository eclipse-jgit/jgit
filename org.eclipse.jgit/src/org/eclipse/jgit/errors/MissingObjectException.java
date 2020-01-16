/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Jonas Fonseca <fonseca@diku.dk>
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2007, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;

/**
 * An expected object is missing.
 */
public class MissingObjectException extends IOException {
	private static final long serialVersionUID = 1L;

	private final ObjectId missing;

	/**
	 * Construct a MissingObjectException for the specified object id.
	 * Expected type is reported to simplify tracking down the problem.
	 *
	 * @param id SHA-1
	 * @param type object type
	 */
	public MissingObjectException(ObjectId id, String type) {
		super(MessageFormat.format(JGitText.get().missingObject, type, id.name()));
		missing = id.copy();
	}

	/**
	 * Construct a MissingObjectException for the specified object id.
	 * Expected type is reported to simplify tracking down the problem.
	 *
	 * @param id SHA-1
	 * @param type object type
	 */
	public MissingObjectException(ObjectId id, int type) {
		this(id, Constants.typeString(type));
	}

	/**
	 * Construct a MissingObjectException for the specified object id. Expected
	 * type is reported to simplify tracking down the problem.
	 *
	 * @param id
	 *            SHA-1
	 * @param type
	 *            object type
	 */
	public MissingObjectException(AbbreviatedObjectId id, int type) {
		super(MessageFormat.format(JGitText.get().missingObject, Constants
				.typeString(type), id.name()));
		missing = null;
	}

	/**
	 * Get the ObjectId that was not found
	 *
	 * @return the ObjectId that was not found
	 */
	public ObjectId getObjectId() {
		return missing;
	}
}

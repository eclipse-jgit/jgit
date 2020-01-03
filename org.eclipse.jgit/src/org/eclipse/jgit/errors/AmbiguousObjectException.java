/*
 * Copyright (C) 2010, Google Inc. and others
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
import java.util.Collection;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ObjectId;

/**
 * An {@link org.eclipse.jgit.lib.AbbreviatedObjectId} cannot be extended.
 */
public class AmbiguousObjectException extends IOException {
	private static final long serialVersionUID = 1L;

	private final AbbreviatedObjectId missing;

	private final Collection<ObjectId> candidates;

	/**
	 * Construct a MissingObjectException for the specified object id. Expected
	 * type is reported to simplify tracking down the problem.
	 *
	 * @param id
	 *            SHA-1
	 * @param candidates
	 *            the candidate matches returned by the ObjectReader.
	 */
	public AmbiguousObjectException(final AbbreviatedObjectId id,
			final Collection<ObjectId> candidates) {
		super(MessageFormat.format(JGitText.get().ambiguousObjectAbbreviation,
				id.name()));
		this.missing = id;
		this.candidates = candidates;
	}

	/**
	 * Get the {@code AbbreviatedObjectId} that has more than one result
	 *
	 * @return the {@code AbbreviatedObjectId} that has more than one result
	 */
	public AbbreviatedObjectId getAbbreviatedObjectId() {
		return missing;
	}

	/**
	 * Get the matching candidates (or at least a subset of them)
	 *
	 * @return the matching candidates (or at least a subset of them)
	 */
	public Collection<ObjectId> getCandidates() {
		return candidates;
	}
}

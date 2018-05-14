/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Jonas Fonseca <fonseca@diku.dk>
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2007, Shawn O. Pearce <spearce@spearce.org>
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

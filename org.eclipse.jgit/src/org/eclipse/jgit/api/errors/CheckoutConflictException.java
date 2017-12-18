/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com> and
 * other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v1.0 which accompanies this
 * distribution, is reproduced below, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.api.errors;

import java.util.LinkedList;
import java.util.List;

/**
 * Exception thrown when a command can't succeed because of unresolved
 * conflicts.
 */
public class CheckoutConflictException extends GitAPIException {
	private static final long serialVersionUID = 1L;
	private List<String> conflictingPaths;

	/**
	 * Translate internal exception to API exception
	 *
	 * @param conflictingPaths
	 *            list of conflicting paths
	 * @param e
	 *            a {@link org.eclipse.jgit.errors.CheckoutConflictException}
	 *            exception
	 */
	public CheckoutConflictException(List<String> conflictingPaths,
			org.eclipse.jgit.errors.CheckoutConflictException e) {
		super(e.getMessage(), e);
		this.conflictingPaths = conflictingPaths;
	}

	CheckoutConflictException(String message, Throwable cause) {
		super(message, cause);
	}

	CheckoutConflictException(String message, List<String> conflictingPaths, Throwable cause) {
		super(message, cause);
		this.conflictingPaths = conflictingPaths;
	}

	CheckoutConflictException(String message) {
		super(message);
	}

	CheckoutConflictException(String message, List<String> conflictingPaths) {
		super(message);
		this.conflictingPaths = conflictingPaths;
	}

	/**
	 * Get conflicting paths
	 *
	 * @return all the paths where unresolved conflicts have been detected
	 */
	public List<String> getConflictingPaths() {
		return conflictingPaths;
	}

	/**
	 * Adds a new conflicting path
	 *
	 * @param conflictingPath
	 * @return {@code this}
	 */
	CheckoutConflictException addConflictingPath(String conflictingPath) {
		if (conflictingPaths == null)
			conflictingPaths = new LinkedList<>();
		conflictingPaths.add(conflictingPath);
		return this;
	}
}

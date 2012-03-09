/*
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2009, Sasa Zivkov <sasa.zivkov@sap.com>
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

import java.util.Map;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.URIish;

/**
 * Indicates a base/common object was required, but is not found.
 */
public class MissingBundlePrerequisiteException extends TransportException {
	private static final long serialVersionUID = 1L;

	private static String format(final Map<ObjectId, String> missingCommits) {
		final StringBuilder r = new StringBuilder();
		r.append(JGitText.get().missingPrerequisiteCommits);
		for (final Map.Entry<ObjectId, String> e : missingCommits.entrySet()) {
			r.append("\n  ");
			r.append(e.getKey().name());
			if (e.getValue() != null)
				r.append(" ").append(e.getValue());
		}
		return r.toString();
	}

	/**
	 * Constructs a MissingBundlePrerequisiteException for a set of objects.
	 *
	 * @param uri
	 *            URI used for transport
	 * @param missingCommits
	 *            the Map of the base/common object(s) we don't have. Keys are
	 *            ids of the missing objects and values are short descriptions.
	 */
	public MissingBundlePrerequisiteException(final URIish uri,
			final Map<ObjectId, String> missingCommits) {
		super(uri, format(missingCommits));
	}
}

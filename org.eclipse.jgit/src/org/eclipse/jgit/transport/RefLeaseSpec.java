/*
 * Copyright (C) 2017 Two Sigma Open Source
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

package org.eclipse.jgit.transport;

import java.io.Serializable;

/**
 * Describes the expected value for a ref being pushed.
 * @since 4.7
 */
public class RefLeaseSpec implements Serializable {
	private static final long serialVersionUID = 1L;

	/** Name of the ref whose value we want to check. */
	private final String ref;

	/** Local commitish to get expected value from. */
	private final String expected;

	/**
	 *
	 * @param ref
	 *            ref being pushed
	 * @param expected
	 *            the expected value of the ref
	 */
	public RefLeaseSpec(String ref, String expected) {
		this.ref = ref;
		this.expected = expected;
	}

	/**
	 * Get the ref to protect.
	 *
	 * @return name of ref to check.
	 */
	public String getRef() {
		return ref;
	}

	/**
	 * Get the expected value of the ref, in the form
	 * of a local committish
	 *
	 * @return expected ref value.
	 */
	public String getExpected() {
		return expected;
	}

	@Override
	public String toString() {
		final StringBuilder r = new StringBuilder();
		r.append(getRef());
		r.append(':');
		r.append(getExpected());
		return r.toString();
	}
}

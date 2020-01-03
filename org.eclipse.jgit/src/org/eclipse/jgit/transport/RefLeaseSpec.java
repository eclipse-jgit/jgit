/*
 * Copyright (C) 2017 Two Sigma Open Source and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.io.Serializable;

/**
 * Describes the expected value for a ref being pushed.
 *
 * @since 4.7
 */
public class RefLeaseSpec implements Serializable {
	private static final long serialVersionUID = 1L;

	/** Name of the ref whose value we want to check. */
	private final String ref;

	/** Local commitish to get expected value from. */
	private final String expected;

	/**
	 * <p>Constructor for RefLeaseSpec.</p>
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

	/** {@inheritDoc} */
	@Override
	public String toString() {
		final StringBuilder r = new StringBuilder();
		r.append(getRef());
		r.append(':');
		r.append(getExpected());
		return r.toString();
	}
}

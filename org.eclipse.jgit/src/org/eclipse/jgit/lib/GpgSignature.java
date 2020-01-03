/*
 * Copyright (C) 2018, Salesforce. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.Serializable;

import org.eclipse.jgit.annotations.NonNull;

/**
 * A structure for holding GPG signature together with additional related data.
 *
 * @since 5.3
 */
public class GpgSignature implements Serializable {

	private static final long serialVersionUID = 1L;

	private byte[] signature;

	/**
	 * Creates a new instance with the specified signature
	 *
	 * @param signature
	 *            the signature
	 */
	public GpgSignature(@NonNull byte[] signature) {
		this.signature = signature;
	}

	/**
	 * Format for Git storage.
	 * <p>
	 * This returns the ASCII Armor as per
	 * https://tools.ietf.org/html/rfc4880#section-6.2.
	 * </p>
	 *
	 * @return a string of the signature ready to be embedded in a Git object
	 */
	public String toExternalString() {
		return new String(signature, US_ASCII);
	}

	/** {@inheritDoc} */
	@Override
	@SuppressWarnings("nls")
	public String toString() {
		final StringBuilder r = new StringBuilder();

		r.append("GpgSignature[");
		r.append(
				this.signature != null ? "length " + signature.length : "null");
		r.append("]");

		return r.toString();
	}
}

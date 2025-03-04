/*
 * Copyright (C) 2024 Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import org.eclipse.jgit.annotations.NonNull;

/**
 * A factory for {@link SignatureVerifier}s.
 *
 * @since 7.0
 */
public interface SignatureVerifierFactory {

	/**
	 * Tells what kind of {@link SignatureVerifier} this factory creates.
	 *
	 * @return the {@link GpgConfig.GpgFormat} of the signer
	 */
	@NonNull
	GpgConfig.GpgFormat getType();

	/**
	 * Creates a new instance of a {@link SignatureVerifier} that can produce
	 * signatures of type {@link #getType()}.
	 *
	 * @return a new {@link SignatureVerifier}
	 */
	@NonNull
	SignatureVerifier create();
}

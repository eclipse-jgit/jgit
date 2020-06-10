/*
 * Copyright (C) 2018, Salesforce. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.bc.internal;

import java.nio.file.Path;

import org.bouncycastle.openpgp.PGPSecretKey;

/**
 * Container which holds a {@link #getSecretKey()} together with the
 * {@link #getOrigin() path it was loaded from}.
 */
class BouncyCastleGpgKey {

	private PGPSecretKey secretKey;

	private Path origin;

	public BouncyCastleGpgKey(PGPSecretKey secretKey, Path origin) {
		this.secretKey = secretKey;
		this.origin = origin;
	}

	public PGPSecretKey getSecretKey() {
		return secretKey;
	}

	public Path getOrigin() {
		return origin;
	}
}
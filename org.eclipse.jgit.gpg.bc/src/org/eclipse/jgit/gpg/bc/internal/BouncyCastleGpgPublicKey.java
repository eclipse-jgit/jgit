/*
 * Copyright (C) 2024 Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.bc.internal;

import java.util.List;

import org.bouncycastle.openpgp.PGPPublicKey;

/**
 * Container for GPG public keys.
 */
class BouncyCastleGpgPublicKey {

	private final PGPPublicKey publicKey;

	private final boolean exactMatch;

	private final List<String> userIds;

	BouncyCastleGpgPublicKey(PGPPublicKey publicKey, boolean exactMatch,
			List<String> userIds) {
		this.publicKey = publicKey;
		this.exactMatch = exactMatch;
		this.userIds = userIds;
	}

	PGPPublicKey getPublicKey() {
		return publicKey;
	}

	boolean isExactMatch() {
		return exactMatch;
	}

	List<String> getUserIds() {
		return userIds;
	}
}

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

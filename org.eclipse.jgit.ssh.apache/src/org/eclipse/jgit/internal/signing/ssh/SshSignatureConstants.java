/*
 * Copyright (C) 2024, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.signing.ssh;

import java.nio.charset.StandardCharsets;

import org.eclipse.jgit.lib.Constants;

/**
 * Defines common constants for SSH signatures.
 */
final class SshSignatureConstants {

	private static final String SIGNATURE_END = "-----END SSH SIGNATURE-----"; //$NON-NLS-1$

	static final byte[] MAGIC = { 'S', 'S', 'H', 'S', 'I', 'G' };

	static final int VERSION = 1;

	static final String NAMESPACE = "git"; //$NON-NLS-1$

	static final byte[] ARMOR_HEAD = Constants.SSH_SIGNATURE_PREFIX
			.getBytes(StandardCharsets.US_ASCII);

	static final byte[] ARMOR_END = SIGNATURE_END
			.getBytes(StandardCharsets.US_ASCII);

	private SshSignatureConstants() {
		// No instantiation
	}
}

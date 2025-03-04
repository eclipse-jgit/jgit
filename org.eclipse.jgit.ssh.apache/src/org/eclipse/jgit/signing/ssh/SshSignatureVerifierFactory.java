/*
 * Copyright (C) 2024, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.signing.ssh;

import org.eclipse.jgit.lib.GpgConfig.GpgFormat;
import org.eclipse.jgit.lib.SignatureVerifier;
import org.eclipse.jgit.internal.signing.ssh.SshSignatureVerifier;
import org.eclipse.jgit.lib.SignatureVerifierFactory;

/**
 * Factory creating {@link SshSignatureVerifier}s.
 *
 * @since 7.1
 */
public final class SshSignatureVerifierFactory
		implements SignatureVerifierFactory {

	@Override
	public GpgFormat getType() {
		return GpgFormat.SSH;
	}

	@Override
	public SignatureVerifier create() {
		return new SshSignatureVerifier();
	}
}

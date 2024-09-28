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
import org.eclipse.jgit.lib.Signer;
import org.eclipse.jgit.internal.signing.ssh.SshSigner;
import org.eclipse.jgit.lib.SignerFactory;

/**
 * Factory creating {@link SshSigner}s.
 *
 * @since 7.1
 */
public final class SshSignerFactory implements SignerFactory {

	@Override
	public GpgFormat getType() {
		return GpgFormat.SSH;
	}

	@Override
	public Signer create() {
		return new SshSigner();
	}
}

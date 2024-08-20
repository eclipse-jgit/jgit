/*
 * Copyright (C) 2021, 2024 Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.bc.internal;

import org.eclipse.jgit.lib.GpgConfig.GpgFormat;
import org.eclipse.jgit.lib.Signer;
import org.eclipse.jgit.lib.SignerFactory;

/**
 * Factory for creating a {@link Signer} for OPENPGP signatures based on Bouncy
 * Castle.
 */
public final class BouncyCastleGpgSignerFactory implements SignerFactory {

	@Override
	public GpgFormat getType() {
		return GpgFormat.OPENPGP;
	}

	@Override
	public Signer create() {
		return new BouncyCastleGpgSigner();
	}
}

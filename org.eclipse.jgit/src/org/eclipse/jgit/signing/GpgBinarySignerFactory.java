/*
 * Copyright (C) 2026 David Baker Effendi <david@brokk.ai> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.signing;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.lib.GpgConfig.GpgFormat;
import org.eclipse.jgit.lib.Signer;
import org.eclipse.jgit.lib.SignerFactory;

/**
 * A {@link SignerFactory} that creates {@link GpgBinarySigner} instances.
 */
public final class GpgBinarySignerFactory implements SignerFactory {

	@Override
	@NonNull
	public GpgFormat getType() {
		return GpgFormat.OPENPGP;
	}

	@Override
	@NonNull
	public Signer create() {
		return new GpgBinarySigner();
	}
}

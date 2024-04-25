/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.bc.internal;

import org.eclipse.jgit.lib.GpgSignatureVerifier;
import org.eclipse.jgit.lib.GpgSignatureVerifierFactory;

/**
 * A {@link GpgSignatureVerifierFactory} that creates
 * {@link GpgSignatureVerifier} instances that verify GPG signatures using
 * BouncyCastle and that do cache public keys.
 *
 * @deprecated Use {{@link BouncyCastleSignatureVerifierFactory} which exposes the same methods
 */
@Deprecated(since = "6.9.1", forRemoval = true)
public final class BouncyCastleGpgSignatureVerifierFactory
		extends BouncyCastleSignatureVerifierFactory {
}

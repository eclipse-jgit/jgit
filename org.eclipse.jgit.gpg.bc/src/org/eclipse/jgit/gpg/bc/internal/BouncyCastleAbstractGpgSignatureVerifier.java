/*
 * Copyright (C) 2024 GerritForge, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.bc.internal;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.jgit.lib.AbstractGpgSignatureVerifier;

import java.security.Security;

/**
 * A {@link BouncyCastleAbstractGpgSignatureVerifier} that creates
 * serves a base for both CMS and GPG keys.
 */
public abstract class BouncyCastleAbstractGpgSignatureVerifier extends AbstractGpgSignatureVerifier {
    /**
     * Register BouncyCastle provider if necessary
     */
    protected static void registerBouncyCastleProviderIfNecessary() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}

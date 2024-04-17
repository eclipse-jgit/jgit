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

import org.eclipse.jgit.lib.GpgSignatureVerifier;
import org.eclipse.jgit.lib.GpgSignatureVerifierFactory;

/**
 * A {@link GpgSignatureVerifierFactory} that creates
 * {@link GpgSignatureVerifier} instances that verify X509 signatures.
 */
public final class BouncyCastleCMSSignatureVerifierFactory
        extends GpgSignatureVerifierFactory {

    @Override
    public GpgSignatureVerifier getVerifier() {
        return new BouncyCastleCMSSignatureVerifier();
    }

    @Override
    protected byte[] getExpectedSigPrefix() {
        return "-----BEGIN SIGNED MESSAGE".getBytes();
    }

}

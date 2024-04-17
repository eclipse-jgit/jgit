package org.eclipse.jgit.gpg.bc.internal;

import org.eclipse.jgit.lib.GpgSignatureVerifier;
import org.eclipse.jgit.lib.GpgSignatureVerifierFactory;

/**
 * A {@link GpgSignatureVerifierFactory} that creates
 * {@link GpgSignatureVerifier} instances that verify X509 signatures.
 */
public final class SMIMESignatureVerifierFactory
        extends GpgSignatureVerifierFactory {

    @Override
    public GpgSignatureVerifier getVerifier() {
        return new SMIMESignatureVerifier();
    }

}

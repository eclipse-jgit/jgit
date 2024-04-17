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

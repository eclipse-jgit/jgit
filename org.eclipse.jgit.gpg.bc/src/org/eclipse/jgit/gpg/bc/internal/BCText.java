package org.eclipse.jgit.gpg.bc.internal;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;

/**
 * Externalized text messages for localization.
 */
public final class BCText extends TranslationBundle {

	/**
	 * Get an instance of this translation bundle.
	 *
	 * @return an instance of this translation bundle
	 */
	public static BCText get() {
		return NLS.getBundleFor(BCText.class);
	}

	// @formatter:off
	/***/ public String credentialPassphrase;
	/***/ public String gpgFailedToParseSecretKey;
	/***/ public String gpgNoCredentialsProvider;
	/***/ public String gpgNoKeyring;
	/***/ public String gpgNoKeyInLegacySecring;
	/***/ public String gpgNoPublicKeyFound;
	/***/ public String gpgNoSecretKeyForPublicKey;
	/***/ public String gpgNotASigningKey;
	/***/ public String gpgKeyInfo;
	/***/ public String gpgSigningCancelled;
	/***/ public String unableToSignCommitNoSecretKey;

}

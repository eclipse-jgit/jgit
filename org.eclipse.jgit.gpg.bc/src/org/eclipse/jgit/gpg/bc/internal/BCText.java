/*
 * Copyright (C) 2018, 2021 Salesforce and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
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
	/***/ public String nonSignatureError;
	/***/ public String signatureInconsistent;
	/***/ public String signatureKeyLookupError;
	/***/ public String signatureNoKeyInfo;
	/***/ public String signatureNoPublicKey;
	/***/ public String signatureParseError;
	/***/ public String signatureVerificationError;
	/***/ public String unableToSignCommitNoSecretKey;

}

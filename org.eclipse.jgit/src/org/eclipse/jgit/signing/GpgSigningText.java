/*
 * Copyright (C) 2024, 2026 Thomas Wolf <twolf@apache.org>, David Baker Effendi
 * <david@brokk.ai> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.signing;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;

/**
 * Externalized text messages for localization.
 */
@SuppressWarnings("MissingSummary")
public final class GpgSigningText extends TranslationBundle {

	/**
	 * Get an instance of this translation bundle.
	 *
	 * @return an instance of this translation bundle
	 */
	public static GpgSigningText get() {
		return NLS.getBundleFor(GpgSigningText.class);
	}

	// @formatter:off
	/***/ public String ExternalGpg_invalidPath;
	/***/ public String ExternalGpgSigner_cannotSearch;
	/***/ public String ExternalGpgSigner_gpgNotFound;
	/***/ public String ExternalGpgSigner_noSignature;
	/***/ public String ExternalGpgSigner_processFailed;
	/***/ public String ExternalGpgSigner_processInterrupted;
	/***/ public String ExternalGpgSigner_signingCanceled;
	/***/ public String ExternalGpgSigner_skipNotAccessiblePath;
	/***/ public String ExternalGpgVerifier_badSignature;
	/***/ public String ExternalGpgVerifier_erroneousSignature;
	/***/ public String ExternalGpgVerifier_expiredKeySignature;
	/***/ public String ExternalGpgVerifier_expiredSignature;
	/***/ public String ExternalGpgVerifier_failure;
	/***/ public String ExternalGpgVerifier_revokedKeySignature;

}

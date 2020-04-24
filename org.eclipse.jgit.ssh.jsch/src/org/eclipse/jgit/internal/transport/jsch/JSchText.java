/*
 * Copyright (C) 2020, Michael Dardis <git@md-5.net> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.jsch;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;

/**
 * Externalized text messages for localization.
 */
public final class JSchText extends TranslationBundle {

	/**
	 * Get an instance of this translation bundle.
	 *
	 * @return an instance of this translation bundle
	 */
	public static JSchText get() {
		return NLS.getBundleFor(JSchText.class);
	}

	// @formatter:off
	/***/ public String connectionFailed;
	/***/ public String sshUserNameError;
	/***/ public String transportSSHRetryInterrupt;
	/***/ public String unknownHost;

}

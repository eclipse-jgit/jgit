/*
 * Copyright (C) 2014, SAP AG and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport.http.apache.internal;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;

/**
 * Translation bundle for archivers
 */
public class HttpApacheText extends TranslationBundle {
	/**
	 * Get an instance of this translation bundle.
	 *
	 * @return an instance of this translation bundle
	 */
	public static HttpApacheText get() {
		return NLS.getBundleFor(HttpApacheText.class);
	}

	// @formatter:off
	/***/ public String unexpectedSSLContextException;
}

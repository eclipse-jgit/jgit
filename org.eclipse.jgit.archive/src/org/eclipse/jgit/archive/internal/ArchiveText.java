/*
 * Copyright (C) 2013, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.archive.internal;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;

/**
 * Translation bundle for archivers
 */
public class ArchiveText extends TranslationBundle {
	/**
	 * Get an instance of this translation bundle.
	 *
	 * @return an instance of this translation bundle
	 */
	public static ArchiveText get() {
		return NLS.getBundleFor(ArchiveText.class);
	}

	// @formatter:off
	/***/ public String cannotSetOption;
	/***/ public String pathDoesNotMatchMode;
	/***/ public String unsupportedMode;
}

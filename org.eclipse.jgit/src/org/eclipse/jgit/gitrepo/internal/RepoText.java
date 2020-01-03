/*
 * Copyright (C) 2014, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.gitrepo.internal;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;

/**
 * Translation bundle for repo command
 */
public class RepoText extends TranslationBundle {

	/**
	 * Get an instance of this translation bundle
	 *
	 * @return an instance of this translation bundle
	 */
	public static RepoText get() {
		return NLS.getBundleFor(RepoText.class);
	}

	// @formatter:off
	/***/ public String errorIncludeFile;
	/***/ public String errorIncludeNotImplemented;
	/***/ public String errorNoDefault;
	/***/ public String errorNoDefaultFilename;
	/***/ public String errorNoFetch;
	/***/ public String errorParsingManifestFile;
	/***/ public String errorRemoteUnavailable;
	/***/ public String invalidManifest;
	/***/ public String repoCommitMessage;
}

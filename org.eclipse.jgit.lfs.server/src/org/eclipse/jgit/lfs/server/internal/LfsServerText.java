/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs.server.internal;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;

/**
 * Translation bundle for JGit LFS server
 */
public class LfsServerText extends TranslationBundle {

	/**
	 * Get an instance of this translation bundle
	 *
	 * @return an instance of this translation bundle
	 */
	public static LfsServerText get() {
		return NLS.getBundleFor(LfsServerText.class);
	}

	// @formatter:off
	/***/ public String failedToCalcSignature;
	/***/ public String invalidPathInfo;
	/***/ public String objectNotFound;
	/***/ public String undefinedS3AccessKey;
	/***/ public String undefinedS3Bucket;
	/***/ public String undefinedS3Region;
	/***/ public String undefinedS3SecretKey;
	/***/ public String undefinedS3StorageClass;
	/***/ public String unparsableEndpoint;
	/***/ public String unsupportedOperation;
	/***/ public String unsupportedUtf8;
}

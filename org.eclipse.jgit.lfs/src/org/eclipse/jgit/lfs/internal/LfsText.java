/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs.internal;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;

/**
 * Translation bundle for JGit LFS server
 */
public class LfsText extends TranslationBundle {

	/**
	 * Get an instance of this translation bundle.
	 *
	 * @return an instance of this translation bundle
	 */
	public static LfsText get() {
		return NLS.getBundleFor(LfsText.class);
	}

	// @formatter:off
	/***/ public String corruptLongObject;
	/***/ public String inconsistentMediafileLength;
	/***/ public String inconsistentContentLength;
	/***/ public String incorrectLONG_OBJECT_ID_LENGTH;
	/***/ public String invalidLongId;
	/***/ public String invalidLongIdLength;
	/***/ public String lfsUnavailable;
	/***/ public String protocolError;
	/***/ public String requiredHashFunctionNotAvailable;
	/***/ public String repositoryNotFound;
	/***/ public String repositoryReadOnly;
	/***/ public String lfsUnathorized;
	/***/ public String lfsFailedToGetRepository;
	/***/ public String lfsNoDownloadUrl;
	/***/ public String serverFailure;
	/***/ public String wrongAmoutOfDataReceived;
	/***/ public String userConfigInvalid;
	/***/ public String missingLocalObject;
	/***/ public String dotLfsConfigReadFailed;
}

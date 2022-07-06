/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd.agent.connector;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;

/**
 * Externalized text messages for localization.
 */
public final class Texts extends TranslationBundle {

	/**
	 * Get an instance of this translation bundle.
	 *
	 * @return an instance of this translation bundle
	 */
	public static Texts get() {
		return NLS.getBundleFor(Texts.class);
	}

	// @formatter:off
	/***/ public String errCloseMappedFile;
	/***/ public String errLastError;
	/***/ public String errReleaseSharedMemory;
	/***/ public String errUnknown;
	/***/ public String errUnknownIdentityAgent;
	/***/ public String logErrorLoadLibrary;
	/***/ public String msgCloseFailed;
	/***/ public String msgConnectFailed;
	/***/ public String msgConnectPipeFailed;
	/***/ public String msgNoMappedFile;
	/***/ public String msgNoSharedMemory;
	/***/ public String msgPageantUnavailable;
	/***/ public String msgReadFailed;
	/***/ public String msgSendFailed;
	/***/ public String msgSendFailed2;
	/***/ public String msgSharedMemoryFailed;
	/***/ public String msgShortRead;
	/***/ public String pageant;
	/***/ public String unixDefaultAgent;
	/***/ public String winOpenSsh;

}

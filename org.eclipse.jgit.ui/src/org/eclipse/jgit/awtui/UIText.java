/*
 * Copyright (C) 2010, Sasa Zivkov <sasa.zivkov@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.awtui;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;

/**
 * Translation bundle for JGit UI
 */
public class UIText extends TranslationBundle {

	/**
	 * Get an instance of this translation bundle.
	 *
	 * @return an instance of this translation bundle
	 */
	public static UIText get() {
		return NLS.getBundleFor(UIText.class);
	}

	// @formatter:off
	/***/ public String authenticationRequired;
	/***/ public String author;
	/***/ public String date;
	/***/ public String enterUsernameAndPasswordFor;
	/***/ public String mustBeSpecialTableModel;
	/***/ public String password;
	/***/ public String username;
	/***/ public String warning;
}

/*
 * Copyright (C) 2010, Sasa Zivkov <sasa.zivkov@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.nls;

public class GermanTranslatedBundle extends TranslationBundle {
	public static GermanTranslatedBundle get() {
		return NLS.getBundleFor(GermanTranslatedBundle.class);
	}

	public String goodMorning;
}

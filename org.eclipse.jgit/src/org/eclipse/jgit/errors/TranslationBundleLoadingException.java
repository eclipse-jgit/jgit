/*
 * Copyright (C) 2010, Sasa Zivkov <sasa.zivkov@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.errors;

import java.util.Locale;

/**
 * This exception will be thrown when a translation bundle loading
 * fails.
 */
public class TranslationBundleLoadingException extends TranslationBundleException {
	private static final long serialVersionUID = 1L;

	/**
	 * Construct a
	 * {@link org.eclipse.jgit.errors.TranslationBundleLoadingException} for the
	 * specified bundle class and locale.
	 *
	 * @param bundleClass
	 *            the bundle class for which the loading failed
	 * @param locale
	 *            the locale for which the loading failed
	 * @param cause
	 *            the original exception thrown from the
	 *            {@link java.util.ResourceBundle#getBundle(String, Locale)}
	 *            method.
	 */
	public TranslationBundleLoadingException(Class bundleClass, Locale locale, Exception cause) {
		super("Loading of translation bundle failed for [" //$NON-NLS-1$
				+ bundleClass.getName() + ", " + locale.toString() + "]", //$NON-NLS-1$ //$NON-NLS-2$
				bundleClass, locale, cause);
	}
}

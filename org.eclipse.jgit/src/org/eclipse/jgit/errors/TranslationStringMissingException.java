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
 * This exception will be thrown when a translation string for a translation
 * bundle and locale is missing.
 */
public class TranslationStringMissingException extends TranslationBundleException {
	private static final long serialVersionUID = 1L;

	private final String key;

	/**
	 * Construct a
	 * {@link org.eclipse.jgit.errors.TranslationStringMissingException} for the
	 * specified bundle class, locale and translation key
	 *
	 * @param bundleClass
	 *            the bundle class for which a translation string was missing
	 * @param locale
	 *            the locale for which a translation string was missing
	 * @param key
	 *            the key of the missing translation string
	 * @param cause
	 *            the original exception thrown from the
	 *            {@link java.util.ResourceBundle#getString(String)} method.
	 */
	public TranslationStringMissingException(Class bundleClass, Locale locale, String key, Exception cause) {
		super("Translation missing for [" + bundleClass.getName() + ", " //$NON-NLS-1$ //$NON-NLS-2$
				+ locale.toString() + ", " + key + "]", bundleClass, locale, //$NON-NLS-1$ //$NON-NLS-2$
				cause);
		this.key = key;
	}

	/**
	 * Get the key of the missing translation string
	 *
	 * @return the key of the missing translation string
	 */
	public String getKey() {
		return key;
	}
}

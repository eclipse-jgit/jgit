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
 * Common base class for all translation bundle related exceptions.
 */
public abstract class TranslationBundleException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private final Class bundleClass;
	private final Locale locale;

	/**
	 * Construct an instance of
	 * {@link org.eclipse.jgit.errors.TranslationBundleException}
	 *
	 * @param message
	 *            exception message
	 * @param bundleClass
	 *            bundle class for which the exception occurred
	 * @param locale
	 *            locale for which the exception occurred
	 * @param cause
	 *            original exception that caused this exception. Usually thrown
	 *            from the {@link java.util.ResourceBundle} class.
	 */
	protected TranslationBundleException(String message, Class bundleClass, Locale locale, Exception cause) {
		super(message, cause);
		this.bundleClass = bundleClass;
		this.locale = locale;
	}

	/**
	 * Get bundle class
	 *
	 * @return bundle class for which the exception occurred
	 */
	public final Class getBundleClass() {
		return bundleClass;
	}

	/**
	 * Get locale for which the exception occurred
	 *
	 * @return locale for which the exception occurred
	 */
	public final Locale getLocale() {
		return locale;
	}
}

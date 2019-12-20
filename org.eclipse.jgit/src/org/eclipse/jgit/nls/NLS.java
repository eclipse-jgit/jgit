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

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jgit.errors.TranslationBundleLoadingException;
import org.eclipse.jgit.errors.TranslationStringMissingException;

/**
 * The purpose of this class is to provide NLS (National Language Support)
 * configurable per thread.
 *
 * <p>
 * The {@link #setLocale(Locale)} method is used to configure locale for the
 * calling thread. The locale setting is thread inheritable. This means that a
 * child thread will have the same locale setting as its creator thread until it
 * changes it explicitly.
 *
 * <p>
 * Example of usage:
 *
 * <pre>
 * NLS.setLocale(Locale.GERMAN);
 * TransportText t = NLS.getBundleFor(TransportText.class);
 * </pre>
 */
public class NLS {
	/**
	 * The root locale constant. It is defined here because the Locale.ROOT is
	 * not defined in Java 5
	 */
	public static final Locale ROOT_LOCALE = new Locale("", "", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	private static final InheritableThreadLocal<NLS> local = new InheritableThreadLocal<>();

	/**
	 * Sets the locale for the calling thread.
	 * <p>
	 * The {@link #getBundleFor(Class)} method will honor this setting if it
	 * is supported by the provided resource bundle property files. Otherwise,
	 * it will use a fall back locale as described in the
	 * {@link TranslationBundle}
	 *
	 * @param locale
	 *            the preferred locale
	 */
	public static void setLocale(Locale locale) {
		local.set(new NLS(locale));
	}

	/**
	 * Sets the JVM default locale as the locale for the calling thread.
	 * <p>
	 * Semantically this is equivalent to
	 * <code>NLS.setLocale(Locale.getDefault())</code>.
	 */
	public static void useJVMDefaultLocale() {
		useJVMDefaultInternal();
	}

	// TODO(ms): change signature of public useJVMDefaultLocale() in 5.0 to get
	// rid of this internal method
	private static NLS useJVMDefaultInternal() {
		NLS b = new NLS(Locale.getDefault());
		local.set(b);
		return b;
	}

	/**
	 * Returns an instance of the translation bundle of the required type. All
	 * public String fields of the bundle instance will get their values
	 * injected as described in the
	 * {@link org.eclipse.jgit.nls.TranslationBundle}.
	 *
	 * @param type
	 *            required bundle type
	 * @return an instance of the required bundle type
	 * @exception TranslationBundleLoadingException
	 *                see
	 *                {@link org.eclipse.jgit.errors.TranslationBundleLoadingException}
	 * @exception TranslationStringMissingException
	 *                see
	 *                {@link org.eclipse.jgit.errors.TranslationStringMissingException}
	 */
	public static <T extends TranslationBundle> T getBundleFor(Class<T> type) {
		NLS b = local.get();
		if (b == null) {
			b = useJVMDefaultInternal();
		}
		return b.get(type);
	}

	/**
	 * Release resources held by NLS
	 * @since 5.8
	 */
	public static void clear() {
		local.remove();
		GlobalBundleCache.clear();
	}

	private final Locale locale;
	private final ConcurrentHashMap<Class, TranslationBundle> map = new ConcurrentHashMap<>();

	private NLS(Locale locale) {
		this.locale = locale;
	}

	@SuppressWarnings("unchecked")
	private <T extends TranslationBundle> T get(Class<T> type) {
		TranslationBundle bundle = map.get(type);
		if (bundle == null) {
			bundle = GlobalBundleCache.lookupBundle(locale, type);
			// There is a small opportunity for a race, which we may
			// lose. Accept defeat and return the winner's instance.
			TranslationBundle old = map.putIfAbsent(type, bundle);
			if (old != null)
				bundle = old;
		}
		return (T) bundle;
	}
}

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

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.eclipse.jgit.errors.TranslationBundleLoadingException;
import org.eclipse.jgit.errors.TranslationStringMissingException;

/**
 * Global cache of translation bundles.
 * <p>
 * Every translation bundle will be cached here when it gets loaded for the
 * first time from a thread. Another lookup for the same translation bundle
 * (same locale and type) from the same or a different thread will return the
 * cached one.
 * <p>
 * Note that NLS instances maintain per-thread Map of loaded translation
 * bundles. Once a thread accesses a translation bundle it will keep reference
 * to it and will not call {@link #lookupBundle(Locale, Class)} again for the
 * same translation bundle as long as its locale doesn't change.
 */
class GlobalBundleCache {
	private static final Map<Locale, Map<Class, TranslationBundle>> cachedBundles
		= new HashMap<>();

	/**
	 * Looks up for a translation bundle in the global cache. If found returns
	 * the cached bundle. If not found creates a new instance puts it into the
	 * cache and returns it.
	 *
	 * @param <T>
	 *            required bundle type
	 * @param locale
	 *            the preferred locale
	 * @param type
	 *            required bundle type
	 * @return an instance of the required bundle type
	 * @exception TranslationBundleLoadingException see {@link TranslationBundle#load(Locale)}
	 * @exception TranslationStringMissingException see {@link TranslationBundle#load(Locale)}
	 */
	@SuppressWarnings("unchecked")
	static synchronized <T extends TranslationBundle> T lookupBundle(Locale locale, Class<T> type) {
		try {
			Map<Class, TranslationBundle> bundles = cachedBundles.get(locale);
			if (bundles == null) {
				bundles = new HashMap<>();
				cachedBundles.put(locale, bundles);
			}
			TranslationBundle bundle = bundles.get(type);
			if (bundle == null) {
				bundle = type.getDeclaredConstructor().newInstance();
				bundle.load(locale);
				bundles.put(type, bundle);
			}
			return (T) bundle;
		} catch (InstantiationException | IllegalAccessException
				| InvocationTargetException | NoSuchMethodException e) {
			throw new Error(e);
		}
	}

	static void clear() {
		cachedBundles.clear();
	}
}

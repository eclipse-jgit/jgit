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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Locale;

import org.eclipse.jgit.errors.TranslationBundleLoadingException;
import org.eclipse.jgit.errors.TranslationStringMissingException;
import org.junit.Test;

public class TranslationBundleTest {

	@Test
	public void testMissingPropertiesFile() {
		try {
			new NoPropertiesBundle().load(NLS.ROOT_LOCALE);
			fail("Expected TranslationBundleLoadingException");
		} catch (TranslationBundleLoadingException e) {
			assertEquals(NoPropertiesBundle.class, e.getBundleClass());
			assertEquals(NLS.ROOT_LOCALE, e.getLocale());
			// pass
		}
	}

	@Test
	public void testMissingString() {
		try {
			new MissingPropertyBundle().load(NLS.ROOT_LOCALE);
			fail("Expected TranslationStringMissingException");
		} catch (TranslationStringMissingException e) {
			assertEquals("nonTranslatedKey", e.getKey());
			assertEquals(MissingPropertyBundle.class, e.getBundleClass());
			assertEquals(NLS.ROOT_LOCALE, e.getLocale());
			// pass
		}
	}

	@Test
	public void testNonTranslatedBundle() {
		NonTranslatedBundle bundle = new NonTranslatedBundle();

		bundle.load(NLS.ROOT_LOCALE);
		assertEquals(NLS.ROOT_LOCALE, bundle.effectiveLocale());
		assertEquals("Good morning {0}", bundle.goodMorning);

		bundle.load(Locale.ENGLISH);
		assertEquals(NLS.ROOT_LOCALE, bundle.effectiveLocale());
		assertEquals("Good morning {0}", bundle.goodMorning);

		bundle.load(Locale.GERMAN);
		assertEquals(NLS.ROOT_LOCALE, bundle.effectiveLocale());
		assertEquals("Good morning {0}", bundle.goodMorning);
	}

	@Test
	public void testGermanTranslation() {
		GermanTranslatedBundle bundle = new GermanTranslatedBundle();

		bundle.load(NLS.ROOT_LOCALE);
		assertEquals(NLS.ROOT_LOCALE, bundle.effectiveLocale());
		assertEquals("Good morning {0}", bundle.goodMorning);

		bundle.load(Locale.GERMAN);
		assertEquals(Locale.GERMAN, bundle.effectiveLocale());
		assertEquals("Guten Morgen {0}", bundle.goodMorning);
	}

}

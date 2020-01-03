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

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.eclipse.jgit.errors.TranslationBundleLoadingException;
import org.eclipse.jgit.errors.TranslationStringMissingException;

/**
 * Base class for all translation bundles that provides injection of translated
 * texts into public String fields.
 *
 * <p>
 * The usage pattern is shown with the following example. First define a new
 * translation bundle:
 *
 * <pre>
 * public class TransportText extends TranslationBundle {
 * 	public static TransportText get() {
 * 		return NLS.getBundleFor(TransportText.class);
 * 	}
 *
 * 	public String repositoryNotFound;
 *
 * 	public String transportError;
 * }
 * </pre>
 *
 * Second, define one or more resource bundle property files.
 *
 * <pre>
 * TransportText_en_US.properties:
 * 		repositoryNotFound=repository {0} not found
 * 		transportError=unknown error talking to {0}
 * TransportText_de.properties:
 * 		repositoryNotFound=repository {0} nicht gefunden
 * 		transportError=unbekannter Fehler während der Kommunikation mit {0}
 * ...
 * </pre>
 *
 * Then make use of it:
 *
 * <pre>
 * NLS.setLocale(Locale.GERMAN); // or skip this call to stick to the JVM default locale
 * ...
 * throw new TransportException(uri, TransportText.get().transportError);
 * </pre>
 *
 * The translated text is automatically injected into the public String fields
 * according to the locale set with
 * {@link org.eclipse.jgit.nls.NLS#setLocale(Locale)}. However, the
 * {@link org.eclipse.jgit.nls.NLS#setLocale(Locale)} method defines only
 * prefered locale which will be honored only if it is supported by the provided
 * resource bundle property files. Basically, this class will use
 * {@link java.util.ResourceBundle#getBundle(String, Locale)} method to load a
 * resource bundle. See the documentation of this method for a detailed
 * explanation of resource bundle loading strategy. After a bundle is created
 * the {@link #effectiveLocale()} method can be used to determine whether the
 * bundle really corresponds to the requested locale or is a fallback.
 *
 * <p>
 * To load a String from a resource bundle property file this class uses the
 * {@link java.util.ResourceBundle#getString(String)}. This method can throw the
 * {@link java.util.MissingResourceException} and this class is not making any
 * effort to catch and/or translate this exception.
 *
 * <p>
 * To define a concrete translation bundle one has to:
 * <ul>
 * <li>extend this class
 * <li>define a public static get() method like in the example above
 * <li>define public static String fields for each text message
 * <li>make sure the translation bundle class provide public no arg constructor
 * <li>provide one or more resource bundle property files in the same package
 * where the translation bundle class resides
 * </ul>
 */
public abstract class TranslationBundle {

	private Locale effectiveLocale;
	private ResourceBundle resourceBundle;

	/**
	 * Get the locale used for loading the resource bundle from which the field
	 * values were taken.
	 *
	 * @return the locale used for loading the resource bundle from which the
	 *         field values were taken.
	 */
	public Locale effectiveLocale() {
		return effectiveLocale;
	}

	/**
	 * Get the resource bundle on which this translation bundle is based.
	 *
	 * @return the resource bundle on which this translation bundle is based.
	 */
	public ResourceBundle resourceBundle() {
		return resourceBundle;
	}

	/**
	 * Injects locale specific text in all instance fields of this instance.
	 * Only public instance fields of type <code>String</code> are considered.
	 * <p>
	 * The name of this (sub)class plus the given <code>locale</code> parameter
	 * define the resource bundle to be loaded. In other words the
	 * <code>this.getClass().getName()</code> is used as the
	 * <code>baseName</code> parameter in the
	 * {@link ResourceBundle#getBundle(String, Locale)} parameter to load the
	 * resource bundle.
	 * <p>
	 *
	 * @param locale
	 *            defines the locale to be used when loading the resource bundle
	 * @exception TranslationBundleLoadingException
	 *                see {@link TranslationBundleLoadingException}
	 * @exception TranslationStringMissingException
	 *                see {@link TranslationStringMissingException}
	 */
	void load(Locale locale)
			throws TranslationBundleLoadingException {
		Class bundleClass = getClass();
		try {
			resourceBundle = ResourceBundle.getBundle(bundleClass.getName(),
					locale, bundleClass.getClassLoader());
		} catch (MissingResourceException e) {
			throw new TranslationBundleLoadingException(bundleClass, locale, e);
		}
		this.effectiveLocale = resourceBundle.getLocale();

		for (Field field : bundleClass.getFields()) {
			if (field.getType().equals(String.class)) {
				try {
					String translatedText = resourceBundle.getString(field.getName());
					field.set(this, translatedText);
				} catch (MissingResourceException e) {
					throw new TranslationStringMissingException(bundleClass, locale, field.getName(), e);
				} catch (IllegalArgumentException | IllegalAccessException e) {
					throw new Error(e);
				}
			}
		}
	}
}

/*
 * Copyright (C) 2010, Sasa Zivkov <sasa.zivkov@sap.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
 * according to the locale set with {@link NLS#setLocale(Locale)}. However, the
 * {@link NLS#setLocale(Locale)} method defines only prefered locale which will
 * be honored only if it is supported by the provided resource bundle property
 * files. Basically, this class will use
 * {@link ResourceBundle#getBundle(String, Locale)} method to load a resource
 * bundle. See the documentation of this method for a detailed explanation of
 * resource bundle loading strategy. After a bundle is created the
 * {@link #effectiveLocale()} method can be used to determine whether the bundle
 * really corresponds to the requested locale or is a fallback.
 * 
 * <p>
 * To load a String from a resource bundle property file this class uses the
 * {@link ResourceBundle#getString(String)}. This method can throw the
 * {@link MissingResourceException} and this class is not making any effort to
 * catch and/or translate this exception.
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
	 * @return the locale locale used for loading the resource bundle from which
	 *         the field values were taken
	 */
	public Locale effectiveLocale() {
		return effectiveLocale;
	}

	/**
	 * @return the resource bundle on which this translation bundle is based
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
				} catch (IllegalArgumentException e) {
					throw new Error(e);
				} catch (IllegalAccessException e) {
					throw new Error(e);
				}
			}
		}
	}
}
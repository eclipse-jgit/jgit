/*
 * Copyright (C) 2021, 2022 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@code GpgSignatureVerifierFactory} creates {@link GpgSignatureVerifier} instances.
 *
 * @since 5.11
 */
public abstract class GpgSignatureVerifierFactory {

	private static final Logger LOG = LoggerFactory
			.getLogger(GpgSignatureVerifierFactory.class);

	private static class DefaultFactory {

		private static volatile GpgSignatureVerifierFactory defaultFactory = loadDefault();

		private static GpgSignatureVerifierFactory loadDefault() {
			try {
				ServiceLoader<GpgSignatureVerifierFactory> loader = ServiceLoader
						.load(GpgSignatureVerifierFactory.class);
				Iterator<GpgSignatureVerifierFactory> iter = loader.iterator();
				if (iter.hasNext()) {
					return iter.next();
				}
			} catch (ServiceConfigurationError e) {
				LOG.error(e.getMessage(), e);
			}
			return null;
		}

		private DefaultFactory() {
			// No instantiation
		}

		public static GpgSignatureVerifierFactory getDefault() {
			return defaultFactory;
		}

		/**
		 * Sets the default factory.
		 *
		 * @param factory
		 *            the new default factory
		 */
		public static void setDefault(GpgSignatureVerifierFactory factory) {
			defaultFactory = factory;
		}
	}

	/**
	 * Retrieves the default factory.
	 *
	 * @return the default factory or {@code null} if none set
	 */
	public static GpgSignatureVerifierFactory getDefault() {
		return DefaultFactory.getDefault();
	}

	/**
	 * Sets the default factory.
	 *
	 * @param factory
	 *            the new default factory
	 */
	public static void setDefault(GpgSignatureVerifierFactory factory) {
		DefaultFactory.setDefault(factory);
	}

	/**
	 * Creates a new {@link GpgSignatureVerifier}.
	 *
	 * @return the new {@link GpgSignatureVerifier}
	 */
	public abstract GpgSignatureVerifier getVerifier();

}

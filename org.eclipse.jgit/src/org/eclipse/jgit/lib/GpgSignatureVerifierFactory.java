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

import java.util.*;

import org.eclipse.jgit.revwalk.RevCommit;
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

		private static volatile List<GpgSignatureVerifierFactory> defaultFactories = loadDefaults();

		private static List<GpgSignatureVerifierFactory> loadDefaults() {
			try {
				ServiceLoader<GpgSignatureVerifierFactory> loader = ServiceLoader
						.load(GpgSignatureVerifierFactory.class);
				Iterator<GpgSignatureVerifierFactory> iter = loader.iterator();
				if (iter.hasNext()) {
					ArrayList<GpgSignatureVerifierFactory> factories = new ArrayList<>();
					iter.forEachRemaining(factories::add);
					return factories;
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
			return defaultFactories == null ? null : defaultFactories.get(0);
		}

		public static List<GpgSignatureVerifierFactory> getDefaults() {
			return defaultFactories;
		}

		/**
		 * Sets the default factory.
		 *
		 * @param factory
		 *            the new default factory
		 */
		public static void setDefault(GpgSignatureVerifierFactory factory) {
			defaultFactories = List.of(factory);
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

	/**
	 * Creates the correct {@link GpgSignatureVerifierFactory} for the type of signature in the commit
	 *
	 * @param commit the commit to verify
	 *
	 * @return the new {@link GpgSignatureVerifierFactory}
	 */
	public static GpgSignatureVerifierFactory getSignatureVerifierFactory(RevCommit commit) {

		for (GpgSignatureVerifierFactory factory: DefaultFactory.getDefaults()) {
			if(factory.supports(commit)) {
				return factory;
			}
		}

		return null;
	}

	private boolean supports(RevCommit commit) {
		byte[] signature = commit.getRawGpgSignature();
		byte[] expectedSignPrefix = getExpectedSigPrefix();
		return signature != null &&
				Arrays.equals(signature, 0, expectedSignPrefix.length, expectedSignPrefix, 0, expectedSignPrefix.length);
	}

	/**
	 *
	 * The signature prefix that identifies what type of signature it is
	 *
	 * @return the signature prefix
	 */
	protected abstract byte[] getExpectedSigPrefix();
}

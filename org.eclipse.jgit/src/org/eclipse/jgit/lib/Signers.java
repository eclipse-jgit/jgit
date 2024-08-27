/*
 * Copyright (C) 2024 Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import java.text.MessageFormat;
import java.util.EnumMap;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.JGitText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the available signers.
 *
 * @since 7.0
 */
public final class Signers {

	private static final Logger LOG = LoggerFactory.getLogger(Signers.class);

	private static final Map<GpgConfig.GpgFormat, SignerFactory> SIGNER_FACTORIES = loadSigners();

	private static final Map<GpgConfig.GpgFormat, Signer> SIGNERS = new ConcurrentHashMap<>();

	private static Map<GpgConfig.GpgFormat, SignerFactory> loadSigners() {
		Map<GpgConfig.GpgFormat, SignerFactory> result = new EnumMap<>(
				GpgConfig.GpgFormat.class);
		try {
			for (SignerFactory factory : ServiceLoader
					.load(SignerFactory.class)) {
				GpgConfig.GpgFormat format = factory.getType();
				SignerFactory existing = result.get(format);
				if (existing != null) {
					LOG.warn("{}", //$NON-NLS-1$
							MessageFormat.format(
									JGitText.get().signatureServiceConflict,
									"SignerFactory", format, //$NON-NLS-1$
									existing.getClass().getCanonicalName(),
									factory.getClass().getCanonicalName()));
				} else {
					result.put(format, factory);
				}
			}
		} catch (ServiceConfigurationError e) {
			LOG.error(e.getMessage(), e);
		}
		return result;
	}

	private Signers() {
		// No instantiation
	}

	/**
	 * Retrieves a {@link Signer} that can produce signatures of the given type
	 * {@code format}.
	 *
	 * @param format
	 *            {@link GpgConfig.GpgFormat} the signer must support
	 * @return a {@link Signer}, or {@code null} if none is available
	 */
	public static Signer get(@NonNull GpgConfig.GpgFormat format) {
		return SIGNERS.computeIfAbsent(format, f -> {
			SignerFactory factory = SIGNER_FACTORIES.get(format);
			if (factory == null) {
				return null;
			}
			return factory.create();
		});
	}

	/**
	 * Sets a specific signer to use for a specific signature type.
	 *
	 * @param format
	 *            signature type to set the {@code signer} for
	 * @param signer
	 *            the {@link Signer} to use for signatures of type
	 *            {@code format}; if {@code null}, a default implementation, if
	 *            available, may be used.
	 */
	public static void set(@NonNull GpgConfig.GpgFormat format, Signer signer) {
		if (signer == null) {
			SIGNERS.remove(format);
		} else {
			SIGNERS.put(format, signer);
		}
	}
}

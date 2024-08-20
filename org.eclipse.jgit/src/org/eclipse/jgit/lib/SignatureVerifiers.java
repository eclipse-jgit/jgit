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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the available signers.
 *
 * @since 7.0
 */
public final class SignatureVerifiers {

	private static final Logger LOG = LoggerFactory.getLogger(SignatureVerifiers.class);

	private static final byte[] PGP_PREFIX = Constants.GPG_SIGNATURE_PREFIX
			.getBytes(StandardCharsets.US_ASCII);

	private static final byte[] X509_PREFIX = Constants.CMS_SIGNATURE_PREFIX
			.getBytes(StandardCharsets.US_ASCII);

	private static final byte[] SSH_PREFIX = Constants.SSH_SIGNATURE_PREFIX
			.getBytes(StandardCharsets.US_ASCII);

	private static final Map<GpgConfig.GpgFormat, SignatureVerifierFactory> FACTORIES = loadSignatureVerifiers();

	private static final Map<GpgConfig.GpgFormat, SignatureVerifier> VERIFIERS = new ConcurrentHashMap<>();

	private static Map<GpgConfig.GpgFormat, SignatureVerifierFactory> loadSignatureVerifiers() {
		Map<GpgConfig.GpgFormat, SignatureVerifierFactory> result = new EnumMap<>(
				GpgConfig.GpgFormat.class);
		try {
			for (SignatureVerifierFactory factory : ServiceLoader
					.load(SignatureVerifierFactory.class)) {
				GpgConfig.GpgFormat format = factory.getType();
				SignatureVerifierFactory existing = result.get(format);
				if (existing != null) {
					LOG.warn("{}", //$NON-NLS-1$
							MessageFormat.format(
									JGitText.get().signatureServiceConflict,
									"SignatureVerifierFactory", format, //$NON-NLS-1$
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

	private SignatureVerifiers() {
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
	public static SignatureVerifier get(@NonNull GpgConfig.GpgFormat format) {
		return VERIFIERS.computeIfAbsent(format, f -> {
			SignatureVerifierFactory factory = FACTORIES.get(format);
			if (factory == null) {
				return null;
			}
			return factory.create();
		});
	}

	/**
	 * Sets a specific signature verifier to use for a specific signature type.
	 *
	 * @param format
	 *            signature type to set the {@code verifier} for
	 * @param verifier
	 *            the {@link SignatureVerifier} to use for signatures of type
	 *            {@code format}; if {@code null}, a default implementation, if
	 *            available, may be used.
	 */
	public static void set(@NonNull GpgConfig.GpgFormat format,
			SignatureVerifier verifier) {
		SignatureVerifier previous;
		if (verifier == null) {
			previous = VERIFIERS.remove(format);
		} else {
			previous = VERIFIERS.put(format, verifier);
		}
		if (previous != null) {
			previous.clear();
		}
	}

	/**
	 * Verifies the signature on a signed commit or tag.
	 *
	 * @param repository
	 *            the {@link Repository} the object is from
	 * @param config
	 *            the {@link GpgConfig} to use
	 * @param object
	 *            to verify
	 * @return a {@link SignatureVerifier.SignatureVerification} describing the
	 *         outcome of the verification, or {@code null} if the object does
	 *         not have a signature of a known type
	 * @throws IOException
	 *             if an error occurs getting a public key
	 * @throws org.eclipse.jgit.api.errors.JGitInternalException
	 *             if signature verification fails
	 */
	@Nullable
	public static SignatureVerifier.SignatureVerification verify(
			@NonNull Repository repository, @NonNull GpgConfig config,
			@NonNull RevObject object) throws IOException {
		if (object instanceof RevCommit) {
			RevCommit commit = (RevCommit) object;
			byte[] signatureData = commit.getRawGpgSignature();
			if (signatureData == null) {
				return null;
			}
			byte[] raw = commit.getRawBuffer();
			// Now remove the GPG signature
			byte[] header = { 'g', 'p', 'g', 's', 'i', 'g' };
			int start = RawParseUtils.headerStart(header, raw, 0);
			if (start < 0) {
				return null;
			}
			int end = RawParseUtils.nextLfSkippingSplitLines(raw, start);
			// start is at the beginning of the header's content
			start -= header.length + 1;
			// end is on the terminating LF; we need to skip that, too
			if (end < raw.length) {
				end++;
			}
			byte[] data = new byte[raw.length - (end - start)];
			System.arraycopy(raw, 0, data, 0, start);
			System.arraycopy(raw, end, data, start, raw.length - end);
			return verify(repository, config, data, signatureData);
		} else if (object instanceof RevTag) {
			RevTag tag = (RevTag) object;
			byte[] signatureData = tag.getRawGpgSignature();
			if (signatureData == null) {
				return null;
			}
			byte[] raw = tag.getRawBuffer();
			// The signature is just tacked onto the end of the message, which
			// is last in the buffer.
			byte[] data = Arrays.copyOfRange(raw, 0,
					raw.length - signatureData.length);
			return verify(repository, config, data, signatureData);
		}
		return null;
	}

	/**
	 * Verifies a given signature for some give data.
	 *
	 * @param repository
	 *            the {@link Repository} the object is from
	 * @param config
	 *            the {@link GpgConfig} to use
	 * @param data
	 *            to verify the signature of
	 * @param signature
	 *            the given signature of the {@code data}
	 * @return a {@link SignatureVerifier.SignatureVerification} describing the
	 *         outcome of the verification, or {@code null} if the signature
	 *         type is unknown
	 * @throws IOException
	 *             if an error occurs getting a public key
	 * @throws org.eclipse.jgit.api.errors.JGitInternalException
	 *             if signature verification fails
	 */
	@Nullable
	public static SignatureVerifier.SignatureVerification verify(
			@NonNull Repository repository, @NonNull GpgConfig config,
			byte[] data, byte[] signature) throws IOException {
		GpgConfig.GpgFormat format = getFormat(signature);
		if (format == null) {
			return null;
		}
		SignatureVerifier verifier = get(format);
		if (verifier == null) {
			return null;
		}
		return verifier.verify(repository, config, data, signature);
	}

	/**
	 * Determines the type of a given signature.
	 *
	 * @param signature
	 *            to get the type of
	 * @return the signature type, or {@code null} if unknown
	 */
	@Nullable
	public static GpgConfig.GpgFormat getFormat(byte[] signature) {
		if (RawParseUtils.match(signature, 0, PGP_PREFIX) > 0) {
			return GpgConfig.GpgFormat.OPENPGP;
		}
		if (RawParseUtils.match(signature, 0, X509_PREFIX) > 0) {
			return GpgConfig.GpgFormat.X509;
		}
		if (RawParseUtils.match(signature, 0, SSH_PREFIX) > 0) {
			return GpgConfig.GpgFormat.SSH;
		}
		return null;
	}
}
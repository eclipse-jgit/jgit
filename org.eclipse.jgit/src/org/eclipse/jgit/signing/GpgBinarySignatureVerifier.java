/*
 * Copyright (C) 2024, 2026 Thomas Wolf <twolf@apache.org>, David Baker Effendi
 * <david@brokk.ai> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.signing;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Date;
import java.util.Locale;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SignatureVerifier;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.SystemReader;
import org.eclipse.jgit.util.TemporaryBuffer;

/**
 * A signature verifier that uses an external GPG binary.
 */
class GpgBinarySignatureVerifier implements SignatureVerifier {

	private static final String GNUPG_PREFIX = "[GNUPG:] "; //$NON-NLS-1$

	private static final DateTimeFormatter GPG_DATE_FORMAT = new DateTimeFormatterBuilder()
			.appendValue(ChronoField.YEAR, 4)
			.appendValue(ChronoField.MONTH_OF_YEAR, 2)
			.appendValue(ChronoField.DAY_OF_MONTH, 2)
			.appendLiteral('T')
			.appendValue(ChronoField.HOUR_OF_DAY, 2)
			.appendValue(ChronoField.MINUTE_OF_HOUR, 2)
			.appendValue(ChronoField.SECOND_OF_MINUTE, 2)
			.toFormatter(Locale.ROOT);

	private GpgProcessConfiguration processConfiguration;

	/**
	 * Sets the {@link GpgProcessConfiguration} to use for the GPG process.
	 * <p>
	 * This configuration is primarily used to adjust GPG execution for GUI
	 * environments, such as suppressing terminal prompts or cleaning the
	 * environment.
	 * </p>
	 *
	 * @param config
	 *            the configuration; may be {@code null}
	 * @since 7.6
	 */
	public void setProcessConfiguration(GpgProcessConfiguration config) {
		this.processConfiguration = config;
	}

	/**
	 * Returns the {@link GpgProcessConfiguration} used for the GPG process.
	 *
	 * @return the configuration; may be {@code null}
	 * @since 7.6
	 */
	public GpgProcessConfiguration getProcessConfiguration() {
		return processConfiguration;
	}

	@Override
	public SignatureVerification verify(@NonNull Repository repository,
			@NonNull GpgConfig config, byte[] data, byte[] signatureData) {
		File tempSigFile = null;
		ExecutionResult result = null;
		try {
			Path gpgPath = new GpgBinary(config.getProgram()).getPath();
			GpgProcessRunner runner = createProcessRunner();

			tempSigFile = File.createTempFile("jgit-gpg-sig", ".asc"); //$NON-NLS-1$ //$NON-NLS-2$
			Files.write(tempSigFile.toPath(), signatureData);

			ProcessBuilder pb = new ProcessBuilder();
			pb.command(gpgPath.toAbsolutePath().toString(),
					"--status-fd", "1", //$NON-NLS-1$ //$NON-NLS-2$
					"--no-tty", //$NON-NLS-1$
					"--verify", //$NON-NLS-1$
					tempSigFile.getAbsolutePath(),
					"-" //$NON-NLS-1$
			);

			try (InputStream stdin = new ByteArrayInputStream(data)) {
				result = runner.run(pb, stdin, false, processConfiguration);
				if (result == null) {
					return new SignatureVerification(getName(), null, null,
							null, null, false, false, TrustLevel.UNKNOWN,
							JGitText.get().externalGpgVerifierFailure);
				}
				return parseGpgOutput(result);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return new SignatureVerification(getName(), null, null, null,
						null, false, false, TrustLevel.UNKNOWN,
						JGitText.get().externalGpgSignerProcessInterrupted);
			}
		} catch (IOException e) {
			return new SignatureVerification(getName(), null, null, null, null,
					false, false, TrustLevel.UNKNOWN, e.getMessage());
		} finally {
			if (tempSigFile != null) {
				tempSigFile.delete();
			}
			if (result != null) {
				if (result.getStdout() != null) {
					result.getStdout().destroy();
				}
				if (result.getStderr() != null) {
					result.getStderr().destroy();
				}
			}
		}
	}

	private SignatureVerification parseGpgOutput(ExecutionResult result)
			throws IOException {
		String verifier = getName();
		TemporaryBuffer stderr = result.getStderr();
		if (stderr != null && stderr.length() > 0) {
			try (BufferedReader r = new BufferedReader(new InputStreamReader(
					stderr.openInputStream(),
					SystemReader.getInstance().getDefaultCharset()))
			) {
				String firstLine = r.readLine();
				if (firstLine != null && firstLine.startsWith("gpg: ")) { //$NON-NLS-1$
					// Use the program name if possible
					verifier = firstLine.substring(0, 3);
				} else if (firstLine != null) {
					int space = firstLine.indexOf(' ');
					if (space > 0) {
						verifier = firstLine.substring(0, space);
					}
				}
			}
		}

		TemporaryBuffer stdout = result.getStdout();
		if (stdout == null) {
			return new SignatureVerification(verifier, null, null, null, null,
					false, false, TrustLevel.UNKNOWN,
					JGitText.get().externalGpgVerifierFailure);
		}

		String signer = null;
		String keyId = null;
		String fingerprint = null;
		String user = null;
		Date creationDate = null;
		boolean verified = false;
		boolean expired = false;
		TrustLevel trustLevel = TrustLevel.UNKNOWN;
		StringBuilder message = new StringBuilder();
		boolean multipleSignatures = false;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				stdout.openInputStream(),
				org.eclipse.jgit.util.SystemReader.getInstance()
						.getDefaultCharset()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith(GNUPG_PREFIX)) {
					String[] parts = line.substring(GNUPG_PREFIX.length())
							.trim().split(" "); //$NON-NLS-1$
					String token = parts[0];

					switch (token) {
					case "SIG_ID": //$NON-NLS-1$
						if (creationDate == null && parts.length > 3) {
							creationDate = parseTimestamp(parts[3]);
						}
						break;
					case "GOODSIG": //$NON-NLS-1$
						if (keyId != null) {
							multipleSignatures = true;
						}
						verified = true;
						keyId = parts[1];
						signer = keyId;
						if (parts.length > 2) {
							StringBuilder sb = new StringBuilder();
							for (int i = 2; i < parts.length; i++) {
								if (sb.length() > 0)
									sb.append(' ');
								sb.append(parts[i]);
							}
							user = sb.toString();
						}
						break;
					case "VALIDSIG": //$NON-NLS-1$
						fingerprint = parts[1];
						if (creationDate == null && parts.length > 2) {
							creationDate = parseTimestamp(parts[2]);
						}
						break;
					case "EXPKEYSIG": //$NON-NLS-1$
						if (keyId != null) {
							multipleSignatures = true;
						}
						verified = false;
						expired = true;
						keyId = parts[1];
						signer = keyId;
						appendMessage(message, JGitText
								.get().externalGpgVerifierExpiredKeySignature);
						break;
					case "EXPSIG": //$NON-NLS-1$
						if (keyId != null) {
							multipleSignatures = true;
						}
						verified = false;
						expired = true;
						keyId = parts[1];
						signer = keyId;
						appendMessage(message, JGitText
								.get().externalGpgVerifierExpiredSignature);
						break;
					case "REVKEYSIG": //$NON-NLS-1$
						if (keyId != null) {
							multipleSignatures = true;
						}
						verified = false;
						keyId = parts[1];
						signer = keyId;
						appendMessage(message, JGitText
								.get().externalGpgVerifierRevokedKeySignature);
						break;
					case "BADSIG": //$NON-NLS-1$
						if (keyId != null) {
							multipleSignatures = true;
						}
						verified = false;
						keyId = parts[1];
						signer = keyId;
						appendMessage(message, JGitText
								.get().externalGpgVerifierBadSignature);
						break;
					case "ERRSIG": //$NON-NLS-1$
						if (keyId != null) {
							multipleSignatures = true;
						}
						verified = false;
						keyId = parts[1];
						signer = keyId;
						appendMessage(message, JGitText
								.get().externalGpgVerifierErroneousSignature);
						break;
					case "TRUST_UNDEFINED": //$NON-NLS-1$
						trustLevel = TrustLevel.UNKNOWN;
						break;
					case "TRUST_NEVER": //$NON-NLS-1$
						trustLevel = TrustLevel.NEVER;
						break;
					case "TRUST_MARGINAL": //$NON-NLS-1$
						trustLevel = TrustLevel.MARGINAL;
						break;
					case "TRUST_FULLY": //$NON-NLS-1$
						trustLevel = TrustLevel.FULL;
						break;
					case "TRUST_ULTIMATE": //$NON-NLS-1$
						trustLevel = TrustLevel.ULTIMATE;
						break;
					default:
						break;
					}
				}
			}
		}

		if (multipleSignatures) {
			verified = false;
			appendMessage(message, "Multiple signatures"); //$NON-NLS-1$
		}

		return new SignatureVerification(verifier, creationDate, signer,
				fingerprint != null ? fingerprint : keyId, user, verified,
				expired, trustLevel,
				message.length() > 0 ? message.toString() : null);
	}

	private void appendMessage(StringBuilder sb, String msg) {
		if (sb.length() > 0) {
			sb.append("; "); //$NON-NLS-1$
		}
		sb.append(msg);
	}

	private Date parseTimestamp(String timestamp) {
		if (timestamp == null || timestamp.isEmpty()) {
			return null;
		}
		try {
			if (timestamp.indexOf('T') > 0) {
				// ISO 8601: yyyyMMddTHHmmss
				return Date.from(GPG_DATE_FORMAT.parse(timestamp, LocalDateTime::from)
						.atOffset(ZoneOffset.UTC).toInstant());
			}
			// Unix seconds
			return new Date(Long.parseLong(timestamp) * 1000);
		} catch (DateTimeParseException | NumberFormatException e) {
			return null;
		}
	}

	@Override
	public @NonNull String getName() {
		return "gpg-binary"; //$NON-NLS-1$
	}

	@Override
	public void clear() {
		// No caching implemented
	}

	/**
	 * Creates a {@link GpgProcessRunner} to run the GPG binary.
	 *
	 * @return the runner
	 */
	protected GpgProcessRunner createProcessRunner() {
		return new GpgProcessRunner();
	}
}

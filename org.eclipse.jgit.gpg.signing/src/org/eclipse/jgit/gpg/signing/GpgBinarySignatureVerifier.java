/*
 * Copyright (C) 2024, SAP SE and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.signing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Date;
import java.util.Scanner;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.gpg.signing.internal.GpgSigningText;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SignatureVerifier;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.TemporaryBuffer;

/**
 * A signature verifier that uses an external GPG binary.
 */
public class GpgBinarySignatureVerifier implements SignatureVerifier {

	@Override
	public SignatureVerification verify(@NonNull Repository repository,
			@NonNull GpgConfig config, byte[] data, byte[] signatureData) {
		try {
			Path gpgPath = new GpgBinary(config.getProgram()).getPath();
			GpgProcessRunner runner = new GpgProcessRunner(
					gpgPath.toAbsolutePath().toString());

			// Create a combined input: signature followed by data
			// GPG --verify expects the signature file then the data file,
			// or just the signature file if it's a combined signature.
			// For detached signatures: gpg --verify <sig> <data>
			// We can use '-' for one of them to read from stdin.
			// Here we pass the signature as an argument via a temp file-like
			// approach or just use stdin if we can.
			// Detached signature verification with stdin:
			// gpg --status-fd 2 --verify <sig-file> -
			
			// To avoid temp files, we use a specific GPG trick or just write the sig to a temp file.
			// However, GpgProcessRunner is currently built for simple stdin.
			// Let's use a simpler approach: gpg --verify takes the signature file.
			// Since we only have byte arrays, we'll write the signature to a temporary buffer
			// but GPG needs a file path. 
			// Instead, we can use: gpg --verify [options] [sigfile] [datafile]
			// We will use '-' for data and pass the signature data via a temporary file.
			
			java.io.File tempSigFile = java.io.File.createTempFile("jgit-gpg-sig", ".asc"); //$NON-NLS-1$ //$NON-NLS-2$
			try {
				java.nio.file.Files.write(tempSigFile.toPath(), signatureData);
				
				String[] args = {
						"--status-fd", "2", //$NON-NLS-1$ //$NON-NLS-2$
						"--no-tty", //$NON-NLS-1$
						"--verify", //$NON-NLS-1$
						tempSigFile.getAbsolutePath(),
						"-" //$NON-NLS-1$
				};

				try (InputStream stdin = new ByteArrayInputStream(data)) {
					ExecutionResult result = runner.run(args, stdin);
					return parseGpgOutput(result.getStderr());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return new SignatureVerification(getName(), null, null, null,
							null, false, false, TrustLevel.UNKNOWN,
							GpgSigningText.get().ExternalGpgSigner_processInterrupted);
				}
			} finally {
				tempSigFile.delete();
			}
		} catch (IOException e) {
			return new SignatureVerification(getName(), null, null, null, null,
					false, false, TrustLevel.UNKNOWN, e.getMessage());
		}
	}

	private SignatureVerification parseGpgOutput(TemporaryBuffer stderr)
			throws IOException {
		if (stderr == null) {
			return new SignatureVerification(getName(), null, null, null, null,
					false, false, TrustLevel.UNKNOWN,
					GpgSigningText.get().ExternalGpgVerifier_failure);
		}

		String signer = null;
		String fingerprint = null;
		String user = null;
		Date creationDate = null;
		boolean verified = false;
		boolean expired = false;
		TrustLevel trustLevel = TrustLevel.UNKNOWN;
		StringBuilder message = new StringBuilder();

		try (Scanner scanner = new Scanner(stderr.openInputStream(), "UTF-8")) { //$NON-NLS-1$
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				if (line.startsWith("[GNUPG:] ")) { //$NON-NLS-1$
					String[] parts = line.substring(9).split(" "); //$NON-NLS-1$
					String token = parts[0];

					switch (token) {
					case "GOODSIG": //$NON-NLS-1$
						verified = true;
						signer = parts[1];
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
						try {
							long timestamp = Long.parseLong(parts[2]);
							creationDate = new Date(timestamp * 1000);
						} catch (NumberFormatException e) {
							// ignore
						}
						break;
					case "EXPKEYSIG": //$NON-NLS-1$
						verified = false;
						expired = true;
						signer = parts[1];
						message.append(GpgSigningText
								.get().ExternalGpgVerifier_expiredKeySignature);
						break;
					case "EXPSIG": //$NON-NLS-1$
						verified = false;
						expired = true;
						signer = parts[1];
						message.append(GpgSigningText
								.get().ExternalGpgVerifier_expiredSignature);
						break;
					case "REVKEYSIG": //$NON-NLS-1$
						verified = false;
						signer = parts[1];
						message.append(GpgSigningText
								.get().ExternalGpgVerifier_revokedKeySignature);
						break;
					case "BADSIG": //$NON-NLS-1$
						verified = false;
						signer = parts[1];
						message.append(GpgSigningText
								.get().ExternalGpgVerifier_badSignature);
						break;
					case "ERRSIG": //$NON-NLS-1$
						verified = false;
						signer = parts[1];
						message.append(GpgSigningText
								.get().ExternalGpgVerifier_erroneousSignature);
						break;
					case "TRUST_UNDEFINED": //$NON-NLS-1$
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

		return new SignatureVerification(getName(), creationDate, signer,
				fingerprint, user, verified, expired, trustLevel,
				message.length() > 0 ? message.toString() : null);
	}

	@Override
	public @NonNull String getName() {
		return "gpg-binary"; //$NON-NLS-1$
	}

	@Override
	public void clear() {
		// No caching implemented
	}
}

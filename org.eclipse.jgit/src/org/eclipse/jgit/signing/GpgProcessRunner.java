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

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;

import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.TemporaryBuffer;

import java.util.stream.Collectors;

import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A helper to run external processes.
 */
final class GpgProcessRunner {

	private static final Logger LOG = LoggerFactory
			.getLogger(GpgProcessRunner.class);

	/**
	 * Runs an external process.
	 *
	 * @param pb
	 *            the {@link ProcessBuilder} to use
	 * @param stdin
	 *            the input to pass to the process; may be {@code null}
	 * @return the execution result
	 * @throws IOException
	 *             if the process failed or was canceled
	 * @throws InterruptedException
	 *             if the process was interrupted
	 */
	public ExecutionResult run(ProcessBuilder pb, InputStream stdin)
			throws IOException, InterruptedException {
		return run(pb, stdin, true);
	}

	// GPG error code for "Operation cancelled", typically from pinentry.
	// This is the lower 16 bits of the error code in [GNUPG:] FAILURE/ERROR
	// status lines (e.g., "FAILURE sign 33554531" where 33554531 & 0xFFFF == 99).
	// Using this is locale-independent, unlike parsing error message text.
	private static final int GPG_ERR_CANCELED = 99;

	/**
	 * Runs an external process.
	 *
	 * @param pb
	 *            the {@link ProcessBuilder} to use
	 * @param stdin
	 *            the input to pass to the process; may be {@code null}
	 * @param failOnNonZero
	 *            whether to throw an {@link IOException} if the process exit
	 *            code is not zero
	 * @return the execution result
	 * @throws IOException
	 *             if the process failed or was canceled
	 * @throws InterruptedException
	 *             if the process was interrupted
	 */
	public ExecutionResult run(ProcessBuilder pb, InputStream stdin,
			boolean failOnNonZero) throws IOException, InterruptedException {
		// Force C locale to ensure GPG outputs predictable, non-localized
		// messages. The [GNUPG:] status tokens are locale-independent by
		// design, but stderr messages (used for program name extraction and
		// diagnostics) are localized. Setting LC_ALL=C ensures consistent
		// behavior across all system locales.
		pb.environment().put("LC_ALL", "C"); //$NON-NLS-1$ //$NON-NLS-2$

		String command = pb.command().stream().collect(Collectors.joining(" ")); //$NON-NLS-1$
		ExecutionResult result = null;
		try {
			LOG.debug("Spawning process: {}", command); //$NON-NLS-1$
			LOG.debug("Environment: {}", pb.environment()); //$NON-NLS-1$

			result = FS.DETECTED.execute(pb, stdin);
			int rc = result.getRc();

			LOG.debug("stderr:\n{}", toString(result.getStderr())); //$NON-NLS-1$
			LOG.debug("stdout:\n{}", toString(result.getStdout())); //$NON-NLS-1$
			LOG.debug("Spawned process exited with exit code {}", //$NON-NLS-1$
					rc);

			if (failOnNonZero && rc != 0) {
				String error = toString(result.getStderr());
				if (isCanceled(error)) {
					throw new IOException(GpgSigningText.get().ExternalGpgSigner_signingCanceled);
				}
				throw new IOException(MessageFormat.format(
						GpgSigningText.get().ExternalGpgSigner_processFailed,
						command, rc + ": " + error)); //$NON-NLS-1$
			}
			return result;
		} finally {
			// In case of non-zero failure where we don't throw, the caller
			// is responsible for destroying buffers. If we throw, we clean up.
			if (result != null && failOnNonZero && result.getRc() != 0) {
				if (result.getStderr() != null) {
					result.getStderr().destroy();
				}
				if (result.getStdout() != null) {
					result.getStdout().destroy();
				}
			}
		}
	}

	/**
	 * Checks if the GPG status output indicates the operation was canceled.
	 * <p>
	 * GPG uses --status-fd to write machine-readable status lines. When the
	 * user cancels the pinentry passphrase dialog, GPG emits a FAILURE or ERROR
	 * line with an error code whose lower 16 bits equal GPG_ERR_CANCELED (99).
	 * <p>
	 * For example: {@code [GNUPG:] FAILURE sign 33554531} where
	 * {@code 33554531 & 0xFFFF == 99}.
	 * <p>
	 * This approach is locale-independent, unlike parsing the human-readable
	 * error message text which varies by language.
	 *
	 * @param statusOutput
	 *            the stderr output (which receives --status-fd 2 output)
	 * @return {@code true} if the output indicates cancellation
	 */
	private static boolean isCanceled(String statusOutput) {
		if (statusOutput == null || statusOutput.isEmpty()) {
			return false;
		}
		// Look for [GNUPG:] FAILURE or [GNUPG:] ERROR lines
		for (String line : statusOutput.split("\n")) { //$NON-NLS-1$
			line = line.trim();
			if (line.startsWith("[GNUPG:] FAILURE ") //$NON-NLS-1$
					|| line.startsWith("[GNUPG:] ERROR ")) { //$NON-NLS-1$
				// Extract the numeric error code (last token on the line)
				int lastSpace = line.lastIndexOf(' ');
				if (lastSpace > 0) {
					try {
						long errorCode = Long.parseLong(
								line.substring(lastSpace + 1));
						if ((errorCode & 0xFFFF) == GPG_ERR_CANCELED) {
							return true;
						}
					} catch (NumberFormatException e) {
						// Not a valid error code, continue
					}
				}
			}
		}
		return false;
	}

	private static String toString(TemporaryBuffer b) {
		if (b != null) {
			try {
				return new String(b.toByteArray(4000),
						SystemReader.getInstance().getDefaultCharset());
			} catch (IOException e) {
				LOG.warn("Error reading process output buffer", e); //$NON-NLS-1$
			}
		}
		return ""; //$NON-NLS-1$
	}
}

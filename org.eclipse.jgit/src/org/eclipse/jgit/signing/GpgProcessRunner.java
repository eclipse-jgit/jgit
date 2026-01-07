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
public class GpgProcessRunner {

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
		String command = pb.command().stream().collect(Collectors.joining(" ")); //$NON-NLS-1$
		ExecutionResult result = null;
		try {
			if (LOG.isDebugEnabled()) {
				LOG.debug("Spawning process: {}", command); //$NON-NLS-1$
				LOG.debug("Environment: {}", pb.environment()); //$NON-NLS-1$
			}
			result = FS.DETECTED.execute(pb, stdin);
			int rc = result.getRc();

			if (LOG.isDebugEnabled()) {
				LOG.debug("stderr:\n{}", toString(result.getStderr())); //$NON-NLS-1$
				LOG.debug("stdout:\n{}", toString(result.getStdout())); //$NON-NLS-1$
				LOG.debug("Spawned process exited with exit code {}", //$NON-NLS-1$
						Integer.valueOf(rc));
			}

			if (failOnNonZero && rc != 0) {
				String error = toString(result.getStderr());
				if (error.contains("operation canceled") //$NON-NLS-1$
						|| error.contains("Operation cancelled")) { //$NON-NLS-1$
					throw new IOException(
							GpgSigningText.get().ExternalGpgSigner_signingCanceled);
				}
				throw new IOException(MessageFormat.format(
						GpgSigningText.get().ExternalGpgSigner_processFailed,
						command, Integer.toString(rc) + ": " + error)); //$NON-NLS-1$
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

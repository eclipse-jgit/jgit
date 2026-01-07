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

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;

import org.eclipse.jgit.gpg.signing.internal.GpgSigningText;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.TemporaryBuffer;

/**
 * A helper to run GPG processes.
 */
public class GpgProcessRunner {

	private final String gpgExecutable;

	/**
	 * Creates a new instance.
	 *
	 * @param gpgExecutable
	 *            the path to the GPG executable
	 */
	public GpgProcessRunner(String gpgExecutable) {
		this.gpgExecutable = gpgExecutable;
	}

	/**
	 * Runs a GPG process.
	 *
	 * @param args
	 *            the arguments to pass to GPG
	 * @param stdin
	 *            the input to pass to GPG
	 * @return the execution result
	 * @throws IOException
	 *             if the process failed
	 * @throws InterruptedException
	 *             if the process was interrupted
	 */
	public ExecutionResult run(String[] args, InputStream stdin)
			throws IOException, InterruptedException {
		String[] command = new String[args.length + 1];
		command[0] = gpgExecutable;
		System.arraycopy(args, 0, command, 1, args.length);

		ProcessBuilder pb = new ProcessBuilder(command);
		ExecutionResult result = FS.DETECTED.execute(pb, stdin);

		int rc = result.getRc();
		if (rc != 0) {
			TemporaryBuffer stderr = result.getStderr();
			if (stderr != null) {
				String error = stderr.toString(1024);
				if (error.contains("operation canceled") //$NON-NLS-1$
						|| error.contains("Operation cancelled")) { //$NON-NLS-1$
					throw new IOException(
							GpgSigningText.get().ExternalGpgSigner_signingCanceled);
				}
			}
			throw new IOException(MessageFormat.format(
					GpgSigningText.get().ExternalGpgSigner_processFailed,
					Integer.valueOf(rc)));
		}
		return result;
	}
}

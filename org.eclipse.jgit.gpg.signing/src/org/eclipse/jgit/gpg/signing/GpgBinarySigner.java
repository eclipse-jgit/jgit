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
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.UnsupportedSigningFormatException;
import org.eclipse.jgit.gpg.signing.internal.GpgSigningText;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.GpgSignature;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.Signer;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.TemporaryBuffer;

/**
 * GPG Signer that uses an external GPG binary.
 */
public class GpgBinarySigner implements Signer {

	@Override
	public GpgSignature sign(@NonNull Repository repository,
			@NonNull GpgConfig config, byte[] data,
			@NonNull PersonIdent committer, @Nullable String signingKey,
			@Nullable CredentialsProvider credentialsProvider)
			throws CanceledException, IOException,
			UnsupportedSigningFormatException {

		String key = signingKey != null ? signingKey : config.getSigningKey();
		if (key == null || key.isEmpty()) {
			key = committer.getEmailAddress();
		}

		Path gpgPath = new GpgBinary(config.getProgram()).getPath();
		GpgProcessRunner runner = createProcessRunner(
				gpgPath.toAbsolutePath().toString());

		List<String> args = new ArrayList<>();
		args.add("--detach-sign"); //$NON-NLS-1$
		args.add("--armor"); //$NON-NLS-1$
		args.add("--no-tty"); //$NON-NLS-1$
		args.add("--status-fd"); //$NON-NLS-1$
		args.add("2"); //$NON-NLS-1$
		args.add("--batch"); //$NON-NLS-1$

		if (key != null && !key.isEmpty()) {
			args.add("--local-user"); //$NON-NLS-1$
			args.add(key);
		}

		try (InputStream stdin = new ByteArrayInputStream(data)) {
			ExecutionResult result = runner.run(args.toArray(new String[0]),
					stdin);

			if (result.getRc() != 0) {
				throw new JGitInternalException(
						GpgSigningText.get().ExternalGpgSigner_processFailed
								+ "\n" + result.getStderr().toString()); //$NON-NLS-1$
			}

			TemporaryBuffer stdout = result.getStdout();
			if (stdout == null || stdout.length() == 0) {
				throw new JGitInternalException(
						GpgSigningText.get().ExternalGpgSigner_noSignature);
			}

			return new GpgSignature(stdout.toByteArray());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new JGitInternalException(
					GpgSigningText.get().ExternalGpgSigner_processInterrupted,
					e);
		}
	}

	@Override
	public boolean canLocateSigningKey(@NonNull Repository repository,
			@NonNull GpgConfig config, @NonNull PersonIdent committer,
			@Nullable String signingKey,
			@Nullable CredentialsProvider credentialsProvider)
			throws CanceledException {
		try {
			String key = signingKey != null ? signingKey
					: config.getSigningKey();
			if (key == null || key.isEmpty()) {
				key = committer.getEmailAddress();
			}

			Path gpgPath = new GpgBinary(config.getProgram()).getPath();
			GpgProcessRunner runner = createProcessRunner(
					gpgPath.toAbsolutePath().toString());

			String[] args = { "--list-secret-keys", "--batch", key }; //$NON-NLS-1$ //$NON-NLS-2$
			runner.run(args, null);
			return true;
		} catch (IOException | InterruptedException e) {
			return false;
		}
	}

	/**
	 * Creates a {@link GpgProcessRunner} to run the GPG binary.
	 *
	 * @param gpgExecutable
	 *            the path to the GPG executable
	 * @return the runner
	 */
	protected GpgProcessRunner createProcessRunner(String gpgExecutable) {
		return new GpgProcessRunner(gpgExecutable);
	}
}

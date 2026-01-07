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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.api.errors.JGitInternalException;
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
class GpgBinarySigner implements Signer {

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
	 */
	public void setProcessConfiguration(GpgProcessConfiguration config) {
		this.processConfiguration = config;
	}

	/**
	 * Returns the {@link GpgProcessConfiguration} used for the GPG process.
	 *
	 * @return the configuration; may be {@code null}
	 */
	public GpgProcessConfiguration getProcessConfiguration() {
		return processConfiguration;
	}

	@Override
	public GpgSignature sign(@NonNull Repository repository,
			@NonNull GpgConfig config, byte[] data,
			@NonNull PersonIdent committer, @Nullable String signingKey,
			@Nullable CredentialsProvider credentialsProvider)
			throws IOException {

		String keySpec = signingKey != null ? signingKey
				: config.getSigningKey();
		if (keySpec == null || keySpec.isEmpty()) {
			keySpec = '<' + committer.getEmailAddress() + '>';
		}

		Path gpgPath = new GpgBinary(config.getProgram()).getPath();
		GpgProcessRunner runner = createProcessRunner();

		ProcessBuilder pb = new ProcessBuilder();
		pb.command(gpgPath.toAbsolutePath().toString(),
				// Detached signature, sign, armor, user
				"-bsau", //$NON-NLS-1$
				keySpec,
				// Write extra status messages to stderr
				"--status-fd", //$NON-NLS-1$
				"2" //$NON-NLS-1$
		);

		try (InputStream stdin = new ByteArrayInputStream(data)) {
			ExecutionResult result = runner.run(pb, stdin,
					processConfiguration);

			if (result == null) {
				throw new JGitInternalException(
						JGitText.get().externalGpgSignerNoSignature);
			}
			TemporaryBuffer stdout = result.getStdout();
			if (stdout == null || stdout.length() == 0) {
				throw new JGitInternalException(
						JGitText.get().externalGpgSignerNoSignature);
			}

			return new GpgSignature(stdout.toByteArray());
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new JGitInternalException(
					JGitText.get().externalGpgSignerProcessInterrupted,
					e);
		}
	}

	@Override
	public boolean canLocateSigningKey(@NonNull Repository repository,
			@NonNull GpgConfig config, @NonNull PersonIdent committer,
			@Nullable String signingKey,
			@Nullable CredentialsProvider credentialsProvider) {
		try {
			String key = signingKey != null ? signingKey
					: config.getSigningKey();
			if (key == null || key.isEmpty()) {
				key = committer.getEmailAddress();
			}

			Path gpgPath = new GpgBinary(config.getProgram()).getPath();
			GpgProcessRunner runner = createProcessRunner();

			ProcessBuilder pb = new ProcessBuilder();
			pb.command(gpgPath.toAbsolutePath().toString(),
					"--list-secret-keys", "--batch", key); //$NON-NLS-1$ //$NON-NLS-2$
			ExecutionResult result = runner.run(pb, null, false,
					processConfiguration);
			return result != null && result.getRc() == 0;
		} catch (IOException | InterruptedException e) {
			return false;
		}
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

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

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;

import org.eclipse.jgit.gpg.signing.internal.GpgSigningText;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper for finding the GPG binary.
 */
public class GpgBinary {

	private static final Logger LOG = LoggerFactory.getLogger(GpgBinary.class);

	private final String gpgExecutable;

	/**
	 * Creates a new instance.
	 *
	 * @param gpgExecutable
	 *            the path to the GPG executable; if {@code null} or empty, a
	 *            default will be searched.
	 */
	public GpgBinary(String gpgExecutable) {
		this.gpgExecutable = gpgExecutable;
	}

	/**
	 * Finds the GPG executable.
	 *
	 * @return the path to the GPG executable
	 * @throws IOException
	 *             if the GPG executable could not be found
	 */
	public Path getPath() throws IOException {
		if (gpgExecutable != null && !gpgExecutable.isEmpty()) {
			try {
				Path path = Paths.get(gpgExecutable);
				if (path.isAbsolute()) {
					return path;
				}
			} catch (InvalidPathException e) {
				throw new IOException(MessageFormat.format(
						GpgSigningText.get().ExternalGpg_invalidPath,
						gpgExecutable), e);
			}
		}

		String command = (gpgExecutable != null && !gpgExecutable.isEmpty())
				? gpgExecutable
				: "gpg"; //$NON-NLS-1$

		File gpg = FS.DETECTED.searchPath(
				SystemReader.getInstance().getenv("PATH"), command); //$NON-NLS-1$
		if (gpg != null) {
			return gpg.toPath();
		}

		if (SystemReader.getInstance().isWindows()) {
			// Try some common locations on Windows
			String[] commonLocations = {
					"C:\\Program Files (x86)\\gnupg\\bin\\gpg.exe", //$NON-NLS-1$
					"C:\\Program Files\\gnupg\\bin\\gpg.exe", //$NON-NLS-1$
					"C:\\Program Files (x86)\\GnuPG\\gpg.exe", //$NON-NLS-1$
					"C:\\Program Files\\GnuPG\\gpg.exe" //$NON-NLS-1$
			};
			for (String loc : commonLocations) {
				try {
					File f = new File(loc);
					if (f.exists() && f.canExecute()) {
						return f.toPath();
					}
				} catch (SecurityException e) {
					LOG.warn(MessageFormat.format(
							GpgSigningText.get().ExternalGpgSigner_skipNotAccessiblePath,
							loc), e);
				}
			}
		}

		throw new IOException(GpgSigningText.get().ExternalGpgSigner_gpgNotFound);
	}
}

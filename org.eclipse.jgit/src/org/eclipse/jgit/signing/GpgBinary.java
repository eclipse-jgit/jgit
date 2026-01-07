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
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper for finding the GPG binary.
 */
class GpgBinary {

	private static final Logger LOG = LoggerFactory.getLogger(GpgBinary.class);

	private static final Map<String, String> EXECUTABLES = new ConcurrentHashMap<>();

	private final String gpgExecutable;

	/**
	 * Creates a new instance.
	 *
	 * @param gpgExecutable
	 *            the path to the GPG executable; if {@code null} or empty, a
	 *            default will be searched.
	 */
	 GpgBinary(String gpgExecutable) {
		this.gpgExecutable = gpgExecutable;
	}

	/**
	 * Finds the GPG executable.
	 *
	 * @return the path to the GPG executable
	 * @throws IOException
	 *             if the GPG executable could not be found
	 */
	 Path getPath() throws IOException {
		String program = (gpgExecutable != null && !gpgExecutable.isEmpty())
				? gpgExecutable
				: "gpg"; //$NON-NLS-1$

		Path exe;
		try {
			exe = Paths.get(program);
		} catch (InvalidPathException e) {
			throw new IOException(MessageFormat.format(
					GpgSigningText.get().ExternalGpg_invalidPath, program), e);
		}

		if (exe.isAbsolute()) {
			return exe;
		}

		// Resolve only simple names from PATH; otherwise use as-is.
		if (exe.getNameCount() == 1) {
			String resolved = get(program);
			if (resolved != null) {
				return Paths.get(resolved);
			}
			throw new IOException(
					GpgSigningText.get().ExternalGpgSigner_gpgNotFound);
		}

		// A relative path (contains separators). Use as-is if it exists and is
		// executable; otherwise fail here to provide a better error.
		File f = exe.toFile();
		try {
			if (f.isFile() && f.canExecute()) {
				return exe;
			}
		} catch (SecurityException e) {
			logSkipNotAccessiblePath(f.getPath(), e);
		}
		throw new IOException(GpgSigningText.get().ExternalGpgSigner_gpgNotFound);
	}

	private static String get(String program) {
		String resolved = EXECUTABLES.computeIfAbsent(program,
				GpgBinary::findProgram);
		return resolved.isEmpty() ? null : resolved;
	}

	private static String findProgram(String program) {
		SystemReader system = SystemReader.getInstance();
		String path = system.getenv("PATH"); //$NON-NLS-1$
		String exe = null;

		if (system.isMacOS()) {
			exe = findProgramViaLoginShell(program, path, system);
		}

		if (exe == null) {
			exe = searchPath(path,
					system.isWindows() ? completeWindowsPath(program) : program);
		}

		return exe == null ? "" : exe; //$NON-NLS-1$
	}

	private static String findProgramViaLoginShell(String program, String path,
			SystemReader system) {
		// On Mac, $PATH is typically much shorter in programs launched from the
		// graphical UI than in the shell. Use the shell $PATH first.
		String bash = searchPath(path, "bash"); //$NON-NLS-1$
		if (bash == null) {
			return null;
		}

		ProcessBuilder process = new ProcessBuilder();
		process.command(bash, "--login", "-c", "which " + program); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		process.directory(FS.DETECTED.userHome());

		try {
			FS.ExecutionResult result = FS.DETECTED.execute(process, null);
			if (result.getRc() != 0 || result.getStdout() == null) {
				return null;
			}
			try (BufferedReader r = new BufferedReader(new InputStreamReader(
					result.getStdout().openInputStream(),
					system.getDefaultCharset()))) {
				return r.readLine();
			}
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			LOG.warn(GpgSigningText.get().ExternalGpgSigner_cannotSearch, e);
			return null;
		}
	}

	private static String completeWindowsPath(String program) {
		// On Windows, Java Process follows the CreateProcess function, which
		// appends ".exe" if the program name has no extension and does not end
		// in a period, unless it includes a path.
		//
		// See
		// https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-createprocessa
		// and
		// https://github.com/openjdk/jdk/blob/43b7e9f54/src/java.base/windows/classes/java/lang/ProcessImpl.java#L338
		String name = new File(program).getName();
		if (name.equals(program) && name.indexOf('.') < 0) {
			return program + ".exe"; //$NON-NLS-1$
		}
		return program;
	}

	private static String searchPath(String path, String name) {
		if (StringUtils.isEmptyOrNull(path)) {
			return null;
		}
		for (String p : path.split(File.pathSeparator)) {
			File exe = new File(p, name);
			try {
				if (exe.isFile() && exe.canExecute()) {
					return exe.getAbsolutePath();
				}
			} catch (SecurityException e) {
				logSkipNotAccessiblePath(exe.getPath(), e);
			}
		}
		return null;
	}

	private static void logSkipNotAccessiblePath(String path,
			SecurityException e) {
		try {
			LOG.warn(MessageFormat.format(
					GpgSigningText.get().ExternalGpgSigner_skipNotAccessiblePath,
					path), e);
		} catch (RuntimeException translationFailure) {
            LOG.warn("Cannot access path: {}", path, e); //$NON-NLS-1$
		}
	}
}

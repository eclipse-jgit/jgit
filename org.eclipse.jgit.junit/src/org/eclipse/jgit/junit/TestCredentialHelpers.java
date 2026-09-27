/*
 * Copyright (C) 2026, JGit contributors and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.junit;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.util.SystemReader;
import org.junit.Assume;

/**
 * Factories for POSIX credential-helper stub scripts used by credential tests.
 * The scripts are {@code #!/bin/sh} with the executable bit set, so the tests
 * that use them run only on POSIX platforms; call {@link #assumeNotWindows()}
 * from such a test's setup.
 */
public final class TestCredentialHelpers {

	private TestCredentialHelpers() {
		// Utility class.
	}

	/**
	 * Skip the calling test on Windows, where the {@code #!/bin/sh} stub scripts
	 * cannot run.
	 */
	public static void assumeNotWindows() {
		Assume.assumeFalse(SystemReader.getInstance().isWindows());
	}

	/**
	 * Write an executable helper whose {@code get} returns the given username
	 * and password.
	 *
	 * @param username
	 *            username the helper returns
	 * @param password
	 *            password the helper returns
	 * @return the executable helper script
	 * @throws IOException
	 *             if the script cannot be written
	 */
	public static Path writeGetHelper(String username, String password)
			throws IOException {
		return writeGetHelperRaw(
				"username=" + username + "\\npassword=" + password + "\\n");
	}

	/**
	 * Write an executable helper whose {@code get} prints the given raw
	 * credential lines (separated by literal {@code \\n}); the record's
	 * terminating blank line is appended automatically.
	 *
	 * @param getOutput
	 *            the credential lines the helper's {@code get} prints
	 * @return the executable helper script
	 * @throws IOException
	 *             if the script cannot be written
	 */
	public static Path writeGetHelperRaw(String getOutput) throws IOException {
		Path helper = Files.createTempFile("git-credential-stub", ".sh");
		Files.write(helper, ("#!/bin/sh\n" + "case \"$1\" in\n" + "get) printf '"
				+ getOutput + "\\n' ;;\n" + "esac\n").getBytes(UTF_8));
		helper.toFile().setExecutable(true);
		return helper;
	}

	/**
	 * Write an executable helper that appends the operation and its stdin to the
	 * given capture file, so a test can assert which operations ran.
	 *
	 * @param capture
	 *            file the helper appends {@code op=<action>} and stdin to
	 * @return the executable helper script
	 * @throws IOException
	 *             if the script cannot be written
	 */
	public static Path writeCapturingHelper(Path capture) throws IOException {
		Path helper = Files.createTempFile("git-credential-capture", ".sh");
		Files.write(helper,
				("#!/bin/sh\n" + "{ printf 'op=%s\\n' \"$1\"; cat; } >> "
						+ capture + "\n").getBytes(UTF_8));
		helper.toFile().setExecutable(true);
		return helper;
	}
}

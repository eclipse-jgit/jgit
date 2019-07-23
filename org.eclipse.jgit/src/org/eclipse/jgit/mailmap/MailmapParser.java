/*
 * Copyright (c) 2019 Brian Riehman <briehman@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.mailmap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.annotations.Nullable;

/**
 * Parses a Git mailmap from a provided {@link InputStream}.
 *
 * @since 8.0
 */
public class MailmapParser {

	private static final String COMMENT_PREFIX = "#"; //$NON-NLS-1$

	/**
	 * Parse the provided input stream as a Git mailmap file
	 *
	 * @param input
	 *            the mailmap input
	 * @return the parsed mailmap
	 * @throws IOException
	 *             if the input stream cannot be read
	 */
	public static Mailmap parse(InputStream input) throws IOException {
		List<MailmapEntry> entries = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(input))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (!line.startsWith(COMMENT_PREFIX)) {
					MailmapResult firstEntry = parseNameAndEmail(line, false);

					if (firstEntry != null) {
						MailmapResult secondEntry = parseNameAndEmail(
								firstEntry.restOfLine, true);

						String newName = firstEntry.name;
						String newEmail = firstEntry.email;
						String oldEmail = secondEntry != null
								? secondEntry.email
								: null;
						String oldName = secondEntry != null ? secondEntry.name
								: null;

						if (oldEmail == null) {
							oldEmail = newEmail;
							newEmail = null;
						}

						entries.add(new MailmapEntry(oldName, oldEmail, newName,
								newEmail));
					}

				}
			}
		}

		return new Mailmap(entries);
	}

	private record MailmapResult(String name, String email, String restOfLine) {
	}

	@Nullable
	private static MailmapResult parseNameAndEmail(String line,
			boolean allowEmptyEmail) {
		int left, right;

		left = line.indexOf('<');
		if (left == -1) {
			return null;
		}
		right = line.indexOf('>', left + 1);
		if (right == -1) {
			return null;
		}

		if (!allowEmptyEmail && (left + 1 == right)) {
			return null;
		}

		String name = left == 0 ? null : line.substring(0, left - 1).trim();
		String email = line.substring(left + 1, right);

		return new MailmapResult(name, email, line.substring(right + 1).trim());
	}
}

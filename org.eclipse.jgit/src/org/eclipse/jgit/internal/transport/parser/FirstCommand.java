/*
 * Copyright (C) 2018, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.parser;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.toSet;

import java.util.Set;

import org.eclipse.jgit.annotations.NonNull;

/**
 * In a push, the client sends a list of commands. The first command
 * is special, as it can include a list of capabilities at its end.
 * <p>
 * For example:
 * "oid oid name\0cap1 cap cap3"
 * <p>
 * Not to be confused with {@link FirstWant}, nor with the first line
 * of the reference advertisement parsed by
 * {@code BasePackConnection.readAdvertisedRefs}.
 * <p>
 * This class parses the inputted command line and holds the results:
 * the actual command line and the capabilities.
 */
public final class FirstCommand {
	private final String line;
	private final Set<String> capabilities;

	/**
	 * Parse the first line of a receive-pack request.
	 *
	 * @param line
	 *            line from the client.
	 * @return an instance of FirstCommand with capabilities parsed out
	 */
	@NonNull
	public static FirstCommand fromLine(String line) {
		int nul = line.indexOf('\0');
		if (nul < 0) {
			return new FirstCommand(line, emptySet());
		}
		Set<String> opts =
				asList(line.substring(nul + 1).split(" ")) //$NON-NLS-1$
					.stream()
					.collect(toSet());
		return new FirstCommand(line.substring(0, nul), unmodifiableSet(opts));
	}

	private FirstCommand(String line, Set<String> capabilities) {
		this.line = line;
		this.capabilities = capabilities;
	}

	/** @return non-capabilities part of the line. */
	@NonNull
	public String getLine() {
		return line;
	}

	/** @return capabilities parsed from the line, as an immutable set. */
	@NonNull
	public Set<String> getCapabilities() {
		return capabilities;
	}
}

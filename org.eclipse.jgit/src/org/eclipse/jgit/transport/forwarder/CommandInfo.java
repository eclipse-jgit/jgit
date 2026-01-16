/*
 * Copyright (C) 2026, Nvidia
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport.forwarder;

import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.transport.PacketLineIn;
import org.eclipse.jgit.transport.RemoteConfig;

/**
 * Parsed command metadata from the first pkt-line.
 *
 * @since 7.7
 */
final class CommandInfo {
	private static final String NUL_NUL = "\0\0"; //$NON-NLS-1$

	/** Parsed project path from the command line, or null if not present. */
	public final String project;

	/** Git command (e.g. git-upload-pack or git-receive-pack), or null if not recognized. */
	public final String command;

	/** Host requested by the client, or null if not present. */
	public final String host;

	/** Extra parameters after the NUL NUL in the pkt-line. */
	public final Collection<String> extraParameters;

	/** Raw bytes of the first pkt-line as received from the client. */
	public final byte[] recordedPrefix;

	/**
	 * Parse the first pkt-line from the client and expose command metadata.
	 *
	 * @param client client socket providing the pkt-line
	 * @throws IOException if reading the pkt-line fails
	 */
	public CommandInfo(Socket client) throws IOException {
		RecordingInputStream stream = new RecordingInputStream(client.getInputStream());
		String line = new PacketLineIn(stream).readStringRaw();
		this.recordedPrefix = stream.getRecordedBytes();

		int nulnul = line.indexOf(NUL_NUL);
		extraParameters = nulnul != -1 ? Arrays.asList(line.substring(nulnul + 2).split("\0")) : List.of(); //$NON-NLS-1$

		String resolvedCommand = null;
		String resolvedArgument = null;
		for (String candidate :
				new String[] {
						RemoteConfig.DEFAULT_UPLOAD_PACK,
						RemoteConfig.DEFAULT_RECEIVE_PACK}) {
			String argumentValue = parseCommandArgument(line, candidate);
			if (argumentValue != null) {
				resolvedCommand = candidate.trim();
				resolvedArgument = argumentValue;
				break;
			}
		}
		this.command = resolvedCommand;
		if (resolvedArgument != null) {
			String stripped = stripLeadingSlashes(resolvedArgument);
			this.project = stripped.isEmpty() ? null : stripped;
		} else {
			this.project = null;
		}
		this.host = parseHostFromLine(line);
	}

	/**
	 * @param line
	 *            packet line payload
	 * @return requested host, or {@code null} if not present
	 */
	@Nullable
	private static String parseHostFromLine(String line) {
		String marker = "host="; //$NON-NLS-1$
		int idx = line.indexOf(marker);
		if (idx < 0) {
			return null;
		}

		int start = idx + marker.length();
		int end = line.indexOf('\0', start);
		if (end < 0) {
			return null;
		}

		return line.substring(start, end);
	}

	/**
	 * Extract the command argument from a git service line.
	 * <p>
	 * Example input: "git-receive-pack /example/repo.git\0"
	 * returns "example/repo.git".
	 *
	 * @param line packet line payload
	 * @param command command prefix to search
	 * @return command argument, or {@code null} if not found
	 */
	@Nullable
	private static String parseCommandArgument(String line, String command) {
		int idx = line.indexOf(command);
		if (idx < 0) {
			return null;
		}

		String rest = line.substring(idx + command.length());

		int end = rest.indexOf('\0');
		if (end < 0) {
			return null;
		}

		return rest.substring(0, end);
	}

	/**
	 * @param in input to normalize
	 * @return input without leading slashes
	 */
	public static String stripLeadingSlashes(String in) {
		int start = 0;
		while (start < in.length() && in.charAt(start) == '/') {
			start++;
		}
		return start == 0 ? in : in.substring(start);
	}
}

/*
 * Copyright (C) 2018, Markus Duft <markus.duft@ssi-schaefer.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.CommandFailedException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RemoteSession;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.io.MessageWriter;
import org.eclipse.jgit.util.io.StreamCopyThread;

/**
 * Extra utilities to support usage of SSH.
 *
 * @since 5.0
 */
public class SshSupport {

	/**
	 * Utility to execute a remote SSH command and read the first line of
	 * output.
	 *
	 * @param sshUri
	 *            the SSH remote URI
	 * @param provider
	 *            the {@link CredentialsProvider} or <code>null</code>.
	 * @param fs
	 *            the {@link FS} implementation passed to
	 *            {@link SshSessionFactory}
	 * @param command
	 *            the remote command to execute.
	 * @param timeout
	 *            a timeout in seconds. The timeout may be exceeded in corner
	 *            cases.
	 * @return The entire output read from stdout.
	 * @throws IOException
	 * @throws CommandFailedException
	 *             if the ssh command execution failed, error message contains
	 *             the content of stderr.
	 */
	public static String runSshCommand(URIish sshUri,
			@Nullable CredentialsProvider provider, FS fs, String command,
			int timeout) throws IOException, CommandFailedException {
		RemoteSession session = null;
		Process process = null;
		StreamCopyThread errorThread = null;
		StreamCopyThread outThread = null;
		CommandFailedException failure = null;
		@SuppressWarnings("resource")
		MessageWriter stderr = new MessageWriter();
		@SuppressWarnings("resource")
		MessageWriter stdout = new MessageWriter();
		String out;
		try {
			long start = System.nanoTime();
			session = SshSessionFactory.getInstance().getSession(sshUri,
					provider, fs, 1000 * timeout);
			int commandTimeout = timeout;
			if (timeout > 0) {
				commandTimeout = checkTimeout(command, timeout, start);
			}
			process = session.exec(command, commandTimeout);
			if (timeout > 0) {
				commandTimeout = checkTimeout(command, timeout, start);
			}
			errorThread = new StreamCopyThread(process.getErrorStream(),
					stderr.getRawStream());
			errorThread.start();
			outThread = new StreamCopyThread(process.getInputStream(),
					stdout.getRawStream());
			outThread.start();
			try {
				boolean finished = false;
				if (timeout <= 0) {
					process.waitFor();
					finished = true;
				} else {
					finished = process.waitFor(commandTimeout,
							TimeUnit.SECONDS);
				}
				if (finished) {
					out = stdout.toString();
				} else {
					out = null; // still running after timeout
				}
			} catch (InterruptedException e) {
				out = null; // error
			}
		} finally {
			if (errorThread != null) {
				try {
					errorThread.halt();
				} catch (InterruptedException e) {
					// Stop waiting and return anyway.
				} finally {
					errorThread = null;
				}
			}
			if (outThread != null) {
				try {
					outThread.halt();
				} catch (InterruptedException e) {
					// Stop waiting and return anyway.
				} finally {
					outThread = null;
				}
			}
			if (process != null) {
				try {
					if (process.exitValue() != 0) {
						failure = new CommandFailedException(
								process.exitValue(),
								MessageFormat.format(
										JGitText.get().sshCommandFailed,
										command, stderr.toString()));
					}
					// It was successful after all
					out = stdout.toString();
				} catch (IllegalThreadStateException e) {
					failure = new CommandFailedException(0,
							MessageFormat.format(
									JGitText.get().sshCommandTimeout, command,
									Integer.valueOf(timeout)));
				}
				process.destroy();
			}
			stderr.close();
			stdout.close();
			if (session != null) {
				SshSessionFactory.getInstance().releaseSession(session);
			}
		}
		if (failure != null) {
			throw failure;
		}
		return out;
	}

	private static int checkTimeout(String command, int timeout, long since)
			throws CommandFailedException {
		long elapsed = System.nanoTime() - since;
		int newTimeout = timeout
				- (int) TimeUnit.NANOSECONDS.toSeconds(elapsed);
		if (newTimeout <= 0) {
			// All time used up for connecting the session
			throw new CommandFailedException(0,
					MessageFormat.format(JGitText.get().sshCommandTimeout,
							command, Integer.valueOf(timeout)));
		}
		return newTimeout;
	}
}

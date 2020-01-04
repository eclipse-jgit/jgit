/*
 * Copyright (C) 2018, Markus Duft <markus.duft@ssi-schaefer.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.util;

import java.io.IOException;
import java.text.MessageFormat;

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
		String out;
		try (MessageWriter stdout = new MessageWriter()) {
			session = SshSessionFactory.getInstance().getSession(sshUri,
					provider, fs, 1000 * timeout);
			process = session.exec(command, 0);
			errorThread = new StreamCopyThread(process.getErrorStream(),
					stderr.getRawStream());
			errorThread.start();
			outThread = new StreamCopyThread(process.getInputStream(),
					stdout.getRawStream());
			outThread.start();
			try {
				// waitFor with timeout has a bug - JSch' exitValue() throws the
				// wrong exception type :(
				if (process.waitFor() == 0) {
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
				if (process.exitValue() != 0) {
					failure = new CommandFailedException(process.exitValue(),
							MessageFormat.format(
							JGitText.get().sshCommandFailed, command,
							stderr.toString()));
				}
				process.destroy();
			}
			stderr.close();
			if (session != null) {
				SshSessionFactory.getInstance().releaseSession(session);
			}
		}
		if (failure != null) {
			throw failure;
		}
		return out;
	}

}

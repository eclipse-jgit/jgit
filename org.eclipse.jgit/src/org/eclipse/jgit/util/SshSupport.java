package org.eclipse.jgit.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RemoteSession;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.io.MessageWriter;
import org.eclipse.jgit.util.io.StreamCopyThread;

/**
 * Extra utilities to support usage of SSH.
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
	 *            a timeout in seconds.
	 * @return The first line of output read from stdout. Stderr is discarded.
	 * @throws IOException
	 */
	public static String runSshCommand(URIish sshUri,
			@Nullable CredentialsProvider provider, FS fs, String command,
			int timeout) throws IOException {
		RemoteSession session = null;
		Process process = null;
		StreamCopyThread errorThread = null;
		try (MessageWriter stderr = new MessageWriter()) {
			session = SshSessionFactory.getInstance().getSession(sshUri,
					provider, fs, 1000 * timeout);
			process = session.exec(command, 0);
			errorThread = new StreamCopyThread(process.getErrorStream(),
					stderr.getRawStream());
			errorThread.start();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(),
							Constants.CHARSET))) {
				return reader.readLine();
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
			if (process != null) {
				process.destroy();
			}
			if (session != null) {
				SshSessionFactory.getInstance().releaseSession(session);
			}
		}
	}

}

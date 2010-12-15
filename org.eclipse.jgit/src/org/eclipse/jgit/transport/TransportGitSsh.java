/*
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.QuotedString;
import org.eclipse.jgit.util.SystemReader;
import org.eclipse.jgit.util.io.MessageWriter;
import org.eclipse.jgit.util.io.StreamCopyThread;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;

/**
 * Transport through an SSH tunnel.
 * <p>
 * The SSH transport requires the remote side to have Git installed, as the
 * transport logs into the remote system and executes a Git helper program on
 * the remote side to read (or write) the remote repository's files.
 * <p>
 * This transport does not support direct SCP style of copying files, as it
 * assumes there are Git specific smarts on the remote side to perform object
 * enumeration, save file modification and hook execution.
 */
public class TransportGitSsh extends SshTransport implements PackTransport {
	static boolean canHandle(final URIish uri) {
		if (!uri.isRemote())
			return false;
		final String scheme = uri.getScheme();
		if ("ssh".equals(scheme))
			return true;
		if ("ssh+git".equals(scheme))
			return true;
		if ("git+ssh".equals(scheme))
			return true;
		if (scheme == null && uri.getHost() != null && uri.getPath() != null)
			return true;
		return false;
	}

	TransportGitSsh(final Repository local, final URIish uri) {
		super(local, uri);
	}

	@Override
	public FetchConnection openFetch() throws TransportException {
		return new SshFetchConnection(newConnection());
	}

	@Override
	public PushConnection openPush() throws TransportException {
		return new SshPushConnection(newConnection());
	}

	private Connection newConnection() {
		if (useExtConnection())
			return new ExtConnection();
		return new JschConnection();
	}

	private static void sqMinimal(final StringBuilder cmd, final String val) {
		if (val.matches("^[a-zA-Z0-9._/-]*$")) {
			// If the string matches only generally safe characters
			// that the shell is not going to evaluate specially we
			// should leave the string unquoted. Not all systems
			// actually run a shell and over-quoting confuses them
			// when it comes to the command name.
			//
			cmd.append(val);
		} else {
			sq(cmd, val);
		}
	}

	private static void sqAlways(final StringBuilder cmd, final String val) {
		sq(cmd, val);
	}

	private static void sq(final StringBuilder cmd, final String val) {
		if (val.length() > 0)
			cmd.append(QuotedString.BOURNE.quote(val));
	}

	String commandFor(final String exe) {
		String path = uri.getPath();
		if (uri.getScheme() != null && uri.getPath().startsWith("/~"))
			path = (uri.getPath().substring(1));

		final StringBuilder cmd = new StringBuilder();
		final int gitspace = exe.indexOf("git ");
		if (gitspace >= 0) {
			sqMinimal(cmd, exe.substring(0, gitspace + 3));
			cmd.append(' ');
			sqMinimal(cmd, exe.substring(gitspace + 4));
		} else
			sqMinimal(cmd, exe);
		cmd.append(' ');
		sqAlways(cmd, path);
		return cmd.toString();
	}

	void checkExecFailure(int status, String exe, String why)
			throws TransportException {
		if (status == 127) {
			IOException cause = null;
			if (why != null && why.length() > 0)
				cause = new IOException(why);
			throw new TransportException(uri, MessageFormat.format(
					JGitText.get().cannotExecute, commandFor(exe)), cause);
		}
	}

	NoRemoteRepositoryException cleanNotFound(NoRemoteRepositoryException nf,
			String why) {
		if (why == null || why.length() == 0)
			return nf;

		String path = uri.getPath();
		if (uri.getScheme() != null && uri.getPath().startsWith("/~"))
			path = uri.getPath().substring(1);

		final StringBuilder pfx = new StringBuilder();
		pfx.append("fatal: ");
		sqAlways(pfx, path);
		pfx.append(": ");
		if (why.startsWith(pfx.toString()))
			why = why.substring(pfx.length());

		return new NoRemoteRepositoryException(uri, why);
	}

	private abstract class Connection {
		abstract void exec(String commandName) throws TransportException;

		abstract void connect() throws TransportException;

		abstract InputStream getInputStream() throws IOException;

		abstract OutputStream getOutputStream() throws IOException;

		abstract InputStream getErrorStream() throws IOException;

		abstract int getExitStatus();

		abstract void close();
	}

	private class JschConnection extends Connection {
		private ChannelExec channel;

		private int exitStatus;

		@Override
		void exec(String commandName) throws TransportException {
			initSession();
			try {
				channel = (ChannelExec) sock.openChannel("exec");
				channel.setCommand(commandFor(commandName));
			} catch (JSchException je) {
				throw new TransportException(uri, je.getMessage(), je);
			}
		}

		@Override
		void connect() throws TransportException {
			try {
				channel.connect(getTimeout() > 0 ? getTimeout() * 1000 : 0);
				if (!channel.isConnected())
					throw new TransportException(uri, "connection failed");
			} catch (JSchException e) {
				throw new TransportException(uri, e.getMessage(), e);
			}
		}

		@Override
		InputStream getInputStream() throws IOException {
			return channel.getInputStream();
		}

		@Override
		OutputStream getOutputStream() throws IOException {
			// JSch won't let us interrupt writes when we use our InterruptTimer
			// to break out of a long-running write operation. To work around
			// that we spawn a background thread to shuttle data through a pipe,
			// as we can issue an interrupted write out of that. Its slower, so
			// we only use this route if there is a timeout.
			//
			final OutputStream out = channel.getOutputStream();
			if (getTimeout() <= 0)
				return out;
			final PipedInputStream pipeIn = new PipedInputStream();
			final StreamCopyThread copier = new StreamCopyThread(pipeIn, out);
			final PipedOutputStream pipeOut = new PipedOutputStream(pipeIn) {
				@Override
				public void flush() throws IOException {
					super.flush();
					copier.flush();
				}

				@Override
				public void close() throws IOException {
					super.close();
					try {
						copier.join(getTimeout() * 1000);
					} catch (InterruptedException e) {
						// Just wake early, the thread will terminate anyway.
					}
				}
			};
			copier.start();
			return pipeOut;
		}

		@Override
		InputStream getErrorStream() throws IOException {
			return channel.getErrStream();
		}

		@Override
		int getExitStatus() {
			return exitStatus;
		}

		@Override
		void close() {
			if (channel != null) {
				try {
					exitStatus = channel.getExitStatus();
					if (channel.isConnected())
						channel.disconnect();
				} finally {
					channel = null;
				}
			}
		}
	}

	private static boolean useExtConnection() {
		return SystemReader.getInstance().getenv("GIT_SSH") != null;
	}

	private class ExtConnection extends Connection {
		private Process proc;

		private int exitStatus;

		@Override
		void exec(String commandName) throws TransportException {
			String ssh = SystemReader.getInstance().getenv("GIT_SSH");
			boolean putty = ssh.toLowerCase().contains("plink");

			List<String> args = new ArrayList<String>();
			args.add(ssh);
			if (putty && !ssh.toLowerCase().contains("tortoiseplink"))
				args.add("-batch");
			if (0 < getURI().getPort()) {
				args.add(putty ? "-P" : "-p");
				args.add(String.valueOf(getURI().getPort()));
			}
			if (getURI().getUser() != null)
				args.add(getURI().getUser() + "@" + getURI().getHost());
			else
				args.add(getURI().getHost());
			args.add(commandFor(commandName));

			ProcessBuilder pb = new ProcessBuilder();
			pb.command(args);

			if (local.getDirectory() != null)
				pb.environment().put(Constants.GIT_DIR_KEY,
						local.getDirectory().getPath());

			try {
				proc = pb.start();
			} catch (IOException err) {
				throw new TransportException(uri, err.getMessage(), err);
			}
		}

		@Override
		void connect() throws TransportException {
			// Nothing to do, the process was already opened.
		}

		@Override
		InputStream getInputStream() throws IOException {
			return proc.getInputStream();
		}

		@Override
		OutputStream getOutputStream() throws IOException {
			return proc.getOutputStream();
		}

		@Override
		InputStream getErrorStream() throws IOException {
			return proc.getErrorStream();
		}

		@Override
		int getExitStatus() {
			return exitStatus;
		}

		@Override
		void close() {
			if (proc != null) {
				try {
					try {
						exitStatus = proc.waitFor();
					} catch (InterruptedException e) {
						// Ignore the interrupt, but return immediately.
					}
				} finally {
					proc = null;
				}
			}
		}
	}

	class SshFetchConnection extends BasePackFetchConnection {
		private Connection conn;

		private StreamCopyThread errorThread;

		SshFetchConnection(Connection conn) throws TransportException {
			super(TransportGitSsh.this);
			this.conn = conn;
			try {
				final MessageWriter msg = new MessageWriter();
				setMessageWriter(msg);

				conn.exec(getOptionUploadPack());

				final InputStream upErr = conn.getErrorStream();
				errorThread = new StreamCopyThread(upErr, msg.getRawStream());
				errorThread.start();

				init(conn.getInputStream(), conn.getOutputStream());
				conn.connect();

			} catch (TransportException err) {
				close();
				throw err;
			} catch (IOException err) {
				close();
				throw new TransportException(uri,
						JGitText.get().remoteHungUpUnexpectedly, err);
			}

			try {
				readAdvertisedRefs();
			} catch (NoRemoteRepositoryException notFound) {
				final String msgs = getMessages();
				checkExecFailure(conn.getExitStatus(), getOptionUploadPack(),
						msgs);
				throw cleanNotFound(notFound, msgs);
			}
		}

		@Override
		public void close() {
			endOut();

			if (errorThread != null) {
				try {
					errorThread.halt();
				} catch (InterruptedException e) {
					// Stop waiting and return anyway.
				} finally {
					errorThread = null;
				}
			}

			super.close();
			conn.close();
		}
	}

	class SshPushConnection extends BasePackPushConnection {
		private Connection conn;

		private StreamCopyThread errorThread;

		SshPushConnection(Connection conn) throws TransportException {
			super(TransportGitSsh.this);
			this.conn = conn;
			try {
				final MessageWriter msg = new MessageWriter();
				setMessageWriter(msg);

				conn.exec(getOptionReceivePack());

				final InputStream rpErr = conn.getErrorStream();
				errorThread = new StreamCopyThread(rpErr, msg.getRawStream());
				errorThread.start();

				init(conn.getInputStream(), conn.getOutputStream());
				conn.connect();

			} catch (TransportException err) {
				close();
				throw err;
			} catch (IOException err) {
				close();
				throw new TransportException(uri,
						JGitText.get().remoteHungUpUnexpectedly, err);
			}

			try {
				readAdvertisedRefs();
			} catch (NoRemoteRepositoryException notFound) {
				final String msgs = getMessages();
				checkExecFailure(conn.getExitStatus(), getOptionReceivePack(),
						msgs);
				throw cleanNotFound(notFound, msgs);
			}
		}

		@Override
		public void close() {
			endOut();

			if (errorThread != null) {
				try {
					errorThread.halt();
				} catch (InterruptedException e) {
					// Stop waiting and return anyway.
				} finally {
					errorThread = null;
				}
			}

			super.close();
			conn.close();
		}
	}
}

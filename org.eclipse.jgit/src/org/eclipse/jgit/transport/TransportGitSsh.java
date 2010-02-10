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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Writer;

import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.QuotedString;
import org.eclipse.jgit.util.RawParseUtils;

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
		return new SshFetchConnection();
	}

	@Override
	public PushConnection openPush() throws TransportException {
		return new SshPushConnection();
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

	private String commandFor(final String exe) {
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

	ChannelExec exec(final String exe, final OutputStream err)
			throws TransportException {
		initSession();

		final int tms = getTimeout() > 0 ? getTimeout() * 1000 : 0;
		try {
			final ChannelExec channel = (ChannelExec) sock.openChannel("exec");
			channel.setCommand(commandFor(exe));
			channel.setErrStream(err);
			channel.connect(tms);
			return channel;
		} catch (JSchException je) {
			throw new TransportException(uri, je.getMessage(), je);
		}
	}

	void checkExecFailure(int status, String exe, String why)
			throws TransportException {
		if (status == 127) {
			IOException cause = null;
			if (why != null && why.length() > 0)
				cause = new IOException(why);
			throw new TransportException(uri, "cannot execute: "
					+ commandFor(exe), cause);
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

	// JSch won't let us interrupt writes when we use our InterruptTimer to
	// break out of a long-running write operation. To work around that we
	// spawn a background thread to shuttle data through a pipe, as we can
	// issue an interrupted write out of that. Its slower, so we only use
	// this route if there is a timeout.
	//
	private OutputStream outputStream(ChannelExec channel) throws IOException {
		final OutputStream out = channel.getOutputStream();
		if (getTimeout() <= 0)
			return out;
		final PipedInputStream pipeIn = new PipedInputStream();
		final CopyThread copyThread = new CopyThread(pipeIn, out);
		final PipedOutputStream pipeOut = new PipedOutputStream(pipeIn) {
			@Override
			public void flush() throws IOException {
				super.flush();
				copyThread.flush();
			}

			@Override
			public void close() throws IOException {
				super.close();
				try {
					copyThread.join(getTimeout() * 1000);
				} catch (InterruptedException e) {
					// Just wake early, the thread will terminate anyway.
				}
			}
		};
		copyThread.start();
		return pipeOut;
	}

	private static class CopyThread extends Thread {
		private final InputStream src;

		private final OutputStream dst;

		private volatile boolean doFlush;

		CopyThread(final InputStream i, final OutputStream o) {
			setName(Thread.currentThread().getName() + "-Output");
			src = i;
			dst = o;
		}

		void flush() {
			if (!doFlush) {
				doFlush = true;
				interrupt();
			}
		}

		@Override
		public void run() {
			try {
				final byte[] buf = new byte[1024];
				for (;;) {
					try {
						if (doFlush) {
							doFlush = false;
							dst.flush();
						}

						final int n;
						try {
							n = src.read(buf);
						} catch (InterruptedIOException wakey) {
							continue;
						}
						if (n < 0)
							break;
						dst.write(buf, 0, n);
					} catch (IOException e) {
						break;
					}
				}
			} finally {
				try {
					src.close();
				} catch (IOException e) {
					// Ignore IO errors on close
				}
				try {
					dst.close();
				} catch (IOException e) {
					// Ignore IO errors on close
				}
			}
		}
	}

	class SshFetchConnection extends BasePackFetchConnection {
		private ChannelExec channel;

		private int exitStatus;

		SshFetchConnection() throws TransportException {
			super(TransportGitSsh.this);
			try {
				final MessageWriter msg = new MessageWriter();
				setMessageWriter(msg);
				channel = exec(getOptionUploadPack(), msg.getRawStream());

				if (channel.isConnected())
					init(channel.getInputStream(), outputStream(channel));
				else
					throw new TransportException(uri, getMessages());

			} catch (TransportException err) {
				close();
				throw err;
			} catch (IOException err) {
				close();
				throw new TransportException(uri,
						"remote hung up unexpectedly", err);
			}

			try {
				readAdvertisedRefs();
			} catch (NoRemoteRepositoryException notFound) {
				close();
				checkExecFailure(exitStatus, getOptionUploadPack(),
						getMessages());
				throw cleanNotFound(notFound, getMessages());
			}
		}

		@Override
		public void close() {
			super.close();

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

	class SshPushConnection extends BasePackPushConnection {
		private ChannelExec channel;

		private int exitStatus;

		SshPushConnection() throws TransportException {
			super(TransportGitSsh.this);
			try {
				final MessageWriter msg = new MessageWriter();
				setMessageWriter(msg);
				channel = exec(getOptionReceivePack(), msg.getRawStream());

				if (channel.isConnected())
					init(channel.getInputStream(), outputStream(channel));
				else
					throw new TransportException(uri, getMessages());

			} catch (TransportException err) {
				close();
				throw err;
			} catch (IOException err) {
				close();
				throw new TransportException(uri,
						"remote hung up unexpectedly", err);
			}

			try {
				readAdvertisedRefs();
			} catch (NoRemoteRepositoryException notFound) {
				close();
				checkExecFailure(exitStatus, getOptionReceivePack(),
						getMessages());
				throw cleanNotFound(notFound, getMessages());
			}
		}

		@Override
		public void close() {
			super.close();

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

	private static class MessageWriter extends Writer {
		private final ByteArrayOutputStream buf;

		private final OutputStreamWriter enc;

		MessageWriter() {
			buf = new ByteArrayOutputStream();
			enc = new OutputStreamWriter(buf, Constants.CHARSET);
		}

		@Override
		public void write(char[] cbuf, int off, int len) throws IOException {
			synchronized (buf) {
				enc.write(cbuf, off, len);
				enc.flush();
			}
		}

		OutputStream getRawStream() {
			return buf;
		}

		@Override
		public void close() throws IOException {
			// Do nothing, we are buffered with no resources.
		}

		@Override
		public void flush() throws IOException {
			// Do nothing, we are buffered with no resources.
		}

		@Override
		public String toString() {
			return RawParseUtils.decode(buf.toByteArray());
		}
	}
}

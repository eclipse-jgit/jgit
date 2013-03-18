/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.util.io.MessageWriter;
import org.eclipse.jgit.util.io.SafeBufferedOutputStream;
import org.eclipse.jgit.util.io.StreamCopyThread;

/**
 * Transport to access a local directory as though it were a remote peer.
 * <p>
 * This transport is suitable for use on the local system, where the caller has
 * direct read or write access to the "remote" repository.
 * <p>
 * By default this transport works by spawning a helper thread within the same
 * JVM, and processes the data transfer using a shared memory buffer between the
 * calling thread and the helper thread. This is a pure-Java implementation
 * which does not require forking an external process.
 * <p>
 * However, during {@link #openFetch()}, if the Transport has configured
 * {@link Transport#getOptionUploadPack()} to be anything other than
 * <code>"git-upload-pack"</code> or <code>"git upload-pack"</code>, this
 * implementation will fork and execute the external process, using an operating
 * system pipe to transfer data.
 * <p>
 * Similarly, during {@link #openPush()}, if the Transport has configured
 * {@link Transport#getOptionReceivePack()} to be anything other than
 * <code>"git-receive-pack"</code> or <code>"git receive-pack"</code>, this
 * implementation will fork and execute the external process, using an operating
 * system pipe to transfer data.
 */
class TransportLocal extends Transport implements PackTransport {
	static final TransportProtocol PROTO_LOCAL = new TransportProtocol() {
		@Override
		public String getName() {
			return JGitText.get().transportProtoLocal;
		}

		public Set<String> getSchemes() {
			return Collections.singleton("file"); //$NON-NLS-1$
		}

		@Override
		public boolean canHandle(URIish uri, Repository local, String remoteName) {
			if (uri.getPath() == null
					|| uri.getPort() > 0
					|| uri.getUser() != null
					|| uri.getPass() != null
					|| uri.getHost() != null
					|| (uri.getScheme() != null && !getSchemes().contains(uri.getScheme())))
				return false;
			return true;
		}

		@Override
		public Transport open(URIish uri, Repository local, String remoteName)
				throws NoRemoteRepositoryException {
			File localPath = local.isBare() ? local.getDirectory() : local.getWorkTree();
			File path = local.getFS().resolve(localPath, uri.getPath());
			// If the reference is to a local file, C Git behavior says
			// assume this is a bundle, since repositories are directories.
			if (path.isFile())
				return new TransportBundleFile(local, uri, path);

			File gitDir = RepositoryCache.FileKey.resolve(path, local.getFS());
			if (gitDir == null)
				throw new NoRemoteRepositoryException(uri, JGitText.get().notFound);
			return new TransportLocal(local, uri, gitDir);
		}
	};

	private final File remoteGitDir;

	TransportLocal(Repository local, URIish uri, File gitDir) {
		super(local, uri);
		remoteGitDir = gitDir;
	}

	UploadPack createUploadPack(final Repository dst) {
		return new UploadPack(dst);
	}

	ReceivePack createReceivePack(final Repository dst) {
		return new ReceivePack(dst);
	}

	@Override
	public FetchConnection openFetch() throws TransportException {
		final String up = getOptionUploadPack();
		if ("git-upload-pack".equals(up) || "git upload-pack".equals(up)) //$NON-NLS-1$ //$NON-NLS-2$
			return new InternalLocalFetchConnection();
		return new ForkLocalFetchConnection();
	}

	@Override
	public PushConnection openPush() throws NotSupportedException,
			TransportException {
		final String rp = getOptionReceivePack();
		if ("git-receive-pack".equals(rp) || "git receive-pack".equals(rp)) //$NON-NLS-1$ //$NON-NLS-2$
			return new InternalLocalPushConnection();
		return new ForkLocalPushConnection();
	}

	@Override
	public void close() {
		// Resources must be established per-connection.
	}

	protected Process spawn(final String cmd)
			throws TransportException {
		try {
			String[] args = { "." }; //$NON-NLS-1$
			ProcessBuilder proc = local.getFS().runInShell(cmd, args);
			proc.directory(remoteGitDir);

			// Remove the same variables CGit does.
			Map<String, String> env = proc.environment();
			env.remove("GIT_ALTERNATE_OBJECT_DIRECTORIES"); //$NON-NLS-1$
			env.remove("GIT_CONFIG"); //$NON-NLS-1$
			env.remove("GIT_CONFIG_PARAMETERS"); //$NON-NLS-1$
			env.remove("GIT_DIR"); //$NON-NLS-1$
			env.remove("GIT_WORK_TREE"); //$NON-NLS-1$
			env.remove("GIT_GRAFT_FILE"); //$NON-NLS-1$
			env.remove("GIT_INDEX_FILE"); //$NON-NLS-1$
			env.remove("GIT_NO_REPLACE_OBJECTS"); //$NON-NLS-1$

			return proc.start();
		} catch (IOException err) {
			throw new TransportException(uri, err.getMessage(), err);
		}
	}

	class InternalLocalFetchConnection extends BasePackFetchConnection {
		private Thread worker;

		InternalLocalFetchConnection() throws TransportException {
			super(TransportLocal.this);

			final Repository dst;
			try {
				dst = new FileRepository(remoteGitDir);
			} catch (IOException err) {
				throw new TransportException(uri, JGitText.get().notAGitDirectory);
			}

			final PipedInputStream in_r;
			final PipedOutputStream in_w;

			final PipedInputStream out_r;
			final PipedOutputStream out_w;
			try {
				in_r = new PipedInputStream();
				in_w = new PipedOutputStream(in_r);

				out_r = new PipedInputStream() {
					// The client (BasePackFetchConnection) can write
					// a huge burst before it reads again. We need to
					// force the buffer to be big enough, otherwise it
					// will deadlock both threads.
					{
						buffer = new byte[MIN_CLIENT_BUFFER];
					}
				};
				out_w = new PipedOutputStream(out_r);
			} catch (IOException err) {
				dst.close();
				throw new TransportException(uri, JGitText.get().cannotConnectPipes, err);
			}

			worker = new Thread("JGit-Upload-Pack") { //$NON-NLS-1$
				public void run() {
					try {
						final UploadPack rp = createUploadPack(dst);
						rp.upload(out_r, in_w, null);
					} catch (IOException err) {
						// Client side of the pipes should report the problem.
						err.printStackTrace();
					} catch (RuntimeException err) {
						// Clients side will notice we went away, and report.
						err.printStackTrace();
					} finally {
						try {
							out_r.close();
						} catch (IOException e2) {
							// Ignore close failure, we probably crashed above.
						}

						try {
							in_w.close();
						} catch (IOException e2) {
							// Ignore close failure, we probably crashed above.
						}

						dst.close();
					}
				}
			};
			worker.start();

			init(in_r, out_w);
			readAdvertisedRefs();
		}

		@Override
		public void close() {
			super.close();

			if (worker != null) {
				try {
					worker.join();
				} catch (InterruptedException ie) {
					// Stop waiting and return anyway.
				} finally {
					worker = null;
				}
			}
		}
	}

	class ForkLocalFetchConnection extends BasePackFetchConnection {
		private Process uploadPack;

		private Thread errorReaderThread;

		ForkLocalFetchConnection() throws TransportException {
			super(TransportLocal.this);

			final MessageWriter msg = new MessageWriter();
			setMessageWriter(msg);

			uploadPack = spawn(getOptionUploadPack());

			final InputStream upErr = uploadPack.getErrorStream();
			errorReaderThread = new StreamCopyThread(upErr, msg.getRawStream());
			errorReaderThread.start();

			InputStream upIn = uploadPack.getInputStream();
			OutputStream upOut = uploadPack.getOutputStream();

			upIn = new BufferedInputStream(upIn);
			upOut = new SafeBufferedOutputStream(upOut);

			init(upIn, upOut);
			readAdvertisedRefs();
		}

		@Override
		public void close() {
			super.close();

			if (uploadPack != null) {
				try {
					uploadPack.waitFor();
				} catch (InterruptedException ie) {
					// Stop waiting and return anyway.
				} finally {
					uploadPack = null;
				}
			}

			if (errorReaderThread != null) {
				try {
					errorReaderThread.join();
				} catch (InterruptedException e) {
					// Stop waiting and return anyway.
				} finally {
					errorReaderThread = null;
				}
			}
		}
	}

	class InternalLocalPushConnection extends BasePackPushConnection {
		private Thread worker;

		InternalLocalPushConnection() throws TransportException {
			super(TransportLocal.this);

			final Repository dst;
			try {
				dst = new FileRepository(remoteGitDir);
			} catch (IOException err) {
				throw new TransportException(uri, JGitText.get().notAGitDirectory);
			}

			final PipedInputStream in_r;
			final PipedOutputStream in_w;

			final PipedInputStream out_r;
			final PipedOutputStream out_w;
			try {
				in_r = new PipedInputStream();
				in_w = new PipedOutputStream(in_r);

				out_r = new PipedInputStream();
				out_w = new PipedOutputStream(out_r);
			} catch (IOException err) {
				dst.close();
				throw new TransportException(uri, JGitText.get().cannotConnectPipes, err);
			}

			worker = new Thread("JGit-Receive-Pack") { //$NON-NLS-1$
				public void run() {
					try {
						final ReceivePack rp = createReceivePack(dst);
						rp.receive(out_r, in_w, System.err);
					} catch (IOException err) {
						// Client side of the pipes should report the problem.
					} catch (RuntimeException err) {
						// Clients side will notice we went away, and report.
					} finally {
						try {
							out_r.close();
						} catch (IOException e2) {
							// Ignore close failure, we probably crashed above.
						}

						try {
							in_w.close();
						} catch (IOException e2) {
							// Ignore close failure, we probably crashed above.
						}

						dst.close();
					}
				}
			};
			worker.start();

			init(in_r, out_w);
			readAdvertisedRefs();
		}

		@Override
		public void close() {
			super.close();

			if (worker != null) {
				try {
					worker.join();
				} catch (InterruptedException ie) {
					// Stop waiting and return anyway.
				} finally {
					worker = null;
				}
			}
		}
	}

	class ForkLocalPushConnection extends BasePackPushConnection {
		private Process receivePack;

		private Thread errorReaderThread;

		ForkLocalPushConnection() throws TransportException {
			super(TransportLocal.this);

			final MessageWriter msg = new MessageWriter();
			setMessageWriter(msg);

			receivePack = spawn(getOptionReceivePack());

			final InputStream rpErr = receivePack.getErrorStream();
			errorReaderThread = new StreamCopyThread(rpErr, msg.getRawStream());
			errorReaderThread.start();

			InputStream rpIn = receivePack.getInputStream();
			OutputStream rpOut = receivePack.getOutputStream();

			rpIn = new BufferedInputStream(rpIn);
			rpOut = new SafeBufferedOutputStream(rpOut);

			init(rpIn, rpOut);
			readAdvertisedRefs();
		}

		@Override
		public void close() {
			super.close();

			if (receivePack != null) {
				try {
					receivePack.waitFor();
				} catch (InterruptedException ie) {
					// Stop waiting and return anyway.
				} finally {
					receivePack = null;
				}
			}

			if (errorReaderThread != null) {
				try {
					errorReaderThread.join();
				} catch (InterruptedException e) {
					// Stop waiting and return anyway.
				} finally {
					errorReaderThread = null;
				}
			}
		}
	}
}

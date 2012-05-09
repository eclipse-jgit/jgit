/*
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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SymbolicRef;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

/**
 * Transport over the non-Git aware SFTP (SSH based FTP) protocol.
 * <p>
 * The SFTP transport does not require any specialized Git support on the remote
 * (server side) repository. Object files are retrieved directly through secure
 * shell's FTP protocol, making it possible to copy objects from a remote
 * repository that is available over SSH, but whose remote host does not have
 * Git installed.
 * <p>
 * Unlike the HTTP variant (see {@link TransportHttp}) we rely upon being able
 * to list files in directories, as the SFTP protocol supports this function. By
 * listing files through SFTP we can avoid needing to have current
 * <code>objects/info/packs</code> or <code>info/refs</code> files on the
 * remote repository and access the data directly, much as Git itself would.
 * <p>
 * Concurrent pushing over this transport is not supported. Multiple concurrent
 * push operations may cause confusion in the repository state.
 *
 * @see WalkFetchConnection
 */
public class TransportSftp extends SshTransport implements WalkTransport {
	static final TransportProtocol PROTO_SFTP = new TransportProtocol() {
		public String getName() {
			return JGitText.get().transportProtoSFTP;
		}

		public Set<String> getSchemes() {
			return Collections.singleton("sftp"); //$NON-NLS-1$
		}

		public Set<URIishField> getRequiredFields() {
			return Collections.unmodifiableSet(EnumSet.of(URIishField.HOST,
					URIishField.PATH));
		}

		public Set<URIishField> getOptionalFields() {
			return Collections.unmodifiableSet(EnumSet.of(URIishField.USER,
					URIishField.PASS, URIishField.PORT));
		}

		public int getDefaultPort() {
			return 22;
		}

		public Transport open(URIish uri, Repository local, String remoteName)
				throws NotSupportedException {
			return new TransportSftp(local, uri);
		}
	};

	TransportSftp(final Repository local, final URIish uri) {
		super(local, uri);
	}

	@Override
	public FetchConnection openFetch() throws TransportException {
		final SftpObjectDB c = new SftpObjectDB(uri.getPath());
		final WalkFetchConnection r = new WalkFetchConnection(this, c);
		r.available(c.readAdvertisedRefs());
		return r;
	}

	@Override
	public PushConnection openPush() throws TransportException {
		final SftpObjectDB c = new SftpObjectDB(uri.getPath());
		final WalkPushConnection r = new WalkPushConnection(this, c);
		r.available(c.readAdvertisedRefs());
		return r;
	}

	ChannelSftp newSftp() throws TransportException {
		final int tms = getTimeout() > 0 ? getTimeout() * 1000 : 0;
		try {
			// @TODO: Fix so that this operation is generic and casting to
			// JschSession is no longer necessary.
			final Channel channel = ((JschSession) getSession())
					.getSftpChannel();
			channel.connect(tms);
			return (ChannelSftp) channel;
		} catch (JSchException je) {
			throw new TransportException(uri, je.getMessage(), je);
		}
	}

	class SftpObjectDB extends WalkRemoteObjectDatabase {
		private final String objectsPath;

		private ChannelSftp ftp;

		SftpObjectDB(String path) throws TransportException {
			if (path.startsWith("/~"))
				path = path.substring(1);
			if (path.startsWith("~/"))
				path = path.substring(2);
			try {
				ftp = newSftp();
				ftp.cd(path);
				ftp.cd("objects");
				objectsPath = ftp.pwd();
			} catch (TransportException err) {
				close();
				throw err;
			} catch (SftpException je) {
				throw new TransportException("Can't enter " + path + "/objects"
						+ ": " + je.getMessage(), je);
			}
		}

		SftpObjectDB(final SftpObjectDB parent, final String p)
				throws TransportException {
			try {
				ftp = newSftp();
				ftp.cd(parent.objectsPath);
				ftp.cd(p);
				objectsPath = ftp.pwd();
			} catch (TransportException err) {
				close();
				throw err;
			} catch (SftpException je) {
				throw new TransportException("Can't enter " + p + " from "
						+ parent.objectsPath + ": " + je.getMessage(), je);
			}
		}

		@Override
		URIish getURI() {
			return uri.setPath(objectsPath);
		}

		@Override
		Collection<WalkRemoteObjectDatabase> getAlternates() throws IOException {
			try {
				return readAlternates(INFO_ALTERNATES);
			} catch (FileNotFoundException err) {
				return null;
			}
		}

		@Override
		WalkRemoteObjectDatabase openAlternate(final String location)
				throws IOException {
			return new SftpObjectDB(this, location);
		}

		@Override
		Collection<String> getPackNames() throws IOException {
			final List<String> packs = new ArrayList<String>();
			try {
				final Collection<ChannelSftp.LsEntry> list = ftp.ls("pack");
				final HashMap<String, ChannelSftp.LsEntry> files;
				final HashMap<String, Integer> mtimes;

				files = new HashMap<String, ChannelSftp.LsEntry>();
				mtimes = new HashMap<String, Integer>();

				for (final ChannelSftp.LsEntry ent : list)
					files.put(ent.getFilename(), ent);
				for (final ChannelSftp.LsEntry ent : list) {
					final String n = ent.getFilename();
					if (!n.startsWith("pack-") || !n.endsWith(".pack"))
						continue;

					final String in = n.substring(0, n.length() - 5) + ".idx";
					if (!files.containsKey(in))
						continue;

					mtimes.put(n, Integer.valueOf(ent.getAttrs().getMTime()));
					packs.add(n);
				}

				Collections.sort(packs, new Comparator<String>() {
					public int compare(final String o1, final String o2) {
						return mtimes.get(o2).intValue()
								- mtimes.get(o1).intValue();
					}
				});
			} catch (SftpException je) {
				throw new TransportException("Can't ls " + objectsPath
						+ "/pack: " + je.getMessage(), je);
			}
			return packs;
		}

		@Override
		FileStream open(final String path) throws IOException {
			try {
				final SftpATTRS a = ftp.lstat(path);
				return new FileStream(ftp.get(path), a.getSize());
			} catch (SftpException je) {
				if (je.id == ChannelSftp.SSH_FX_NO_SUCH_FILE)
					throw new FileNotFoundException(path);
				throw new TransportException("Can't get " + objectsPath + "/"
						+ path + ": " + je.getMessage(), je);
			}
		}

		@Override
		void deleteFile(final String path) throws IOException {
			try {
				ftp.rm(path);
			} catch (SftpException je) {
				if (je.id == ChannelSftp.SSH_FX_NO_SUCH_FILE)
					return;
				throw new TransportException("Can't delete " + objectsPath
						+ "/" + path + ": " + je.getMessage(), je);
			}

			// Prune any now empty directories.
			//
			String dir = path;
			int s = dir.lastIndexOf('/');
			while (s > 0) {
				try {
					dir = dir.substring(0, s);
					ftp.rmdir(dir);
					s = dir.lastIndexOf('/');
				} catch (SftpException je) {
					// If we cannot delete it, leave it alone. It may have
					// entries still in it, or maybe we lack write access on
					// the parent. Either way it isn't a fatal error.
					//
					break;
				}
			}
		}

		@Override
		OutputStream writeFile(final String path,
				final ProgressMonitor monitor, final String monitorTask)
				throws IOException {
			try {
				return ftp.put(path);
			} catch (SftpException je) {
				if (je.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
					mkdir_p(path);
					try {
						return ftp.put(path);
					} catch (SftpException je2) {
						je = je2;
					}
				}

				throw new TransportException("Can't write " + objectsPath + "/"
						+ path + ": " + je.getMessage(), je);
			}
		}

		@Override
		void writeFile(final String path, final byte[] data) throws IOException {
			final String lock = path + ".lock";
			try {
				super.writeFile(lock, data);
				try {
					ftp.rename(lock, path);
				} catch (SftpException je) {
					throw new TransportException("Can't write " + objectsPath
							+ "/" + path + ": " + je.getMessage(), je);
				}
			} catch (IOException err) {
				try {
					ftp.rm(lock);
				} catch (SftpException e) {
					// Ignore deletion failure, we are already
					// failing anyway.
				}
				throw err;
			}
		}

		private void mkdir_p(String path) throws IOException {
			final int s = path.lastIndexOf('/');
			if (s <= 0)
				return;

			path = path.substring(0, s);
			try {
				ftp.mkdir(path);
			} catch (SftpException je) {
				if (je.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
					mkdir_p(path);
					try {
						ftp.mkdir(path);
						return;
					} catch (SftpException je2) {
						je = je2;
					}
				}

				throw new TransportException("Can't mkdir " + objectsPath + "/"
						+ path + ": " + je.getMessage(), je);
			}
		}

		Map<String, Ref> readAdvertisedRefs() throws TransportException {
			final TreeMap<String, Ref> avail = new TreeMap<String, Ref>();
			readPackedRefs(avail);
			readRef(avail, ROOT_DIR + Constants.HEAD, Constants.HEAD);
			readLooseRefs(avail, ROOT_DIR + "refs", "refs/");
			return avail;
		}

		private void readLooseRefs(final TreeMap<String, Ref> avail,
				final String dir, final String prefix)
				throws TransportException {
			final Collection<ChannelSftp.LsEntry> list;
			try {
				list = ftp.ls(dir);
			} catch (SftpException je) {
				throw new TransportException("Can't ls " + objectsPath + "/"
						+ dir + ": " + je.getMessage(), je);
			}

			for (final ChannelSftp.LsEntry ent : list) {
				final String n = ent.getFilename();
				if (".".equals(n) || "..".equals(n))
					continue;

				final String nPath = dir + "/" + n;
				if (ent.getAttrs().isDir())
					readLooseRefs(avail, nPath, prefix + n + "/");
				else
					readRef(avail, nPath, prefix + n);
			}
		}

		private Ref readRef(final TreeMap<String, Ref> avail,
				final String path, final String name) throws TransportException {
			final String line;
			try {
				final BufferedReader br = openReader(path);
				try {
					line = br.readLine();
				} finally {
					br.close();
				}
			} catch (FileNotFoundException noRef) {
				return null;
			} catch (IOException err) {
				throw new TransportException("Cannot read " + objectsPath + "/"
						+ path + ": " + err.getMessage(), err);
			}

			if (line == null)
				throw new TransportException("Empty ref: " + name);

			if (line.startsWith("ref: ")) {
				final String target = line.substring("ref: ".length());
				Ref r = avail.get(target);
				if (r == null)
					r = readRef(avail, ROOT_DIR + target, target);
				if (r == null)
					r = new ObjectIdRef.Unpeeled(Ref.Storage.NEW, target, null);
				r = new SymbolicRef(name, r);
				avail.put(r.getName(), r);
				return r;
			}

			if (ObjectId.isId(line)) {
				final Ref r = new ObjectIdRef.Unpeeled(loose(avail.get(name)),
						name, ObjectId.fromString(line));
				avail.put(r.getName(), r);
				return r;
			}

			throw new TransportException("Bad ref: " + name + ": " + line);
		}

		private Storage loose(final Ref r) {
			if (r != null && r.getStorage() == Storage.PACKED)
				return Storage.LOOSE_PACKED;
			return Storage.LOOSE;
		}

		@Override
		void close() {
			if (ftp != null) {
				try {
					if (ftp.isConnected())
						ftp.disconnect();
				} finally {
					ftp = null;
				}
			}
		}
	}
}

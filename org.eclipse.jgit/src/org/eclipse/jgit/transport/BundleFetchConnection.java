/*
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008-2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2009, Sasa Zivkov <sasa.zivkov@sap.com>
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

import static org.eclipse.jgit.lib.RefDatabase.ALL;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.errors.MissingBundlePrerequisiteException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.PackLock;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Fetch connection for bundle based classes. It used by
 * instances of {@link TransportBundle}
 */
class BundleFetchConnection extends BaseFetchConnection {

	private final Transport transport;

	InputStream bin;

	final Map<ObjectId, String> prereqs = new HashMap<ObjectId, String>();

	private String lockMessage;

	private PackLock packLock;

	BundleFetchConnection(Transport transportBundle, final InputStream src) throws TransportException {
		transport = transportBundle;
		bin = new BufferedInputStream(src);
		try {
			switch (readSignature()) {
			case 2:
				readBundleV2();
				break;
			default:
				throw new TransportException(transport.uri, JGitText.get().notABundle);
			}
		} catch (TransportException err) {
			close();
			throw err;
		} catch (IOException err) {
			close();
			throw new TransportException(transport.uri, err.getMessage(), err);
		} catch (RuntimeException err) {
			close();
			throw new TransportException(transport.uri, err.getMessage(), err);
		}
	}

	private int readSignature() throws IOException {
		final String rev = readLine(new byte[1024]);
		if (TransportBundle.V2_BUNDLE_SIGNATURE.equals(rev))
			return 2;
		throw new TransportException(transport.uri, JGitText.get().notABundle);
	}

	private void readBundleV2() throws IOException {
		final byte[] hdrbuf = new byte[1024];
		final LinkedHashMap<String, Ref> avail = new LinkedHashMap<String, Ref>();
		for (;;) {
			String line = readLine(hdrbuf);
			if (line.length() == 0)
				break;

			if (line.charAt(0) == '-') {
				ObjectId id = ObjectId.fromString(line.substring(1, 41));
				String shortDesc = null;
				if (line.length() > 42)
					shortDesc = line.substring(42);
				prereqs.put(id, shortDesc);
				continue;
			}

			final String name = line.substring(41, line.length());
			final ObjectId id = ObjectId.fromString(line.substring(0, 40));
			final Ref prior = avail.put(name, new ObjectIdRef.Unpeeled(
					Ref.Storage.NETWORK, name, id));
			if (prior != null)
				throw duplicateAdvertisement(name);
		}
		available(avail);
	}

	private PackProtocolException duplicateAdvertisement(final String name) {
		return new PackProtocolException(transport.uri,
				MessageFormat.format(JGitText.get().duplicateAdvertisementsOf, name));
	}

	private String readLine(final byte[] hdrbuf) throws IOException {
		bin.mark(hdrbuf.length);
		final int cnt = bin.read(hdrbuf);
		int lf = 0;
		while (lf < cnt && hdrbuf[lf] != '\n')
			lf++;
		bin.reset();
		IO.skipFully(bin, lf);
		if (lf < cnt && hdrbuf[lf] == '\n')
			IO.skipFully(bin, 1);
		return RawParseUtils.decode(Constants.CHARSET, hdrbuf, 0, lf);
	}

	public boolean didFetchTestConnectivity() {
		return false;
	}

	@Override
	protected void doFetch(final ProgressMonitor monitor,
			final Collection<Ref> want, final Set<ObjectId> have)
			throws TransportException {
		verifyPrerequisites();
		try {
			ObjectInserter ins = transport.local.newObjectInserter();
			try {
				PackParser parser = ins.newPackParser(bin);
				parser.setAllowThin(true);
				parser.setObjectChecking(transport.isCheckFetchedObjects());
				parser.setLockMessage(lockMessage);
				packLock = parser.parse(NullProgressMonitor.INSTANCE);
				ins.flush();
			} finally {
				ins.release();
			}
		} catch (IOException err) {
			close();
			throw new TransportException(transport.uri, err.getMessage(), err);
		} catch (RuntimeException err) {
			close();
			throw new TransportException(transport.uri, err.getMessage(), err);
		}
	}

	public void setPackLockMessage(final String message) {
		lockMessage = message;
	}

	public Collection<PackLock> getPackLocks() {
		if (packLock != null)
			return Collections.singleton(packLock);
		return Collections.<PackLock> emptyList();
	}

	private void verifyPrerequisites() throws TransportException {
		if (prereqs.isEmpty())
			return;

		final RevWalk rw = new RevWalk(transport.local);
		try {
			final RevFlag PREREQ = rw.newFlag("PREREQ"); //$NON-NLS-1$
			final RevFlag SEEN = rw.newFlag("SEEN"); //$NON-NLS-1$

			final Map<ObjectId, String> missing = new HashMap<ObjectId, String>();
			final List<RevObject> commits = new ArrayList<RevObject>();
			for (final Map.Entry<ObjectId, String> e : prereqs.entrySet()) {
				ObjectId p = e.getKey();
				try {
					final RevCommit c = rw.parseCommit(p);
					if (!c.has(PREREQ)) {
						c.add(PREREQ);
						commits.add(c);
					}
				} catch (MissingObjectException notFound) {
					missing.put(p, e.getValue());
				} catch (IOException err) {
					throw new TransportException(transport.uri, MessageFormat
							.format(JGitText.get().cannotReadCommit, p.name()),
							err);
				}
			}
			if (!missing.isEmpty())
				throw new MissingBundlePrerequisiteException(transport.uri,
						missing);

			Map<String, Ref> localRefs;
			try {
				localRefs = transport.local.getRefDatabase().getRefs(ALL);
			} catch (IOException e) {
				throw new TransportException(transport.uri, e.getMessage(), e);
			}
			for (final Ref r : localRefs.values()) {
				try {
					rw.markStart(rw.parseCommit(r.getObjectId()));
				} catch (IOException readError) {
					// If we cannot read the value of the ref skip it.
				}
			}

			int remaining = commits.size();
			try {
				RevCommit c;
				while ((c = rw.next()) != null) {
					if (c.has(PREREQ)) {
						c.add(SEEN);
						if (--remaining == 0)
							break;
					}
				}
			} catch (IOException err) {
				throw new TransportException(transport.uri,
						JGitText.get().cannotReadObject, err);
			}

			if (remaining > 0) {
				for (final RevObject o : commits) {
					if (!o.has(SEEN))
						missing.put(o, prereqs.get(o));
				}
				throw new MissingBundlePrerequisiteException(transport.uri,
						missing);
			}
		} finally {
			rw.release();
		}
	}

	@Override
	public void close() {
		if (bin != null) {
			try {
				bin.close();
			} catch (IOException ie) {
				// Ignore close failures.
			} finally {
				bin = null;
			}
		}
	}
}

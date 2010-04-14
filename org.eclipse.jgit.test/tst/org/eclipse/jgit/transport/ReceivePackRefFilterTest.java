/*
 * Copyright (C) 2010, Google Inc.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Deflater;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectDirectory;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.TemporaryBuffer;

public class ReceivePackRefFilterTest extends LocalDiskRepositoryTestCase {
	private static final NullProgressMonitor PM = NullProgressMonitor.INSTANCE;

	private static final String R_MASTER = Constants.R_HEADS + Constants.MASTER;

	private static final String R_PRIVATE = Constants.R_HEADS + "private";

	private Repository src;

	private Repository dst;

	private RevCommit A, B;

	private RevBlob littlea;

	@Override
	protected void setUp() throws Exception {
		super.setUp();

		src = createBareRepository();
		dst = createBareRepository();

		// Fill dst with a some common history.
		//
		TestRepository d = new TestRepository(dst);
		littlea = d.blob("a");
		A = d.commit(d.tree(d.file("a", littlea)));
		B = d.commit().parent(A).create();
		d.update(R_MASTER, B);

		// Clone from dst into src
		//
		Transport t = Transport.open(src, uriOf(dst));
		try {
			t.fetch(PM, Collections.singleton(new RefSpec("+refs/*:refs/*")));
			assertEquals(B.copy(), src.resolve(R_MASTER));
		} finally {
			t.close();
		}

		// Now put private stuff into dst.
		//
		RevCommit P = d.commit(d.tree(d.file("b", d.blob("b"))), A);
		d.update(R_PRIVATE, P);
	}

	public void testSuccess() throws Exception {
		// Manually force a delta of an object so we reuse it later.
		//
		final byte[] hdr = new byte[4];
		TemporaryBuffer.Heap tinyPack = new TemporaryBuffer.Heap(4096);
		tinyPack.write(Constants.PACK_SIGNATURE);
		NB.encodeInt32(hdr, 0, 2);
		tinyPack.write(hdr, 0, 4);
		tinyPack.write(hdr, 0, 4);

		tinyPack.write((Constants.OBJ_BLOB) << 4 | 1);
		deflate(tinyPack, new byte[] { 'a' });

		tinyPack.write((Constants.OBJ_REF_DELTA) << 4 | 4);
		littlea.copyRawTo(tinyPack);
		deflate(tinyPack, new byte[] { 0x1, 0x1, 0x1, 'b' });

		digest(tinyPack);
		openPack(tinyPack);

		// Now create something like it in src, but different parent.
		//
		TestRepository s = new TestRepository(src);
		RevBlob littleb = s.blob("b");
		RevCommit N = s.commit().parent(B).add("q", littleb).create();
		s.update(R_MASTER, N);

		// Verify the only storage of b is our packed delta above.
		//
		ObjectDirectory od = (ObjectDirectory) src.getObjectDatabase();
		assertFalse("b not loose", od.fileFor(littleb).exists());
		assertTrue("has b", od.hasObject(littleb));

		// Push this new content to the remote, doing strict validation.
		//
		TransportLocal t = new TransportLocal(src, uriOf(dst)) {
			@Override
			ReceivePack createReceivePack(final Repository db) {
				final ReceivePack rp = super.createReceivePack(dst);
				rp.setCheckReceivedObjects(true);
				rp.setEnsureProvidedObjectsVisible(true);
				rp.setRefFilter(new RefFilter() {
					public Map<String, Ref> filter(Map<String, Ref> refs) {
						Map<String, Ref> r = new HashMap<String, Ref>(refs);
						assertNotNull(r.remove(R_PRIVATE));
						return r;
					}
				});
				return rp;
			}
		};
		RemoteRefUpdate u = new RemoteRefUpdate( //
				src, //
				R_MASTER, // src name
				R_MASTER, // dst name
				false, // do not force update
				null, // local tracking branch
				null // expected id
		);
		PushResult r;
		try {
			t.setPushThin(true);
			r = t.push(PM, Collections.singleton(u));
		} finally {
			t.close();
		}

		assertNotNull("have result", r);
		assertNull("private not advertised", r.getAdvertisedRef(R_PRIVATE));
		assertSame("master updated", RemoteRefUpdate.Status.OK, u.getStatus());
		assertEquals(N.copy(), dst.resolve(R_MASTER));
	}

	private void deflate(TemporaryBuffer.Heap tinyPack, final byte[] content)
			throws IOException {
		final Deflater deflater = new Deflater();
		final byte[] buf = new byte[128];
		deflater.setInput(content, 0, content.length);
		deflater.finish();
		do {
			final int n = deflater.deflate(buf, 0, buf.length);
			if (n > 0)
				tinyPack.write(buf, 0, n);
		} while (!deflater.finished());
	}

	private void digest(TemporaryBuffer.Heap buf) throws IOException {
		MessageDigest md = Constants.newMessageDigest();
		md.update(buf.toByteArray());
		buf.write(md.digest());
	}

	private void openPack(TemporaryBuffer.Heap buf) throws IOException {
		final byte[] raw = buf.toByteArray();
		IndexPack ip = IndexPack.create(src, new ByteArrayInputStream(raw));
		ip.index(PM);
		ip.renameAndOpenPack();
	}

	private static URIish uriOf(Repository r) throws URISyntaxException {
		return new URIish(r.getDirectory().getAbsolutePath());
	}
}

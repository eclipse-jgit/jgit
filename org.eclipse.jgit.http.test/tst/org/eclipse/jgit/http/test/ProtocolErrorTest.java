/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.http.server.GitSmartHttpTools;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.http.HttpTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.transport.PacketLineIn;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.NB;
import org.junit.Before;
import org.junit.Test;

public class ProtocolErrorTest extends HttpTestCase {
	private Repository remoteRepository;

	private URIish remoteURI;

	private RevBlob a_blob;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		final TestRepository<Repository> src = createTestRepository();
		final String srcName = src.getRepository().getDirectory().getName();

		ServletContextHandler app = server.addContext("/git");
		GitServlet gs = new GitServlet();
		gs.setRepositoryResolver((HttpServletRequest req, String name) -> {
			if (!name.equals(srcName)) {
				throw new RepositoryNotFoundException(name);
			}
			final Repository db = src.getRepository();
			db.incrementOpen();
			return db;
		});
		app.addServlet(new ServletHolder(gs), "/*");

		server.setUp();

		remoteRepository = src.getRepository();
		remoteURI = toURIish(app, srcName);

		StoredConfig cfg = remoteRepository.getConfig();
		cfg.setBoolean("http", null, "receivepack", true);
		cfg.save();

		a_blob = src.blob("a");
	}

	@Test
	public void testPush_UnpackError_TruncatedPack() throws Exception {
		StringBuilder sb = new StringBuilder();
		sb.append(ObjectId.zeroId().name());
		sb.append(' ');
		sb.append(a_blob.name());
		sb.append(' ');
		sb.append("refs/objects/A");
		sb.append('\0');
		sb.append("report-status");

		ByteArrayOutputStream reqbuf = new ByteArrayOutputStream();
		PacketLineOut reqpck = new PacketLineOut(reqbuf);
		reqpck.writeString(sb.toString());
		reqpck.end();

		packHeader(reqbuf, 1);

		byte[] reqbin = reqbuf.toByteArray();

		URL u = new URL(remoteURI.toString() + "/git-receive-pack");
		HttpURLConnection c = (HttpURLConnection) u.openConnection();
		try {
			c.setRequestMethod("POST");
			c.setDoOutput(true);
			c.setRequestProperty("Content-Type",
					GitSmartHttpTools.RECEIVE_PACK_REQUEST_TYPE);
			c.setFixedLengthStreamingMode(reqbin.length);
			try (OutputStream out = c.getOutputStream()) {
				out.write(reqbin);
			}

			assertEquals(200, c.getResponseCode());
			assertEquals(GitSmartHttpTools.RECEIVE_PACK_RESULT_TYPE,
					c.getContentType());

			try (InputStream rawin = c.getInputStream()) {
				PacketLineIn pckin = new PacketLineIn(rawin);
				assertEquals("unpack error "
						+ JGitText.get().packfileIsTruncatedNoParam,
						pckin.readString());
				assertEquals("ng refs/objects/A n/a (unpacker error)",
						pckin.readString());
				assertTrue(PacketLineIn.isEnd(pckin.readString()));
			}
		} finally {
			c.disconnect();
		}
	}

	private static void packHeader(ByteArrayOutputStream tinyPack, int cnt)
			throws IOException {
		final byte[] hdr = new byte[8];
		NB.encodeInt32(hdr, 0, 2);
		NB.encodeInt32(hdr, 4, cnt);

		tinyPack.write(Constants.PACK_SIGNATURE);
		tinyPack.write(hdr, 0, 8);
	}
}

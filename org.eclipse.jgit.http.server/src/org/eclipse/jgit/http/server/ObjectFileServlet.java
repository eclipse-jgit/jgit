/*
 * Copyright (C) 2009-2010, Google Inc.
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

package org.eclipse.jgit.http.server;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;
import static org.eclipse.jgit.http.server.ServletUtils.getRepository;
import static org.eclipse.jgit.util.HttpSupport.HDR_ETAG;
import static org.eclipse.jgit.util.HttpSupport.HDR_IF_MODIFIED_SINCE;
import static org.eclipse.jgit.util.HttpSupport.HDR_IF_NONE_MATCH;
import static org.eclipse.jgit.util.HttpSupport.HDR_LAST_MODIFIED;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Instant;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.internal.storage.file.ObjectDirectory;
import org.eclipse.jgit.lib.Repository;

/** Sends any object from {@code GIT_DIR/objects/??/0 38}, or any pack file. */
abstract class ObjectFileServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	static class Loose extends ObjectFileServlet {
		private static final long serialVersionUID = 1L;

		Loose() {
			super("application/x-git-loose-object");
		}

		@Override
		String etag(FileSender sender) throws IOException {
			Instant lastModified = sender.getLastModified();
			return Long.toHexString(lastModified.getEpochSecond())
					+ Long.toHexString(lastModified.getNano());
		}
	}

	private abstract static class PackData extends ObjectFileServlet {
		private static final long serialVersionUID = 1L;

		PackData(String contentType) {
			super(contentType);
		}

		@Override
		String etag(FileSender sender) throws IOException {
			return sender.getTailChecksum();
		}
	}

	static class Pack extends PackData {
		private static final long serialVersionUID = 1L;

		Pack() {
			super("application/x-git-packed-objects");
		}
	}

	static class PackIdx extends PackData {
		private static final long serialVersionUID = 1L;

		PackIdx() {
			super("application/x-git-packed-objects-toc");
		}
	}

	private final String contentType;

	ObjectFileServlet(String contentType) {
		this.contentType = contentType;
	}

	abstract String etag(FileSender sender) throws IOException;

	/** {@inheritDoc} */
	@Override
	public void doGet(final HttpServletRequest req,
			final HttpServletResponse rsp) throws IOException {
		serve(req, rsp, true);
	}

	/** {@inheritDoc} */
	@Override
	protected void doHead(final HttpServletRequest req,
			final HttpServletResponse rsp) throws ServletException, IOException {
		serve(req, rsp, false);
	}

	private void serve(final HttpServletRequest req,
			final HttpServletResponse rsp, final boolean sendBody)
			throws IOException {
		final File obj = new File(objects(req), req.getPathInfo());
		final FileSender sender;
		try {
			sender = new FileSender(obj);
		} catch (FileNotFoundException e) {
			rsp.sendError(SC_NOT_FOUND);
			return;
		}

		try {
			final String etag = etag(sender);
			// HTTP header Last-Modified header has a resolution of 1 sec, see
			// https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.29
			final long lastModified = sender.getLastModified().getEpochSecond();

			String ifNoneMatch = req.getHeader(HDR_IF_NONE_MATCH);
			if (etag != null && etag.equals(ifNoneMatch)) {
				rsp.sendError(SC_NOT_MODIFIED);
				return;
			}

			long ifModifiedSince = req.getDateHeader(HDR_IF_MODIFIED_SINCE);
			if (0 < lastModified && lastModified < ifModifiedSince) {
				rsp.sendError(SC_NOT_MODIFIED);
				return;
			}

			if (etag != null)
				rsp.setHeader(HDR_ETAG, etag);
			if (0 < lastModified)
				rsp.setDateHeader(HDR_LAST_MODIFIED, lastModified);
			rsp.setContentType(contentType);
			sender.serve(req, rsp, sendBody);
		} finally {
			sender.close();
		}
	}

	private static File objects(HttpServletRequest req) {
		final Repository db = getRepository(req);
		return ((ObjectDirectory) db.getObjectDatabase()).getDirectory();
	}
}

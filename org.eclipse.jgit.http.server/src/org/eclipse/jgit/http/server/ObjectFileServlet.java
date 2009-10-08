/*
 * Copyright (C) 2009, Google Inc.
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectDirectory;

/** Sends any object from {@code GIT_DIR/objects/??/0 38}, or any pack file. */
abstract class ObjectFileServlet extends RepositoryServlet {
	private static final long serialVersionUID = 1L;

	static class Loose extends ObjectFileServlet {
		private static final long serialVersionUID = 1L;

		Loose() {
			super("application/x-git-loose-object");
		}

		@Override
		String etag(final Sender.FileSender sender) throws IOException {
			return Long.toHexString(sender.getLastModified());
		}
	}

	private static abstract class PackData extends ObjectFileServlet {
		private static final long serialVersionUID = 1L;

		PackData(String contentType) {
			super(contentType);
		}

		@Override
		String etag(final Sender.FileSender sender) throws IOException {
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

	ObjectFileServlet(final String contentType) {
		this.contentType = contentType;
	}

	abstract String etag(Sender.FileSender sender) throws IOException;

	@Override
	public void doGet(final HttpServletRequest req,
			final HttpServletResponse rsp) throws IOException {
		serve(req, rsp, true);
	}

	@Override
	protected void doHead(final HttpServletRequest req,
			final HttpServletResponse rsp) throws ServletException, IOException {
		serve(req, rsp, false);
	}

	private void serve(final HttpServletRequest req,
			final HttpServletResponse rsp, final boolean sendBody)
			throws IOException {
		final String fileName = req.getPathInfo();
		final Sender.FileSender sender = createSender(req, fileName);
		if (sender == null) {
			nocache(rsp);
			rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		try {
			final String etag = etag(sender);
			final long lastModified = sender.getLastModified() / 1000 * 1000;

			String ifNoneMatch = req.getHeader("If-None-Match");
			if (etag.equals(ifNoneMatch)) {
				rsp.sendError(HttpServletResponse.SC_NOT_MODIFIED);
				return;
			}

			long ifModifiedSince = req.getDateHeader("If-Modified-Since");
			if (lastModified < ifModifiedSince) {
				rsp.sendError(HttpServletResponse.SC_NOT_MODIFIED);
				return;
			}

			cacheForever(rsp);
			rsp.setHeader("ETag", etag);
			rsp.setDateHeader("Last-Modified", lastModified);
			rsp.setContentType(contentType);
			rsp.setContentLength(sender.getContentLength());
			if (sendBody)
				sender.sendBody(rsp);
		} finally {
			sender.close();
		}
	}

	private Sender.FileSender createSender(final HttpServletRequest req,
			final String fileName) {
		final ObjectDatabase db = getRepository(req).getObjectDatabase();
		if (db instanceof ObjectDirectory) {
			final File dir = ((ObjectDirectory) db).getDirectory();
			final File obj = new File(dir, fileName);
			try {
				return new Sender.FileSender(obj);
			} catch (FileNotFoundException e) {
				return null;
			}

		} else {
			// TODO For loose object service, unpack the object & serve as loose
			// For pack services, we are forced to return null as other
			// database types might not support pack files.
			//
			return null;
		}
	}
}

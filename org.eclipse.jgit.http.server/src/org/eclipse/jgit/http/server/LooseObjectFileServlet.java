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

/** Sends any one object from {@code GIT_DIR/objects/??/0 38} . */
class LooseObjectFileServlet extends RepositoryServlet {
	private static final long serialVersionUID = 1L;

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
		final String etag = req.getPathInfo();
		final Sender send = createSender(req, etag);
		if (send != null) {
			try {
				cacheForever(rsp);
				rsp.setHeader("ETag", etag);
				rsp.setContentType("application/octet-stream");
				rsp.setContentLength(send.getContentLength());
				if (sendBody)
					send.sendBody(rsp);
			} finally {
				send.close();
			}
		} else {
			rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
		}
	}

	private Sender createSender(final HttpServletRequest req,
			final String looseFileName) {
		final ObjectDatabase db = getRepository(req).getObjectDatabase();
		if (db instanceof ObjectDirectory) {
			final File dir = ((ObjectDirectory) db).getDirectory();
			final File obj = new File(dir, looseFileName);
			try {
				return new Sender.FileSender(obj);
			} catch (FileNotFoundException e) {
				return null;
			}

		} else {
			// TODO Unpack the object, compress as a loose object and send.
			//
			return null;
		}
	}
}

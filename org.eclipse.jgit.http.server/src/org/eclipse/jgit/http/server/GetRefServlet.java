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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/** Sends the current value of any ref, loose or packed. */
class GetRefServlet extends RepositoryServlet {
	private static final long serialVersionUID = 1L;

	private static final String ENCODING = "UTF-8";

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
		final byte[] raw = read(req);
		if (raw != null) {
			rsp.setContentType("text/plain");
			rsp.setCharacterEncoding(ENCODING);
			send(raw, req, rsp, sendBody);
		} else {
			rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
		}
	}

	private static byte[] read(final HttpServletRequest req) throws IOException {
		final String refName = req.getPathInfo();
		if (!isValidName(refName))
			return null;

		final Ref ref = getRepository(req).getRef(refName);
		if (ref == null)
			return null;

		final StringBuilder out = new StringBuilder();
		if (isSymref(ref)) {
			out.append("ref: ");
			out.append(ref.getName());
		} else {
			out.append(ref.getObjectId().getName());
		}
		out.append('\n');
		return out.toString().getBytes(ENCODING);
	}

	private static boolean isValidName(final String refName) {
		if (refName.equals(Constants.HEAD))
			return true;
		return refName.startsWith("refs/") //
				&& !refName.contains("..") //
				&& !Repository.isValidRefName(refName);
	}

	private static boolean isSymref(final Ref ref) {
		return !ref.getName().equals(ref.getOrigName());
	}
}

/*
 * Copyright (C) 2015, Sasa Zivkov <sasa.zivkov@sap.com>
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
package org.eclipse.jgit.lfs.server;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * LFS protocol handler implementing the LFS batch API [1]
 *
 * [1] https://github.com/github/git-lfs/blob/master/docs/api/http-v1-batch.md
 *
 * @since 4.3
 */
public abstract class LfsProtocolServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final String CONTENTTYPE_VND_GIT_LFS_JSON = "application/vnd.git-lfs+json"; //$NON-NLS-1$

	private Gson gson = createGson();

	/**
	 * Get the large file repository
	 *
	 * @return the large file repository storing large files
	 */
	protected abstract LargeFileRepository getLargeFileRepository();

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse res)
			throws ServletException, IOException {
		res.setStatus(SC_OK);
		res.setContentType(CONTENTTYPE_VND_GIT_LFS_JSON);

		Writer w = new BufferedWriter(
				new OutputStreamWriter(res.getOutputStream(), UTF_8));

		Reader r = new BufferedReader(new InputStreamReader(req.getInputStream(), UTF_8));
		LfsRequest request = gson.fromJson(r, LfsRequest.class);

		LargeFileRepository repo = getLargeFileRepository();
		if (repo == null) {
			res.setStatus(SC_SERVICE_UNAVAILABLE);
			return;
		}

		TransferHandler handler = TransferHandler
				.forOperation(request.operation, repo, request.objects);
		gson.toJson(handler.process(), w);
		w.flush();
	}

	private static class LfsRequest {
		String operation;

		List<LfsObject> objects;
	}

	private static Gson createGson() {
		GsonBuilder gb = new GsonBuilder()
				.setFieldNamingPolicy(
						FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
				.setPrettyPrinting().disableHtmlEscaping();
		return gb.create();
	}
}

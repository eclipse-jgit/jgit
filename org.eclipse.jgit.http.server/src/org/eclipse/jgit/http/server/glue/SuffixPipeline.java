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

package org.eclipse.jgit.http.server.glue;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Selects requests by matching the suffix of the URI.
 * <p>
 * The suffix string is literally matched against the path info of the servlet
 * request, as this class assumes it is invoked by {@link MetaServlet}. Suffix
 * strings may include path components. Examples include {@code /info/refs}, or
 * just simple extension matches like {@code .txt}.
 * <p>
 * When dispatching to the rest of the pipeline the HttpServletRequest is
 * modified so that {@code getPathInfo()} does not contain the suffix that
 * caused this pipeline to be selected.
 */
class SuffixPipeline extends UrlPipeline {
	static class Binder extends ServletBinderImpl {
		private final String suffix;

		Binder(final String suffix) {
			this.suffix = suffix;
		}

		@Override
		UrlPipeline create() {
			return new SuffixPipeline(suffix, getFilters(), getServlet());
		}
	}

	private final String suffix;

	private final int suffixLen;

	SuffixPipeline(final String suffix, final Filter[] filters,
			final HttpServlet servlet) {
		super(filters, servlet);
		this.suffix = suffix;
		this.suffixLen = suffix.length();
	}

	@Override
	boolean match(final HttpServletRequest req) {
		final String pathInfo = req.getPathInfo();
		return pathInfo != null && pathInfo.endsWith(suffix);
	}

	@Override
	void service(HttpServletRequest req, HttpServletResponse rsp)
			throws ServletException, IOException {
		String curInfo = req.getPathInfo();
		String newPath = req.getServletPath() + curInfo;
		String newInfo = curInfo.substring(0, curInfo.length() - suffixLen);
		super.service(new WrappedRequest(req, newPath, newInfo), rsp);
	}

	@Override
	public String toString() {
		return "Pipeline[ *" + suffix + " ]";
	}
}

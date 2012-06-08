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

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.eclipse.jgit.http.server.glue.MetaFilter.REGEX_GROUPS;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Selects requests by matching the URI against a regular expression.
 * <p>
 * The pattern is bound and matched against the path info of the servlet
 * request, as this class assumes it is invoked by {@link MetaServlet}.
 * <p>
 * If there are capture groups in the regular expression, the matched ranges of
 * the capture groups are stored as an array of modified HttpServetRequests,
 * into the request attribute {@link MetaFilter#REGEX_GROUPS}. Using a capture
 * group that may not capture, e.g. {@code "(/foo)?"}, will cause an error at
 * request handling time.
 * <p>
 * Each servlet request has been altered to have its {@code getServletPath()}
 * method return the original path info up to the beginning of the corresponding
 * capture group, and its {@code getPathInfo()} method return the matched text.
 * A {@link RegexGroupFilter} can be applied in the pipeline to switch the
 * current HttpServletRequest to reference a different capture group before
 * running additional filters, or the final servlet.
 * <p>
 * Note that for {@code getPathInfo()} to start with a leading "/" as described
 * in the servlet documentation, capture groups must actually capture the
 * leading "/".
 * <p>
 * This class dispatches the remainder of the pipeline using the first capture
 * group as the current request, making {@code RegexGroupFilter} required only
 * to access capture groups beyond the first.
 */
class RegexPipeline extends UrlPipeline {
	static class Binder extends ServletBinderImpl {
		private final Pattern pattern;

		Binder(final String p) {
			pattern = Pattern.compile(p);
		}

		Binder(final Pattern p) {
			pattern = p;
		}

		UrlPipeline create() {
			return new RegexPipeline(pattern, getFilters(), getServlet());
		}
	}

	private final Pattern pattern;

	RegexPipeline(final Pattern pattern, final Filter[] filters,
			final HttpServlet servlet) {
		super(filters, servlet);
		this.pattern = pattern;
	}

	boolean match(final HttpServletRequest req) {
		final String pathInfo = req.getPathInfo();
		return pathInfo != null && pattern.matcher(pathInfo).matches();
	}

	@Override
	void service(HttpServletRequest req, HttpServletResponse rsp)
			throws ServletException, IOException {
		final String reqInfo = req.getPathInfo();
		if (reqInfo == null) {
			rsp.sendError(SC_NOT_FOUND);
			return;
		}

		final Matcher cur = pattern.matcher(reqInfo);
		if (!cur.matches()) {
			rsp.sendError(SC_NOT_FOUND);
			return;
		}

		final String reqPath = req.getServletPath();
		final Object old = req.getAttribute(REGEX_GROUPS);
		try {
			if (1 <= cur.groupCount()) {
				// If there are groups extract every capture group and
				// build a request for them so RegexGroupFilter can pick
				// a different capture group later. Continue using the
				// first capture group as the path info.
				WrappedRequest groups[] = new WrappedRequest[cur.groupCount()];
				for (int groupId = 1; groupId <= cur.groupCount(); groupId++) {
					final int s = cur.start(groupId);
					final String path, info;

					path = reqPath + reqInfo.substring(0, s);
					info = cur.group(groupId);
					groups[groupId - 1] = new WrappedRequest(req, path, info);
				}
				req.setAttribute(REGEX_GROUPS, groups);
				super.service(groups[0], rsp);

			} else {
				// No capture groups were present, service the whole request.
				final String path = reqPath + reqInfo;
				final String info = null;
				super.service(new WrappedRequest(req, path, info), rsp);
			}
		} finally {
			if (old != null)
				req.setAttribute(REGEX_GROUPS, old);
			else
				req.removeAttribute(REGEX_GROUPS);
		}
	}

	@Override
	public String toString() {
		return "Pipeline[regex: " + pattern + " ]";
	}
}

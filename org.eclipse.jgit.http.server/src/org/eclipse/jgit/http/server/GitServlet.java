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

import java.util.Enumeration;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.http.server.glue.MetaServlet;
import org.eclipse.jgit.http.server.resolver.AsIsFileService;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;

/**
 * Handles Git repository access over HTTP.
 * <p>
 * Applications embedding this servlet should map a directory path within the
 * application to this servlet, for example:
 *
 * <pre>
 *   &lt;servlet&gt;
 *     &lt;servlet-name&gt;GitServlet&lt;/servlet-name&gt;
 *     &lt;servlet-class&gt;org.eclipse.jgit.http.server.GitServlet&lt;/servlet-class&gt;
 *     &lt;init-param&gt;
 *       &lt;param-name&gt;base-path&lt;/param-name&gt;
 *       &lt;param-value&gt;/var/srv/git&lt;/param-value&gt;
 *     &lt;/init-param&gt;
 *     &lt;init-param&gt;
 *       &lt;param-name&gt;export-all&lt;/param-name&gt;
 *       &lt;param-value&gt;0&lt;/param-value&gt;
 *     &lt;/init-param&gt;
 * &lt;/servlet&gt;
 *   &lt;servlet-mapping&gt;
 *     &lt;servlet-name&gt;GitServlet&lt;/servlet-name&gt;
 *     &lt;url-pattern&gt;/git/*&lt;/url-pattern&gt;
 *   &lt;/servlet-mapping&gt;
 * </pre>
 *
 * <p>
 * Applications may wish to add additional repository action URLs to this
 * servlet by taking advantage of its extension from {@link MetaServlet}.
 * Callers may register their own URL suffix translations through
 * {@link #serve(String)}, or their regex translations through
 * {@link #serveRegex(String)}. Each translation should contain a complete
 * filter pipeline which ends with the HttpServlet that should handle the
 * requested action.
 */
public class GitServlet extends MetaServlet {
	private static final long serialVersionUID = 1L;

	private final GitFilter gitFilter;

	/**
	 * New servlet that will load its base directory from {@code web.xml}.
	 * <p>
	 * The required parameter {@code base-path} must be configured to point to
	 * the local filesystem directory where all served Git repositories reside.
	 */
	public GitServlet() {
		super(new GitFilter());
		gitFilter = (GitFilter) getDelegateFilter();
	}

	/**
	 * New servlet configured with a specific resolver.
	 *
	 * @param resolver
	 *            the resolver to use when matching URL to Git repository. If
	 *            null the {@code base-path} parameter will be looked for in the
	 *            parameter table during init, which usually comes from the
	 *            {@code web.xml} file of the web application.
	 */
	public void setRepositoryResolver(RepositoryResolver<HttpServletRequest> resolver) {
		gitFilter.setRepositoryResolver(resolver);
	}

	/**
	 * @param f
	 *            the filter to validate direct access to repository files
	 *            through a dumb client. If {@code null} then dumb client
	 *            support is completely disabled.
	 */
	public void setAsIsFileService(AsIsFileService f) {
		gitFilter.setAsIsFileService(f);
	}

	/**
	 * @param f
	 *            the factory to construct and configure an {@link UploadPack}
	 *            session when a fetch or clone is requested by a client.
	 */
	public void setUploadPackFactory(UploadPackFactory<HttpServletRequest> f) {
		gitFilter.setUploadPackFactory(f);
	}

	/**
	 * @param filter
	 *            filter to apply before any of the UploadPack operations. The
	 *            UploadPack instance is available in the request attribute
	 *            {@link ServletUtils#ATTRIBUTE_HANDLER}.
	 */
	public void addUploadPackFilter(Filter filter) {
		gitFilter.addUploadPackFilter(filter);
	}

	/**
	 * @param f
	 *            the factory to construct and configure a {@link ReceivePack}
	 *            session when a push is requested by a client.
	 */
	public void setReceivePackFactory(ReceivePackFactory<HttpServletRequest> f) {
		gitFilter.setReceivePackFactory(f);
	}

	/**
	 * @param filter
	 *            filter to apply before any of the ReceivePack operations. The
	 *            ReceivePack instance is available in the request attribute
	 *            {@link ServletUtils#ATTRIBUTE_HANDLER}.
	 */
	public void addReceivePackFilter(Filter filter) {
		gitFilter.addReceivePackFilter(filter);
	}

	@Override
	public void init(final ServletConfig config) throws ServletException {
		gitFilter.init(new FilterConfig() {
			public String getFilterName() {
				return gitFilter.getClass().getName();
			}

			public String getInitParameter(String name) {
				return config.getInitParameter(name);
			}

			public Enumeration<String> getInitParameterNames() {
				return config.getInitParameterNames();
			}

			public ServletContext getServletContext() {
				return config.getServletContext();
			}
		});
	}
}

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

import java.io.File;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.http.server.glue.ErrorServlet;
import org.eclipse.jgit.http.server.glue.MetaServlet;
import org.eclipse.jgit.http.server.glue.RegexGroupFilter;
import org.eclipse.jgit.http.server.glue.ServletBinder;
import org.eclipse.jgit.http.server.resolver.DefaultReceivePackFactory;
import org.eclipse.jgit.http.server.resolver.DefaultUploadPackFactory;
import org.eclipse.jgit.http.server.resolver.FileResolver;
import org.eclipse.jgit.http.server.resolver.AsIsFileService;
import org.eclipse.jgit.http.server.resolver.ReceivePackFactory;
import org.eclipse.jgit.http.server.resolver.RepositoryResolver;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.http.server.resolver.UploadPackFactory;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.util.StringUtils;

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

	private volatile boolean initialized;

	private RepositoryResolver resolver;

	private AsIsFileService asIs = new AsIsFileService();

	private UploadPackFactory uploadPackFactory = new DefaultUploadPackFactory();

	private ReceivePackFactory receivePackFactory = new DefaultReceivePackFactory();

	/**
	 * New servlet that will load its base directory from {@code web.xml}.
	 * <p>
	 * The required parameter {@code base-path} must be configured to point to
	 * the local filesystem directory where all served Git repositories reside.
	 */
	public GitServlet() {
		// Initialized above by field declarations.
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
	public void setRepositoryResolver(RepositoryResolver resolver) {
		assertNotInitialized();
		this.resolver = resolver;
	}

	/**
	 * @param f
	 *            the filter to validate direct access to repository files
	 *            through a dumb client. If {@code null} then dumb client
	 *            support is completely disabled.
	 */
	public void setAsIsFileService(AsIsFileService f) {
		assertNotInitialized();
		this.asIs = f != null ? f : AsIsFileService.DISABLED;
	}

	/**
	 * @param f
	 *            the factory to construct and configure an {@link UploadPack}
	 *            session when a fetch or clone is requested by a client.
	 */
	public void setUploadPackFactory(UploadPackFactory f) {
		assertNotInitialized();
		this.uploadPackFactory = f != null ? f : UploadPackFactory.DISABLED;
	}

	/**
	 * @param f
	 *            the factory to construct and configure a {@link ReceivePack}
	 *            session when a push is requested by a client.
	 */
	public void setReceivePackFactory(ReceivePackFactory f) {
		assertNotInitialized();
		this.receivePackFactory = f != null ? f : ReceivePackFactory.DISABLED;
	}

	private void assertNotInitialized() {
		if (initialized)
			throw new IllegalStateException("Already initialized by container");
	}

	@Override
	public void init(final ServletConfig config) throws ServletException {
		super.init(config);

		if (resolver == null) {
			final File root = getFile("base-path");
			final boolean exportAll = getBoolean("export-all");
			setRepositoryResolver(new FileResolver(root, exportAll));
		}

		initialized = true;

		if (uploadPackFactory != ReceivePackFactory.DISABLED) {
			serve("*/git-upload-pack")//
					.with(new UploadPackServlet(uploadPackFactory));
		}

		if (receivePackFactory != ReceivePackFactory.DISABLED) {
			serve("*/git-receive-pack")//
					.with(new ReceivePackServlet(receivePackFactory));
		}

		ServletBinder refs = serve("*/" + Constants.INFO_REFS);
		if (uploadPackFactory != UploadPackFactory.DISABLED) {
			refs = refs.through(//
					new UploadPackServlet.InfoRefs(uploadPackFactory));
		}
		if (receivePackFactory != ReceivePackFactory.DISABLED) {
			refs = refs.through(//
					new ReceivePackServlet.InfoRefs(receivePackFactory));
		}
		if (asIs != AsIsFileService.DISABLED) {
			refs = refs.through(new IsLocalFilter());
			refs = refs.through(new AsIsFileFilter(asIs));
			refs.with(new InfoRefsServlet());
		} else
			refs.with(new ErrorServlet(HttpServletResponse.SC_FORBIDDEN));

		if (asIs != AsIsFileService.DISABLED) {
			final IsLocalFilter mustBeLocal = new IsLocalFilter();
			final AsIsFileFilter enabled = new AsIsFileFilter(asIs);

			serve("*/" + Constants.HEAD)//
					.through(mustBeLocal)//
					.through(enabled)//
					.with(new TextFileServlet(Constants.HEAD));

			final String info_alternates = "objects/info/alternates";
			serve("*/" + info_alternates)//
					.through(mustBeLocal)//
					.through(enabled)//
					.with(new TextFileServlet(info_alternates));

			final String http_alternates = "objects/info/http-alternates";
			serve("*/" + http_alternates)//
					.through(mustBeLocal)//
					.through(enabled)//
					.with(new TextFileServlet(http_alternates));

			serve("*/objects/info/packs")//
					.through(mustBeLocal)//
					.through(enabled)//
					.with(new InfoPacksServlet());

			serveRegex("^/(.*)/objects/([0-9a-f]{2}/[0-9a-f]{38})$")//
					.through(mustBeLocal)//
					.through(enabled)//
					.through(new RegexGroupFilter(2))//
					.with(new ObjectFileServlet.Loose());

			serveRegex("^/(.*)/objects/(pack/pack-[0-9a-f]{40}\\.pack)$")//
					.through(mustBeLocal)//
					.through(enabled)//
					.through(new RegexGroupFilter(2))//
					.with(new ObjectFileServlet.Pack());

			serveRegex("^/(.*)/objects/(pack/pack-[0-9a-f]{40}\\.idx)$")//
					.through(mustBeLocal)//
					.through(enabled)//
					.through(new RegexGroupFilter(2))//
					.with(new ObjectFileServlet.PackIdx());
		}
	}

	private File getFile(final String param) throws ServletException {
		String n = getInitParameter(param);
		if (n == null || "".equals(n))
			throw new ServletException("Parameter " + param + " not set");

		File path = new File(n);
		if (!path.exists())
			throw new ServletException(path + " (for " + param + ") not found");
		return path;
	}

	private boolean getBoolean(String param) throws ServletException {
		String n = getInitParameter(param);
		if (n == null)
			return false;
		try {
			return StringUtils.toBoolean(n);
		} catch (IllegalArgumentException err) {
			throw new ServletException("Invalid boolean " + param + " = " + n);
		}
	}

	@Override
	protected ServletBinder register(ServletBinder binder) {
		if (resolver == null)
			throw new IllegalStateException("No resolver available");
		binder = binder.through(new NoCacheFilter());
		binder = binder.through(new RepositoryFilter(resolver));
		return binder;
	}
}

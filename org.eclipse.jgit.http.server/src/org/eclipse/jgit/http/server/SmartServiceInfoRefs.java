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

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.eclipse.jgit.http.server.GitSmartHttpTools.infoRefsResultType;
import static org.eclipse.jgit.http.server.GitSmartHttpTools.sendError;
import static org.eclipse.jgit.http.server.ServletUtils.ATTRIBUTE_HANDLER;
import static org.eclipse.jgit.http.server.ServletUtils.getRepository;

import java.io.IOException;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

/** Filter in front of {@link InfoRefsServlet} to catch smart service requests. */
abstract class SmartServiceInfoRefs implements Filter {
	private final String svc;

	private final Filter[] filters;

	SmartServiceInfoRefs(String service, List<Filter> filters) {
		this.svc = service;
		this.filters = filters.toArray(new Filter[filters.size()]);
	}

	/** {@inheritDoc} */
	@Override
	public void init(FilterConfig config) throws ServletException {
		// Do nothing.
	}

	/** {@inheritDoc} */
	@Override
	public void destroy() {
		// Do nothing.
	}

	/** {@inheritDoc} */
	@Override
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		final HttpServletRequest req = (HttpServletRequest) request;
		final HttpServletResponse res = (HttpServletResponse) response;

		if (svc.equals(req.getParameter("service"))) {
			final Repository db = getRepository(req);
			try {
				begin(req, db);
			} catch (ServiceNotAuthorizedException e) {
				res.sendError(SC_UNAUTHORIZED, e.getMessage());
				return;
			} catch (ServiceNotEnabledException e) {
				sendError(req, res, SC_FORBIDDEN, e.getMessage());
				return;
			}

			try {
				if (filters.length == 0)
					service(req, response);
				else
					new Chain().doFilter(request, response);
			} finally {
				req.removeAttribute(ATTRIBUTE_HANDLER);
			}
		} else {
			chain.doFilter(request, response);
		}
	}

	private void service(ServletRequest request, ServletResponse response)
			throws IOException {
		final HttpServletRequest req = (HttpServletRequest) request;
		final HttpServletResponse res = (HttpServletResponse) response;
		final SmartOutputStream buf = new SmartOutputStream(req, res, true);
		try {
			res.setContentType(infoRefsResultType(svc));

			final PacketLineOut out = new PacketLineOut(buf);
			respond(req, out, svc);
			buf.close();
		} catch (ServiceNotAuthorizedException e) {
			res.sendError(SC_UNAUTHORIZED, e.getMessage());
		} catch (ServiceNotEnabledException e) {
			sendError(req, res, SC_FORBIDDEN, e.getMessage());
		} catch (ServiceMayNotContinueException e) {
			if (e.isOutput())
				buf.close();
			else
				sendError(req, res, e.getStatusCode(), e.getMessage());
		}
	}

	/**
	 * Begin service.
	 *
	 * @param req
	 *            request
	 * @param db
	 *            repository
	 * @throws IOException
	 * @throws ServiceNotEnabledException
	 * @throws ServiceNotAuthorizedException
	 */
	protected abstract void begin(HttpServletRequest req, Repository db)
			throws IOException, ServiceNotEnabledException,
			ServiceNotAuthorizedException;

	/**
	 * Advertise.
	 *
	 * @param req
	 *            request
	 * @param pck
	 * @throws IOException
	 * @throws ServiceNotEnabledException
	 * @throws ServiceNotAuthorizedException
	 */
	protected abstract void advertise(HttpServletRequest req,
			PacketLineOutRefAdvertiser pck) throws IOException,
			ServiceNotEnabledException, ServiceNotAuthorizedException;

	/**
	 * Writes the appropriate response to an info/refs request received by
	 * a smart service. In protocol v0, this starts with "#
	 * service=serviceName" followed by a flush packet, but this is not
	 * necessarily the case in other protocol versions.
	 * <p>
	 * The default implementation writes "# service=serviceName" and a
	 * flush packet, then calls {@link #advertise}. Subclasses should
	 * override this method if they support protocol versions other than
	 * protocol v0.
	 *
	 * @param req
	 *            request
	 * @param pckOut
	 *            destination of response
	 * @param serviceName
	 *            service name to be written out in protocol v0; may or may
	 *            not be used in other versions
	 * @throws IOException
	 * @throws ServiceNotEnabledException
	 * @throws ServiceNotAuthorizedException
	 */
	protected void respond(HttpServletRequest req,
			PacketLineOut pckOut, String serviceName)
			throws IOException, ServiceNotEnabledException,
			ServiceNotAuthorizedException {
		pckOut.writeString("# service=" + svc + '\n'); //$NON-NLS-1$
		pckOut.end();
		advertise(req, new PacketLineOutRefAdvertiser(pckOut));
	}

	private class Chain implements FilterChain {
		private int filterIdx;

		@Override
		public void doFilter(ServletRequest req, ServletResponse rsp)
				throws IOException, ServletException {
			if (filterIdx < filters.length)
				filters[filterIdx++].doFilter(req, rsp, this);
			else
				service(req, rsp);
		}
	}
}

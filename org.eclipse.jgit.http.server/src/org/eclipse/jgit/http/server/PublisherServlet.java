/*
 * Copyright (C) 2012, Google Inc.
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
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static javax.servlet.http.HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE;
import static org.eclipse.jgit.http.server.GitSmartHttpTools.PUBLISH_SUBSCRIBE_REQUEST_TYPE;
import static org.eclipse.jgit.http.server.GitSmartHttpTools.PUBLISH_SUBSCRIBE_RESULT_TYPE;
import static org.eclipse.jgit.http.server.GitSmartHttpTools.sendError;
import static org.eclipse.jgit.http.server.ServletUtils.ATTRIBUTE_HANDLER;
import static org.eclipse.jgit.http.server.ServletUtils.getInputStream;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.transport.Publisher;
import org.eclipse.jgit.transport.PublisherClient;
import org.eclipse.jgit.transport.PublisherException;
import org.eclipse.jgit.transport.resolver.PublisherClientFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

/** Server side implementation of publish-subscribe over HTTP. */
public class PublisherServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	static class Factory implements Filter {
		private final PublisherClientFactory<HttpServletRequest>
				publisherFactory;

		Factory(PublisherClientFactory<HttpServletRequest> publisherFactory) {
			this.publisherFactory = publisherFactory;
		}

		public void doFilter(ServletRequest request, ServletResponse response,
				FilterChain chain) throws IOException, ServletException {
			HttpServletRequest req = (HttpServletRequest) request;
			HttpServletResponse rsp = (HttpServletResponse) response;
			PublisherClient pc;
			try {
				pc = publisherFactory.create(req);
			} catch (ServiceNotEnabledException e) {
				sendError(req, rsp, SC_FORBIDDEN);
				return;
			} catch (ServiceNotAuthorizedException e) {
				rsp.sendError(SC_UNAUTHORIZED);
				return;
			}

			try {
				req.setAttribute(ATTRIBUTE_HANDLER, pc);
				chain.doFilter(req, rsp);
			} finally {
				req.removeAttribute(ATTRIBUTE_HANDLER);
			}
		}

		public void init(FilterConfig filterConfig) throws ServletException {
			// Nothing.
		}

		public void destroy() {
			// Nothing.
		}
	}

	private final Publisher publisher;

	/**
	 * @param publisher
	 */
	public PublisherServlet(Publisher publisher) {
		this.publisher = publisher;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse rsp)
			throws ServletException, IOException {
		if (!PUBLISH_SUBSCRIBE_REQUEST_TYPE.equals(req.getContentType())) {
			rsp.sendError(SC_UNSUPPORTED_MEDIA_TYPE);
			return;
		}
		SmartOutputStream out = new SmartOutputStream(req, rsp) {
			@Override
			public void flush() throws IOException {
				doFlush();
			}
		};

		PublisherClient pc = (PublisherClient) req.getAttribute(
				ATTRIBUTE_HANDLER);
		try {
			rsp.setContentType(PUBLISH_SUBSCRIBE_RESULT_TYPE);
			// Block inside subscribe until the client disconnects
			try {
				pc.subscribeLoop(getInputStream(req), out, null);
			} catch (PublisherException e) {
				// Fatal error, stop publisher
				publisher.close();
				throw e;
			}
			out.close();
		} catch (RepositoryNotFoundException e) {
			sendError(req, rsp, SC_NOT_FOUND, e.getMessage());
			return;
		} catch (ServiceNotAuthorizedException e) {
			// The user needs to authenticate first (401)
			if (!rsp.isCommitted())
				rsp.sendError(SC_UNAUTHORIZED);
			return;
		} catch (ServiceNotEnabledException e) {
			// The current user does not have access to this service (403)
			sendError(req, rsp, SC_FORBIDDEN, e.getMessage());
			return;
		} catch (Throwable e) {
			getServletContext().log(
					HttpServerText.get().internalErrorDuringPublishSubscribe,
					e);
			if (!rsp.isCommitted()) {
				rsp.reset();
				sendError(req, rsp, SC_INTERNAL_SERVER_ERROR);
			}
		}
	}
}

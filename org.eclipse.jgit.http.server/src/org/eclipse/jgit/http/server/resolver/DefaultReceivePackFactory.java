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

package org.eclipse.jgit.http.server.resolver;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

/**
 * Create and configure {@link org.eclipse.jgit.transport.ReceivePack} service
 * instance.
 * <p>
 * Writing by receive-pack is permitted if any of the following is true:
 * <ul>
 * <li>The container has authenticated the user and set
 * {@link javax.servlet.http.HttpServletRequest#getRemoteUser()} to the
 * authenticated name.
 * <li>The repository configuration file has {@code http.receivepack} explicitly
 * set to true.
 * </ul>
 * and explicitly rejected otherwise.
 */
public class DefaultReceivePackFactory implements
		ReceivePackFactory<HttpServletRequest> {
	private static class ServiceConfig {
		final boolean set;

		final boolean enabled;

		ServiceConfig(final Config cfg) {
			set = cfg.getString("http", null, "receivepack") != null;
			enabled = cfg.getBoolean("http", "receivepack", false);
		}
	}

	/** {@inheritDoc} */
	@Override
	public ReceivePack create(final HttpServletRequest req, final Repository db)
			throws ServiceNotEnabledException, ServiceNotAuthorizedException {
		final ServiceConfig cfg = db.getConfig().get(ServiceConfig::new);
		String user = req.getRemoteUser();

		if (cfg.set) {
			if (cfg.enabled) {
				if (user == null || "".equals(user))
					user = "anonymous";
				return createFor(req, db, user);
			}
			throw new ServiceNotEnabledException();
		}

		if (user != null && !"".equals(user))
			return createFor(req, db, user);
		throw new ServiceNotAuthorizedException();
	}

	private static ReceivePack createFor(final HttpServletRequest req,
			final Repository db, final String user) {
		final ReceivePack rp = new ReceivePack(db);
		rp.setRefLogIdent(toPersonIdent(req, user));
		return rp;
	}

	private static PersonIdent toPersonIdent(HttpServletRequest req, String user) {
		return new PersonIdent(user, user + "@" + req.getRemoteHost());
	}
}

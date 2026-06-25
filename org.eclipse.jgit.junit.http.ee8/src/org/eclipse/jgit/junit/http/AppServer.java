/*
 * Copyright (C) 2010, 2017 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.junit.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.eclipse.jetty.ee8.nested.ServletConstraint;
import org.eclipse.jetty.ee8.security.ConstraintMapping;
import org.eclipse.jetty.ee8.security.ConstraintSecurityHandler;
import org.eclipse.jetty.ee8.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.security.AbstractLoginService;

/**
 * Tiny web application server for unit testing, wired for the EE8
 * (javax.servlet / Jetty EE8) generation.
 * <p>
 * Only the Jetty security wiring is generation specific; everything else is
 * inherited from the generated {@link AppServerBase}. This is the hand
 * maintained EE8 overlay that the canonical {@code AppServer} cannot be
 * rewritten into automatically, because the Jetty EE8 security API
 * ({@code ServletConstraint} + {@code setSecurityHandler}) differs structurally
 * from the EE10 one.
 */
public class AppServer extends AppServerBase {
	/**
	 * Constructor for <code>AppServer</code>.
	 */
	public AppServer() {
		super();
	}

	/**
	 * Constructor for <code>AppServer</code>.
	 *
	 * @param port
	 *            the http port number; may be zero to allocate a port
	 *            dynamically
	 * @since 4.2
	 */
	public AppServer(int port) {
		super(port);
	}

	/**
	 * Constructor for <code>AppServer</code>.
	 *
	 * @param port
	 *            for http, may be zero to allocate a port dynamically
	 * @param sslPort
	 *            for https,may be zero to allocate a port dynamically. If
	 *            negative, the server will be set up without https support.
	 * @since 4.9
	 */
	public AppServer(int port, int sslPort) {
		super(port, sslPort);
	}

	private ConstraintMapping createConstraintMapping() {
		ConstraintMapping cm = new ConstraintMapping();
		cm.setConstraint(new ServletConstraint());
		cm.getConstraint().setAuthenticate(true);
		cm.getConstraint().setDataConstraint(ServletConstraint.DC_NONE);
		cm.getConstraint().setRoles(new String[] { authRole });
		return cm;
	}

	@Override
	protected void configureAuthentication(ServletContextHandler ctx,
			String[] methods) {
		AbstractLoginService users = new TestMappedLoginService(authRole);
		List<ConstraintMapping> mappings = new ArrayList<>();
		if (methods == null || methods.length == 0) {
			ConstraintMapping cm = createConstraintMapping();
			cm.setPathSpec("/*");
			mappings.add(cm);
		} else {
			for (String method : methods) {
				ConstraintMapping cm = createConstraintMapping();
				cm.setMethod(method.toUpperCase(Locale.ROOT));
				cm.setPathSpec("/*");
				mappings.add(cm);
			}
		}

		ConstraintSecurityHandler sec = new ConstraintSecurityHandler();
		sec.setRealmName(realm);
		sec.setAuthenticator(new BasicAuthenticator());
		sec.setLoginService(users);
		sec.setConstraintMappings(mappings.toArray(new ConstraintMapping[0]));
		ctx.setSecurityHandler(sec);
	}
}

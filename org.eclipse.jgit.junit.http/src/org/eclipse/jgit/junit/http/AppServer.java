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

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.security.ConstraintMapping;
import org.eclipse.jetty.ee10.servlet.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.AbstractLoginService;
import org.eclipse.jetty.security.Constraint;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;

/**
 * Tiny web application server for unit testing, wired for the Jakarta servlet
 * (Jetty EE10) generation.
 * <p>
 * Only the Jetty security wiring is generation specific; everything else lives
 * in {@link AppServerBase}.
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
		Constraint constraint = new Constraint.Builder()
				.authorization(Constraint.Authorization.SPECIFIC_ROLE)
				.roles(new String[] { authRole }).build();
		cm.setConstraint(constraint);
		cm.setPathSpec("/*");
		return cm;
	}

	@Override
	protected void configureAuthentication(ServletContextHandler ctx,
			String[] methods) {
		AbstractLoginService users = new TestMappedLoginService(authRole);
		List<ConstraintMapping> mappings = new ArrayList<>();
		if (methods == null || methods.length == 0) {
			mappings.add(createConstraintMapping());
		} else {
			for (String method : methods) {
				ConstraintMapping cm = createConstraintMapping();
				cm.setMethod(method.toUpperCase(Locale.ROOT));
				mappings.add(cm);
			}
		}

		ConstraintSecurityHandler sec = new ConstraintSecurityHandler();
		sec.setRealmName(realm);
		sec.setAuthenticator(new BasicAuthenticator());
		sec.setLoginService(users);
		sec.setConstraintMappings(mappings.toArray(new ConstraintMapping[0]));
		sec.setHandler(ctx);

		contexts.removeHandler(ctx);
		contexts.addHandler(sec);
	}
}

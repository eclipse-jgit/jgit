/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.security;

/**
 * AuthenticationService service for authenticating users and getting their
 * roles.
 * 
 * @author edewit@redhat.com
 */
public interface AuthenticationService {

	User login(String username, String password);

	/**
	 * @return True iff the user is currently logged in.
	 */
	boolean isLoggedIn();

	/**
	 * Log out the currently authenticated user. Has no effect if there is no
	 * current user.
	 */
	void logout();

	User getUser();
}

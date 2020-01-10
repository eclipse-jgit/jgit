/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import java.nio.file.spi.FileSystemProvider;

import org.eclipse.jgit.niofs.internal.security.AuthenticationService;
import org.eclipse.jgit.niofs.internal.security.FileSystemAuthorization;
import org.eclipse.jgit.niofs.internal.security.PublicKeyAuthenticator;

/**
 * Specialization of {@link FileSystemProvider} for file systems that require
 * username/password authentication and support authorization of certain
 * actions.
 */
public abstract class SecuredFileSystemProvider extends FileSystemProvider {

	/**
	 * Sets the authenticator that decides which username/password pairs are valid
	 * for the file systems managed by this provider.
	 * 
	 * @param authenticator The authenticator to use. Must not be null.
	 */
	public abstract void setJAASAuthenticator(final AuthenticationService authenticator);

	public abstract void setHTTPAuthenticator(final AuthenticationService authenticator);

	public abstract void setSSHAuthenticator(final PublicKeyAuthenticator authenticator);

	public abstract void setAuthorizer(final FileSystemAuthorization authorizer);
}

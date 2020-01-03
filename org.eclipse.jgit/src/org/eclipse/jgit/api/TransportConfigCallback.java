/*
 * Copyright (C) 2011, Roberto Tyley <roberto.tyley@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.api;

import org.eclipse.jgit.transport.Transport;

/**
 * Receives a callback allowing type-specific configuration to be set
 * on the Transport instance after it's been created.
 * <p>
 * This allows consumers of the JGit command API to perform custom
 * configuration that would be difficult anticipate and expose on the
 * API command builders.
 * <p>
 * For instance, if a client needs to replace the SshSessionFactorys
 * on any SSHTransport used (eg to control available SSH identities),
 * they can set the TransportConfigCallback on the JGit API command -
 * once the transport has been created by the command, the callback
 * will be invoked and passed the transport instance, which the
 * client can then inspect and configure as necessary.
 */
public interface TransportConfigCallback {

	/**
	 * Add any additional transport-specific configuration required.
	 *
	 * @param transport
	 *            a {@link org.eclipse.jgit.transport.Transport} object.
	 */
	void configure(Transport transport);
}

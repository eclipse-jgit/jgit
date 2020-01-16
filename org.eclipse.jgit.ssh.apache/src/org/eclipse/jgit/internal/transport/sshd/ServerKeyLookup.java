/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd;

import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.List;

import org.apache.sshd.client.session.ClientSession;
import org.eclipse.jgit.annotations.NonNull;

/**
 * Offers operations to retrieve server keys from known_hosts files.
 */
public interface ServerKeyLookup {

	/**
	 * Retrieves all public keys known for a given remote.
	 *
	 * @param session
	 *            needed to determine the config files if specified in the ssh
	 *            config
	 * @param remote
	 *            to find entries for
	 * @return a possibly empty list of entries found, including revoked ones
	 */
	@NonNull
	List<PublicKey> lookup(ClientSession session, SocketAddress remote);
}

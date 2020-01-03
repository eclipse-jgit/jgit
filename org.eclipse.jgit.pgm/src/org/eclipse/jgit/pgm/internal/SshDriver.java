/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm.internal;

/**
 * Simple enumeration for the available built-in ssh clients.
 */
public enum SshDriver {

	/** Default client: use JSch. */
	JSCH,

	/** Use the Apache MINA sshd client from org.eclipse.jgit.ssh.apache. */
	APACHE;

}

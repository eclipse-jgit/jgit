/*
 * Copyright (C) 2018 Thomas Wolf <thomas.wolf@paranor.ch>
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

package org.eclipse.jgit.transport;

import org.eclipse.jgit.lib.Constants;

/**
 * Constants relating to ssh.
 *
 * @since 5.2
 */
@SuppressWarnings("nls")
public final class SshConstants {

	private SshConstants() {
		// No instances, please.
	}

	/** IANA assigned port number for ssh. */
	public static final int SSH_DEFAULT_PORT = 22;

	/** URI scheme for ssh. */
	public static final String SSH_SCHEME = "ssh";

	/** URI scheme for sftp. */
	public static final String SFTP_SCHEME = "sftp";

	/** Default name for a ssh directory. */
	public static final String SSH_DIR = ".ssh";

	/** Name of the ssh config file. */
	public static final String CONFIG = Constants.CONFIG;

	/** Default name of the user "known hosts" file. */
	public static final String KNOWN_HOSTS = "known_hosts";

	// Config file keys

	/** Key in an ssh config file. */
	public static final String BATCH_MODE = "BatchMode";

	/** Key in an ssh config file. */
	public static final String CANONICAL_DOMAINS = "CanonicalDomains";

	/** Key in an ssh config file. */
	public static final String CERTIFICATE_FILE = "CertificateFile";

	/** Key in an ssh config file. */
	public static final String CIPHERS = "Ciphers";

	/** Key in an ssh config file. */
	public static final String COMPRESSION = "Compression";

	/** Key in an ssh config file. */
	public static final String CONNECTION_ATTEMPTS = "ConnectionAttempts";

	/** Key in an ssh config file. */
	public static final String CONTROL_PATH = "ControlPath";

	/** Key in an ssh config file. */
	public static final String GLOBAL_KNOWN_HOSTS_FILE = "GlobalKnownHostsFile";

	/** Key in an ssh config file. */
	public static final String HOST = "Host";

	/** Key in an ssh config file. */
	public static final String HOST_KEY_ALGORITHMS = "HostKeyAlgorithms";

	/** Key in an ssh config file. */
	public static final String HOST_NAME = "HostName";

	/** Key in an ssh config file. */
	public static final String IDENTITIES_ONLY = "IdentitiesOnly";

	/** Key in an ssh config file. */
	public static final String IDENTITY_AGENT = "IdentityAgent";

	/** Key in an ssh config file. */
	public static final String IDENTITY_FILE = "IdentityFile";

	/** Key in an ssh config file. */
	public static final String KEX_ALGORITHMS = "KexAlgorithms";

	/** Key in an ssh config file. */
	public static final String LOCAL_COMMAND = "LocalCommand";

	/** Key in an ssh config file. */
	public static final String LOCAL_FORWARD = "LocalForward";

	/** Key in an ssh config file. */
	public static final String MACS = "MACs";

	/** Key in an ssh config file. */
	public static final String NUMBER_OF_PASSWORD_PROMPTS = "NumberOfPasswordPrompts";

	/** Key in an ssh config file. */
	public static final String PORT = "Port";

	/** Key in an ssh config file. */
	public static final String PREFERRED_AUTHENTICATIONS = "PreferredAuthentications";

	/** Key in an ssh config file. */
	public static final String PROXY_COMMAND = "ProxyCommand";

	/** Key in an ssh config file. */
	public static final String REMOTE_COMMAND = "RemoteCommand";

	/** Key in an ssh config file. */
	public static final String REMOTE_FORWARD = "RemoteForward";

	/** Key in an ssh config file. */
	public static final String SEND_ENV = "SendEnv";

	/** Key in an ssh config file. */
	public static final String STRICT_HOST_KEY_CHECKING = "StrictHostKeyChecking";

	/** Key in an ssh config file. */
	public static final String USER = "User";

	/** Key in an ssh config file. */
	public static final String USER_KNOWN_HOSTS_FILE = "UserKnownHostsFile";

	// Values

	/** Flag value. */
	public static final String YES = "yes";

	/** Flag value. */
	public static final String ON = "on";

	/** Flag value. */
	public static final String TRUE = "true";

	/** Flag value. */
	public static final String NO = "no";

	/** Flag value. */
	public static final String OFF = "off";

	/** Flag value. */
	public static final String FALSE = "false";

	// Default identity file names

	/** Name of the default RSA private identity file. */
	public static final String ID_RSA = "id_rsa";

	/** Name of the default DSA private identity file. */
	public static final String ID_DSA = "id_dsa";

	/** Name of the default ECDSA private identity file. */
	public static final String ID_ECDSA = "id_ecdsa";

	/** Name of the default ECDSA private identity file. */
	public static final String ID_ED25519 = "id_ed25519";

	/** All known default identity file names. */
	public static final String[] DEFAULT_IDENTITIES = { //
			ID_RSA, ID_DSA, ID_ECDSA // , ID_ED25519 // not yet...
	};
}

/*
 * Copyright (C) 2015, Google Inc.
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

/**
 * The required information to verify the push.
 */
public class PushCertificate {

	/** The tuple "name <email>" as presented in the push certificate */
	String pusher;

	/** The remote URL the signed push goes to */
	String pushee;

	/** What we think about the returned signed nonce */
	NonceStatus nonceStatus;

	/**
	 *
	 *
	 */
	public enum NonceStatus {
		/**
		 *
		 */
		UNSOLICITED,
		/**
		 *
		 */
		BAD,
		/**
		 *
		 */
		MISSING,
		/**
		 *
		 */
		OK,
		/**
		 *
		 */
		SLOP
	}

	/**
	 *
	 */
	String commandList;

	/**
	 *
	 */
	String signature;

	/**
	 *
	 * @return the signature, consisting of the lines received between the lines
	 *         '----BEGIN GPG SIGNATURE-----\n' and the '----END GPG
	 *         SIGNATURE-----\n'
	 */
	public String getSignature() {
		return signature;
	}

	/**
	 * @return the list of commands as one string to be feed into the signature
	 *         verifier.
	 */
	public String getCommandList() {
		return commandList;
	}

	/**
	 * @return the pushedCertPusher
	 */
	public String getPusher() {
		return pusher;
	}

	/**
	 * @return the pushedCertPushee
	 */
	public String getPushee() {
		return pushee;
	}

	/**
	 * @return the pushCertNonceStatus
	 */
	public NonceStatus getNonceStatus() {
		return nonceStatus;
	}
}

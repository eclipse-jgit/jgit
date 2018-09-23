/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch>
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
package org.eclipse.jgit.internal.transport.sshd;

import java.io.IOException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

import org.apache.sshd.client.auth.AbstractUserAuth;
import org.apache.sshd.client.auth.pubkey.PublicKeyIdentity;
import org.apache.sshd.client.auth.pubkey.UserAuthPublicKey;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.signature.Signature;
import org.apache.sshd.common.signature.SignatureFactoriesManager;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;

/**
 * A replacement for
 * {@link org.apache.sshd.client.auth.pubkey.UserAuthPublicKey} that uses our
 * own {@link JGitPublicKeyIterator}. Unfortunately we cannot simply subclass
 * because we'd have no way to invoke
 * {@link AbstractUserAuth#init(ClientSession, String)} directly, bypassing the
 * version in {@code UserAuthPublicKey}. But we must do so, because that
 * constructs a
 * {@link org.apache.sshd.client.auth.pubkey.UserAuthPublicKeyIterator}, which
 * in its constructor does some strange {@link java.util.stream.Stream} "magic"
 * that ends up loading keys prematurely.
 */
public class JGitPublicKeyAuthentication extends AbstractUserAuth
		implements SignatureFactoriesManager {

	private JGitPublicKeyIterator keys;

	private PublicKeyIdentity current;

	private List<NamedFactory<Signature>> factories;

	/**
	 * Creates a new {@link JGitPublicKeyAuthentication}.
	 *
	 * @param factories
	 *            signature factories to use
	 */
	public JGitPublicKeyAuthentication(
			List<NamedFactory<Signature>> factories) {
		super(UserAuthPublicKey.NAME);
		this.factories = factories;
	}

	@Override
	public void init(ClientSession session, String service) throws Exception {
		super.init(session, service);
		releaseKeys();
		// The following line is the only real change from UserAuthPublicKey!
		keys = new JGitPublicKeyIterator(session, this);
	}

	@Override
	protected boolean sendAuthDataRequest(ClientSession session, String service)
			throws Exception {
		if (keys == null || !keys.hasNext()) {
			return false;
		}
		current = keys.next();
		PublicKey key = current.getPublicKey();
		Buffer buffer = session
				.createBuffer(SshConstants.SSH_MSG_USERAUTH_REQUEST);
		buffer.putString(session.getUsername());
		buffer.putString(service);
		buffer.putString(getName());
		buffer.putBoolean(false);
		buffer.putString(KeyUtils.getKeyType(key));
		buffer.putPublicKey(key);
		session.writePacket(buffer);
		return true;
	}

	@Override
	protected boolean processAuthDataRequest(ClientSession session,
			String service, Buffer buffer) throws Exception {
		String name = getName();
		int cmd = buffer.getUByte();
		if (cmd != SshConstants.SSH_MSG_USERAUTH_PK_OK) {
			throw new IllegalStateException(
					"processAuthDataRequest(" + session + ")[" + service + "][" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							+ name + "]" + " received unknown packet: cmd=" //$NON-NLS-1$ //$NON-NLS-2$
							+ SshConstants.getCommandMessageName(cmd));
		}
		PublicKey key = current.getPublicKey();
		String algorithm = KeyUtils.getKeyType(key);
		String responseKeyType = buffer.getString();
		if (!responseKeyType.equals(algorithm)) {
			throw new InvalidKeySpecException(
					"processAuthDataRequest(" + session + ")[" + service + "][" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							+ name + "]" + " mismatched key types: expected=" //$NON-NLS-1$ //$NON-NLS-2$
							+ algorithm + ", actual=" + responseKeyType); //$NON-NLS-1$
		}
		PublicKey responseKey = buffer.getPublicKey();
		if (!KeyUtils.compareKeys(responseKey, key)) {
			throw new InvalidKeySpecException("processAuthDataRequest(" //$NON-NLS-1$
					+ session + ")[" + service + "][" + name + "]" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					+ " mismatched " + algorithm + " keys: expected=" //$NON-NLS-1$ //$NON-NLS-2$
					+ KeyUtils.getFingerPrint(key) + ", actual=" //$NON-NLS-1$
					+ KeyUtils.getFingerPrint(responseKey));
		}
		String username = session.getUsername();
		Buffer out = session
				.createBuffer(SshConstants.SSH_MSG_USERAUTH_REQUEST);
		out.putString(username);
		out.putString(service);
		out.putString(name);
		out.putBoolean(true);
		out.putString(algorithm);
		out.putPublicKey(key);
		appendSignature(session, service, name, username, algorithm, key, out);
		session.writePacket(out);
		return true;
	}

	private void appendSignature(ClientSession session, String service,
			String name, String username, String algorithm, PublicKey key,
			Buffer buffer) throws Exception {
		byte[] id = session.getSessionId();
		Buffer bs = new ByteArrayBuffer(id.length + username.length()
				+ service.length() + name.length() + algorithm.length()
				+ ByteArrayBuffer.DEFAULT_SIZE + Long.SIZE, false);
		bs.putBytes(id);
		bs.putByte(SshConstants.SSH_MSG_USERAUTH_REQUEST);
		bs.putString(username);
		bs.putString(service);
		bs.putString(name);
		bs.putBoolean(true);
		bs.putString(algorithm);
		bs.putPublicKey(key);

		byte[] contents = bs.getCompactData();
		byte[] sig = current.sign(contents);

		bs.clear();
		bs.putString(algorithm);
		bs.putBytes(sig);
		buffer.putBytes(bs.array(), bs.rpos(), bs.available());
	}

	@Override
	public List<NamedFactory<Signature>> getSignatureFactories() {
		return factories;
	}

	@Override
	public void setSignatureFactories(List<NamedFactory<Signature>> factories) {
		this.factories = factories;
	}

	@Override
	public void destroy() {
		try {
			releaseKeys();
		} catch (IOException e) {
			throw new RuntimeException(
					"Failed to close agent: " + e.getMessage(), e); //$NON-NLS-1$
		} finally {
			super.destroy();
		}
	}

	private void releaseKeys() throws IOException {
		if (keys == null) {
			return;
		}
		try {
			keys.close();
		} finally {
			keys = null;
		}
	}

}

/*
 * Copyright (C) 2026, Nataphon Chiwattanakul <nataphon@ktsystems.cc> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.lang.reflect.Proxy;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collections;
import java.util.Map;

import org.apache.sshd.client.auth.pubkey.PublicKeyIdentity;
import org.apache.sshd.client.auth.pubkey.UserAuthPublicKey;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.auth.AbstractUserAuthServiceFactory;
import org.apache.sshd.common.config.keys.u2f.SkEcdsaPublicKey;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.junit.Test;

public class JGitPublicKeyAuthenticationTest {

	@Test
	public void appendSignatureDoesNotWrapSecurityKeySignatureBlob()
			throws Exception {
		KeyPairGenerator generator = SecurityUtils.getKeyPairGenerator("EC");
		generator.initialize(256);
		ECPublicKey key = (ECPublicKey) generator.generateKeyPair()
				.getPublic();
		SkEcdsaPublicKey sk = new SkEcdsaPublicKey("ssh", false, false, key);

		byte[] rawSignature = { 1, 2, 3, 4, 5 };
		byte flags = 1;
		long counter = 42;
		byte[] signatureBlob = createSkSignature(sk, rawSignature, flags,
				counter);

		TestPublicKeyAuthentication auth = new TestPublicKeyAuthentication(
				new StaticSignatureIdentity(sk, signatureBlob));
		byte[] actual = auth.appendSignature(session(new byte[] { 9, 8, 7, 6 }),
				AbstractUserAuthServiceFactory.DEFAULT_NAME,
				UserAuthPublicKey.NAME, "testuser", sk.getKeyType(), sk);

		assertSkSignature(sk, rawSignature, flags, counter, actual);
	}

	private static ClientSession session(byte[] sessionId) {
		return (ClientSession) Proxy.newProxyInstance(
				ClientSession.class.getClassLoader(),
				new Class<?>[] { ClientSession.class }, (proxy, method, args) -> {
					switch (method.getName()) {
					case "getSessionId": //$NON-NLS-1$
						return sessionId.clone();
					case "toString": //$NON-NLS-1$
						return "test-session"; //$NON-NLS-1$
					case "hashCode": //$NON-NLS-1$
						return Integer.valueOf(System.identityHashCode(proxy));
					case "equals": //$NON-NLS-1$
						return Boolean.valueOf(proxy == args[0]);
					default:
						throw new UnsupportedOperationException(
								method.toString());
					}
				});
	}

	private static byte[] createSkSignature(SkEcdsaPublicKey key,
			byte[] rawSignature, byte flags, long counter) {
		ByteArrayBuffer signature = new ByteArrayBuffer();
		signature.putString(key.getKeyType());
		signature.putBytes(rawSignature);
		signature.putByte(flags);
		signature.putUInt(counter);
		return signature.getCompactData();
	}

	private static void assertSkSignature(SkEcdsaPublicKey key,
			byte[] rawSignature, byte flags, long counter, byte[] signature) {
		ByteArrayBuffer buffer = new ByteArrayBuffer(signature);
		assertEquals(key.getKeyType(), buffer.getString());
		assertArrayEquals(rawSignature, buffer.getBytes());
		assertEquals(flags, buffer.getByte());
		assertEquals(counter, buffer.getUInt());
		assertEquals(0, buffer.available());
	}

	private static class TestPublicKeyAuthentication
			extends JGitPublicKeyAuthentication {

		TestPublicKeyAuthentication(PublicKeyIdentity identity) {
			super(Collections.emptyList());
			current = identity;
		}

		byte[] appendSignature(ClientSession session, String service,
				String name, String username, String algo, SkEcdsaPublicKey key)
				throws Exception {
			ByteArrayBuffer buffer = new ByteArrayBuffer();
			super.appendSignature(session, service, name, username, algo, key,
					null, buffer);
			return buffer.getBytes();
		}
	}

	private static class StaticSignatureIdentity implements PublicKeyIdentity {

		private final SkEcdsaPublicKey key;

		private final byte[] signature;

		StaticSignatureIdentity(SkEcdsaPublicKey key, byte[] signature) {
			this.key = key;
			this.signature = signature.clone();
		}

		@Override
		public KeyPair getKeyIdentity() {
			return new KeyPair(key, null);
		}

		@Override
		public Map.Entry<String, byte[]> sign(SessionContext session,
				String algo, byte[] data) {
			return new SimpleImmutableEntry<>(key.getKeyType(),
					signature.clone());
		}
	}
}

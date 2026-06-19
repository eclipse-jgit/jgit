/*
 * Copyright (C) 2026, Nataphon Chiwattanakul <nataphon@ktsystems.cc> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd.agent;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.util.Map;

import org.apache.sshd.agent.SshAgentConstants;
import org.apache.sshd.common.config.keys.u2f.SkEcdsaPublicKey;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.eclipse.jgit.transport.sshd.agent.Connector;
import org.junit.Test;

public class SshAgentClientTest {

	@Test
	public void signPreservesSecurityKeySignatureBlob() throws Exception {
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

		try (SshAgentClient client = new SshAgentClient(
				new StaticSignatureConnector(signatureBlob))) {
			Map.Entry<String, byte[]> result = client.sign(null, sk,
					sk.getKeyType(), new byte[] { 'd', 'a', 't', 'a' });

			assertEquals(sk.getKeyType(), result.getKey());
			assertSkSignature(sk, rawSignature, flags, counter,
					result.getValue());
		}
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

	private static byte[] createAgentResponse(byte[] signatureBlob) {
		ByteArrayBuffer response = new ByteArrayBuffer();
		response.putByte(SshAgentConstants.SSH2_AGENT_SIGN_RESPONSE);
		response.putBytes(signatureBlob);
		return response.getCompactData();
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

	private static class StaticSignatureConnector implements Connector {

		private final byte[] signatureBlob;

		StaticSignatureConnector(byte[] signatureBlob) {
			this.signatureBlob = signatureBlob.clone();
		}

		@Override
		public boolean connect() throws IOException {
			return true;
		}

		@Override
		public byte[] rpc(byte command, byte[] message) throws IOException {
			assertEquals(SshAgentConstants.SSH2_AGENTC_SIGN_REQUEST, command);
			return createAgentResponse(signatureBlob);
		}

		@Override
		public void close() throws IOException {
			// Nothing to close.
		}
	}
}

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
package org.eclipse.jgit.internal.transport.sshd.proxy;

import static java.text.MessageFormat.format;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.util.Readable;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.BufferUtils;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.transport.sshd.GssApiMechanisms;
import org.eclipse.jgit.internal.transport.sshd.SshdText;
import org.eclipse.jgit.internal.transport.sshd.auth.AuthenticationHandler;
import org.eclipse.jgit.internal.transport.sshd.auth.BasicAuthentication;
import org.eclipse.jgit.internal.transport.sshd.auth.GssApiAuthentication;
import org.eclipse.jgit.transport.SshConstants;
import org.ietf.jgss.GSSContext;

/**
 * A {@link AbstractClientProxyConnector} to connect through a SOCKS5 proxy.
 *
 * @see <a href="https://tools.ietf.org/html/rfc1928">RFC 1928</a>
 */
public class Socks5ClientConnector extends AbstractClientProxyConnector {

	// private static final byte SOCKS_VERSION_4 = 4;
	private static final byte SOCKS_VERSION_5 = 5;

	private static final byte SOCKS_CMD_CONNECT = 1;
	// private static final byte SOCKS5_CMD_BIND = 2;
	// private static final byte SOCKS5_CMD_UDP_ASSOCIATE = 3;

	// Address types

	private static final byte SOCKS_ADDRESS_IPv4 = 1;

	private static final byte SOCKS_ADDRESS_FQDN = 3;

	private static final byte SOCKS_ADDRESS_IPv6 = 4;

	// Reply codes

	private static final byte SOCKS_REPLY_SUCCESS = 0;

	private static final byte SOCKS_REPLY_FAILURE = 1;

	private static final byte SOCKS_REPLY_FORBIDDEN = 2;

	private static final byte SOCKS_REPLY_NETWORK_UNREACHABLE = 3;

	private static final byte SOCKS_REPLY_HOST_UNREACHABLE = 4;

	private static final byte SOCKS_REPLY_CONNECTION_REFUSED = 5;

	private static final byte SOCKS_REPLY_TTL_EXPIRED = 6;

	private static final byte SOCKS_REPLY_COMMAND_UNSUPPORTED = 7;

	private static final byte SOCKS_REPLY_ADDRESS_UNSUPPORTED = 8;

	/**
	 * Authentication methods for SOCKS5.
	 *
	 * @see <a href=
	 *      "https://www.iana.org/assignments/socks-methods/socks-methods.xhtml">SOCKS
	 *      Methods, IANA.org</a>
	 */
	private enum SocksAuthenticationMethod {

		ANONYMOUS(0),
		GSSAPI(1),
		PASSWORD(2),
		// CHALLENGE_HANDSHAKE(3),
		// CHALLENGE_RESPONSE(5),
		// SSL(6),
		// NDS(7),
		// MULTI_AUTH(8),
		// JSON(9),
		NONE_ACCEPTABLE(0xFF);

		private byte value;

		SocksAuthenticationMethod(int value) {
			this.value = (byte) value;
		}

		public byte getValue() {
			return value;
		}
	}

	private enum ProtocolState {
		NONE,

		INIT {
			@Override
			public void handleMessage(Socks5ClientConnector connector,
					IoSession session, Buffer data) throws Exception {
				connector.versionCheck(data.getByte());
				SocksAuthenticationMethod authMethod = connector.getAuthMethod(
						data.getByte());
				switch (authMethod) {
				case ANONYMOUS:
					connector.sendConnectInfo(session);
					break;
				case PASSWORD:
					connector.doPasswordAuth(session);
					break;
				case GSSAPI:
					connector.doGssApiAuth(session);
					break;
				default:
					throw new IOException(
							format(SshdText.get().proxyCannotAuthenticate,
									connector.proxyAddress));
				}
			}
		},

		AUTHENTICATING {
			@Override
			public void handleMessage(Socks5ClientConnector connector,
					IoSession session, Buffer data) throws Exception {
				connector.authStep(session, data);
			}
		},

		CONNECTING {
			@Override
			public void handleMessage(Socks5ClientConnector connector,
					IoSession session, Buffer data) throws Exception {
				// Special case: when GSS-API authentication completes, the
				// client moves into CONNECTING as soon as the GSS context is
				// established and sends the connect request. This is per RFC
				// 1961. But for the server, RFC 1961 says it _should_ send an
				// empty token even if none generated when its server side
				// context is established. That means we may actually get an
				// empty token here. That message is 4 bytes long (and has
				// content 0x01, 0x01, 0x00, 0x00). We simply skip this message
				// if we get it here. If the server for whatever reason sends
				// back a "GSS failed" message (it shouldn't, at this point)
				// it will be two bytes 0x01 0xFF, which will fail the version
				// check.
				if (data.available() != 4) {
					connector.versionCheck(data.getByte());
					connector.establishConnection(data);
				}
			}
		},

		CONNECTED,

		FAILED;

		public void handleMessage(Socks5ClientConnector connector,
				@SuppressWarnings("unused") IoSession session, Buffer data)
				throws Exception {
			throw new IOException(
					format(SshdText.get().proxySocksUnexpectedMessage,
							connector.proxyAddress, this,
							BufferUtils.toHex(data.array())));
		}
	}

	private ProtocolState state;

	private AuthenticationHandler<Buffer, Buffer> authenticator;

	private GSSContext context;

	private byte[] authenticationProposals;

	/**
	 * Creates a new {@link Socks5ClientConnector}. The connector supports
	 * anonymous connections as well as username-password or Kerberos5 (GSS-API)
	 * authentication.
	 *
	 * @param proxyAddress
	 *            of the proxy server we're connecting to
	 * @param remoteAddress
	 *            of the target server to connect to
	 */
	public Socks5ClientConnector(@NonNull InetSocketAddress proxyAddress,
			@NonNull InetSocketAddress remoteAddress) {
		this(proxyAddress, remoteAddress, null, null);
	}

	/**
	 * Creates a new {@link Socks5ClientConnector}. The connector supports
	 * anonymous connections as well as username-password or Kerberos5 (GSS-API)
	 * authentication.
	 *
	 * @param proxyAddress
	 *            of the proxy server we're connecting to
	 * @param remoteAddress
	 *            of the target server to connect to
	 * @param proxyUser
	 *            to authenticate at the proxy with
	 * @param proxyPassword
	 *            to authenticate at the proxy with
	 */
	public Socks5ClientConnector(@NonNull InetSocketAddress proxyAddress,
			@NonNull InetSocketAddress remoteAddress,
			String proxyUser, char[] proxyPassword) {
		super(proxyAddress, remoteAddress, proxyUser, proxyPassword);
		this.state = ProtocolState.NONE;
	}

	@Override
	public void sendClientProxyMetadata(ClientSession sshSession)
			throws Exception {
		init(sshSession);
		IoSession session = sshSession.getIoSession();
		// Send the initial request
		Buffer buffer = new ByteArrayBuffer(5, false);
		buffer.putByte(SOCKS_VERSION_5);
		context = getGSSContext(remoteAddress);
		authenticationProposals = getAuthenticationProposals();
		buffer.putByte((byte) authenticationProposals.length);
		buffer.putRawBytes(authenticationProposals);
		state = ProtocolState.INIT;
		session.writePacket(buffer).verify(getTimeout());
	}

	private byte[] getAuthenticationProposals() {
		byte[] proposals = new byte[3];
		int i = 0;
		proposals[i++] = SocksAuthenticationMethod.ANONYMOUS.getValue();
		proposals[i++] = SocksAuthenticationMethod.PASSWORD.getValue();
		if (context != null) {
			proposals[i++] = SocksAuthenticationMethod.GSSAPI.getValue();
		}
		if (i == proposals.length) {
			return proposals;
		} else {
			byte[] result = new byte[i];
			System.arraycopy(proposals, 0, result, 0, i);
			return result;
		}
	}

	private void sendConnectInfo(IoSession session) throws Exception {
		GssApiMechanisms.closeContextSilently(context);

		byte[] rawAddress = getRawAddress(remoteAddress);
		byte[] remoteName = null;
		byte type;
		int length = 0;
		if (rawAddress == null) {
			remoteName = remoteAddress.getHostString()
					.getBytes(StandardCharsets.US_ASCII);
			if (remoteName == null || remoteName.length == 0) {
				throw new IOException(
						format(SshdText.get().proxySocksNoRemoteHostName,
								remoteAddress));
			} else if (remoteName.length > 255) {
				// Should not occur; host names must not be longer than 255
				// US_ASCII characters. Internal error, no translation.
				throw new IOException(format(
						"Proxy host name too long for SOCKS (at most 255 characters): {0}", //$NON-NLS-1$
						remoteAddress.getHostString()));
			}
			type = SOCKS_ADDRESS_FQDN;
			length = remoteName.length + 1;
		} else {
			length = rawAddress.length;
			type = length == 4 ? SOCKS_ADDRESS_IPv4 : SOCKS_ADDRESS_IPv6;
		}
		Buffer buffer = new ByteArrayBuffer(4 + length + 2, false);
		buffer.putByte(SOCKS_VERSION_5);
		buffer.putByte(SOCKS_CMD_CONNECT);
		buffer.putByte((byte) 0); // Reserved
		buffer.putByte(type);
		if (remoteName != null) {
			buffer.putByte((byte) remoteName.length);
			buffer.putRawBytes(remoteName);
		} else {
			buffer.putRawBytes(rawAddress);
		}
		int port = remoteAddress.getPort();
		if (port <= 0) {
			port = SshConstants.SSH_DEFAULT_PORT;
		}
		buffer.putByte((byte) ((port >> 8) & 0xFF));
		buffer.putByte((byte) (port & 0xFF));
		state = ProtocolState.CONNECTING;
		session.writePacket(buffer).verify(getTimeout());
	}

	private void doPasswordAuth(IoSession session) throws Exception {
		GssApiMechanisms.closeContextSilently(context);
		authenticator = new SocksBasicAuthentication();
		session.addCloseFutureListener(f -> close());
		startAuth(session);
	}

	private void doGssApiAuth(IoSession session) throws Exception {
		authenticator = new SocksGssApiAuthentication();
		session.addCloseFutureListener(f -> close());
		startAuth(session);
	}

	private void close() {
		AuthenticationHandler<?, ?> handler = authenticator;
		authenticator = null;
		if (handler != null) {
			handler.close();
		}
	}

	private void startAuth(IoSession session) throws Exception {
		Buffer buffer = null;
		try {
			authenticator.setParams(null);
			authenticator.start();
			buffer = authenticator.getToken();
			state = ProtocolState.AUTHENTICATING;
			if (buffer == null) {
				// Internal error; no translation
				throw new IOException(
						"No data for proxy authentication with " //$NON-NLS-1$
								+ proxyAddress);
			}
			session.writePacket(buffer).verify(getTimeout());
		} finally {
			if (buffer != null) {
				buffer.clear(true);
			}
		}
	}

	private void authStep(IoSession session, Buffer input) throws Exception {
		Buffer buffer = null;
		try {
			authenticator.setParams(input);
			authenticator.process();
			buffer = authenticator.getToken();
			if (buffer != null) {
				session.writePacket(buffer).verify(getTimeout());
			}
		} finally {
			if (buffer != null) {
				buffer.clear(true);
			}
		}
		if (authenticator.isDone()) {
			sendConnectInfo(session);
		}
	}

	private void establishConnection(Buffer data) throws Exception {
		byte reply = data.getByte();
		switch (reply) {
		case SOCKS_REPLY_SUCCESS:
			state = ProtocolState.CONNECTED;
			setDone(true);
			return;
		case SOCKS_REPLY_FAILURE:
			throw new IOException(format(
					SshdText.get().proxySocksFailureGeneral, proxyAddress));
		case SOCKS_REPLY_FORBIDDEN:
			throw new IOException(
					format(SshdText.get().proxySocksFailureForbidden,
							proxyAddress, remoteAddress));
		case SOCKS_REPLY_NETWORK_UNREACHABLE:
			throw new IOException(
					format(SshdText.get().proxySocksFailureNetworkUnreachable,
							proxyAddress, remoteAddress));
		case SOCKS_REPLY_HOST_UNREACHABLE:
			throw new IOException(
					format(SshdText.get().proxySocksFailureHostUnreachable,
							proxyAddress, remoteAddress));
		case SOCKS_REPLY_CONNECTION_REFUSED:
			throw new IOException(
					format(SshdText.get().proxySocksFailureRefused,
							proxyAddress, remoteAddress));
		case SOCKS_REPLY_TTL_EXPIRED:
			throw new IOException(
					format(SshdText.get().proxySocksFailureTTL, proxyAddress));
		case SOCKS_REPLY_COMMAND_UNSUPPORTED:
			throw new IOException(
					format(SshdText.get().proxySocksFailureUnsupportedCommand,
							proxyAddress));
		case SOCKS_REPLY_ADDRESS_UNSUPPORTED:
			throw new IOException(
					format(SshdText.get().proxySocksFailureUnsupportedAddress,
							proxyAddress));
		default:
			throw new IOException(format(
					SshdText.get().proxySocksFailureUnspecified, proxyAddress));
		}
	}

	@Override
	public void messageReceived(IoSession session, Readable buffer)
			throws Exception {
		try {
			// Dispatch according to protocol state
			ByteArrayBuffer data = new ByteArrayBuffer(buffer.available(),
					false);
			data.putBuffer(buffer);
			data.compact();
			state.handleMessage(this, session, data);
		} catch (Exception e) {
			state = ProtocolState.FAILED;
			if (authenticator != null) {
				authenticator.close();
				authenticator = null;
			}
			try {
				setDone(false);
			} catch (Exception inner) {
				e.addSuppressed(inner);
			}
			throw e;
		}
	}

	private void versionCheck(byte version) throws Exception {
		if (version != SOCKS_VERSION_5) {
			throw new IOException(
					format(SshdText.get().proxySocksUnexpectedVersion,
							Integer.toString(version & 0xFF)));
		}
	}

	private SocksAuthenticationMethod getAuthMethod(byte value) {
		if (value != SocksAuthenticationMethod.NONE_ACCEPTABLE.getValue()) {
			for (byte proposed : authenticationProposals) {
				if (proposed == value) {
					for (SocksAuthenticationMethod method : SocksAuthenticationMethod
							.values()) {
						if (method.getValue() == value) {
							return method;
						}
					}
					break;
				}
			}
		}
		return SocksAuthenticationMethod.NONE_ACCEPTABLE;
	}

	private static byte[] getRawAddress(@NonNull InetSocketAddress address) {
		InetAddress ipAddress = GssApiMechanisms.resolve(address);
		return ipAddress == null ? null : ipAddress.getAddress();
	}

	private static GSSContext getGSSContext(
			@NonNull InetSocketAddress address) {
		if (!GssApiMechanisms.getSupportedMechanisms()
				.contains(GssApiMechanisms.KERBEROS_5)) {
			return null;
		}
		return GssApiMechanisms.createContext(GssApiMechanisms.KERBEROS_5,
				GssApiMechanisms.getCanonicalName(address));
	}

	/**
	 * @see <a href="https://tools.ietf.org/html/rfc1929">RFC 1929</a>
	 */
	private class SocksBasicAuthentication
			extends BasicAuthentication<Buffer, Buffer> {

		private static final byte SOCKS_BASIC_PROTOCOL_VERSION = 1;

		private static final byte SOCKS_BASIC_AUTH_SUCCESS = 0;

		public SocksBasicAuthentication() {
			super(proxyAddress, proxyUser, proxyPassword);
		}

		@Override
		public void process() throws Exception {
			// Retries impossible. RFC 1929 specifies that the server MUST
			// close the connection if authentication is unsuccessful.
			done = true;
			if (params.getByte() != SOCKS_BASIC_PROTOCOL_VERSION
					|| params.getByte() != SOCKS_BASIC_AUTH_SUCCESS) {
				throw new IOException(format(
						SshdText.get().proxySocksAuthenticationFailed, proxy));
			}
		}

		@Override
		protected void askCredentials() {
			super.askCredentials();
			adjustTimeout();
		}

		@Override
		public Buffer getToken() throws IOException {
			if (done) {
				return null;
			}
			try {
				byte[] rawUser = user.getBytes(StandardCharsets.UTF_8);
				if (rawUser.length > 255) {
					throw new IOException(format(
							SshdText.get().proxySocksUsernameTooLong, proxy,
							Integer.toString(rawUser.length), user));
				}

				if (password.length > 255) {
					throw new IOException(
							format(SshdText.get().proxySocksPasswordTooLong,
									proxy, Integer.toString(password.length)));
				}
				ByteArrayBuffer buffer = new ByteArrayBuffer(
						3 + rawUser.length + password.length, false);
				buffer.putByte(SOCKS_BASIC_PROTOCOL_VERSION);
				buffer.putByte((byte) rawUser.length);
				buffer.putRawBytes(rawUser);
				buffer.putByte((byte) password.length);
				buffer.putRawBytes(password);
				return buffer;
			} finally {
				clearPassword();
				done = true;
			}
		}
	}

	/**
	 * @see <a href="https://tools.ietf.org/html/rfc1961">RFC 1961</a>
	 */
	private class SocksGssApiAuthentication
			extends GssApiAuthentication<Buffer, Buffer> {

		private static final byte SOCKS5_GSSAPI_VERSION = 1;

		private static final byte SOCKS5_GSSAPI_TOKEN = 1;

		private static final int SOCKS5_GSSAPI_FAILURE = 0xFF;

		public SocksGssApiAuthentication() {
			super(proxyAddress);
		}

		@Override
		protected GSSContext createContext() throws Exception {
			return context;
		}

		@Override
		public Buffer getToken() throws Exception {
			if (token == null) {
				return null;
			}
			Buffer buffer = new ByteArrayBuffer(4 + token.length, false);
			buffer.putByte(SOCKS5_GSSAPI_VERSION);
			buffer.putByte(SOCKS5_GSSAPI_TOKEN);
			buffer.putByte((byte) ((token.length >> 8) & 0xFF));
			buffer.putByte((byte) (token.length & 0xFF));
			buffer.putRawBytes(token);
			return buffer;
		}

		@Override
		protected byte[] extractToken(Buffer input) throws Exception {
			if (context == null) {
				return null;
			}
			int version = input.getUByte();
			if (version != SOCKS5_GSSAPI_VERSION) {
				throw new IOException(
						format(SshdText.get().proxySocksGssApiVersionMismatch,
								remoteAddress, Integer.toString(version)));
			}
			int msgType = input.getUByte();
			if (msgType == SOCKS5_GSSAPI_FAILURE) {
				throw new IOException(format(
						SshdText.get().proxySocksGssApiFailure, remoteAddress));
			} else if (msgType != SOCKS5_GSSAPI_TOKEN) {
				throw new IOException(format(
						SshdText.get().proxySocksGssApiUnknownMessage,
						remoteAddress, Integer.toHexString(msgType & 0xFF)));
			}
			if (input.available() >= 2) {
				int length = (input.getUByte() << 8) + input.getUByte();
				if (input.available() >= length) {
					byte[] value = new byte[length];
					if (length > 0) {
						input.getRawBytes(value);
					}
					return value;
				}
			}
			throw new IOException(
					format(SshdText.get().proxySocksGssApiMessageTooShort,
							remoteAddress));
		}
	}
}

/*
 * Copyright (C) 2018, 2022 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd;

import static java.text.MessageFormat.format;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.sshd.client.auth.AbstractUserAuth;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.MessageProp;
import org.ietf.jgss.Oid;

/**
 * GSSAPI-with-MIC authentication handler (Kerberos 5).
 *
 * @see <a href="https://tools.ietf.org/html/rfc4462">RFC 4462</a>
 */
public class GssApiWithMicAuthentication extends AbstractUserAuth {

	/** Synonym used in RFC 4462. */
	private static final byte SSH_MSG_USERAUTH_GSSAPI_RESPONSE = SshConstants.SSH_MSG_USERAUTH_INFO_REQUEST;

	/** Synonym used in RFC 4462. */
	private static final byte SSH_MSG_USERAUTH_GSSAPI_TOKEN = SshConstants.SSH_MSG_USERAUTH_INFO_RESPONSE;

	private enum ProtocolState {
		STARTED, TOKENS, MIC_SENT, FAILED
	}

	private Collection<Oid> mechanisms;

	private Iterator<Oid> nextMechanism;

	private Oid currentMechanism;

	private ProtocolState state;

	private GSSContext context;

	/** Creates a new {@link GssApiWithMicAuthentication}. */
	public GssApiWithMicAuthentication() {
		super(GssApiWithMicAuthFactory.NAME);
	}

	@Override
	protected boolean sendAuthDataRequest(ClientSession session, String service)
			throws Exception {
		if (mechanisms == null) {
			mechanisms = GssApiMechanisms.getSupportedMechanisms();
			nextMechanism = mechanisms.iterator();
		}
		if (context != null) {
			close(false);
		}
		GssApiWithMicAuthenticationReporter reporter = session.getAttribute(
				GssApiWithMicAuthenticationReporter.GSS_AUTHENTICATION_REPORTER);
		if (!nextMechanism.hasNext()) {
			reporter.signalAuthenticationExhausted(session, service);
			return false;
		}
		state = ProtocolState.STARTED;
		currentMechanism = nextMechanism.next();
		// RFC 4462 states that SPNEGO must not be used with ssh
		while (GssApiMechanisms.SPNEGO.equals(currentMechanism)) {
			if (!nextMechanism.hasNext()) {
				reporter.signalAuthenticationExhausted(session, service);
				return false;
			}
			currentMechanism = nextMechanism.next();
		}
		try {
			String hostName = getHostName(session);
			context = GssApiMechanisms.createContext(currentMechanism,
					hostName);
			context.requestMutualAuth(true);
			context.requestConf(true);
			context.requestInteg(true);
			context.requestCredDeleg(true);
			context.requestAnonymity(false);
		} catch (GSSException | NullPointerException e) {
			close(true);
			if (log.isDebugEnabled()) {
				log.debug(format(SshdText.get().gssapiInitFailure,
						currentMechanism.toString()));
			}
			currentMechanism = null;
			state = ProtocolState.FAILED;
			return false;
		}
		if (reporter != null) {
			reporter.signalAuthenticationAttempt(session, service,
					currentMechanism.toString());
		}
		Buffer buffer = session
				.createBuffer(SshConstants.SSH_MSG_USERAUTH_REQUEST);
		buffer.putString(session.getUsername());
		buffer.putString(service);
		buffer.putString(getName());
		buffer.putInt(1);
		buffer.putBytes(currentMechanism.getDER());
		session.writePacket(buffer);
		return true;
	}

	@Override
	protected boolean processAuthDataRequest(ClientSession session,
			String service, Buffer in) throws Exception {
		// SSH_MSG_USERAUTH_FAILURE and SSH_MSG_USERAUTH_SUCCESS, as well as
		// SSH_MSG_USERAUTH_BANNER are handled by the framework.
		int command = in.getUByte();
		if (context == null) {
			return false;
		}
		try {
			switch (command) {
			case SSH_MSG_USERAUTH_GSSAPI_RESPONSE: {
				if (state != ProtocolState.STARTED) {
					return unexpectedMessage(command);
				}
				// Initial reply from the server with the mechanism to use.
				Oid mechanism = new Oid(in.getBytes());
				if (!currentMechanism.equals(mechanism)) {
					return false;
				}
				replyToken(session, service, new byte[0]);
				return true;
			}
			case SSH_MSG_USERAUTH_GSSAPI_TOKEN: {
				if (context.isEstablished() || state != ProtocolState.TOKENS) {
					return unexpectedMessage(command);
				}
				// Server sent us a token
				replyToken(session, service, in.getBytes());
				return true;
			}
			default:
				return unexpectedMessage(command);
			}
		} catch (GSSException e) {
			log.warn(format(SshdText.get().gssapiFailure,
					currentMechanism.toString()), e);
			state = ProtocolState.FAILED;
			return false;
		}
	}

	@Override
	public void destroy() {
		try {
			close(false);
		} finally {
			super.destroy();
		}
	}

	private void close(boolean silent) {
		try {
			if (context != null) {
				context.dispose();
				context = null;
			}
		} catch (GSSException e) {
			if (!silent) {
				log.warn(SshdText.get().gssapiFailure, e);
			}
		}
	}

	private void sendToken(ClientSession session, byte[] receivedToken)
			throws IOException, GSSException {
		state = ProtocolState.TOKENS;
		byte[] token = context.initSecContext(receivedToken, 0,
				receivedToken.length);
		if (token != null) {
			Buffer buffer = session.createBuffer(SSH_MSG_USERAUTH_GSSAPI_TOKEN);
			buffer.putBytes(token);
			session.writePacket(buffer);
		}
	}

	private void sendMic(ClientSession session, String service)
			throws IOException, GSSException {
		state = ProtocolState.MIC_SENT;
		// Produce MIC
		Buffer micBuffer = new ByteArrayBuffer();
		micBuffer.putBytes(session.getSessionId());
		micBuffer.putByte(SshConstants.SSH_MSG_USERAUTH_REQUEST);
		micBuffer.putString(session.getUsername());
		micBuffer.putString(service);
		micBuffer.putString(getName());
		byte[] micBytes = micBuffer.getCompactData();
		byte[] mic = context.getMIC(micBytes, 0, micBytes.length,
				new MessageProp(0, true));
		Buffer buffer = session
				.createBuffer(SshConstants.SSH_MSG_USERAUTH_GSSAPI_MIC);
		buffer.putBytes(mic);
		session.writePacket(buffer);
	}

	private void replyToken(ClientSession session, String service, byte[] bytes)
			throws IOException, GSSException {
		sendToken(session, bytes);
		if (context.isEstablished()) {
			sendMic(session, service);
		}
	}

	private String getHostName(ClientSession session) {
		SocketAddress remote = session.getConnectAddress();
		if (remote instanceof InetSocketAddress) {
			InetAddress address = GssApiMechanisms
					.resolve((InetSocketAddress) remote);
			if (address != null) {
				return address.getCanonicalHostName();
			}
		}
		if (session instanceof JGitClientSession) {
			String hostName = ((JGitClientSession) session).getHostConfigEntry()
					.getHostName();
			try {
				hostName = InetAddress.getByName(hostName)
						.getCanonicalHostName();
			} catch (UnknownHostException e) {
				// Ignore here; try with the non-canonical name
			}
			return hostName;
		}
		throw new IllegalStateException(
				"Wrong session class :" + session.getClass().getName()); //$NON-NLS-1$
	}

	private boolean unexpectedMessage(int command) {
		log.warn(format(SshdText.get().gssapiUnexpectedMessage, getName(),
				Integer.toString(command)));
		return false;
	}

	@Override
	public void signalAuthMethodSuccess(ClientSession session, String service,
			Buffer buffer) throws Exception {
		GssApiWithMicAuthenticationReporter reporter = session.getAttribute(
				GssApiWithMicAuthenticationReporter.GSS_AUTHENTICATION_REPORTER);
		if (reporter != null) {
			reporter.signalAuthenticationSuccess(session, service,
					currentMechanism.toString());
		}
	}

	@Override
	public void signalAuthMethodFailure(ClientSession session, String service,
			boolean partial, List<String> serverMethods, Buffer buffer)
			throws Exception {
		GssApiWithMicAuthenticationReporter reporter = session.getAttribute(
				GssApiWithMicAuthenticationReporter.GSS_AUTHENTICATION_REPORTER);
		if (reporter != null) {
			reporter.signalAuthenticationFailure(session, service,
					currentMechanism.toString(), partial, serverMethods);
		}
	}
}

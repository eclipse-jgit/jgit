/*
 * Copyright (C) 2021 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.sshd.common.AttributeRepository.AttributeKey;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.kex.KexProposalOption;
import org.apache.sshd.common.kex.extension.KexExtensionHandler;
import org.apache.sshd.common.kex.extension.KexExtensions;
import org.apache.sshd.common.kex.extension.parser.ServerSignatureAlgorithms;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.signature.Signature;
import org.apache.sshd.common.util.logging.AbstractLoggingBean;
import org.eclipse.jgit.util.StringUtils;

/**
 * Do not use the DefaultClientKexExtensionHandler from sshd; it doesn't work
 * properly because of misconceptions. See SSHD-1141.
 *
 * @see <a href="https://issues.apache.org/jira/browse/SSHD-1141">SSHD-1141</a>
 */
public class JGitKexExtensionHandler extends AbstractLoggingBean
		implements KexExtensionHandler {

	/** Singleton instance. */
	public static final JGitKexExtensionHandler INSTANCE = new JGitKexExtensionHandler();

	/**
	 * Session {@link AttributeKey} used to store whether the extension
	 * indicator was already sent.
	 */
	private static final AttributeKey<Boolean> CLIENT_PROPOSAL_MADE = new AttributeKey<>();

	/**
	 * Session {@link AttributeKey} storing the algorithms announced by the
	 * server as known.
	 */
	public static final AttributeKey<Set<String>> SERVER_ALGORITHMS = new AttributeKey<>();

	private JGitKexExtensionHandler() {
		// No public instantiation for singleton
	}

	@Override
	public boolean isKexExtensionsAvailable(Session session,
			AvailabilityPhase phase) throws IOException {
		return !AvailabilityPhase.PREKEX.equals(phase);
	}

	@Override
	public void handleKexInitProposal(Session session, boolean initiator,
			Map<KexProposalOption, String> proposal) throws IOException {
		// If it's the very first time, we may add the marker telling the server
		// that we are ready to handle SSH_MSG_EXT_INFO
		if (session == null || session.isServerSession() || !initiator) {
			return;
		}
		if (session.getAttribute(CLIENT_PROPOSAL_MADE) != null) {
			return;
		}
		String kexAlgorithms = proposal.get(KexProposalOption.SERVERKEYS);
		if (StringUtils.isEmptyOrNull(kexAlgorithms)) {
			return;
		}
		List<String> algorithms = new ArrayList<>();
		// We're a client. We mustn't send the server extension, and we should
		// send the client extension only once.
		for (String algo : kexAlgorithms.split(",")) { //$NON-NLS-1$
			if (KexExtensions.CLIENT_KEX_EXTENSION.equalsIgnoreCase(algo)
					|| KexExtensions.SERVER_KEX_EXTENSION
							.equalsIgnoreCase(algo)) {
				continue;
			}
			algorithms.add(algo);
		}
		// Tell the server that we want to receive SSH2_MSG_EXT_INFO
		algorithms.add(KexExtensions.CLIENT_KEX_EXTENSION);
		if (log.isDebugEnabled()) {
			log.debug(
					"handleKexInitProposal({}): proposing HostKeyAlgorithms {}", //$NON-NLS-1$
					session, algorithms);
		}
		proposal.put(KexProposalOption.SERVERKEYS,
				String.join(",", algorithms)); //$NON-NLS-1$
		session.setAttribute(CLIENT_PROPOSAL_MADE, Boolean.TRUE);
	}

	@Override
	public boolean handleKexExtensionRequest(Session session, int index,
			int count, String name, byte[] data) throws IOException {
		if (ServerSignatureAlgorithms.NAME.equals(name)) {
			handleServerSignatureAlgorithms(session,
					ServerSignatureAlgorithms.INSTANCE.parseExtension(data));
		}
		return true;
	}

	/**
	 * Perform updates after a server-sig-algs extension has been received.
	 *
	 * @param session
	 *            the message was received for
	 * @param serverAlgorithms
	 *            signature algorithm names announced by the server
	 */
	protected void handleServerSignatureAlgorithms(Session session,
			Collection<String> serverAlgorithms) {
		if (log.isDebugEnabled()) {
			log.debug("handleServerSignatureAlgorithms({}): {}", session, //$NON-NLS-1$
					serverAlgorithms);
		}
		// Client determines order; server says what it supports. Re-order
		// such that supported ones are at the front, in client order,
		// followed by unsupported ones, also in client order.
		if (serverAlgorithms != null && !serverAlgorithms.isEmpty()) {
			List<NamedFactory<Signature>> clientAlgorithms = new ArrayList<>(
					session.getSignatureFactories());
			if (log.isDebugEnabled()) {
				log.debug(
						"handleServerSignatureAlgorithms({}): PubkeyAcceptedAlgorithms before: {}", //$NON-NLS-1$
						session, clientAlgorithms);
			}
			List<NamedFactory<Signature>> unknown = new ArrayList<>();
			Set<String> known = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
			known.addAll(serverAlgorithms);
			for (Iterator<NamedFactory<Signature>> iter = clientAlgorithms
					.iterator(); iter.hasNext();) {
				NamedFactory<Signature> algo = iter.next();
				if (!known.contains(algo.getName())) {
					unknown.add(algo);
					iter.remove();
				}
			}
			// Re-add the unknown ones at the end. Per RFC 8308, some
			// servers may not announce _all_ their supported algorithms,
			// and a client may use unknown algorithms.
			clientAlgorithms.addAll(unknown);
			if (log.isDebugEnabled()) {
				log.debug(
						"handleServerSignatureAlgorithms({}): PubkeyAcceptedAlgorithms after: {}", //$NON-NLS-1$
						session, clientAlgorithms);
			}
			session.setAttribute(SERVER_ALGORITHMS, known);
			session.setSignatureFactories(clientAlgorithms);
		}
	}
}

/*
 * Copyright (C) 2026 Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.apache.sshd.common.kex.KexProposalOption;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.sshd.SshdSessionContext;
import org.eclipse.jgit.transport.sshd.SshdSessionListener;
import org.eclipse.jgit.transport.sshd.SshdSessionListener.KexOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges Apache MINA sshd {@link SessionListener} callbacks to public
 * {@link SshdSessionListener} instances without exposing Apache MINA sshd
 * types.
 */
public final class SshdSessionListenerAdapter implements SessionListener {

	private static final Logger LOG = LoggerFactory
			.getLogger(SshdSessionListenerAdapter.class);

	private final @NonNull URIish uri;

	private final @NonNull List<SshdSessionListener> listeners;

	/**
	 * Creates a bridge adapter for the given URI and listeners.
	 *
	 * @param uri
	 *            target Git remote URI
	 * @param listeners
	 *            non-empty snapshot of JGit session listeners
	 */
	public SshdSessionListenerAdapter(@NonNull URIish uri,
			@NonNull List<SshdSessionListener> listeners) {
		this.uri = Objects.requireNonNull(uri);
		this.listeners = Objects.requireNonNull(listeners);
	}

	@Override
	public void sessionEstablished(Session session) {
		SshdSessionContext ctx = snapshot(session);
		dispatch(l -> l.sessionEstablished(ctx));
	}

	@Override
	public void sessionCreated(Session session) {
		SshdSessionContext ctx = snapshot(session);
		dispatch(l -> l.sessionCreated(ctx));
	}

	@Override
	public void sessionPeerIdentificationSend(Session session, String version,
			List<String> extraLines) {
		SshdSessionContext ctx = snapshot(session);
		String safeVersion = version != null ? version : ""; //$NON-NLS-1$
		List<String> lines = copyLines(extraLines);
		dispatch(l -> l.sessionPeerIdentificationSend(ctx, safeVersion, lines));
	}

	@Override
	public void sessionPeerIdentificationLine(Session session, String line,
			List<String> extraLines) {
		SshdSessionContext ctx = snapshot(session);
		String safeLine = line != null ? line : ""; //$NON-NLS-1$
		List<String> lines = copyLines(extraLines);
		dispatch(l -> l.sessionPeerIdentificationLine(ctx, safeLine, lines));
	}

	@Override
	public void sessionPeerIdentificationReceived(Session session,
			String version, List<String> extraLines) {
		SshdSessionContext ctx = snapshot(session);
		String safeVersion = version != null ? version : ""; //$NON-NLS-1$
		List<String> lines = copyLines(extraLines);
		dispatch(l -> l.sessionPeerIdentificationReceived(ctx, safeVersion,
				lines));
	}

	@Override
	public void sessionNegotiationOptionsCreated(Session session,
			Map<KexProposalOption, String> proposal) {
		SshdSessionContext ctx = snapshot(session);
		Map<KexOption, String> proposalMap = toKexOptions(proposal);
		dispatch(l -> l.sessionNegotiationOptionsCreated(ctx, proposalMap));
	}

	@Override
	public void sessionNegotiationStart(Session session,
			Map<KexProposalOption, String> clientProposal,
			Map<KexProposalOption, String> serverProposal) {
		SshdSessionContext ctx = snapshot(session);
		Map<KexOption, String> clientMap = toKexOptions(clientProposal);
		Map<KexOption, String> serverMap = toKexOptions(serverProposal);
		dispatch(l -> l.sessionNegotiationStart(ctx, clientMap, serverMap));
	}

	@Override
	public void sessionNegotiationEnd(Session session,
			Map<KexProposalOption, String> clientProposal,
			Map<KexProposalOption, String> serverProposal,
			Map<KexProposalOption, String> negotiatedOptions,
			Throwable reason) {
		SshdSessionContext ctx = snapshot(session);
		Map<KexOption, String> clientMap = toKexOptions(clientProposal);
		Map<KexOption, String> serverMap = toKexOptions(serverProposal);
		Map<KexOption, String> negotiatedMap = toKexOptions(negotiatedOptions);
		dispatch(l -> l.sessionNegotiationEnd(ctx, clientMap, serverMap,
				negotiatedMap, reason));
	}

	@Override
	public void sessionEvent(Session session, Event event) {
		SshdSessionListener.Event mapped = toEvent(event);
		if (mapped != null) {
			SshdSessionContext ctx = snapshot(session);
			dispatch(l -> l.sessionEvent(ctx, mapped));
		}
	}

	@Override
	public void sessionException(Session session, Throwable t) {
		if (t != null) {
			SshdSessionContext ctx = snapshot(session);
			dispatch(l -> l.sessionException(ctx, t));
		}
	}

	@Override
	public void sessionDisconnect(Session session, int reason, String msg,
			String language, boolean initiator) {
		SshdSessionContext ctx = snapshot(session);
		String safeMessage = msg != null ? msg : ""; //$NON-NLS-1$
		String safeLanguage = language != null ? language : ""; //$NON-NLS-1$
		dispatch(l -> l.sessionDisconnect(ctx, reason, safeMessage,
				safeLanguage, initiator));
	}

	@Override
	public void sessionClosed(Session session) {
		SshdSessionContext ctx = snapshot(session);
		dispatch(l -> l.sessionClosed(ctx));
	}

	private void dispatch(Consumer<SshdSessionListener> action) {
		for (SshdSessionListener listener : listeners) {
			if (listener == null) {
				continue;
			}
			try {
				action.accept(listener);
			} catch (RuntimeException e) {
				LOG.warn("SSH session listener threw an exception", e); //$NON-NLS-1$
			}
		}
	}

	private SshdSessionContext snapshot(@Nullable Session session) {
		return session == null
				? new SshdSessionContext(uri, null, null, null, null, null,
						null, Collections.emptyMap(), false, false)
				: new SshdSessionContext(uri, session.getLocalAddress(),
						session.getRemoteAddress(), session.getUsername(),
						session.getClientVersion(), session.getServerVersion(),
						session.getSessionId(),
						snapshotNegotiatedKex(session),
						session.isAuthenticated(), session.isOpen());
	}

	private static Map<KexOption, String> snapshotNegotiatedKex(
			Session session) {
		Map<KexOption, String> mapped = new EnumMap<>(KexOption.class);
		for (KexProposalOption option : KexProposalOption.VALUES) {
			KexOption kexOption = toKexOption(option);
			if (kexOption != null) {
				String value = session.getNegotiatedKexParameter(option);
				if (value != null) {
					mapped.put(kexOption, value);
				}
			}
		}
		return mapped.isEmpty() ? Collections.emptyMap()
				: Collections.unmodifiableMap(mapped);
	}

	private static List<String> copyLines(@Nullable List<String> extraLines) {
		if (extraLines == null || extraLines.isEmpty()) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(new ArrayList<>(extraLines));
	}

	private static @Nullable SshdSessionListener.Event toEvent(
			@Nullable Event event) {
		if (event == null) {
			return null;
		}
		switch (event) {
		case KeyEstablished:
			return SshdSessionListener.Event.KEY_ESTABLISHED;
		case Authenticated:
			return SshdSessionListener.Event.AUTHENTICATED;
		case KexCompleted:
			return SshdSessionListener.Event.KEX_COMPLETED;
		default:
			return null;
		}
	}

	private static Map<KexOption, String> toKexOptions(
			@Nullable Map<KexProposalOption, String> source) {
		if (source == null || source.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<KexOption, String> mapped = new EnumMap<>(KexOption.class);
		for (Map.Entry<KexProposalOption, String> entry : source.entrySet()) {
			KexOption option = toKexOption(entry.getKey());
			if (option != null && entry.getValue() != null) {
				mapped.put(option, entry.getValue());
			}
		}
		return Collections.unmodifiableMap(mapped);
	}

	private static @Nullable KexOption toKexOption(
			@Nullable KexProposalOption option) {
		if (option == null) {
			return null;
		}
		switch (option) {
		case ALGORITHMS:
			return KexOption.KEX_ALGORITHMS;
		case SERVERKEYS:
			return KexOption.SERVER_HOST_KEY_ALGORITHMS;
		case C2SENC:
			return KexOption.CLIENT_TO_SERVER_CIPHERS;
		case S2CENC:
			return KexOption.SERVER_TO_CLIENT_CIPHERS;
		case C2SMAC:
			return KexOption.CLIENT_TO_SERVER_MACS;
		case S2CMAC:
			return KexOption.SERVER_TO_CLIENT_MACS;
		case C2SCOMP:
			return KexOption.CLIENT_TO_SERVER_COMPRESSION;
		case S2CCOMP:
			return KexOption.SERVER_TO_CLIENT_COMPRESSION;
		case C2SLANG:
			return KexOption.CLIENT_TO_SERVER_LANGUAGES;
		case S2CLANG:
			return KexOption.SERVER_TO_CLIENT_LANGUAGES;
		default:
			return null;
		}
	}
}

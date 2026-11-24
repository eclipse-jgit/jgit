/*
 * Copyright (C) 2026 Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.sshd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.junit.ssh.SshTestHarness;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionListener.KexOption;
import org.eclipse.jgit.transport.sshd.SshdSessionListener.State;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

/**
 * Tests for {@link SshdSessionListener} and {@link SshdSessionContext}.
 */
public class SshdSessionListenerTest extends SshTestHarness {

	private final List<State> states = new CopyOnWriteArrayList<>();

	private final List<Throwable> failures = new CopyOnWriteArrayList<>();

	private final AtomicReference<SshdSessionContext> authenticatedContext = new AtomicReference<>();

	private final AtomicReference<SshdSessionContext> negotiatedContext = new AtomicReference<>();

	private final AtomicReference<String> clientBanner = new AtomicReference<>();

	private final AtomicReference<String> serverBanner = new AtomicReference<>();

	private final AtomicBoolean optionsCreatedCalled = new AtomicBoolean();

	private final AtomicBoolean negotiationStartedCalled = new AtomicBoolean();

	private final AtomicReference<Map<KexOption, String>> negotiated = new AtomicReference<>();

	private final AtomicReference<Map<KexOption, String>> clientOffer = new AtomicReference<>();

	private final AtomicReference<Map<KexOption, String>> serverOffer = new AtomicReference<>();

	private final AtomicReference<Throwable> negotiationError = new AtomicReference<>();

	private boolean useSubclassFactory;

	private boolean includeThrowingListener;

	private final SshdSessionListener recordingListener = new SshdSessionListener() {
		@Override
		public void onStateChanged(SshdSessionContext context, State state) {
			states.add(state);
			if (state == State.AUTHENTICATED) {
				authenticatedContext.set(context);
			}
		}

		@Override
		public void onPeerIdentificationSend(SshdSessionContext context,
				String version, List<String> extraLines) {
			clientBanner.set(version);
		}

		@Override
		public void onPeerIdentificationReceived(SshdSessionContext context,
				String version, List<String> extraLines) {
			serverBanner.set(version);
		}

		@Override
		public void onNegotiationOptionsCreated(SshdSessionContext context,
				Map<KexOption, String> proposal) {
			optionsCreatedCalled.set(!proposal.isEmpty());
		}

		@Override
		public void onNegotiationStart(SshdSessionContext context,
				Map<KexOption, String> clientProposal,
				Map<KexOption, String> serverProposal) {
			negotiationStartedCalled.set(!clientProposal.isEmpty()
					&& !serverProposal.isEmpty());
		}

		@Override
		public void onNegotiationEnd(SshdSessionContext context,
				Map<KexOption, String> clientProposal,
				Map<KexOption, String> serverProposal,
				Map<KexOption, String> negotiatedOptions, Throwable error) {
			negotiatedContext.set(context);
			clientOffer.set(clientProposal);
			serverOffer.set(serverProposal);
			negotiated.set(negotiatedOptions);
			negotiationError.set(error);
		}

		@Override
		public void onSessionFailed(SshdSessionContext context,
				Throwable cause) {
			failures.add(cause);
		}
	};

	@Override
	protected SshSessionFactory createSessionFactory() {
		if (useSubclassFactory) {
			SshdSessionFactory result = new SshdSessionFactory(
					new JGitKeyCache(), null) {
				@Override
				protected List<SshdSessionListener> getSessionListeners() {
					if (includeThrowingListener) {
						SshdSessionListener throwing = new SshdSessionListener() {
							@Override
							public void onStateChanged(
									SshdSessionContext context, State state) {
								throw new RuntimeException("boom"); //$NON-NLS-1$
							}
						};
						return Arrays.asList(throwing, recordingListener);
					}
					return Collections.singletonList(recordingListener);
				}
			};
			result.setHomeDirectory(FS.DETECTED.userHome());
			result.setSshDirectory(sshDir);
			return result;
		}
		return new SshdSessionFactoryBuilder() //
				.setHomeDirectory(FS.DETECTED.userHome()) //
				.setSshDirectory(sshDir) //
				.addSessionListener(recordingListener) //
				.build(new JGitKeyCache());
	}

	@Override
	protected void installConfig(String... config) {
		File configFile = new File(sshDir, Constants.CONFIG);
		if (config != null) {
			try {
				Files.write(configFile.toPath(), Arrays.asList(config));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	@Test
	public void testBuilderListenerObservesLifecycleAndNegotiation()
			throws Exception {
		clearRecorded();
		String remoteUri = "ssh://" + TEST_USER + "@localhost:" + testPort
				+ "/doesntmatter";
		cloneWith(remoteUri, new File(getTemporaryDirectory(), "cloned"), null,
				"Host localhost",
				"IdentityFile " + privateKey1.getAbsolutePath());

		SshdSessionContext negCtx = negotiatedContext.get();
		assertNotNull("Expected onNegotiationEnd invocation", negCtx);
		assertNull(negotiationError.get());
		assertEquals(remoteUri, negCtx.getUri().toString());
		assertEquals(TEST_USER, negCtx.getUsername());
		assertNotNull(negCtx.getLocalAddress());
		assertNotNull(negCtx.getRemoteAddress());
		assertNotNull(clientBanner.get());
		assertNotNull(serverBanner.get());
		assertEquals(clientBanner.get(), negCtx.getClientVersion());
		assertEquals(serverBanner.get(), negCtx.getServerVersion());
		assertTrue(optionsCreatedCalled.get());
		assertTrue(negotiationStartedCalled.get());

		SshdSessionContext authCtx = authenticatedContext.get();
		assertNotNull(authCtx);
		assertTrue(authCtx.isAuthenticated());
		assertTrue(authCtx.isOpen());
		assertNotNull(authCtx.getSessionId());

		String kex = negotiated.get().get(KexOption.KEX_ALGORITHMS);
		assertNotNull(kex);
		assertFalse(kex.isEmpty());
		assertNotNull(negotiated.get().get(KexOption.SERVER_HOST_KEY_ALGORITHMS));
		assertFalse(clientOffer.get().isEmpty());
		assertFalse(serverOffer.get().isEmpty());

		assertTrue(states.contains(State.CREATED));
		assertTrue(states.contains(State.KEX_COMPLETED));
		assertTrue(states.contains(State.KEY_ESTABLISHED));
		assertTrue(states.contains(State.AUTHENTICATED));
		assertTrue(states.contains(State.CLOSED));
	}

	@Test
	public void testSubclassListenerAndThrowingListenerDoNotBreakSession()
			throws Exception {
		useSubclassFactory = true;
		includeThrowingListener = true;
		SshSessionFactory.setInstance(createSessionFactory());
		clearRecorded();

		cloneWith(
				"ssh://" + TEST_USER + "@localhost:" + testPort
						+ "/doesntmatter",
				new File(getTemporaryDirectory(), "clonedSubclass"), null,
				"Host localhost",
				"IdentityFile " + privateKey1.getAbsolutePath());

		assertNotNull(negotiated.get());
		assertTrue(states.contains(State.CLOSED));
	}

	@Test
	public void testAuthFailureStillObservesNegotiationAndClose()
			throws Exception {
		clearRecorded();

		assertThrows(TransportException.class, () -> cloneWith(
				"ssh://" + TEST_USER + "@localhost:" + testPort
						+ "/doesntmatter",
				new File(getTemporaryDirectory(), "clonedAuthFail"), null,
				"Host localhost",
				"IdentityFile " + privateKey2.getAbsolutePath()));

		assertNotNull(negotiated.get());
		assertFalse(states.contains(State.AUTHENTICATED));
		assertTrue(states.contains(State.CLOSED));
	}

	private void clearRecorded() {
		states.clear();
		failures.clear();
		authenticatedContext.set(null);
		negotiatedContext.set(null);
		clientBanner.set(null);
		serverBanner.set(null);
		optionsCreatedCalled.set(false);
		negotiationStartedCalled.set(false);
		negotiated.set(null);
		clientOffer.set(null);
		serverOffer.set(null);
		negotiationError.set(null);
	}
}

/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.sshd.client.auth.keyboard.UserInteraction;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshConstants;
import org.eclipse.jgit.transport.URIish;

/**
 * A {@link UserInteraction} callback implementation based on a
 * {@link CredentialsProvider}.
 */
public class JGitUserInteraction implements UserInteraction {

	private final CredentialsProvider provider;

	/**
	 * We need to reset the JGit credentials provider if we have repeated
	 * attempts.
	 */
	private final Map<Session, SessionListener> ongoing = new ConcurrentHashMap<>();

	/**
	 * Creates a new {@link JGitUserInteraction} for interactive password input
	 * based on the given {@link CredentialsProvider}.
	 *
	 * @param provider
	 *            to use
	 */
	public JGitUserInteraction(CredentialsProvider provider) {
		this.provider = provider;
	}

	@Override
	public boolean isInteractionAllowed(ClientSession session) {
		return provider != null && provider.isInteractive();
	}

	@Override
	public String[] interactive(ClientSession session, String name,
			String instruction, String lang, String[] prompt, boolean[] echo) {
		// This is keyboard-interactive or password authentication
		List<CredentialItem> items = new ArrayList<>();
		int numberOfHiddenInputs = 0;
		for (int i = 0; i < prompt.length; i++) {
			boolean hidden = i < echo.length && !echo[i];
			if (hidden) {
				numberOfHiddenInputs++;
			}
		}
		// RFC 4256 (SSH_MSG_USERAUTH_INFO_REQUEST) says: "The language tag is
		// deprecated and SHOULD be the empty string." and "[If there are no
		// prompts] the client SHOULD still display the name and instruction
		// fields" and "[The] client SHOULD print the name and instruction (if
		// non-empty)"
		if (name != null && !name.isEmpty()) {
			items.add(new CredentialItem.InformationalMessage(name));
		}
		if (instruction != null && !instruction.isEmpty()) {
			items.add(new CredentialItem.InformationalMessage(instruction));
		}
		for (int i = 0; i < prompt.length; i++) {
			boolean hidden = i < echo.length && !echo[i];
			if (hidden && numberOfHiddenInputs == 1) {
				// We need to somehow trigger storing the password in the
				// Eclipse secure storage in EGit. Currently, this is done only
				// for password fields.
				items.add(new CredentialItem.Password());
				// TODO Possibly change EGit to store all hidden strings
				// (keyed by the URI and the prompt?) so that we don't have to
				// use this kludge here.
			} else {
				items.add(new CredentialItem.StringType(prompt[i], hidden));
			}
		}
		if (items.isEmpty()) {
			// Huh? No info, no prompts?
			return prompt; // Is known to have length zero here
		}
		URIish uri = toURI(session.getUsername(),
				(InetSocketAddress) session.getConnectAddress());
		// Reset the provider for this URI if it's not the first attempt and we
		// have hidden inputs. Otherwise add a session listener that will remove
		// itself once authenticated.
		if (numberOfHiddenInputs > 0) {
			SessionListener listener = ongoing.get(session);
			if (listener != null) {
				provider.reset(uri);
			} else {
				listener = new SessionAuthMarker(ongoing);
				ongoing.put(session, listener);
				session.addSessionListener(listener);
			}
		}
		if (provider.get(uri, items)) {
			return items.stream().map(i -> {
				if (i instanceof CredentialItem.Password) {
					return new String(((CredentialItem.Password) i).getValue());
				} else if (i instanceof CredentialItem.StringType) {
					return ((CredentialItem.StringType) i).getValue();
				}
				return null;
			}).filter(s -> s != null).toArray(String[]::new);
		}
		// TODO What to throw to abort the connection/authentication process?
		// In UserAuthKeyboardInteractive.getUserResponses() it's clear that
		// returning null is valid and signifies "an error"; we'll try the
		// next authentication method. But if the user explicitly canceled,
		// then we don't want to try the next methods...
		//
		// Probably not a serious issue with the typical order of public-key,
		// keyboard-interactive, password.
		return null;
	}

	@Override
	public String getUpdatedPassword(ClientSession session, String prompt,
			String lang) {
		// TODO Implement password update in password authentication?
		return null;
	}

	/**
	 * Creates a {@link URIish} from the given remote address and user name.
	 *
	 * @param userName
	 *            for the uri
	 * @param remote
	 *            address of the remote host
	 * @return the uri, with {@link SshConstants#SSH_SCHEME} as scheme
	 */
	public static URIish toURI(String userName, InetSocketAddress remote) {
		String host = remote.getHostString();
		int port = remote.getPort();
		return new URIish() //
				.setScheme(SshConstants.SSH_SCHEME) //
				.setHost(host) //
				.setPort(port) //
				.setUser(userName);
	}

	/**
	 * A {@link SessionListener} that removes itself from the session when
	 * authentication is done or the session is closed.
	 */
	private static class SessionAuthMarker implements SessionListener {

		private final Map<Session, SessionListener> registered;

		public SessionAuthMarker(Map<Session, SessionListener> registered) {
			this.registered = registered;
		}

		@Override
		public void sessionEvent(Session session, SessionListener.Event event) {
			if (event == SessionListener.Event.Authenticated) {
				session.removeSessionListener(this);
				registered.remove(session, this);
			}
		}

		@Override
		public void sessionClosed(Session session) {
			session.removeSessionListener(this);
			registered.remove(session, this);
		}
	}
}

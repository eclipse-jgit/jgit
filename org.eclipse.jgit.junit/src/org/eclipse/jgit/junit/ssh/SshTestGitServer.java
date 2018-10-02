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
package org.eclipse.jgit.junit.ssh;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.server.ServerAuthenticationManager;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.UserAuth;
import org.apache.sshd.server.auth.gss.GSSAuthenticator;
import org.apache.sshd.server.auth.gss.UserAuthGSS;
import org.apache.sshd.server.auth.gss.UserAuthGSSFactory;
import org.apache.sshd.server.command.AbstractCommandSupport;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.shell.UnknownCommand;
import org.apache.sshd.server.subsystem.sftp.SftpSubsystemFactory;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.UploadPack;

/**
 * A simple ssh/sftp git <em>test</em> server based on Apache MINA sshd.
 * <p>
 * Supports only a single repository. Authenticates only the given test user
 * against his given test public key. Supports fetch and push.
 * </p>
 *
 * @since 5.2
 */
public class SshTestGitServer {

	@NonNull
	private String testUser;

	@NonNull
	private PublicKey testKey;

	@NonNull
	private Repository repository;

	private final ExecutorService executorService = Executors
			.newFixedThreadPool(2);

	private final SshServer server;

	/**
	 * Creates a ssh git <em>test</em> server. It serves one single repository,
	 * and accepts public-key authentication for exactly one test user.
	 *
	 * @param testUser
	 *            user name of the test user
	 * @param testKey
	 *            <em>private</em> key file of the test user; the server will
	 *            only user the public key from it
	 * @param repository
	 *            to serve
	 * @param hostKey
	 *            the unencrypted private key to use as host key
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	public SshTestGitServer(@NonNull String testUser, @NonNull Path testKey,
			@NonNull Repository repository, @NonNull byte[] hostKey)
			throws IOException, GeneralSecurityException {
		this.testUser = testUser;
		setTestUserPublicKey(testKey);
		this.repository = repository;
		server = SshServer.setUpDefaultServer();
		// Set host key
		server.setKeyPairProvider(new KeyPairProvider() {

			@Override
			public Iterable<KeyPair> loadKeys() {
				try (ByteArrayInputStream in = new ByteArrayInputStream(
						hostKey)) {
					return Collections.singletonList(
							SecurityUtils.loadKeyPairIdentity("", in, null));
				} catch (IOException | GeneralSecurityException e) {
					return null;
				}
			}

		});
		// SFTP.
		server.setFileSystemFactory(new VirtualFileSystemFactory() {

			@Override
			protected Path computeRootDir(Session session) throws IOException {
				return SshTestGitServer.this.repository.getDirectory()
						.getParentFile().getAbsoluteFile().toPath();
			}
		});
		server.setUserAuthFactories(getAuthFactories());
		server.setSubsystemFactories(Collections
				.singletonList((new SftpSubsystemFactory.Builder()).build()));
		// No shell
		server.setShellFactory(null);
		// Disable some authentications
		server.setPasswordAuthenticator(null);
		server.setKeyboardInteractiveAuthenticator(null);
		server.setHostBasedAuthenticator(null);
		// Pretend we did gssapi-with-mic.
		server.setGSSAuthenticator(new GSSAuthenticator() {
			@Override
			public boolean validateInitialUser(ServerSession session,
					String user) {
				return false;
			}
		});
		// Accept only the test user/public key
		server.setPublickeyAuthenticator((userName, publicKey, session) -> {
			return SshTestGitServer.this.testUser.equals(userName) && KeyUtils
					.compareKeys(SshTestGitServer.this.testKey, publicKey);
		});
		server.setCommandFactory(command -> {
			if (command.startsWith(RemoteConfig.DEFAULT_UPLOAD_PACK)) {
				return new GitUploadPackCommand(command, executorService);
			} else if (command.startsWith(RemoteConfig.DEFAULT_RECEIVE_PACK)) {
				return new GitReceivePackCommand(command, executorService);
			}
			return new UnknownCommand(command);
		});
	}

	private static class FakeUserAuthGSS extends UserAuthGSS {
		@Override
		protected Boolean doAuth(Buffer buffer, boolean initial)
				throws Exception {
			// We always reply that we did do this, but then we fail at the
			// first token message. That way we can test that the client-side
			// sends the correct initial request and then is skipped correctly,
			// even if it causes a GSSException if Kerberos isn't configured at
			// all.
			if (initial) {
				ServerSession session = getServerSession();
				Buffer b = session.createBuffer(
						SshConstants.SSH_MSG_USERAUTH_INFO_REQUEST);
				b.putBytes(KRB5_MECH.getDER());
				session.writePacket(b);
				return null;
			}
			return Boolean.FALSE;
		}
	}

	private List<NamedFactory<UserAuth>> getAuthFactories() {
		List<NamedFactory<UserAuth>> authentications = new ArrayList<>();
		authentications.add(
				ServerAuthenticationManager.DEFAULT_USER_AUTH_PUBLIC_KEY_FACTORY);
		authentications.add(new UserAuthGSSFactory() {
			@Override
			public UserAuth create() {
				return new FakeUserAuthGSS();
			}
		});
		return authentications;
	}

	/**
	 * Starts the test server, listening on a random port.
	 *
	 * @return the port the server listens on; test clients should connect to
	 *         that port
	 * @throws IOException
	 */
	public int start() throws IOException {
		server.start();
		return server.getPort();
	}

	/**
	 * Stops the test server.
	 *
	 * @throws IOException
	 */
	public void stop() throws IOException {
		executorService.shutdownNow();
		server.stop(true);
	}

	public void setTestUserPublicKey(Path key)
			throws IOException, GeneralSecurityException {
		this.testKey = AuthorizedKeyEntry.readAuthorizedKeys(key).get(0)
				.resolvePublicKey(PublicKeyEntryResolver.IGNORING);
	}

	private class GitUploadPackCommand extends AbstractCommandSupport {

		protected GitUploadPackCommand(String command,
				ExecutorService executorService) {
			super(command, executorService, false);
		}

		@Override
		public void run() {
			UploadPack uploadPack = new UploadPack(repository);
			String gitProtocol = getEnvironment().getEnv().get("GIT_PROTOCOL");
			if (gitProtocol != null) {
				uploadPack
						.setExtraParameters(Collections.singleton(gitProtocol));
			}
			try {
				uploadPack.upload(getInputStream(), getOutputStream(),
						getErrorStream());
				onExit(0);
			} catch (IOException e) {
				log.warn(
						MessageFormat.format("Could not run {0}", getCommand()),
						e);
				onExit(-1, e.toString());
			}
		}

	}

	private class GitReceivePackCommand extends AbstractCommandSupport {

		protected GitReceivePackCommand(String command,
				ExecutorService executorService) {
			super(command, executorService, false);
		}

		@Override
		public void run() {
			try {
				new ReceivePack(repository).receive(getInputStream(),
						getOutputStream(), getErrorStream());
				onExit(0);
			} catch (IOException e) {
				log.warn(
						MessageFormat.format("Could not run {0}", getCommand()),
						e);
				onExit(-1, e.toString());
			}
		}

	}
}

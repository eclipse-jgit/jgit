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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.PropertyResolverUtils;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.common.util.threads.CloseableExecutorService;
import org.apache.sshd.common.util.threads.ThreadUtils;
import org.apache.sshd.server.ServerAuthenticationManager;
import org.apache.sshd.server.ServerFactoryManager;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.UserAuth;
import org.apache.sshd.server.auth.gss.GSSAuthenticator;
import org.apache.sshd.server.auth.gss.UserAuthGSS;
import org.apache.sshd.server.auth.gss.UserAuthGSSFactory;
import org.apache.sshd.server.auth.keyboard.DefaultKeyboardInteractiveAuthenticator;
import org.apache.sshd.server.command.AbstractCommandSupport;
import org.apache.sshd.server.command.Command;
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
	protected final String testUser;

	@NonNull
	protected final Repository repository;

	@NonNull
	protected final List<KeyPair> hostKeys = new ArrayList<>();

	protected final SshServer server;

	@NonNull
	protected PublicKey testKey;

	private final CloseableExecutorService executorService = ThreadUtils
			.newFixedThreadPool("SshTestGitServerPool", 2);

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
		try (ByteArrayInputStream in = new ByteArrayInputStream(hostKey)) {
			SecurityUtils.loadKeyPairIdentities(null, null, in, null)
					.forEach((k) -> hostKeys.add(k));
		} catch (IOException | GeneralSecurityException e) {
			// Ignore.
		}
		server.setKeyPairProvider((session) -> hostKeys);

		configureAuthentication();

		List<NamedFactory<Command>> subsystems = configureSubsystems();
		if (!subsystems.isEmpty()) {
			server.setSubsystemFactories(subsystems);
		}

		configureShell();

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
		authentications.add(new UserAuthGSSFactory() {
			@Override
			public UserAuth create() {
				return new FakeUserAuthGSS();
			}
		});
		authentications.add(
				ServerAuthenticationManager.DEFAULT_USER_AUTH_PUBLIC_KEY_FACTORY);
		authentications.add(
				ServerAuthenticationManager.DEFAULT_USER_AUTH_KB_INTERACTIVE_FACTORY);
		authentications.add(
				ServerAuthenticationManager.DEFAULT_USER_AUTH_PASSWORD_FACTORY);
		return authentications;
	}

	/**
	 * Configures the authentication mechanisms of this test server. Invoked
	 * from the constructor. The default sets up public key authentication for
	 * the test user, and a gssapi-with-mic authenticator that pretends to
	 * support this mechanism, but that then refuses to authenticate anyone.
	 */
	protected void configureAuthentication() {
		server.setUserAuthFactories(getAuthFactories());
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
	}

	/**
	 * Configures the test server's subsystems (sftp, scp). Invoked from the
	 * constructor. The default provides a simple SFTP setup with the root
	 * directory as the given repository's .git directory's parent. (I.e., at
	 * the directory containing the .git directory.)
	 *
	 * @return A possibly empty collection of subsystems.
	 */
	@NonNull
	protected List<NamedFactory<Command>> configureSubsystems() {
		// SFTP.
		server.setFileSystemFactory(new VirtualFileSystemFactory() {

			@Override
			protected Path computeRootDir(Session session) throws IOException {
				return SshTestGitServer.this.repository.getDirectory()
						.getParentFile().getAbsoluteFile().toPath();
			}
		});
		return Collections
				.singletonList((new SftpSubsystemFactory.Builder()).build());
	}

	/**
	 * Configures shell access for the test server. The default provides no
	 * shell at all.
	 */
	protected void configureShell() {
		// No shell
		server.setShellFactory(null);
	}

	/**
	 * Adds an additional host key to the server.
	 *
	 * @param key
	 *            path to the private key file; should not be encrypted
	 * @param inFront
	 *            whether to add the new key before other existing keys
	 * @throws IOException
	 *             if the file denoted by the {@link Path} {@code key} cannot be
	 *             read
	 * @throws GeneralSecurityException
	 *             if the key contained in the file cannot be read
	 */
	public void addHostKey(@NonNull Path key, boolean inFront)
			throws IOException, GeneralSecurityException {
		try (InputStream in = Files.newInputStream(key)) {
			KeyPair pair = SecurityUtils
					.loadKeyPairIdentities(null,
							NamedResource.ofName(key.toString()), in, null)
					.iterator().next();
			if (inFront) {
				hostKeys.add(0, pair);
			} else {
				hostKeys.add(pair);
			}
		}
	}

	/**
	 * Enable password authentication. The server will accept the test user's
	 * name, converted to all upper-case, as password.
	 */
	public void enablePasswordAuthentication() {
		server.setPasswordAuthenticator((user, pwd, session) -> {
			return testUser.equals(user)
					&& testUser.toUpperCase(Locale.ROOT).equals(pwd);
		});
	}

	/**
	 * Enable keyboard-interactive authentication. The server will accept the
	 * test user's name, converted to all upper-case, as password.
	 */
	public void enableKeyboardInteractiveAuthentication() {
		server.setPasswordAuthenticator((user, pwd, session) -> {
			return testUser.equals(user)
					&& testUser.toUpperCase(Locale.ROOT).equals(pwd);
		});
		server.setKeyboardInteractiveAuthenticator(
				DefaultKeyboardInteractiveAuthenticator.INSTANCE);
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

	/**
	 * Sets the test user's public key on the server.
	 *
	 * @param key
	 *            to set
	 * @throws IOException
	 *             if the file cannot be read
	 * @throws GeneralSecurityException
	 *             if the public key cannot be extracted from the file
	 */
	public void setTestUserPublicKey(Path key)
			throws IOException, GeneralSecurityException {
		this.testKey = AuthorizedKeyEntry.readAuthorizedKeys(key).get(0)
				.resolvePublicKey(null, PublicKeyEntryResolver.IGNORING);
	}

	/**
	 * Sets the lines the server sends before its server identification in the
	 * initial protocol version exchange.
	 *
	 * @param lines
	 *            to send
	 * @since 5.5
	 */
	public void setPreamble(String... lines) {
		if (lines != null && lines.length > 0) {
			PropertyResolverUtils.updateProperty(this.server,
					ServerFactoryManager.SERVER_EXTRA_IDENTIFICATION_LINES,
					String.join("|", lines));
		}
	}

	private class GitUploadPackCommand extends AbstractCommandSupport {

		protected GitUploadPackCommand(String command,
				CloseableExecutorService executorService) {
			super(command, ThreadUtils.noClose(executorService));
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
				CloseableExecutorService executorService) {
			super(command, ThreadUtils.noClose(executorService));
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

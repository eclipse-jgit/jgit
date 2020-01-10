/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.daemon.ssh;

import static org.apache.sshd.common.NamedFactory.setUpBuiltinFactories;
import static org.apache.sshd.server.ServerBuilder.builder;
import static org.eclipse.jgit.niofs.internal.daemon.common.PortUtil.validateOrGetNew;
import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotEmpty;
import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.sshd.common.BuiltinFactory;
import org.apache.sshd.common.cipher.BuiltinCiphers;
import org.apache.sshd.common.cipher.Cipher;
import org.apache.sshd.common.mac.BuiltinMacs;
import org.apache.sshd.common.mac.Mac;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.pubkey.CachingPublicKeyAuthenticator;
import org.apache.sshd.server.keyprovider.AbstractGeneratorHostKeyProvider;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.shell.UnknownCommand;
import org.eclipse.jgit.niofs.internal.JGitFileSystemProvider;
import org.eclipse.jgit.niofs.internal.security.AuthenticationService;
import org.eclipse.jgit.niofs.internal.security.PublicKeyAuthenticator;
import org.eclipse.jgit.niofs.internal.security.User;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitSSHService {

	private static final Logger LOG = LoggerFactory.getLogger(GitSSHService.class);

	private final List<BuiltinFactory<Cipher>> managedCiphers = Collections.unmodifiableList(Arrays.asList(
			BuiltinCiphers.aes128ctr, BuiltinCiphers.aes192ctr, BuiltinCiphers.aes256ctr, BuiltinCiphers.arcfour256,
			BuiltinCiphers.arcfour128, BuiltinCiphers.aes192cbc, BuiltinCiphers.aes256cbc));

	private final List<BuiltinFactory<Mac>> managedMACs = Collections
			.unmodifiableList(Arrays.asList(BuiltinMacs.hmacmd5, BuiltinMacs.hmacsha1, BuiltinMacs.hmacsha256,
					BuiltinMacs.hmacsha512, BuiltinMacs.hmacsha196, BuiltinMacs.hmacmd596));

	private SshServer sshd;
	private AuthenticationService authenticationService;
	private PublicKeyAuthenticator publicKeyAuthenticator;

	private SshServer buildSshServer(String ciphersConfigured, String macsConfigured) {

		return builder().cipherFactories((List) setUpBuiltinFactories(false, checkAndSetGitCiphers(ciphersConfigured)))
				.macFactories((List) setUpBuiltinFactories(false, checkAndSetGitMacs(macsConfigured))).build();
	}

	public void setup(final File certDir, final InetSocketAddress inetSocketAddress, final String sshIdleTimeout,
			final String algorithm, final ReceivePackFactory receivePackFactory,
			final UploadPackFactory uploadPackFactory,
			final JGitFileSystemProvider.RepositoryResolverImpl<BaseGitCommand> repositoryResolver,
			final ExecutorService executorService) {
		setup(certDir, inetSocketAddress, sshIdleTimeout, algorithm, receivePackFactory, uploadPackFactory,
				repositoryResolver, executorService, null, null);
	}

	public void setup(final File certDir, final InetSocketAddress inetSocketAddress, final String sshIdleTimeout,
			final String algorithm, final ReceivePackFactory receivePackFactory,
			final UploadPackFactory uploadPackFactory,
			final JGitFileSystemProvider.RepositoryResolverImpl<BaseGitCommand> repositoryResolver,
			final ExecutorService executorService, final String gitSshCiphers, final String gitSshMacs) {
		checkNotNull("certDir", certDir);
		checkNotEmpty("sshIdleTimeout", sshIdleTimeout);
		checkNotEmpty("algorithm", algorithm);
		checkNotNull("receivePackFactory", receivePackFactory);
		checkNotNull("uploadPackFactory", uploadPackFactory);
		checkNotNull("repositoryResolver", repositoryResolver);

		buildSSHServer(gitSshCiphers, gitSshMacs);

		sshd.getProperties().put(SshServer.IDLE_TIMEOUT, sshIdleTimeout);

		if (inetSocketAddress != null) {
			sshd.setHost(inetSocketAddress.getHostName());
			sshd.setPort(validateOrGetNew(inetSocketAddress.getPort()));

			if (inetSocketAddress.getPort() != sshd.getPort()) {
				LOG.error("SSH for Git original port {} not available, new free port {} assigned.",
						inetSocketAddress.getPort(), sshd.getPort());
			}
		}

		if (!certDir.exists()) {
			certDir.mkdirs();
		}

		final AbstractGeneratorHostKeyProvider keyPairProvider = new SimpleGeneratorHostKeyProvider(
				new File(certDir, "hostkey.ser").toPath());

		try {
			SecurityUtils.getKeyPairGenerator(algorithm);
			keyPairProvider.setAlgorithm(algorithm);
		} catch (final Exception ignore) {
			throw new RuntimeException(String.format("Can't use '%s' algorithm for ssh key pair generator.", algorithm),
					ignore);
		}

		sshd.setKeyPairProvider(keyPairProvider);
		sshd.setCommandFactory((channel, command) -> {
			if (command.startsWith("git-upload-pack")) {
				return new GitUploadCommand(command, repositoryResolver, uploadPackFactory, executorService);
			} else if (command.startsWith("git-receive-pack")) {
				return new GitReceiveCommand(command, repositoryResolver, receivePackFactory, executorService);
			} else {
				return new UnknownCommand(command);
			}
		});
		sshd.setPublickeyAuthenticator(new CachingPublicKeyAuthenticator((username, key, session) -> {

			final User user = getPublicKeyAuthenticator().authenticate(username, key);

			if (user == null) {
				return false;
			}

			session.setAttribute(BaseGitCommand.SUBJECT_KEY, user);

			return true;
		}));
		sshd.setPasswordAuthenticator((username, password, session) -> {

			final User user = getUserPassAuthenticator().login(username, password);

			if (user == null) {
				return false;
			}

			session.setAttribute(BaseGitCommand.SUBJECT_KEY, user);
			return true;
		});
	}

	private void buildSSHServer(String gitSshCiphers, String gitSshMacs) {

		sshd = buildSshServer(gitSshCiphers, gitSshMacs);
	}

	private List<BuiltinFactory<Cipher>> checkAndSetGitCiphers(String gitSshCiphers) {
		if (gitSshCiphers == null || gitSshCiphers.isEmpty()) {
			return managedCiphers;
		} else {
			List<BuiltinFactory<Cipher>> ciphersHandled = new ArrayList<>();
			List<String> ciphers = Arrays.asList(gitSshCiphers.split(","));
			for (String cipherCode : ciphers) {
				BuiltinCiphers cipher = BuiltinCiphers.fromFactoryName(cipherCode.trim().toLowerCase());
				if (cipher != null && managedCiphers.contains(cipher)) {
					ciphersHandled.add(cipher);
					LOG.info("Added Cipher {} to the git ssh configuration. ", cipher);
				} else {
					LOG.warn("Cipher {} not handled in git ssh configuration. ", cipher);
				}
			}
			return ciphersHandled;
		}
	}

	private List<BuiltinFactory<Mac>> checkAndSetGitMacs(String gitSshMacs) {

		if (gitSshMacs == null || gitSshMacs.isEmpty()) {
			return managedMACs;
		} else {

			List<BuiltinFactory<Mac>> macs = new ArrayList<>();
			List<String> macsInput = Arrays.asList(gitSshMacs.split(","));
			for (String macCode : macsInput) {
				BuiltinMacs mac = BuiltinMacs.fromFactoryName(macCode.trim().toLowerCase());
				if (mac != null && managedMACs.contains(mac)) {
					macs.add(mac);
					LOG.info("Added MAC {} to the git ssh configuration. ", mac);
				} else {
					LOG.warn("MAC {} not handled in git ssh configuration. ", mac);
				}
			}
			return macs;
		}
	}

	public void stop() {
		try {
			sshd.stop(true);
		} catch (IOException ignored) {
		}
	}

	public void start() {
		try {
			sshd.start();
		} catch (IOException e) {
			throw new RuntimeException("Couldn't start SSH daemon at " + sshd.getHost() + ":" + sshd.getPort(), e);
		}
	}

	public boolean isRunning() {
		return !(sshd.isClosed() || sshd.isClosing());
	}

	SshServer getSshServer() {
		return sshd;
	}

	public Map<String, Object> getProperties() {
		return Collections.unmodifiableMap(sshd.getProperties());
	}

	public AuthenticationService getUserPassAuthenticator() {
		return authenticationService;
	}

	public void setUserPassAuthenticator(AuthenticationService authenticationService) {
		this.authenticationService = authenticationService;
	}

	public PublicKeyAuthenticator getPublicKeyAuthenticator() {
		return publicKeyAuthenticator;
	}

	public void setPublicKeyAuthenticator(PublicKeyAuthenticator publicKeyAuthenticator) {
		this.publicKeyAuthenticator = publicKeyAuthenticator;
	}

	public List<BuiltinFactory<Cipher>> getManagedCiphers() {
		return managedCiphers;
	}

	public List<BuiltinFactory<Mac>> getManagedMACs() {
		return managedMACs;
	}
}

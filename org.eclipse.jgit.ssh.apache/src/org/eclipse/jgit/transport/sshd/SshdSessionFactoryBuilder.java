/*
 * Copyright (C) 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.sshd;

import java.io.File;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshConfigStore;
import org.eclipse.jgit.util.StringUtils;

/**
 * A builder API to configure {@link SshdSessionFactory SshdSessionFactories}.
 *
 * @since 5.8
 */
public final class SshdSessionFactoryBuilder {

	private final State state = new State();

	/**
	 * Sets the {@link ProxyDataFactory} to use for {@link SshdSessionFactory
	 * SshdSessionFactories} created by {@link #build(KeyCache)}.
	 *
	 * @param proxyDataFactory
	 *            to use
	 * @return this {@link SshdSessionFactoryBuilder}
	 */
	public SshdSessionFactoryBuilder setProxyDataFactory(
			ProxyDataFactory proxyDataFactory) {
		this.state.proxyDataFactory = proxyDataFactory;
		return this;
	}

	/**
	 * Sets the home directory to use for {@link SshdSessionFactory
	 * SshdSessionFactories} created by {@link #build(KeyCache)}.
	 *
	 * @param homeDirectory
	 *            to use; may be {@code null}, in which case the home directory
	 *            as defined by {@link org.eclipse.jgit.util.FS#userHome()
	 *            FS.userHome()} is assumed
	 * @return this {@link SshdSessionFactoryBuilder}
	 */
	public SshdSessionFactoryBuilder setHomeDirectory(File homeDirectory) {
		this.state.homeDirectory = homeDirectory;
		return this;
	}

	/**
	 * Sets the SSH directory to use for {@link SshdSessionFactory
	 * SshdSessionFactories} created by {@link #build(KeyCache)}.
	 *
	 * @param sshDirectory
	 *            to use; may be {@code null}, in which case ".ssh" under the
	 *            {@link #setHomeDirectory(File) home directory} is assumed
	 * @return this {@link SshdSessionFactoryBuilder}
	 */
	public SshdSessionFactoryBuilder setSshDirectory(File sshDirectory) {
		this.state.sshDirectory = sshDirectory;
		return this;
	}

	/**
	 * Sets the default preferred authentication mechanisms to use for
	 * {@link SshdSessionFactory SshdSessionFactories} created by
	 * {@link #build(KeyCache)}.
	 *
	 * @param authentications
	 *            comma-separated list of authentication mechanism names; if
	 *            {@code null} or empty, the default as specified by
	 *            {@link SshdSessionFactory#getDefaultPreferredAuthentications()}
	 *            will be used
	 * @return this {@link SshdSessionFactoryBuilder}
	 */
	public SshdSessionFactoryBuilder setPreferredAuthentications(
			String authentications) {
		this.state.preferredAuthentications = authentications;
		return this;
	}

	/**
	 * Sets a function that returns the SSH config file, given the SSH
	 * directory. The function may return {@code null}, in which case no SSH
	 * config file will be used. If a non-null file is returned, it will be used
	 * when it exists. If no supplier has been set, or the supplier has been set
	 * explicitly to {@code null}, by default a file named
	 * {@link org.eclipse.jgit.transport.SshConstants#CONFIG
	 * SshConstants.CONFIG} in the {@link #setSshDirectory(File) SSH directory}
	 * is used.
	 *
	 * @param supplier
	 *            returning a {@link File} for the SSH config file to use, or
	 *            returning {@code null} if no config file is to be used
	 * @return this {@link SshdSessionFactoryBuilder}
	 */
	public SshdSessionFactoryBuilder setConfigFile(
			Function<File, File> supplier) {
		this.state.configFileFinder = supplier;
		return this;
	}

	/**
	 * A factory interface for creating a @link SshConfigStore}.
	 */
	@FunctionalInterface
	public interface ConfigStoreFactory {

		/**
		 * Creates a {@link SshConfigStore}. May return {@code null} if none is
		 * to be used.
		 *
		 * @param homeDir
		 *            to use for ~-replacements
		 * @param configFile
		 *            to use, may be {@code null} if none
		 * @param localUserName
		 *            name of the current user in the local OS
		 * @return the {@link SshConfigStore}, or {@code null} if none is to be
		 *         used
		 */
		SshConfigStore create(@NonNull File homeDir, File configFile,
				String localUserName);
	}

	/**
	 * Sets a factory for the {@link SshConfigStore} to use. If not set or
	 * explicitly set to {@code null}, the default as specified by
	 * {@link SshdSessionFactory#createSshConfigStore(File, File, String)} is
	 * used.
	 *
	 * @param factory
	 *            to set
	 * @return this {@link SshdSessionFactoryBuilder}
	 */
	public SshdSessionFactoryBuilder setConfigStoreFactory(
			ConfigStoreFactory factory) {
		this.state.configFactory = factory;
		return this;
	}

	/**
	 * Sets a function that returns the default known hosts files, given the SSH
	 * directory. If not set or explicitly set to {@code null}, the defaults as
	 * specified by {@link SshdSessionFactory#getDefaultKnownHostsFiles(File)}
	 * are used.
	 *
	 * @param supplier
	 *            to get the default known hosts files
	 * @return this {@link SshdSessionFactoryBuilder}
	 */
	public SshdSessionFactoryBuilder setDefaultKnownHostsFiles(
			Function<File, List<Path>> supplier) {
		this.state.knownHostsFileFinder = supplier;
		return this;
	}

	/**
	 * Sets a function that returns the default private key files, given the SSH
	 * directory. If not set or explicitly set to {@code null}, the defaults as
	 * specified by {@link SshdSessionFactory#getDefaultIdentities(File)} are
	 * used.
	 *
	 * @param supplier
	 *            to get the default private key files
	 * @return this {@link SshdSessionFactoryBuilder}
	 */
	public SshdSessionFactoryBuilder setDefaultIdentities(
			Function<File, List<Path>> supplier) {
		this.state.defaultKeyFileFinder = supplier;
		return this;
	}

	/**
	 * Sets a function that returns the default private keys, given the SSH
	 * directory. If not set or explicitly set to {@code null}, the defaults as
	 * specified by {@link SshdSessionFactory#getDefaultKeys(File)} are used.
	 *
	 * @param provider
	 *            to get the default private key files
	 * @return this {@link SshdSessionFactoryBuilder}
	 */
	public SshdSessionFactoryBuilder setDefaultKeysProvider(
			Function<File, Iterable<KeyPair>> provider) {
		this.state.defaultKeysProvider = provider;
		return this;
	}

	/**
	 * Sets a factory function to create a {@link KeyPasswordProvider}. If not
	 * set or explicitly set to {@code null}, or if the factory returns
	 * {@code null}, the default as specified by
	 * {@link SshdSessionFactory#createKeyPasswordProvider(CredentialsProvider)}
	 * is used.
	 *
	 * @param factory
	 *            to create a {@link KeyPasswordProvider}
	 * @return this {@link SshdSessionFactoryBuilder}
	 */
	public SshdSessionFactoryBuilder setKeyPasswordProvider(
			Function<CredentialsProvider, KeyPasswordProvider> factory) {
		this.state.passphraseProviderFactory = factory;
		return this;
	}

	/**
	 * Sets a function that creates a new {@link ServerKeyDatabase}, given the
	 * SSH and home directory. If not set or explicitly set to {@code null}, or
	 * if the {@code factory} returns {@code null}, the default as specified by
	 * {@link SshdSessionFactory#createServerKeyDatabase(File, File)} is used.
	 *
	 * @param factory
	 *            to create a {@link ServerKeyDatabase}
	 * @return this {@link SshdSessionFactoryBuilder}
	 */
	public SshdSessionFactoryBuilder setServerKeyDatabase(
			BiFunction<File, File, ServerKeyDatabase> factory) {
		this.state.serverKeyDatabaseCreator = factory;
		return this;
	}

	/**
	 * Builds a {@link SshdSessionFactory} as configured, using the given
	 * {@link KeyCache} for caching keys.
	 * <p>
	 * Different {@link SshdSessionFactory SshdSessionFactories} should
	 * <em>not</em> share the same {@link KeyCache} since the cache is
	 * invalidated when the factory itself or when the last {@link SshdSession}
	 * created from the factory is closed.
	 * </p>
	 *
	 * @param cache
	 *            to use for caching ssh keys; may be {@code null} if no caching
	 *            is desired.
	 * @return the {@link SshdSessionFactory}
	 */
	public SshdSessionFactory build(KeyCache cache) {
		// Use a copy to avoid that subsequent calls to setters affect an
		// already created SshdSessionFactory.
		return state.copy().build(cache);
	}

	private static class State {

		ProxyDataFactory proxyDataFactory;

		File homeDirectory;

		File sshDirectory;

		String preferredAuthentications;

		Function<File, File> configFileFinder;

		ConfigStoreFactory configFactory;

		Function<CredentialsProvider, KeyPasswordProvider> passphraseProviderFactory;

		Function<File, List<Path>> knownHostsFileFinder;

		Function<File, List<Path>> defaultKeyFileFinder;

		Function<File, Iterable<KeyPair>> defaultKeysProvider;

		BiFunction<File, File, ServerKeyDatabase> serverKeyDatabaseCreator;

		State copy() {
			State c = new State();
			c.proxyDataFactory = proxyDataFactory;
			c.homeDirectory = homeDirectory;
			c.sshDirectory = sshDirectory;
			c.preferredAuthentications = preferredAuthentications;
			c.configFileFinder = configFileFinder;
			c.configFactory = configFactory;
			c.passphraseProviderFactory = passphraseProviderFactory;
			c.knownHostsFileFinder = knownHostsFileFinder;
			c.defaultKeyFileFinder = defaultKeyFileFinder;
			c.defaultKeysProvider = defaultKeysProvider;
			c.serverKeyDatabaseCreator = serverKeyDatabaseCreator;
			return c;
		}

		SshdSessionFactory build(KeyCache cache) {
			SshdSessionFactory factory = new SessionFactory(cache,
					proxyDataFactory);
			factory.setHomeDirectory(homeDirectory);
			factory.setSshDirectory(sshDirectory);
			return factory;
		}

		private class SessionFactory extends SshdSessionFactory {

			public SessionFactory(KeyCache cache,
					ProxyDataFactory proxyDataFactory) {
				super(cache, proxyDataFactory);
			}

			@Override
			protected File getSshConfig(File sshDir) {
				if (configFileFinder != null) {
					return configFileFinder.apply(sshDir);
				}
				return super.getSshConfig(sshDir);
			}

			@Override
			protected List<Path> getDefaultKnownHostsFiles(File sshDir) {
				if (knownHostsFileFinder != null) {
					List<Path> result = knownHostsFileFinder.apply(sshDir);
					return result == null ? Collections.emptyList() : result;
				}
				return super.getDefaultKnownHostsFiles(sshDir);
			}

			@Override
			protected List<Path> getDefaultIdentities(File sshDir) {
				if (defaultKeyFileFinder != null) {
					List<Path> result = defaultKeyFileFinder.apply(sshDir);
					return result == null ? Collections.emptyList() : result;
				}
				return super.getDefaultIdentities(sshDir);
			}

			@Override
			protected String getDefaultPreferredAuthentications() {
				if (!StringUtils.isEmptyOrNull(preferredAuthentications)) {
					return preferredAuthentications;
				}
				return super.getDefaultPreferredAuthentications();
			}

			@Override
			protected Iterable<KeyPair> getDefaultKeys(File sshDir) {
				if (defaultKeysProvider != null) {
					Iterable<KeyPair> result = defaultKeysProvider
							.apply(sshDir);
					return result == null ? Collections.emptyList() : result;
				}
				return super.getDefaultKeys(sshDir);
			}

			@Override
			protected KeyPasswordProvider createKeyPasswordProvider(
					CredentialsProvider provider) {
				if (passphraseProviderFactory != null) {
					KeyPasswordProvider result = passphraseProviderFactory
							.apply(provider);
					if (result != null) {
						return result;
					}
				}
				return super.createKeyPasswordProvider(provider);
			}

			@Override
			protected ServerKeyDatabase createServerKeyDatabase(File homeDir,
					File sshDir) {
				if (serverKeyDatabaseCreator != null) {
					ServerKeyDatabase result = serverKeyDatabaseCreator
							.apply(homeDir, sshDir);
					if (result != null) {
						return result;
					}
				}
				return super.createServerKeyDatabase(homeDir, sshDir);
			}

			@Override
			protected SshConfigStore createSshConfigStore(File homeDir,
					File configFile, String localUserName) {
				if (configFactory != null) {
					return configFactory.create(homeDir, configFile,
							localUserName);
				}
				return super.createSshConfigStore(homeDir, configFile,
						localUserName);
			}
		}
	}
}

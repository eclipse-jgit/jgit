/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchEvent;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.ketch.KetchLeaderCache;
import org.eclipse.jgit.internal.ketch.KetchSystem;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.niofs.fs.AmbiguousFileSystemNameException;
import org.eclipse.jgit.niofs.fs.FileSystemState;
import org.eclipse.jgit.niofs.fs.attribute.DiffAttributes;
import org.eclipse.jgit.niofs.fs.attribute.HiddenAttributeView;
import org.eclipse.jgit.niofs.fs.attribute.HiddenAttributes;
import org.eclipse.jgit.niofs.fs.attribute.VersionAttributeView;
import org.eclipse.jgit.niofs.fs.attribute.VersionAttributes;
import org.eclipse.jgit.niofs.fs.options.CherryPickCopyOption;
import org.eclipse.jgit.niofs.fs.options.CommentedOption;
import org.eclipse.jgit.niofs.fs.options.MergeCopyOption;
import org.eclipse.jgit.niofs.fs.options.SquashOption;
import org.eclipse.jgit.niofs.internal.config.ConfigProperties;
import org.eclipse.jgit.niofs.internal.daemon.git.Daemon;
import org.eclipse.jgit.niofs.internal.daemon.ssh.BaseGitCommand;
import org.eclipse.jgit.niofs.internal.daemon.ssh.GitSSHService;
import org.eclipse.jgit.niofs.internal.hook.FileSystemHooks;
import org.eclipse.jgit.niofs.internal.manager.JGitFileSystemsManager;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.commands.Clone;
import org.eclipse.jgit.niofs.internal.op.commands.PathUtil;
import org.eclipse.jgit.niofs.internal.op.model.CommitContent;
import org.eclipse.jgit.niofs.internal.op.model.CommitInfo;
import org.eclipse.jgit.niofs.internal.op.model.CopyCommitContent;
import org.eclipse.jgit.niofs.internal.op.model.DefaultCommitContent;
import org.eclipse.jgit.niofs.internal.op.model.MoveCommitContent;
import org.eclipse.jgit.niofs.internal.op.model.PathInfo;
import org.eclipse.jgit.niofs.internal.op.model.PathType;
import org.eclipse.jgit.niofs.internal.op.model.RevertCommitContent;
import org.eclipse.jgit.niofs.internal.security.AuthenticationService;
import org.eclipse.jgit.niofs.internal.security.FileSystemAuthorization;
import org.eclipse.jgit.niofs.internal.security.PublicKeyAuthenticator;
import org.eclipse.jgit.niofs.internal.security.User;
import org.eclipse.jgit.niofs.internal.util.DescriptiveThreadFactory;
import org.eclipse.jgit.niofs.internal.util.EncodingUtil;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.ProcessResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.Collections.emptyList;
import static org.eclipse.jgit.lib.Constants.DOT_GIT_EXT;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.DEFAULT_SCHEME_SIZE;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_ENV_KEY_BRANCH_LIST;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_ENV_KEY_DEFAULT_REMOTE_NAME;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_ENV_KEY_DEST_PATH;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_ENV_KEY_INIT;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_ENV_KEY_MIRROR;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_ENV_KEY_PASSWORD;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_ENV_KEY_SUBDIRECTORY;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_ENV_KEY_USER_NAME;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.SCHEME;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.SCHEME_SIZE;
import static org.eclipse.jgit.niofs.internal.op.model.PathType.DIRECTORY;
import static org.eclipse.jgit.niofs.internal.op.model.PathType.NOT_FOUND;
import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkCondition;
import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotEmpty;
import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotNull;

public class JGitFileSystemProvider extends SecuredFileSystemProvider implements Disposable {

	private static final Logger LOG = LoggerFactory.getLogger(JGitFileSystemProvider.class);
	private static final TimeUnit LOCK_LAST_ACCESS_TIME_UNIT = TimeUnit.SECONDS;
	private static final long LOCK_LAST_ACCESS_THRESHOLD = 10;

	private final Map<String, String> fullHostNames = new HashMap<>();

	private final Object postponedEventsLock = new Object();

	private Daemon daemonService = null;

	private GitSSHService gitSSHService = null;

	private FS detectedFS = FS.DETECTED;

	private ExecutorService executorService;

	final KetchSystem system = new KetchSystem();

	final KetchLeaderCache leaders = new KetchLeaderCache(system);

	private AuthenticationService httpAuthenticator;
	private FileSystemAuthorization authorizer;

	JGitFileSystemProviderConfiguration config;

	JGitFileSystemsManager fsManager;

	JGitFileSystemsEventsManager fsEventsManager;

	/**
	 * Creates a JGit filesystem provider which takes its configuration from system
	 * properties. In a normal production deployment, this is the constructor that
	 * will be invoked by the ServiceLoader mechanism. For a list of properties that
	 * affect the configuration of JGitFileSystemProvider, see the DEBUG log output
	 * of this class during startup.
	 */
	public JGitFileSystemProvider() {
		this(new ConfigProperties(System.getProperties()),
				Executors.newCachedThreadPool(new DescriptiveThreadFactory()));
	}

	/**
	 * Creates a JGit filesystem provider which takes its configuration from the
	 * given map. For a list of properties that affect the configuration of
	 * JGitFileSystemProvider, see the DEBUG log output of this class during
	 * startup.
	 */
	public JGitFileSystemProvider(final Map<String, String> gitPrefs) throws IOException {
		this(new ConfigProperties(gitPrefs), Executors.newCachedThreadPool(new DescriptiveThreadFactory()));
	}

	/**
	 * Creates a JGit filesystem provider which takes its configuration from the
	 * given ConfigProperties instance. For a list of properties that affect the
	 * configuration of JGitFileSystemProvider, see the DEBUG log output of this
	 * class during startup.
	 */
	public JGitFileSystemProvider(final ConfigProperties gitPrefs, final ExecutorService executorService) {
		this.executorService = executorService;

		setupConfigs(gitPrefs);

		setupFileSystemsManager();

		setupFSEvents();

		setupGitDefaultCredentials();

		setupSSH();

		setupFullHostNames();

		setupDaemon();

		setupGitSSH();
	}

	private void setupFSEvents() {
		fsEventsManager = new JGitFileSystemsEventsManager();
	}

	protected void setupFileSystemsManager() {
		fsManager = new JGitFileSystemsManager(this, config);
	}

	private void setupConfigs(ConfigProperties gitPrefs) {
		config = new JGitFileSystemProviderConfiguration();

		loadConfig(gitPrefs);
	}

	private void setupGitSSH() {
		if (config.isSshEnabled()) {
			buildAndStartSSH();
		} else {
			gitSSHService = null;
		}
	}

	private void setupDaemon() {
		if (config.isDaemonEnabled()) {
			buildAndStartDaemon();
		} else {
			daemonService = null;
		}
	}

	private void setupFullHostNames() {
		if (config.isDaemonEnabled()) {
			fullHostNames.put("git", config.getDaemonHostName() + ":" + config.getDaemonPort());
		}
		if (config.isSshEnabled()) {
			fullHostNames.put("ssh", config.getSshHostName() + ":" + config.getSshPort());
		}
	}

	private void setupSSH() {
		SshSessionFactory.setInstance(new JGitSSHConfigSessionFactory(config));
	}

	private void setupGitDefaultCredentials() {
		CredentialsProvider.setDefault(new UsernamePasswordCredentialsProvider("guest", ""));
	}

	private void loadConfig(final ConfigProperties systemConfig) {

		config.load(systemConfig);

		if (config.httpProxyIsDefined()) {
			setupProxyAuthentication();
		}
	}

	private void setupProxyAuthentication() {
		Authenticator.setDefault(new HTTPProxyAuthenticator(config.getHttpProxyUser(), config.getHttpProxyPassword(),
				config.getHttpsProxyUser(), config.getHttpsProxyPassword()));
	}

	public void onCloseFileSystem(final JGitFileSystem fileSystem) {
		fsManager.addClosedFileSystems(fileSystem);

		synchronized (postponedEventsLock) {
			fileSystem.clearPostponedWatchEvents();
		}

		if (fsManager.allTheFSAreClosed()) {
			forceStopDaemon();
			shutdownSSH();
			shutdownEventsManager();
		}
	}

	protected void shutdownEventsManager() {
		this.fsEventsManager.shutdown();
	}

	public void onDisposeFileSystem(final JGitFileSystem fileSystem) {
		onCloseFileSystem(fileSystem);
		fsManager.remove(fileSystem.id());
	}

	@Override
	public void setAuthorizer(FileSystemAuthorization authorizer) {
		this.authorizer = checkNotNull("authorizer", authorizer);
	}

	@Override
	public void setJAASAuthenticator(AuthenticationService authenticator) {
		if (gitSSHService != null) {
			gitSSHService.setUserPassAuthenticator(authenticator);
		}
	}

	@Override
	public void setHTTPAuthenticator(final AuthenticationService httpAuthenticator) {
		this.httpAuthenticator = httpAuthenticator;
	}

	@Override
	public void setSSHAuthenticator(PublicKeyAuthenticator authenticator) {
		checkNotNull("authenticator", authenticator);

		if (gitSSHService != null) {
			gitSSHService.setPublicKeyAuthenticator(authenticator);
		}
	}

	@Override
	public void dispose() {
		shutdown();
	}

	public void addHostName(final String protocol, String s) {
		fullHostNames.put(protocol, s);
	}

	public Map<String, String> getFullHostNames() {
		return new HashMap<>(fullHostNames);
	}

	public class RepositoryResolverImpl<T> implements RepositoryResolver<T> {

		@Override
		public Repository open(final T client, final String name)
				throws RepositoryNotFoundException, ServiceNotAuthorizedException {
			final User user = extractUser(client);
			final JGitFileSystem fs = fsManager.get(name);
			if (fs == null) {
				throw new RepositoryNotFoundException(name);
			}

			if (authorizer != null && !authorizer.authorize(fs, user)) {
				throw new ServiceNotAuthorizedException("User not authorized.");
			}

			return fs.getGit().getRepository();
		}

		public JGitFileSystem resolveFileSystem(final Repository repository) {
			return fsManager.get(repository);
		}
	}

	private User extractUser(Object client) {
		if (httpAuthenticator != null && client instanceof HttpServletRequest) {
			return httpAuthenticator.getUser();
		} else if (client instanceof BaseGitCommand) {
			return ((BaseGitCommand) client).getUser();
		}

		return () -> "ANONYMOUS";
	}

	private void buildAndStartSSH() {
		final ReceivePackFactory receivePackFactory = (req, db) -> getReceivePack("ssh", req, db);

		final UploadPackFactory uploadPackFactory = (UploadPackFactory<BaseGitCommand>) (req,
				db) -> new UploadPack(db) {
					{
						final JGitFileSystem fs = fsManager.get(db);
						fs.filterBranchAccess(this, req.getUser());
					}
				};

		gitSSHService = new GitSSHService();

		gitSSHService.setup(config.getSshFileCertDir(),
				InetSocketAddress.createUnresolved(config.getSshHostAddr(), config.getSshPort()),
				config.getSshIdleTimeout(), config.getSshAlgorithm(), receivePackFactory, uploadPackFactory,
				getRepositoryResolver(), executorService, config.getGitSshCiphers(), config.getGitSshMACs());

		gitSSHService.start();
	}

	public <T> ReceivePack getReceivePack(final String protocol, final T req, final Repository db) {
		return new ReceivePack(db) {
			{

				final JGitFileSystem fs = fsManager.get(db);
				final Map<String, RevCommit> oldTreeRefs = new HashMap<>();

				setPreReceiveHook((rp, commands2) -> {
					fs.lock();
					final User user = extractUser(req);
					for (final ReceiveCommand command : commands2) {
						fs.checkBranchAccess(command, user);
						final RevCommit lastCommit = fs.getGit().getLastCommit(command.getRefName());
						oldTreeRefs.put(command.getRefName(), lastCommit);
					}
				});

				setPostReceiveHook((rp, commands) -> {
					fs.unlock();
					fs.notifyExternalUpdate();
					final User user = extractUser(req);
					for (Map.Entry<String, RevCommit> oldTreeRef : oldTreeRefs.entrySet()) {
						final List<RevCommit> commits = fs.getGit().listCommits(oldTreeRef.getValue(),
								fs.getGit().getLastCommit(oldTreeRef.getKey()));
						for (final RevCommit revCommit : commits) {
							final RevTree parent = revCommit.getParentCount() > 0 ? revCommit.getParent(0).getTree()
									: null;
							notifyDiffs(fs, oldTreeRef.getKey(), "<" + protocol + ">", user.getIdentifier(),
									revCommit.getFullMessage(), parent, revCommit.getTree());
						}
					}
				});
			}
		};
	}

	public <T> RepositoryResolverImpl<T> getRepositoryResolver() {
		return new RepositoryResolverImpl<>();
	}

	void buildAndStartDaemon() {
		if (daemonService == null || !daemonService.isRunning()) {
			daemonService = new Daemon(new InetSocketAddress(config.getDaemonHostAddr(), config.getDaemonPort()),
					new ExecutorWrapper(executorService), executorService, config.isEnableKetch() ? leaders : null);
			daemonService.setRepositoryResolver(new RepositoryResolverImpl<>());
			try {
				daemonService.start();
			} catch (java.io.IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private void shutdownSSH() {
		if (gitSSHService != null) {
			gitSSHService.stop();
		}
	}

	void forceStopDaemon() {
		if (daemonService != null && daemonService.isRunning()) {
			daemonService.stop();
		}
	}

	/**
	 * Closes and disposes all open filesystems and stops the Git and SSH daemons if
	 * they are running. This filesystem provider can be reactivated by attempting
	 * to open a new filesystem or by creating a new filesystem.
	 */
	public void shutdown() {

		for (JGitFileSystem jGitFileSystem : fsManager.getOpenFileSystems()) {
			try {
				jGitFileSystem.close();
			} catch (IOException e) {
				LOG.error("Failed to close the file system [" + jGitFileSystem.getName() + "].", e);
			}
		}
		shutdownSSH();
		forceStopDaemon();
		fsManager.clear();
	}

	/**
	 * Returns the directory that contains all the git repositories managed by this
	 * file system provider.
	 */
	public File getGitRepoContainerDir() {
		return config.getGitReposParentDir();
	}

	@Override
	public String getScheme() {
		return SCHEME;
	}

	@Override
	public FileSystem newFileSystem(final Path path, final Map<String, ?> env)
			throws IllegalArgumentException, UnsupportedOperationException, IOException, SecurityException {
		throw new UnsupportedOperationException();
	}

	@Override
	public FileSystem newFileSystem(final URI uri, final Map<String, ?> env)
			throws IllegalArgumentException, IOException, SecurityException, FileSystemAlreadyExistsException {
		checkNotNull("uri", uri);
		checkCondition("uri scheme not supported", uri.getScheme().equals(getScheme()));
		checkURI("uri", uri);
		checkNotNull("env", env);

		String fsName = extractFSName(uri);

		validateFSName(uri, fsName);

		String envUsername = extractEnvProperty(GIT_ENV_KEY_USER_NAME, env);
		String envPassword = extractEnvProperty(GIT_ENV_KEY_PASSWORD, env);

		fsManager.newFileSystem(() -> fullHostNames, () -> createNewGitRepo(env, fsName), () -> fsName,
				() -> buildCredential(envUsername, envPassword), () -> fsEventsManager, () -> extractFSHooks(env));

		JGitFileSystem fs = fsManager.get(fsName);

		boolean init = false;

		if (env.containsKey(GIT_ENV_KEY_INIT) && Boolean.parseBoolean(env.get(GIT_ENV_KEY_INIT).toString())) {
			init = true;
		}

		if (!env.containsKey(GIT_ENV_KEY_DEFAULT_REMOTE_NAME) && init) {
			try {
				final URI initURI = URI.create(getScheme() + "://master@" + fsName + "/readme.md");
				final OutputStream stream = newOutputStream(getPath(initURI), (CommentedOption) null);
				final String _init = "Repository Init Content\n" + "=======================\n" + "\n"
						+ "Your project description here.";
				stream.write(_init.getBytes());
				stream.close();
			} catch (final Exception e) {
				e.printStackTrace();
				LOG.info("Repository initialization may have failed.", e);
			}
		}

		if (config.isEnableKetch()) {
			createNewGitRepo(env, fsName).enableKetch();
		}

		if (config.isDaemonEnabled() && daemonService != null && !daemonService.isRunning()) {
			buildAndStartDaemon();
		}

		return fs;
	}

	static Map<FileSystemHooks, ?> extractFSHooks(Map<String, ?> env) {

		return Arrays.stream(FileSystemHooks.values()).filter(h -> env.get(h.name()) != null)
				.collect(Collectors.toMap(Function.identity(), k -> env.get(k.name())));
	}

	private void validateFSName(URI uri, String fsName) {
		if (fsManager.containsKey(fsName)) {
			throw new FileSystemAlreadyExistsException("There is already a FS for " + uri + ".");
		}
		if (fsName.contains("/")) {
			String fsNameRoot = fsName.substring(0, fsName.indexOf("/"));
			if (fsManager.containsKey(fsNameRoot)) {
				throw new AmbiguousFileSystemNameException(
						"The file system name" + fsName + " is ambiguous with" + " another FS registered");
			}
		}
		if (fsManager.containsRoot(fsName)) {
			throw new AmbiguousFileSystemNameException(
					"The file system name" + fsName + " is ambiguous with" + " another FS registered");
		}
	}

	private String extractEnvProperty(String key, Map<String, ?> env) {
		if (env == null || env.get(key) == null) {
			return null;
		}
		return env.get(key).toString();
	}

	protected Git createNewGitRepo(Map<String, ?> env, String fsName) {

		final File repoDest = getRepoDest(env, fsName);

		File directory = repoDest.getParentFile();
		String lockName = directory.getName();

		if (!directory.exists()) {
			directory.mkdirs();
		}

		FileSystemLock physicalLock = createLock(directory, lockName);
		try {
			physicalLock.lock();

			return createNewGitRepo(env, fsName, repoDest);
		} finally {
			physicalLock.unlock();
		}
	}

	protected Git createNewGitRepo(Map<String, ?> env, String fsName, File repoDest) {
		final Git git;

		String envUsername = extractEnvProperty(GIT_ENV_KEY_USER_NAME, env);
		String envPassword = extractEnvProperty(GIT_ENV_KEY_PASSWORD, env);
		Boolean envMirror = (Boolean) env.get(GIT_ENV_KEY_MIRROR);
		boolean isMirror = envMirror == null ? true : envMirror;

		CredentialsProvider credential = buildCredential(envUsername, envPassword);

		if (syncWithRemote(env, repoDest)) {
			final String origin = env.get(GIT_ENV_KEY_DEFAULT_REMOTE_NAME).toString();
			final List<String> branches = (List<String>) env.get(GIT_ENV_KEY_BRANCH_LIST);
			final String subdirectory = (String) env.get(GIT_ENV_KEY_SUBDIRECTORY);
			try {
				if (this.isForkOrigin(origin)) {
					git = Git.fork(this.getGitRepoContainerDir(), origin, fsName, branches, credential,
							config.isEnableKetch() ? leaders : null, config.getHookDir(), config.isSslVerify());
				} else if (subdirectory != null) {
					if (isMirror) {
						throw new UnsupportedOperationException(
								"Cannot make mirror repository when cloning subdirectory.");
					}
					git = Git.cloneSubdirectory(repoDest, origin, subdirectory, branches, credential, leaders,
							config.getHookDir(), config.isSslVerify());
				} else {
					git = Git.clone(repoDest, origin, isMirror, branches, credential,
							config.isEnableKetch() ? leaders : null, config.getHookDir(), config.isSslVerify());
				}
			} catch (Clone.CloneException | IOException ce) {
				fsManager.remove(fsName);
				throw new RuntimeException(ce);
			}
		} else {
			git = Git.createRepository(repoDest, config.getHookDir(), config.isEnableKetch() ? leaders : null,
					config.isSslVerify());
		}
		return git;
	}

	private FileSystemLock createLock(File directory, String lockName) {
		return FileSystemLockManager.getInstance().getFileSystemLock(directory, lockName + ".lock",
				LOCK_LAST_ACCESS_TIME_UNIT, LOCK_LAST_ACCESS_THRESHOLD);
	}

	private File getRepoDest(Map<String, ?> env, String fsName) {
		final String outPath = (String) env.get(GIT_ENV_KEY_DEST_PATH);
		final File repoDest;

		if (outPath != null) {
			repoDest = new File(outPath, fsName + DOT_GIT_EXT);
		} else {
			repoDest = new File(config.getGitReposParentDir(), fsName + DOT_GIT_EXT);
		}
		return repoDest;
	}

	private boolean syncWithRemote(Map<String, ?> env, File repoDest) {
		return env.containsKey(GIT_ENV_KEY_DEFAULT_REMOTE_NAME) && !repoDest.exists();
	}

	String extractFSName(final URI _uri) {
		String uri = _uri.toString().replace("git://", "").replace("default://", "");
		if (uri.endsWith("/")) {
			uri = uri.substring(0, uri.length() - 1);
		}
		if (uri.contains("@")) {
			uri = uri.substring(uri.indexOf('@') + 1);
		}
		if (uri.contains(":")) {
			uri = uri.substring(0, uri.indexOf(':') - 1);
		}

		return uri;
	}

	private boolean isForkOrigin(final String originURI) {
		return originURI.matches("(^\\w+\\/\\w+$)");
	}

	@Override
	public FileSystem getFileSystem(final URI uri)
			throws IllegalArgumentException, FileSystemNotFoundException, SecurityException {
		checkNotNull("uri", uri);
		checkCondition("uri scheme not supported", uri.getScheme().equals(getScheme()));
		checkURI("uri", uri);

		JGitFileSystem fileSystem = deepLookupFSFrom(uri);

		if (hasSyncFlag(uri)) {
			try {
				final String treeRef = "master";
				final ObjectId oldHead = fileSystem.getGit().getTreeFromRef(treeRef);
				final Map<String, String> params = getQueryParams(uri);
				try {
					fileSystem.lock();
					final Map.Entry<String, String> remote = new AbstractMap.SimpleEntry<>("upstream",
							params.get("sync"));
					fileSystem.getGit().fetch(fileSystem.getCredential(), remote, emptyList());
					fileSystem.getGit().syncRemote(remote);
				} finally {
					fileSystem.unlock();
				}
				final ObjectId newHead = fileSystem.getGit().getTreeFromRef(treeRef);
				notifyDiffs(fileSystem, treeRef, "<system>", "<system>", "", oldHead, newHead);
			} catch (final Exception ex) {
				throw new RuntimeException("Failed to sync repository.", ex);
			}
		}
		if (hasPushFlag(uri)) {
			try {
				final Map<String, String> params = getQueryParams(uri);
				fileSystem.getGit().push(fileSystem.getCredential(),
						new AbstractMap.SimpleEntry<>("upstream", params.get("push")), hasForceFlag(uri), emptyList());
			} catch (final Exception ex) {
				throw new RuntimeException("Failed to push repository.", ex);
			}
		}

		return fileSystem;
	}

	String extractFSNameWithPath(final URI uri) {
		checkNotNull("uri", uri);

		String path = uri.getAuthority() + uri.getPath();

		int index = path.indexOf('@');
		if (index != -1) {
			path = path.substring(index + 1);
		}
		return path;
	}

	@Override
	public Path getPath(final URI uri) throws IllegalArgumentException, FileSystemNotFoundException, SecurityException {
		checkNotNull("uri", uri);
		checkCondition("uri scheme not supported", uri.getScheme().equals(getScheme()));
		checkURI("uri", uri);

		if (LOG.isDebugEnabled()) {
			LOG.debug("Accessing uri " + uri.toString());
		}

		Path path;

		JGitFileSystem fileSystem = deepLookupFSFrom(uri);

		String branch = extractBranchFrom(uri);

		String host = buildHostFrom(fileSystem, branch);

		String pathStr = buildPathFrom(uri, host);
		path = JGitPathImpl.create(fileSystem, pathStr, host, false);

		return path;
	}

	private String buildPathFrom(URI uri, String host) {
		String pathStr = uri.toString();
		pathStr = pathStr.replace(host, "");
		pathStr = pathStr.replace("git://", "").replace("default://", "");
		pathStr = EncodingUtil.decode(pathStr);
		if (pathStr.startsWith("/:")) {
			pathStr = pathStr.substring(2);
		}
		return pathStr;
	}

	private String buildHostFrom(JGitFileSystem fileSystem, String branch) {
		String host = branch + fileSystem.getName();

		host = host.replace("git://", "").replace("default://", "");
		return host;
	}

	private String extractBranchFrom(URI uri) {
		String branch = "";

		int index = uri.toString().indexOf('@');
		if (index != -1) {
			branch = uri.toString().substring(0, index + 1);
		}
		return branch;
	}

	public String extractPath(final URI uri) {
		checkNotNull("uri", uri);

		return getPath(uri).toString();
	}

	private JGitFileSystem deepLookupFSFrom(URI uri) {

		String fullURI = extractFSNameWithPath(uri);
		int index = fullURI.indexOf("/");
		JGitFileSystem jGitFileSystem = fsManager.get(fullURI);

		while (jGitFileSystem == null && index >= 0) {

			String fsCandidate = fullURI.substring(0, index);
			jGitFileSystem = fsManager.get(fsCandidate);

			index = fullURI.indexOf("/", index + 1);
		}

		if (jGitFileSystem == null) {
			throw new FileSystemNotFoundException("No filesystem for uri (" + uri + ") found.");
		}

		return jGitFileSystem;
	}

	@Override
	public InputStream newInputStream(final Path path, final OpenOption... options)
			throws IllegalArgumentException, UnsupportedOperationException, IOException, SecurityException {
		checkNotNull("path", path);

		final JGitPathImpl gPath = toPathImpl(path);

		return cast(gPath.getFileSystem()).getGit().blobAsInputStream(gPath.getRefTree(), gPath.getPath());
	}

	@Override
	public OutputStream newOutputStream(final Path path, final OpenOption... options)
			throws IllegalArgumentException, UnsupportedOperationException, IOException, SecurityException {
		checkNotNull("path", path);

		final JGitPathImpl gPath = toPathImpl(path);
		final PathInfo result = cast(gPath.getFileSystem()).getGit().getPathInfo(gPath.getRefTree(), gPath.getPath());

		if (result.getPathType().equals(PathType.DIRECTORY)) {
			throw new NotDirectoryException(path.toString());
		}

		try {
			final File file = File.createTempFile("gitz", "woot");
			return new FilterOutputStream(new FileOutputStream(file)) {
				@Override
				public void close() throws java.io.IOException {
					super.close();

					commit(gPath, buildCommitInfo("{" + toPathImpl(path).getPath() + "}", Arrays.asList(options)),
							new DefaultCommitContent(new HashMap<String, File>() {
								{
									put(gPath.getPath(), file);
								}
							}));
				}
			};
		} catch (java.io.IOException e) {
			throw new IOException("Could not create file or output stream.", e);
		}
	}

	private JGitFileSystem cast(final FileSystem fileSystem) {
		return (JGitFileSystem) fileSystem;
	}

	private CommitInfo buildCommitInfo(final String defaultMessage, final Collection<?> options) {
		String sessionId = null;
		String name = null;
		String email = null;
		String message = defaultMessage;
		TimeZone timeZone = null;
		Date when = null;

		if (options != null && !options.isEmpty()) {
			final CommentedOption op = extractCommentedOption(options);
			if (op != null) {
				sessionId = op.getSessionId();
				name = op.getName();
				email = op.getEmail();
				if (op.getMessage() != null && !op.getMessage().trim().isEmpty()) {
					message = op.getMessage() + " " + defaultMessage;
				}
				timeZone = op.getTimeZone();
				when = op.getWhen();
			}
		}

		return new CommitInfo(sessionId, name, email, message, timeZone, when);
	}

	private CommentedOption extractCommentedOption(final Collection<?> options) {
		for (final Object option : options) {
			if (option instanceof CommentedOption) {
				return (CommentedOption) option;
			}
		}
		return null;
	}

	@Override
	public FileChannel newFileChannel(final Path path, Set<? extends OpenOption> options,
			final FileAttribute<?>... attrs)
			throws IllegalArgumentException, UnsupportedOperationException, IOException, SecurityException {
		throw new UnsupportedOperationException();
	}

	@Override
	public AsynchronousFileChannel newAsynchronousFileChannel(final Path path, final Set<? extends OpenOption> options,
			final ExecutorService executor, FileAttribute<?>... attrs)
			throws IllegalArgumentException, UnsupportedOperationException, IOException, SecurityException {
		throw new UnsupportedOperationException();
	}

	@Override
	public SeekableByteChannel newByteChannel(final Path path, final Set<? extends OpenOption> options,
			final FileAttribute<?>... attrs)
			throws IllegalArgumentException, UnsupportedOperationException, IOException, SecurityException {
		final JGitPathImpl gPath = toPathImpl(path);

		if (exists(path)) {
			if (!shouldCreateOrOpenAByteChannel(options)) {
				throw new FileAlreadyExistsException(path.toString());
			}
		}

		final PathInfo result = cast(gPath.getFileSystem()).getGit().getPathInfo(gPath.getRefTree(), gPath.getPath());

		if (result.getPathType().equals(PathType.DIRECTORY)) {
			throw new NotDirectoryException(path.toString());
		}

		try {
			if (options != null && options.contains(READ)) {
				return openAByteChannel(path);
			} else {
				return createANewByteChannel(path, options, gPath, attrs);
			}
		} catch (java.io.IOException e) {
			throw new IOException("Failed to open or create a byte channel.", e);
		} finally {
			cast(path).clearCache();
		}
	}

	private SeekableByteChannel createANewByteChannel(final Path path, final Set<? extends OpenOption> options,
			final JGitPathImpl gPath, final FileAttribute<?>[] attrs) throws java.io.IOException {
		final File file = File.createTempFile("gitz", "woot");

		return new SeekableByteChannelWrapper(new RandomAccessFile(file, "rw").getChannel()) {
			@Override
			public void close() throws java.io.IOException {
				super.close();
				commit(gPath, buildCommitInfo("{" + toPathImpl(path).getPath() + "}", options),
						new DefaultCommitContent(new HashMap<String, File>() {
							{
								put(gPath.getPath(), file);
							}
						}));
			}
		};
	}

	private SeekableByteChannel openAByteChannel(Path path) throws FileNotFoundException {
		return new RandomAccessFile(path.toFile(), "r").getChannel();
	}

	private boolean shouldCreateOrOpenAByteChannel(Set<? extends OpenOption> options) {
		return (options != null && (options.contains(TRUNCATE_EXISTING) || options.contains(READ)));
	}

	protected boolean exists(final Path path) {
		try {
			readAttributes(path, BasicFileAttributes.class);
			return true;
		} catch (final Exception ignored) {
			// this means the file does not exist
		}
		return false;
	}

	@Override
	public DirectoryStream<Path> newDirectoryStream(final Path path, final DirectoryStream.Filter<? super Path> pfilter)
			throws IOException, SecurityException {
		checkNotNull("path", path);
		final DirectoryStream.Filter<? super Path> filter;
		if (pfilter == null) {
			filter = entry -> true;
		} else {
			filter = pfilter;
		}

		final JGitPathImpl gPath = toPathImpl(path);

		final PathInfo result = cast(gPath.getFileSystem()).getGit().getPathInfo(gPath.getRefTree(), gPath.getPath());

		if (!result.getPathType().equals(PathType.DIRECTORY)) {
			throw new NotDirectoryException(path.toString());
		}

		final List<PathInfo> pathContent = cast(gPath.getFileSystem()).getGit().listPathContent(gPath.getRefTree(),
				gPath.getPath());

		return new DirectoryStream<Path>() {
			boolean isClosed = false;

			@Override
			public void close() throws IOException {
				if (isClosed) {
					throw new IOException("This stream is closed.");
				}
				isClosed = true;
			}

			@Override
			public Iterator<Path> iterator() {
				if (isClosed) {
					throw new RuntimeException("This stream is closed.");
				}
				return new Iterator<Path>() {
					int i = -1;
					Path nextEntry = null;
					boolean atEof = false;

					@Override
					public boolean hasNext() {
						if (nextEntry == null && !atEof) {
							nextEntry = readNextEntry();
						}
						return nextEntry != null;
					}

					@Override
					public Path next() {
						final Path result;
						if (nextEntry == null && !atEof) {
							result = readNextEntry();
						} else {
							result = nextEntry;
							nextEntry = null;
						}
						if (result == null) {
							throw new NoSuchElementException();
						}
						return result;
					}

					private Path readNextEntry() {
						if (atEof) {
							return null;
						}

						Path result = null;
						while (true) {
							i++;
							if (i >= pathContent.size()) {
								atEof = true;
								break;
							}

							final PathInfo content = pathContent.get(i);
							final Path path = JGitPathImpl.create(gPath.getFileSystem(), "/" + content.getPath(),
									gPath.getHost(), content.getObjectId(), gPath.isRealPath());
							try {
								if (filter.accept(path)) {
									result = path;
									break;
								}
							} catch (IOException e) {
								throw new RuntimeException(e);
							}
						}

						return result;
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}

	@Override
	public void createDirectory(final Path path, final FileAttribute<?>... attrs)
			throws UnsupportedOperationException, IOException, SecurityException {
		checkNotNull("path", path);

		final JGitPathImpl gPath = toPathImpl(path);

		final PathInfo result = cast(gPath.getFileSystem()).getGit().getPathInfo(gPath.getRefTree(), gPath.getPath());

		if (!result.getPathType().equals(NOT_FOUND)) {
			throw new FileAlreadyExistsException(path.toString());
		}

		try {
			final OutputStream outputStream = newOutputStream(path.resolve(".gitkeep"));
			outputStream.close();
		} catch (final Exception e) {
			throw new IOException("Failed to write to or close the output stream.", e);
		}
	}

	@Override
	public void createSymbolicLink(final Path link, final Path target, final FileAttribute<?>... attrs)
			throws UnsupportedOperationException, IOException, SecurityException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void createLink(final Path link, final Path existing)
			throws UnsupportedOperationException, IOException, SecurityException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void delete(final Path path) throws IOException, SecurityException {
		checkNotNull("path", path);

		if (path instanceof JGitFSPath) {
			deleteFS(path.getFileSystem());
			return;
		}

		final JGitPathImpl gPath = toPathImpl(path);

		if (isBranch(gPath)) {
			deleteBranch(gPath);
			return;
		}

		deleteAsset(gPath);
	}

	protected boolean deleteFS(final FileSystem _fs) throws IOException {

		final JGitFileSystemImpl fileSystem = (JGitFileSystemImpl) _fs;
		final File gitDir = fileSystem.getGit().getRepository().getDirectory();
		File parentDir = gitDir.getParentFile();
		FileSystemLock physicalLock = createLock(parentDir, parentDir.getName());

		try {
			physicalLock.lock();
			fileSystem.close();
			fileSystem.dispose();
			if (System.getProperty("os.name").toLowerCase().contains("windows")) {
				// this operation forces a cache clean freeing any lock -> windows only issue!
				new WindowCacheConfig().install();
			}

			fsManager.remove(fileSystem.getName());

			FileUtils.delete(gitDir, FileUtils.RECURSIVE | FileUtils.SKIP_MISSING | FileUtils.RETRY);

			cleanupParentDir(gitDir);
			return true;
		} catch (java.io.IOException e) {
			throw new IOException("Failed to remove the git repository.", e);
		} finally {
			physicalLock.unlock();
		}
	}

	private void cleanupParentDir(File gitDir) throws java.io.IOException {
		final File parentDir = gitDir.getParentFile();
		if (parentDir.isDirectory() && parentDirIsEmpty(parentDir) && !parentDir.equals(getGitRepoContainerDir())) {
			FileUtils.delete(parentDir, FileUtils.RECURSIVE | FileUtils.RETRY);
		}
	}

	private boolean parentDirIsEmpty(File parentDir) {
		return parentDir.list().length == 0;
	}

	public void deleteAsset(final JGitPathImpl path) throws IOException {
		final PathInfo result = cast(path.getFileSystem()).getGit().getPathInfo(path.getRefTree(), path.getPath());

		if (result.getPathType().equals(PathType.DIRECTORY)) {
			if (deleteNonEmptyDirectory()) {
				deleteResource(path);
				return;
			}
			final List<PathInfo> content = cast(path.getFileSystem()).getGit().listPathContent(path.getRefTree(),
					path.getPath());
			if (content.size() == 1 && content.get(0).getPath().equals(path.getPath().substring(1) + "/.gitkeep")) {
				delete(path.resolve(".gitkeep"));
				deleteResource(path);
				return;
			}
			throw new DirectoryNotEmptyException(path.toString());
		}

		if (result.getPathType().equals(NOT_FOUND)) {
			throw new NoSuchFileException(path.toString());
		}

		deleteResource(path);
	}

	private void deleteResource(final JGitPathImpl path) {
		delete(path, buildCommitInfo("delete {" + path.getPath() + "}", emptyList()));
	}

	private boolean deleteNonEmptyDirectory() {
		return false;
	}

	public void deleteBranch(final JGitPathImpl path) throws IOException {
		final Ref branch = cast(path.getFileSystem()).getGit().getRef(path.getRefTree());

		if (branch == null) {
			throw new NoSuchFileException(path.toString());
		}

		try {
			cast(path.getFileSystem()).lock();
			cast(path.getFileSystem()).getGit().deleteRef(branch);
		} finally {
			cast(path.getFileSystem()).unlock();
		}
	}

	@Override
	public boolean deleteIfExists(final Path path) throws IOException, SecurityException {
		checkNotNull("path", path);

		if (path instanceof JGitFSPath) {
			return deleteFS(path.getFileSystem());
		}

		final JGitPathImpl gPath = toPathImpl(path);

		if (isBranch(gPath)) {
			return deleteBranchIfExists(gPath);
		}

		return deleteAssetIfExists(gPath);
	}

	public boolean deleteBranchIfExists(final JGitPathImpl path) throws IOException {
		try {
			deleteBranch(path);
			return true;
		} catch (final NoSuchFileException ignored) {
			return false;
		}
	}

	public boolean deleteAssetIfExists(final JGitPathImpl path) throws IOException {
		final PathInfo result = cast(path.getFileSystem()).getGit().getPathInfo(path.getRefTree(), path.getPath());

		if (result.getPathType().equals(PathType.DIRECTORY)) {
			if (deleteNonEmptyDirectory()) {
				deleteResource(path);
				return true;
			}
			final List<PathInfo> content = cast(path.getFileSystem()).getGit().listPathContent(path.getRefTree(),
					path.getPath());
			if (content.size() == 1 && content.get(0).getPath().equals(path.getPath().substring(1) + "/.gitkeep")) {
				delete(path.resolve(".gitkeep"));
				return true;
			}
			throw new DirectoryNotEmptyException(path.toString());
		}

		if (result.getPathType().equals(NOT_FOUND)) {
			return false;
		}

		deleteResource(path);
		return true;
	}

	@Override
	public Path readSymbolicLink(final Path link) throws UnsupportedOperationException, IOException, SecurityException {
		checkNotNull("link", link);
		throw new UnsupportedOperationException();
	}

	@Override
	public void copy(final Path source, final Path target, final CopyOption... options)
			throws UnsupportedOperationException, IOException, SecurityException {
		checkNotNull("source", source);
		checkNotNull("target", target);

		final JGitPathImpl gSource = toPathImpl(source);
		final JGitPathImpl gTarget = toPathImpl(target);
		final boolean isBranch = isBranch(gSource) && isBranch(gTarget);

		if (options.length == 1 && options[0] instanceof MergeCopyOption) {
			if (!isBranch) {
				throw new IOException("Merge needs source and target as root.");
			}
			this.merge(gSource, gTarget);
		} else if (options.length == 1 && options[0] instanceof CherryPickCopyOption) {
			if (!isBranch) {
				throw new IOException("Cherry pick needs source and target as root.");
			}
			final String[] commits = ((CherryPickCopyOption) options[0]).getCommits();
			if (commits == null || commits.length == 0) {
				throw new IOException("Cherry pick needs at least one commit id.");
			}
			cherryPick(gSource, gTarget, commits);
		} else {
			if (isBranch) {
				copyBranch(gSource, gTarget);
				return;
			}
			copyAsset(gSource, gTarget, options);
		}
	}

	private void merge(final JGitPathImpl source, final JGitPathImpl target) throws IOException {

		try {
			cast(target.getFileSystem()).lock();
			cast(source.getFileSystem()).getGit().merge(source.getRefTree(), target.getRefTree());
		} finally {
			cast(target.getFileSystem()).unlock();
		}
	}

	private void cherryPick(final JGitPathImpl source, final JGitPathImpl target, final String... commits)
			throws IOException {
		try {
			cast(target.getFileSystem()).lock();
			cast(source.getFileSystem()).getGit().cherryPick(target, commits);
		} finally {
			cast(target.getFileSystem()).unlock();
		}
	}

	private void copyBranch(final JGitPathImpl source, final JGitPathImpl target)
			throws FileAlreadyExistsException, NoSuchFileException {
		checkCondition("source and target should have same file system", hasSameFileSystem(source, target));
		if (existsBranch(target)) {
			throw new FileAlreadyExistsException(target.toString());
		}
		if (!existsBranch(source)) {
			throw new NoSuchFileException(target.toString());
		}
		createBranch(source, target);
	}

	private void copyAsset(final JGitPathImpl source, final JGitPathImpl target, final CopyOption... options)
			throws IOException {
		final PathInfo sourceResult = cast(source.getFileSystem()).getGit().getPathInfo(source.getRefTree(),
				source.getPath());
		final PathInfo targetResult = cast(target.getFileSystem()).getGit().getPathInfo(target.getRefTree(),
				target.getPath());

		if (!isRoot(target) && targetResult.getPathType() != NOT_FOUND) {
			if (!contains(options, StandardCopyOption.REPLACE_EXISTING)) {
				throw new FileAlreadyExistsException(target.toString());
			}
		}

		if (sourceResult.getPathType() == NOT_FOUND) {
			throw new NoSuchFileException(target.toString());
		}

		if (!source.getRefTree().equals(target.getRefTree())) {
			copyAssetContent(source, target, options);
		} else if (!source.getFileSystem().equals(target.getFileSystem())) {
			copyAssetContent(source, target, options);
		} else {
			final Map<JGitPathImpl, JGitPathImpl> sourceDest = new HashMap<>();
			if (sourceResult.getPathType() == DIRECTORY) {
				sourceDest.putAll(mapDirectoryContent(source, target, options));
			} else {
				sourceDest.put(source, target);
			}

			copyFiles(source, target, sourceDest, options);
		}
	}

	private void copyAssetContent(final JGitPathImpl source, final JGitPathImpl target, final CopyOption... options)
			throws IOException {
		final PathInfo sourceResult = cast(source.getFileSystem()).getGit().getPathInfo(source.getRefTree(),
				source.getPath());
		final PathInfo targetResult = cast(target.getFileSystem()).getGit().getPathInfo(target.getRefTree(),
				target.getPath());

		if (!isRoot(target) && targetResult.getPathType() != NOT_FOUND) {
			if (!contains(options, StandardCopyOption.REPLACE_EXISTING)) {
				throw new FileAlreadyExistsException(target.toString());
			}
		}

		if (sourceResult.getPathType() == NOT_FOUND) {
			throw new NoSuchFileException(target.toString());
		}

		if (sourceResult.getPathType() == DIRECTORY) {
			copyDirectory(source, target, options);
			return;
		}

		copyFile(source, target, options);
	}

	private boolean contains(final CopyOption[] options, final CopyOption opt) {
		for (final CopyOption option : options) {
			if (option.equals(opt)) {
				return true;
			}
		}
		return false;
	}

	private void copyDirectory(final JGitPathImpl source, final JGitPathImpl target, final CopyOption... options)
			throws IOException {
		final List<JGitPathImpl> directories = new ArrayList<>();
		for (final Path path : newDirectoryStream(source, null)) {
			final JGitPathImpl gPath = toPathImpl(path);
			final PathInfo pathResult = cast(gPath.getFileSystem()).getGit().getPathInfo(gPath.getRefTree(),
					gPath.getPath());
			if (pathResult.getPathType() == DIRECTORY) {
				directories.add(gPath);
				continue;
			}
			final JGitPathImpl gTarget = composePath(target, (JGitPathImpl) gPath.getFileName());

			copyFile(gPath, gTarget);
		}
		for (final JGitPathImpl directory : directories) {
			createDirectory(composePath(target, (JGitPathImpl) directory.getFileName()));
		}
	}

	private JGitPathImpl composePath(final JGitPathImpl directory, final JGitPathImpl fileName,
			final CopyOption... options) {
		if (directory.getPath().endsWith("/")) {
			return toPathImpl(getPath(URI.create(directory.toUri().toString() + uriEncode(fileName.toString(false)))));
		}
		return toPathImpl(
				getPath(URI.create(directory.toUri().toString() + "/" + uriEncode(fileName.toString(false)))));
	}

	private String uriEncode(final String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			return s;
		}
	}

	private void copyFile(final JGitPathImpl source, final JGitPathImpl target, final CopyOption... options)
			throws IOException {

		final InputStream in = newInputStream(source, convert(options));
		final SeekableByteChannel out = newByteChannel(target, new HashSet<OpenOption>() {
			{
				add(StandardOpenOption.TRUNCATE_EXISTING);
				for (final CopyOption _option : options) {
					if (_option instanceof OpenOption) {
						add((OpenOption) _option);
					}
				}
			}
		});

		try {
			int count;
			byte[] buffer = new byte[8192];
			while ((count = in.read(buffer)) > 0) {
				out.write(ByteBuffer.wrap(buffer, 0, count));
			}
		} catch (Exception e) {
			throw new IOException("Failed to copy file from '" + source + "' to '" + target + "'", e);
		} finally {
			try {
				out.close();
			} catch (java.io.IOException e) {
				throw new IOException("Could not close output stream.", e);
			} finally {
				try {
					in.close();
				} catch (java.io.IOException e) {
					throw new IOException("Could not close input stream.", e);
				}
			}
		}
	}

	private OpenOption[] convert(CopyOption... options) {
		if (options == null || options.length == 0) {
			return new OpenOption[0];
		}
		final List<OpenOption> newOptions = new ArrayList<>(options.length);
		for (final CopyOption option : options) {
			if (option instanceof OpenOption) {
				newOptions.add((OpenOption) option);
			}
		}

		return newOptions.toArray(new OpenOption[newOptions.size()]);
	}

	private void createBranch(final JGitPathImpl source, final JGitPathImpl target) {
		try {
			cast(target.getFileSystem()).lock();
			cast(source.getFileSystem()).getGit().createRef(source.getRefTree(), target.getRefTree());
		} finally {
			cast(target.getFileSystem()).unlock();
		}
	}

	private boolean existsBranch(final JGitPathImpl path) {
		return cast(path.getFileSystem()).getGit().getRef(path.getRefTree()) != null;
	}

	private boolean isBranch(final JGitPathImpl path) {
		return path.getPath().length() == 1 && path.getPath().equals("/");
	}

	private boolean isRoot(final JGitPathImpl path) {
		return isBranch(path);
	}

	private boolean hasSameFileSystem(final JGitPathImpl source, final JGitPathImpl target) {
		return source.getFileSystem().equals(target.getFileSystem());
	}

	@Override
	public void move(final Path source, final Path target, final CopyOption... options)
			throws DirectoryNotEmptyException, AtomicMoveNotSupportedException, IOException, SecurityException {
		checkNotNull("source", source);
		checkNotNull("target", target);

		final JGitPathImpl gSource = toPathImpl(source);
		final JGitPathImpl gTarget = toPathImpl(target);

		final boolean isSourceBranch = isBranch(gSource);
		final boolean isTargetBranch = isBranch(gTarget);

		if (isSourceBranch && isTargetBranch) {
			moveBranch(gSource, gTarget, options);
			return;
		}
		moveAsset(gSource, gTarget, options);
	}

	private void moveBranch(final JGitPathImpl source, final JGitPathImpl target, final CopyOption... options)
			throws IOException {
		checkCondition("source and target should have same file system", hasSameFileSystem(source, target));

		if (!exists(source)) {
			throw new NoSuchFileException(target.toString());
		}

		boolean targetExists = existsBranch(target);
		if (targetExists && !contains(options, StandardCopyOption.REPLACE_EXISTING)) {
			throw new FileAlreadyExistsException(target.toString());
		}

		if (!targetExists) {
			createBranch(source, target);
			deleteBranch(source);
		} else {
			commit(target, buildCommitInfo("reverting from {" + source.getPath() + "}", Arrays.asList(options)),
					new RevertCommitContent(source.getRefTree()));
		}
	}

	private void moveAsset(final JGitPathImpl source, final JGitPathImpl target, final CopyOption... options)
			throws IOException {
		final PathInfo sourceResult = cast(source.getFileSystem()).getGit().getPathInfo(source.getRefTree(),
				source.getPath());
		final PathInfo targetResult = cast(target.getFileSystem()).getGit().getPathInfo(target.getRefTree(),
				target.getPath());

		if (!isRoot(target) && targetResult.getPathType() != NOT_FOUND) {
			if (!contains(options, StandardCopyOption.REPLACE_EXISTING)) {
				throw new FileAlreadyExistsException(target.toString());
			}
		}

		if (sourceResult.getPathType() == NOT_FOUND) {
			throw new NoSuchFileException(target.toString());
		}

		if (!source.getRefTree().equals(target.getRefTree())) {
			copy(source, target, options);
			delete(source);
		} else {
			final Map<JGitPathImpl, JGitPathImpl> fromTo = new HashMap<>();
			if (sourceResult.getPathType() == DIRECTORY) {
				fromTo.putAll(mapDirectoryContent(source, target, options));
			} else {
				fromTo.put(source, target);
			}

			moveFiles(source, target, fromTo, options);
		}
	}

	private Map<JGitPathImpl, JGitPathImpl> mapDirectoryContent(final JGitPathImpl source, final JGitPathImpl target,
			final CopyOption... options) throws IOException {
		final Map<JGitPathImpl, JGitPathImpl> fromTo = new HashMap<>();
		for (final Path path : newDirectoryStream(source, null)) {
			final JGitPathImpl gPath = toPathImpl(path);
			final PathInfo pathResult = cast(gPath.getFileSystem()).getGit().getPathInfo(gPath.getRefTree(),
					gPath.getPath());
			if (pathResult.getPathType() == DIRECTORY) {
				fromTo.putAll(mapDirectoryContent(gPath, composePath(target, (JGitPathImpl) gPath.getFileName())));
			} else {
				final JGitPathImpl gTarget = composePath(target, (JGitPathImpl) gPath.getFileName());
				fromTo.put(gPath, gTarget);
			}
		}

		return fromTo;
	}

	private void moveFiles(final JGitPathImpl source, final JGitPathImpl target,
			final Map<JGitPathImpl, JGitPathImpl> fromTo, final CopyOption... options) {
		final Map<String, String> result = new HashMap<>(fromTo.size());
		for (final Map.Entry<JGitPathImpl, JGitPathImpl> fromToEntry : fromTo.entrySet()) {
			result.put(PathUtil.normalize(fromToEntry.getKey().getPath()),
					PathUtil.normalize(fromToEntry.getValue().getPath()));
		}
		commit(source, buildCommitInfo("moving from {" + source.getPath() + "} to {" + target.getPath() + "}",
				Arrays.asList(options)), new MoveCommitContent(result));
	}

	private void copyFiles(final JGitPathImpl source, final JGitPathImpl target,
			final Map<JGitPathImpl, JGitPathImpl> sourceDest, final CopyOption... options) {
		final Map<String, String> result = new HashMap<>(sourceDest.size());
		for (final Map.Entry<JGitPathImpl, JGitPathImpl> sourceDestEntry : sourceDest.entrySet()) {
			result.put(PathUtil.normalize(sourceDestEntry.getKey().getPath()),
					PathUtil.normalize(sourceDestEntry.getValue().getPath()));
		}
		commit(source, buildCommitInfo("copy from {" + source.getPath() + "} to {" + target.getPath() + "}",
				Arrays.asList(options)), new CopyCommitContent(result));
	}

	@Override
	public boolean isSameFile(final Path pathA, final Path pathB) throws IOException, SecurityException {
		checkNotNull("pathA", pathA);
		checkNotNull("pathB", pathB);

		final JGitPathImpl gPathA = toPathImpl(pathA);
		final JGitPathImpl gPathB = toPathImpl(pathB);

		final PathInfo resultA = cast(gPathA.getFileSystem()).getGit().getPathInfo(gPathA.getRefTree(),
				gPathA.getPath());
		final PathInfo resultB = cast(gPathB.getFileSystem()).getGit().getPathInfo(gPathB.getRefTree(),
				gPathB.getPath());

		if (resultA.getPathType() == PathType.FILE && resultA.getObjectId().equals(resultB.getObjectId())) {
			return true;
		}

		return pathA.equals(pathB);
	}

	@Override
	public boolean isHidden(final Path path) throws IllegalArgumentException, IOException, SecurityException {
		checkNotNull("path", path);

		final JGitPathImpl gPath = toPathImpl(path);

		if (gPath.getFileName() == null) {
			return false;
		}

		return toPathImpl(path.getFileName()).toString(false).startsWith(".");
	}

	@Override
	public FileStore getFileStore(final Path path) throws IOException, SecurityException {
		checkNotNull("path", path);

		return new JGitFileStore(cast(toPathImpl(path).getFileSystem()).getGit().getRepository());
	}

	@Override
	public void checkAccess(final Path path, final AccessMode... modes) throws UnsupportedOperationException,
			NoSuchFileException, AccessDeniedException, IOException, SecurityException {
		checkNotNull("path", path);

		final JGitPathImpl gPath = toPathImpl(path);

		final PathInfo result = cast(gPath.getFileSystem()).getGit().getPathInfo(gPath.getRefTree(), gPath.getPath());

		if (result.getPathType().equals(NOT_FOUND)) {
			throw new NoSuchFileException(path.toString());
		}
	}

	@Override
	public <V extends FileAttributeView> V getFileAttributeView(final Path path, final Class<V> type,
			final LinkOption... options) {
		checkNotNull("path", path);
		checkNotNull("type", type);

		final JGitPathImpl gPath = toPathImpl(path);

		final PathInfo pathResult = cast(gPath.getFileSystem()).getGit().getPathInfo(gPath.getRefTree(),
				gPath.getPath());
		if (pathResult.getPathType().equals(NOT_FOUND)) {
			throw new RuntimeException(path.toString());
		}

		final V resultView = gPath.getAttrView(type);

		if (resultView == null) {
			if (type == BasicFileAttributeView.class || type == JGitBasicAttributeView.class) {
				final V newView = (V) new JGitBasicAttributeView(gPath);
				gPath.addAttrView(newView);
				return newView;
			} else if (type == HiddenAttributeView.class || type == HiddenAttributeViewImpl.class
					|| type == JGitHiddenAttributeViewImpl.class) {
				final V newView = (V) new JGitHiddenAttributeViewImpl(gPath);
				gPath.addAttrView(newView);
				return newView;
			} else if (type == VersionAttributeView.class || type == VersionAttributeViewImpl.class
					|| type == JGitVersionAttributeViewImpl.class) {
				final V newView = (V) new JGitVersionAttributeViewImpl(gPath);
				gPath.addAttrView(newView);
				return newView;
			}
		}

		return resultView;
	}

	private ExtendedAttributeView getFileAttributeView(final JGitPathImpl path, final String name, final String params,
			final LinkOption... options) {
		final ExtendedAttributeView view = cast(path).getAttrView(name);

		if (view == null) {
			if (name.equals("basic")) {
				final JGitBasicAttributeView newView = new JGitBasicAttributeView(path);
				path.addAttrView(newView);
				return newView;
			} else if (name.equals("extended")) {
				final JGitHiddenAttributeViewImpl newView = new JGitHiddenAttributeViewImpl(path);
				path.addAttrView(newView);
				return newView;
			} else if (name.equals("version")) {
				final JGitVersionAttributeViewImpl newView = new JGitVersionAttributeViewImpl(path);
				path.addAttrView(newView);
				return newView;
			} else if (name.equals("diff")) {
				final JGitDiffAttributeViewImpl newView = new JGitDiffAttributeViewImpl(path, params);
				path.addAttrView(newView);
				return newView;
			}
		}
		return view;
	}

	private JGitPathImpl cast(final Path path) {
		return (JGitPathImpl) path;
	}

	@Override
	public <A extends BasicFileAttributes> A readAttributes(final Path path, final Class<A> type,
			final LinkOption... options)
			throws NoSuchFileException, UnsupportedOperationException, IOException, SecurityException {
		checkNotNull("path", path);
		checkNotNull("type", type);

		final JGitPathImpl gPath = toPathImpl(path);

		final PathInfo pathResult = cast(gPath.getFileSystem()).getGit().getPathInfo(gPath.getRefTree(),
				gPath.getPath());
		if (pathResult.getPathType().equals(NOT_FOUND)) {
			throw new NoSuchFileException(path.toString());
		}

		if (type == VersionAttributes.class) {
			final JGitVersionAttributeViewImpl view = getFileAttributeView(path, JGitVersionAttributeViewImpl.class,
					options);
			return (A) view.readAttributes();
		} else if (type == HiddenAttributes.class) {
			final JGitHiddenAttributeViewImpl view = getFileAttributeView(path, JGitHiddenAttributeViewImpl.class,
					options);
			return (A) view.readAttributes();
		} else if (type == DiffAttributes.class) {
			final JGitDiffAttributeViewImpl view = getFileAttributeView(path, JGitDiffAttributeViewImpl.class, options);
			return (A) view.readAttributes();
		} else if (type == BasicFileAttributes.class) {
			final JGitBasicAttributeView view = getFileAttributeView(path, JGitBasicAttributeView.class, options);
			return (A) view.readAttributes();
		}

		return null;
	}

	@Override
	public Map<String, Object> readAttributes(final Path path, final String attributes, final LinkOption... options)
			throws UnsupportedOperationException, IllegalArgumentException, IOException, SecurityException {
		checkNotNull("path", path);
		checkNotEmpty("attributes", attributes);

		final String[] s = split(attributes);
		if (s[0].length() == 0) {
			throw new IllegalArgumentException(attributes);
		}

		if (s[0].equals("diff")) {
			final ExtendedAttributeView view = getFileAttributeView(toPathImpl(path), s[0], s[1], options);

			return view.readAttributes("diff");
		} else {
			final ExtendedAttributeView view = getFileAttributeView(toPathImpl(path), s[0], s[1], options);
			if (view == null) {
				throw new UnsupportedOperationException("View '" + s[0] + "' not available");
			}

			return view.readAttributes(s[1].split(","));
		}
	}

	@Override
	public void setAttribute(final Path path, final String attribute, final Object value, final LinkOption... options)
			throws UnsupportedOperationException, IllegalArgumentException, ClassCastException, IOException,
			SecurityException {
		checkNotNull("path", path);
		checkNotEmpty("attributes", attribute);

		if (attribute.equals(SquashOption.SQUASH_ATTR) && value instanceof SquashOption) {
			this.lockAndSquash(path, (SquashOption) value);
			return;
		}

		if (attribute.equals(FileSystemState.FILE_SYSTEM_STATE_ATTR)) {
			JGitFileSystem fileSystem = (JGitFileSystem) path.getFileSystem();
			try {
				fileSystem.lock();

				if (value instanceof CommentedOption) {
					fileSystem.setBatchCommitInfo("Batch mode", (CommentedOption) value);
					fileSystem.unlock();
					return;
				}

				final boolean isOriginalStateBatch = fileSystem.isOnBatch();

				fileSystem.setState(value.toString());
				FileSystemState.valueOf(value.toString());

				if (isOriginalStateBatch && !fileSystem.isOnBatch()) {
					fileSystem.setBatchCommitInfo(null);
					firePostponedBatchEvents(fileSystem);
					postCommitHook(fileSystem);
				}
				fileSystem.setHadCommitOnBatchState(false);
			} finally {
				fileSystem.unlock();
			}
			return;
		}

		final String[] s = split(attribute);
		if (s[0].length() == 0) {
			throw new IllegalArgumentException(attribute);
		}
		final ExtendedAttributeView view = getFileAttributeView(toPathImpl(path), s[0], null, options);
		if (view == null) {
			throw new UnsupportedOperationException("View '" + s[0] + "' not available");
		}

		view.setAttribute(s[1], value);
	}

	private void lockAndSquash(final Path path, final SquashOption value) {
		final JGitFileSystem fileSystem = (JGitFileSystem) path.getFileSystem();
		try {
			fileSystem.lock();
			final JGitPathImpl gSource = toPathImpl(path);
			String commitMessage = checkNotEmpty("commitMessage", value.getMessage());
			String startCommit = checkNotEmpty("startCommit", value.getRecord().id());
			cast(gSource.getFileSystem()).getGit().squash(gSource.getRefTree(), startCommit, commitMessage);
		} finally {
			fileSystem.unlock();
		}
	}

	private void checkURI(final String paramName, final URI uri) throws IllegalArgumentException {
		checkNotNull("uri", uri);

		if (uri.getAuthority() == null || uri.getAuthority().isEmpty()) {
			throw new IllegalArgumentException(
					"Parameter named '" + paramName + "' is invalid, missing host repository!");
		}

		int atIndex = uri.getPath().indexOf("@");
		if (atIndex != -1 && !uri.getAuthority().contains("@")) {
			if (uri.getPath().indexOf("/", atIndex) == -1) {
				throw new IllegalArgumentException(
						"Parameter named '" + paramName + "' is invalid, missing host repository!");
			}
		}
	}

	public String extractHostForPath(final URI uri) {
		checkNotNull("uri", uri);

		int atIndex = uri.getPath().indexOf("@");
		if (atIndex != -1 && !uri.getAuthority().contains("@")) {
			return uri.getAuthority() + uri.getPath().substring(0, uri.getPath().indexOf("/", atIndex));
		}

		return uri.getAuthority();
	}

	private boolean hasSyncFlag(final URI uri) {
		checkNotNull("uri", uri);

		return uri.getQuery() != null && uri.getQuery().contains("sync");
	}

	private boolean hasForceFlag(URI uri) {
		checkNotNull("uri", uri);

		return uri.getQuery() != null && uri.getQuery().contains("force");
	}

	private boolean hasPushFlag(final URI uri) {
		checkNotNull("uri", uri);

		return uri.getQuery() != null && uri.getQuery().contains("push");
	}

	// by spec, it should be a list of pairs, but here we're just using a map.
	private static Map<String, String> getQueryParams(final URI uri) {
		final String[] params = uri.getQuery().split("&");
		return new HashMap<String, String>(params.length) {
			{
				for (String param : params) {
					final String[] kv = param.split("=");
					final String name = kv[0];
					final String value;
					if (kv.length == 2) {
						value = kv[1];
					} else {
						value = "";
					}

					put(name, value);
				}
			}
		};
	}

	private CredentialsProvider buildCredential(String username, String password) {
		if (username != null) {
			if (password != null) {
				return new UsernamePasswordCredentialsProvider(username, password);
			}
			return new UsernamePasswordCredentialsProvider(username, "");
		}
		return CredentialsProvider.getDefault();
	}

	private JGitPathImpl toPathImpl(final Path path) {
		if (path instanceof JGitPathImpl) {
			return (JGitPathImpl) path;
		}
		throw new IllegalArgumentException("Path not supported by current provider.");
	}

	private String[] split(final String attribute) {
		final String[] s = new String[2];
		final int pos = attribute.indexOf(':');
		if (pos == -1) {
			s[0] = "basic";
			s[1] = attribute;
		} else {
			s[0] = attribute.substring(0, pos);
			s[1] = (pos == attribute.length()) ? "" : attribute.substring(pos + 1);
		}
		return s;
	}

	private int getSchemeSize(final URI uri) {
		if (uri.getScheme().equals(SCHEME)) {
			return SCHEME_SIZE;
		}
		return DEFAULT_SCHEME_SIZE;
	}

	private void delete(final JGitPathImpl path, final CommitInfo commitInfo) {
		commit(path, commitInfo, new DefaultCommitContent(new HashMap<String, File>() {
			{
				put(path.getPath(), null);
			}
		}));
	}

	private void commit(final JGitPathImpl path, final CommitInfo commitInfo, final CommitContent commitContent) {

		final JGitFileSystem fileSystem = (JGitFileSystem) path.getFileSystem();
		try {
			fileSystem.lock();

			final Git git = fileSystem.getGit();
			final String branchName = path.getRefTree();
			final boolean batchState = fileSystem.isOnBatch();
			final boolean amend = batchState && fileSystem.isHadCommitOnBatchState(path.getRoot());
			final ObjectId oldHead = cast(path.getFileSystem()).getGit().getTreeFromRef(branchName);

			final boolean hasCommit;
			if (batchState && fileSystem.getBatchCommitInfo() != null) {
				hasCommit = git.commit(branchName, fileSystem.getBatchCommitInfo(), amend, null, commitContent);
			} else {
				hasCommit = git.commit(branchName, commitInfo, amend, null, commitContent);
			}

			if (!batchState) {
				if (hasCommit) {
					int value = fileSystem.incrementAndGetCommitCount();
					if (value >= config.getCommitLimit()) {
						git.gc();
						fileSystem.resetCommitCount();
					}
				}

				final ObjectId newHead = cast(path.getFileSystem()).getGit().getTreeFromRef(branchName);

				postCommitHook(fileSystem);

				notifyDiffs((JGitFileSystem) path.getFileSystem(), branchName, commitInfo.getSessionId(),
						commitInfo.getName(), commitInfo.getMessage(), oldHead, newHead);
			} else {
				synchronized (postponedEventsLock) {

					String sessionId;
					String userName;
					String message;
					if (fileSystem.getBatchCommitInfo() != null) {
						sessionId = fileSystem.getBatchCommitInfo().getSessionId();
						userName = fileSystem.getBatchCommitInfo().getName();
						message = fileSystem.getBatchCommitInfo().getMessage();
					} else {
						sessionId = commitInfo.getSessionId();
						userName = commitInfo.getName();
						message = commitInfo.getMessage();
					}

					final ObjectId newHead = cast(path.getFileSystem()).getGit().getTreeFromRef(branchName);
					List<WatchEvent<?>> postponedWatchEvents = compareDiffs(cast(path.getFileSystem()), branchName,
							sessionId, userName, message, oldHead, newHead);

					fileSystem.addPostponedWatchEvents(postponedWatchEvents);
				}
			}

			if (cast(path.getFileSystem()).isOnBatch() && !fileSystem.isHadCommitOnBatchState(path.getRoot())) {
				fileSystem.setHadCommitOnBatchState(path.getRoot(), hasCommit);
			}
		} finally {
			fileSystem.unlock();
		}
	}

	private void postCommitHook(final JGitFileSystem fileSystem) {

		ProcessResult result = detectedFS.runHookIfPresent(fileSystem.getGit().getRepository(), "post-commit",
				new String[0]);

		if (result.getStatus().equals(ProcessResult.Status.OK)) {
			fileSystem.notifyPostCommit(result.getExitCode());
		}
	}

	private void firePostponedBatchEvents(JGitFileSystem fileSystem) {
		synchronized (postponedEventsLock) {

			if (fileSystem.hasPostponedEvents()) {
				fileSystem.publishEvents(fileSystem.getRootDirectories().iterator().next(),
						fileSystem.getPostponedWatchEvents());
			}

			fileSystem.clearPostponedWatchEvents();

			int value = fileSystem.incrementAndGetCommitCount();
			if (value >= config.getCommitLimit()) {
				fileSystem.getGit().gc();
				fileSystem.resetCommitCount();
			}
		}
	}

	List<WatchEvent<?>> notifyDiffs(final JGitFileSystem fs, final String _tree, final String sessionId,
			final String userName, final String message, final ObjectId oldHead, final ObjectId newHead) {

		List<WatchEvent<?>> watchEvents = compareDiffs(fs, _tree, sessionId, userName, message, oldHead, newHead);

		final String tree;
		if (_tree.startsWith("refs/")) {
			tree = _tree.substring(_tree.lastIndexOf("/") + 1);
		} else {
			tree = _tree;
		}

		final String host = tree + "@" + fs.getName();

		final Path root = JGitPathImpl.createRoot(fs, "/", host, false);
		if (!watchEvents.isEmpty()) {
			fs.publishEvents(root, watchEvents);
		}
		return watchEvents;
	}

	List<WatchEvent<?>> compareDiffs(final JGitFileSystem fs, final String _tree, final String sessionId,
			final String userName, final String message, final ObjectId oldHead, final ObjectId newHead) {

		final String tree;
		if (_tree.startsWith("refs/")) {
			tree = _tree.substring(_tree.lastIndexOf("/") + 1);
		} else {
			tree = _tree;
		}

		final String host = tree + "@" + fs.getName();

		final List<DiffEntry> diff = fs.getGit().listDiffs(oldHead, newHead);
		final List<WatchEvent<?>> events = new ArrayList<>(diff.size());

		for (final DiffEntry diffEntry : diff) {
			final Path oldPath;
			if (!diffEntry.getOldPath().equals(DiffEntry.DEV_NULL)) {
				oldPath = JGitPathImpl.create(fs, "/" + diffEntry.getOldPath(), host, null, false);
			} else {
				oldPath = null;
			}

			final Path newPath;
			if (!diffEntry.getNewPath().equals(DiffEntry.DEV_NULL)) {
				final PathInfo pathInfo = fs.getGit().getPathInfo(tree, diffEntry.getNewPath());
				newPath = JGitPathImpl.create(fs, "/" + pathInfo.getPath(), host, pathInfo.getObjectId(), false);
			} else {
				newPath = null;
			}

			WatchEvent e = new JGitWatchEvent(sessionId, userName, message, diffEntry.getChangeType().name(), oldPath,
					newPath);
			events.add(e);
		}

		return events;
	}

	GitSSHService getGitSSHService() {
		return gitSSHService;
	}

	public JGitFileSystemProviderConfiguration getConfig() {
		return config;
	}

	/**
	 * implement Executor directly due to bugs in some older CDI implementations.
	 */
	private static class ExecutorWrapper implements Executor {

		private final ExecutorService simpleAsyncExecutor;

		public ExecutorWrapper(ExecutorService simpleAsyncExecutor) {
			this.simpleAsyncExecutor = checkNotNull("simpleAsyncExecutor", simpleAsyncExecutor);
		}

		@Override
		public void execute(Runnable command) {
			simpleAsyncExecutor.execute(command);
		}
	}

	public void setDetectedFS(final FS detectedFS) {
		this.detectedFS = detectedFS;
	}

	public JGitFileSystemsManager getFsManager() {
		return fsManager;
	}
}

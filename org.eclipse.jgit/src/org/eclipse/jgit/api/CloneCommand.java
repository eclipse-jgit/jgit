/*
 * Copyright (C) 2011, 2017 Chris Aniszczyk <caniszczyk@gmail.com>
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
package org.eclipse.jgit.api;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BranchConfig.BranchRebaseMode;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.FS;

/**
 * Clone a repository into a new working directory
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-clone.html"
 *      >Git documentation about Clone</a>
 */
public class CloneCommand extends TransportCommand<CloneCommand, Git> {

	private String uri;

	private File directory;

	private File gitDir;

	private boolean bare;

	private FS fs;

	private String remote = Constants.DEFAULT_REMOTE_NAME;

	private String branch = Constants.HEAD;

	private ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

	private boolean cloneAllBranches;

	private boolean cloneSubmodules;

	private boolean noCheckout;

	private Collection<String> branchesToClone;

	private Callback callback;

	private boolean directoryExistsInitially;

	private boolean gitDirExistsInitially;

	/**
	 * Callback for status of clone operation.
	 *
	 * @since 4.8
	 */
	public interface Callback {
		/**
		 * Notify initialized submodules.
		 *
		 * @param submodules
		 *            the submodules
		 *
		 */
		void initializedSubmodules(Collection<String> submodules);

		/**
		 * Notify starting to clone a submodule.
		 *
		 * @param path
		 *            the submodule path
		 */
		void cloningSubmodule(String path);

		/**
		 * Notify checkout of commit
		 *
		 * @param commit
		 *            the id of the commit being checked out
		 * @param path
		 *            the submodule path
		 */
		void checkingOut(AnyObjectId commit, String path);
	}

	/**
	 * Create clone command with no repository set
	 */
	public CloneCommand() {
		super(null);
	}

	/**
	 * Get the git directory. This is primarily used for tests.
	 *
	 * @return the git directory
	 */
	@Nullable
	File getDirectory() {
		return directory;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Executes the {@code Clone} command.
	 *
	 * The Git instance returned by this command needs to be closed by the
	 * caller to free resources held by the underlying {@link Repository}
	 * instance. It is recommended to call this method as soon as you don't need
	 * a reference to this {@link Git} instance and the underlying
	 * {@link Repository} instance anymore.
	 */
	@Override
	public Git call() throws GitAPIException, InvalidRemoteException,
			org.eclipse.jgit.api.errors.TransportException {
		URIish u = null;
		try {
			u = new URIish(uri);
			verifyDirectories(u);
		} catch (URISyntaxException e) {
			throw new InvalidRemoteException(
					MessageFormat.format(JGitText.get().invalidURL, uri));
		}
		@SuppressWarnings("resource") // Closed by caller
		Repository repository = init();
		FetchResult fetchResult = null;
		Thread cleanupHook = new Thread(() -> cleanup());
		Runtime.getRuntime().addShutdownHook(cleanupHook);
		try {
			fetchResult = fetch(repository, u);
		} catch (IOException ioe) {
			if (repository != null) {
				repository.close();
			}
			cleanup();
			throw new JGitInternalException(ioe.getMessage(), ioe);
		} catch (URISyntaxException e) {
			if (repository != null) {
				repository.close();
			}
			cleanup();
			throw new InvalidRemoteException(MessageFormat.format(
					JGitText.get().invalidRemote, remote));
		} catch (GitAPIException | RuntimeException e) {
			if (repository != null) {
				repository.close();
			}
			cleanup();
			throw e;
		} finally {
			Runtime.getRuntime().removeShutdownHook(cleanupHook);
		}
		if (!noCheckout) {
			try {
				checkout(repository, fetchResult);
			} catch (IOException ioe) {
				repository.close();
				throw new JGitInternalException(ioe.getMessage(), ioe);
			} catch (GitAPIException | RuntimeException e) {
				repository.close();
				throw e;
			}
		}
		return new Git(repository, true);
	}

	private static boolean isNonEmptyDirectory(File dir) {
		if (dir != null && dir.exists()) {
			File[] files = dir.listFiles();
			return files != null && files.length != 0;
		}
		return false;
	}

	void verifyDirectories(URIish u) {
		if (directory == null && gitDir == null) {
			directory = new File(u.getHumanishName() + (bare ? Constants.DOT_GIT_EXT : "")); //$NON-NLS-1$
		}
		directoryExistsInitially = directory != null && directory.exists();
		gitDirExistsInitially = gitDir != null && gitDir.exists();
		validateDirs(directory, gitDir, bare);
		if (isNonEmptyDirectory(directory)) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().cloneNonEmptyDirectory, directory.getName()));
		}
		if (isNonEmptyDirectory(gitDir)) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().cloneNonEmptyDirectory, gitDir.getName()));
		}
	}

	private Repository init() throws GitAPIException {
		InitCommand command = Git.init();
		command.setBare(bare);
		if (fs != null) {
			command.setFs(fs);
		}
		if (directory != null) {
			command.setDirectory(directory);
		}
		if (gitDir != null) {
			command.setGitDir(gitDir);
		}
		return command.call().getRepository();
	}

	private FetchResult fetch(Repository clonedRepo, URIish u)
			throws URISyntaxException,
			org.eclipse.jgit.api.errors.TransportException, IOException,
			GitAPIException {
		// create the remote config and save it
		RemoteConfig config = new RemoteConfig(clonedRepo.getConfig(), remote);
		config.addURI(u);

		final String dst = (bare ? Constants.R_HEADS : Constants.R_REMOTES
				+ config.getName() + '/') + '*';
		boolean fetchAll = cloneAllBranches || branchesToClone == null
				|| branchesToClone.isEmpty();

		config.setFetchRefSpecs(calculateRefSpecs(fetchAll, dst));
		config.update(clonedRepo.getConfig());

		clonedRepo.getConfig().save();

		// run the fetch command
		FetchCommand command = new FetchCommand(clonedRepo);
		command.setRemote(remote);
		command.setProgressMonitor(monitor);
		command.setTagOpt(fetchAll ? TagOpt.FETCH_TAGS : TagOpt.AUTO_FOLLOW);
		configure(command);

		return command.call();
	}

	private List<RefSpec> calculateRefSpecs(boolean fetchAll, String dst) {
		RefSpec heads = new RefSpec();
		heads = heads.setForceUpdate(true);
		heads = heads.setSourceDestination(Constants.R_HEADS + '*', dst);
		List<RefSpec> specs = new ArrayList<>();
		if (!fetchAll) {
			RefSpec tags = new RefSpec();
			tags = tags.setForceUpdate(true);
			tags = tags.setSourceDestination(Constants.R_TAGS + '*',
					Constants.R_TAGS + '*');
			for (String selectedRef : branchesToClone) {
				if (heads.matchSource(selectedRef)) {
					specs.add(heads.expandFromSource(selectedRef));
				} else if (tags.matchSource(selectedRef)) {
					specs.add(tags.expandFromSource(selectedRef));
				}
			}
		} else {
			// We'll fetch the tags anyway.
			specs.add(heads);
		}
		return specs;
	}

	private void checkout(Repository clonedRepo, FetchResult result)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException, GitAPIException {

		Ref head = null;
		if (branch.equals(Constants.HEAD)) {
			Ref foundBranch = findBranchToCheckout(result);
			if (foundBranch != null)
				head = foundBranch;
		}
		if (head == null) {
			head = result.getAdvertisedRef(branch);
			if (head == null)
				head = result.getAdvertisedRef(Constants.R_HEADS + branch);
			if (head == null)
				head = result.getAdvertisedRef(Constants.R_TAGS + branch);
		}

		if (head == null || head.getObjectId() == null)
			return; // TODO throw exception?

		if (head.getName().startsWith(Constants.R_HEADS)) {
			final RefUpdate newHead = clonedRepo.updateRef(Constants.HEAD);
			newHead.disableRefLog();
			newHead.link(head.getName());
			addMergeConfig(clonedRepo, head);
		}

		final RevCommit commit = parseCommit(clonedRepo, head);

		boolean detached = !head.getName().startsWith(Constants.R_HEADS);
		RefUpdate u = clonedRepo.updateRef(Constants.HEAD, detached);
		u.setNewObjectId(commit.getId());
		u.forceUpdate();

		if (!bare) {
			DirCache dc = clonedRepo.lockDirCache();
			DirCacheCheckout co = new DirCacheCheckout(clonedRepo, dc,
					commit.getTree());
			co.setProgressMonitor(monitor);
			co.checkout();
			if (cloneSubmodules)
				cloneSubmodules(clonedRepo);
		}
	}

	private void cloneSubmodules(Repository clonedRepo) throws IOException,
			GitAPIException {
		SubmoduleInitCommand init = new SubmoduleInitCommand(clonedRepo);
		Collection<String> submodules = init.call();
		if (submodules.isEmpty()) {
			return;
		}
		if (callback != null) {
			callback.initializedSubmodules(submodules);
		}

		SubmoduleUpdateCommand update = new SubmoduleUpdateCommand(clonedRepo);
		configure(update);
		update.setProgressMonitor(monitor);
		update.setCallback(callback);
		if (!update.call().isEmpty()) {
			SubmoduleWalk walk = SubmoduleWalk.forIndex(clonedRepo);
			while (walk.next()) {
				try (Repository subRepo = walk.getRepository()) {
					if (subRepo != null) {
						cloneSubmodules(subRepo);
					}
				}
			}
		}
	}

	private Ref findBranchToCheckout(FetchResult result) {
		final Ref idHEAD = result.getAdvertisedRef(Constants.HEAD);
		ObjectId headId = idHEAD != null ? idHEAD.getObjectId() : null;
		if (headId == null) {
			return null;
		}

		Ref master = result.getAdvertisedRef(Constants.R_HEADS
				+ Constants.MASTER);
		ObjectId objectId = master != null ? master.getObjectId() : null;
		if (headId.equals(objectId)) {
			return master;
		}

		Ref foundBranch = null;
		for (Ref r : result.getAdvertisedRefs()) {
			final String n = r.getName();
			if (!n.startsWith(Constants.R_HEADS))
				continue;
			if (headId.equals(r.getObjectId())) {
				foundBranch = r;
				break;
			}
		}
		return foundBranch;
	}

	private void addMergeConfig(Repository clonedRepo, Ref head)
			throws IOException {
		String branchName = Repository.shortenRefName(head.getName());
		clonedRepo.getConfig().setString(ConfigConstants.CONFIG_BRANCH_SECTION,
				branchName, ConfigConstants.CONFIG_KEY_REMOTE, remote);
		clonedRepo.getConfig().setString(ConfigConstants.CONFIG_BRANCH_SECTION,
				branchName, ConfigConstants.CONFIG_KEY_MERGE, head.getName());
		String autosetupRebase = clonedRepo.getConfig().getString(
				ConfigConstants.CONFIG_BRANCH_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOSETUPREBASE);
		if (ConfigConstants.CONFIG_KEY_ALWAYS.equals(autosetupRebase)
				|| ConfigConstants.CONFIG_KEY_REMOTE.equals(autosetupRebase))
			clonedRepo.getConfig().setEnum(
					ConfigConstants.CONFIG_BRANCH_SECTION, branchName,
					ConfigConstants.CONFIG_KEY_REBASE, BranchRebaseMode.REBASE);
		clonedRepo.getConfig().save();
	}

	private RevCommit parseCommit(Repository clonedRepo, Ref ref)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		final RevCommit commit;
		try (RevWalk rw = new RevWalk(clonedRepo)) {
			commit = rw.parseCommit(ref.getObjectId());
		}
		return commit;
	}

	/**
	 * Set the URI to clone from
	 *
	 * @param uri
	 *            the URI to clone from, or {@code null} to unset the URI. The
	 *            URI must be set before {@link #call} is called.
	 * @return this instance
	 */
	public CloneCommand setURI(String uri) {
		this.uri = uri;
		return this;
	}

	/**
	 * The optional directory associated with the clone operation. If the
	 * directory isn't set, a name associated with the source uri will be used.
	 *
	 * @see URIish#getHumanishName()
	 * @param directory
	 *            the directory to clone to, or {@code null} if the directory
	 *            name should be taken from the source uri
	 * @return this instance
	 * @throws java.lang.IllegalStateException
	 *             if the combination of directory, gitDir and bare is illegal.
	 *             E.g. if for a non-bare repository directory and gitDir point
	 *             to the same directory of if for a bare repository both
	 *             directory and gitDir are specified
	 */
	public CloneCommand setDirectory(File directory) {
		validateDirs(directory, gitDir, bare);
		this.directory = directory;
		return this;
	}

	/**
	 * Set the repository meta directory (.git)
	 *
	 * @param gitDir
	 *            the repository meta directory, or {@code null} to choose one
	 *            automatically at clone time
	 * @return this instance
	 * @throws java.lang.IllegalStateException
	 *             if the combination of directory, gitDir and bare is illegal.
	 *             E.g. if for a non-bare repository directory and gitDir point
	 *             to the same directory of if for a bare repository both
	 *             directory and gitDir are specified
	 * @since 3.6
	 */
	public CloneCommand setGitDir(File gitDir) {
		validateDirs(directory, gitDir, bare);
		this.gitDir = gitDir;
		return this;
	}

	/**
	 * Set whether the cloned repository shall be bare
	 *
	 * @param bare
	 *            whether the cloned repository is bare or not
	 * @return this instance
	 * @throws java.lang.IllegalStateException
	 *             if the combination of directory, gitDir and bare is illegal.
	 *             E.g. if for a non-bare repository directory and gitDir point
	 *             to the same directory of if for a bare repository both
	 *             directory and gitDir are specified
	 */
	public CloneCommand setBare(boolean bare) throws IllegalStateException {
		validateDirs(directory, gitDir, bare);
		this.bare = bare;
		return this;
	}

	/**
	 * Set the file system abstraction to be used for repositories created by
	 * this command.
	 *
	 * @param fs
	 *            the abstraction.
	 * @return {@code this} (for chaining calls).
	 * @since 4.10
	 */
	public CloneCommand setFs(FS fs) {
		this.fs = fs;
		return this;
	}

	/**
	 * The remote name used to keep track of the upstream repository for the
	 * clone operation. If no remote name is set, the default value of
	 * <code>Constants.DEFAULT_REMOTE_NAME</code> will be used.
	 *
	 * @see Constants#DEFAULT_REMOTE_NAME
	 * @param remote
	 *            name that keeps track of the upstream repository.
	 *            {@code null} means to use DEFAULT_REMOTE_NAME.
	 * @return this instance
	 */
	public CloneCommand setRemote(String remote) {
		if (remote == null) {
			remote = Constants.DEFAULT_REMOTE_NAME;
		}
		this.remote = remote;
		return this;
	}

	/**
	 * Set the initial branch
	 *
	 * @param branch
	 *            the initial branch to check out when cloning the repository.
	 *            Can be specified as ref name (<code>refs/heads/master</code>),
	 *            branch name (<code>master</code>) or tag name
	 *            (<code>v1.2.3</code>). The default is to use the branch
	 *            pointed to by the cloned repository's HEAD and can be
	 *            requested by passing {@code null} or <code>HEAD</code>.
	 * @return this instance
	 */
	public CloneCommand setBranch(String branch) {
		if (branch == null) {
			branch = Constants.HEAD;
		}
		this.branch = branch;
		return this;
	}

	/**
	 * The progress monitor associated with the clone operation. By default,
	 * this is set to <code>NullProgressMonitor</code>
	 *
	 * @see NullProgressMonitor
	 * @param monitor
	 *            a {@link org.eclipse.jgit.lib.ProgressMonitor}
	 * @return {@code this}
	 */
	public CloneCommand setProgressMonitor(ProgressMonitor monitor) {
		if (monitor == null) {
			monitor = NullProgressMonitor.INSTANCE;
		}
		this.monitor = monitor;
		return this;
	}

	/**
	 * Set whether all branches have to be fetched.
	 * <p>
	 * If {@code false}, use {@link #setBranchesToClone(Collection)} to define
	 * what will be cloned. If neither are set, all branches will be cloned.
	 * </p>
	 *
	 * @param cloneAllBranches
	 *            {@code true} when all branches have to be fetched (indicates
	 *            wildcard in created fetch refspec), {@code false} otherwise.
	 * @return {@code this}
	 */
	public CloneCommand setCloneAllBranches(boolean cloneAllBranches) {
		this.cloneAllBranches = cloneAllBranches;
		return this;
	}

	/**
	 * Set whether to clone submodules
	 *
	 * @param cloneSubmodules
	 *            true to initialize and update submodules. Ignored when
	 *            {@link #setBare(boolean)} is set to true.
	 * @return {@code this}
	 */
	public CloneCommand setCloneSubmodules(boolean cloneSubmodules) {
		this.cloneSubmodules = cloneSubmodules;
		return this;
	}

	/**
	 * Set the branches or tags to clone.
	 * <p>
	 * This is ignored if {@link #setCloneAllBranches(boolean)
	 * setCloneAllBranches(true)} is used. If {@code branchesToClone} is
	 * {@code null} or empty, it's also ignored and all branches will be cloned.
	 * </p>
	 *
	 * @param branchesToClone
	 *            collection of branches to clone. Must be specified as full ref
	 *            names (e.g. {@code refs/heads/master} or
	 *            {@code refs/tags/v1.0.0}).
	 * @return {@code this}
	 */
	public CloneCommand setBranchesToClone(Collection<String> branchesToClone) {
		this.branchesToClone = branchesToClone;
		return this;
	}

	/**
	 * Set whether to skip checking out a branch
	 *
	 * @param noCheckout
	 *            if set to <code>true</code> no branch will be checked out
	 *            after the clone. This enhances performance of the clone
	 *            command when there is no need for a checked out branch.
	 * @return {@code this}
	 */
	public CloneCommand setNoCheckout(boolean noCheckout) {
		this.noCheckout = noCheckout;
		return this;
	}

	/**
	 * Register a progress callback.
	 *
	 * @param callback
	 *            the callback
	 * @return {@code this}
	 * @since 4.8
	 */
	public CloneCommand setCallback(Callback callback) {
		this.callback = callback;
		return this;
	}

	private static void validateDirs(File directory, File gitDir, boolean bare)
			throws IllegalStateException {
		if (directory != null) {
			if (directory.exists() && !directory.isDirectory()) {
				throw new IllegalStateException(MessageFormat.format(
						JGitText.get().initFailedDirIsNoDirectory, directory));
			}
			if (gitDir != null && gitDir.exists() && !gitDir.isDirectory()) {
				throw new IllegalStateException(MessageFormat.format(
						JGitText.get().initFailedGitDirIsNoDirectory,
						gitDir));
			}
			if (bare) {
				if (gitDir != null && !gitDir.equals(directory))
					throw new IllegalStateException(MessageFormat.format(
							JGitText.get().initFailedBareRepoDifferentDirs,
							gitDir, directory));
			} else {
				if (gitDir != null && gitDir.equals(directory))
					throw new IllegalStateException(MessageFormat.format(
							JGitText.get().initFailedNonBareRepoSameDirs,
							gitDir, directory));
			}
		}
	}

	private void cleanup() {
		try {
			if (directory != null) {
				if (!directoryExistsInitially) {
					FileUtils.delete(directory, FileUtils.RECURSIVE
							| FileUtils.SKIP_MISSING | FileUtils.IGNORE_ERRORS);
				} else {
					deleteChildren(directory);
				}
			}
			if (gitDir != null) {
				if (!gitDirExistsInitially) {
					FileUtils.delete(gitDir, FileUtils.RECURSIVE
							| FileUtils.SKIP_MISSING | FileUtils.IGNORE_ERRORS);
				} else {
					deleteChildren(gitDir);
				}
			}
		} catch (IOException e) {
			// Ignore; this is a best-effort cleanup in error cases, and
			// IOException should not be raised anyway
		}
	}

	private void deleteChildren(File file) throws IOException {
		File[] files = file.listFiles();
		if (files == null) {
			return;
		}
		for (File child : files) {
			FileUtils.delete(child, FileUtils.RECURSIVE | FileUtils.SKIP_MISSING
					| FileUtils.IGNORE_ERRORS);
		}
	}
}

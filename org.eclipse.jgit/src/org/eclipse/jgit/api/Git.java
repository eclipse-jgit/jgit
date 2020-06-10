/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.internal.WorkQueue;
import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.util.FS;

/**
 * Offers a "GitPorcelain"-like API to interact with a git repository.
 * <p>
 * The GitPorcelain commands are described in the <a href=
 * "http://www.kernel.org/pub/software/scm/git/docs/git.html#_high_level_commands_porcelain"
 * >Git Documentation</a>.
 * <p>
 * This class only offers methods to construct so-called command classes. Each
 * GitPorcelain command is represented by one command class.<br>
 * Example: this class offers a {@code commit()} method returning an instance of
 * the {@code CommitCommand} class. The {@code CommitCommand} class has setters
 * for all the arguments and options. The {@code CommitCommand} class also has a
 * {@code call} method to actually execute the commit. The following code show's
 * how to do a simple commit:
 *
 * <pre>
 * Git git = new Git(myRepo);
 * git.commit().setMessage(&quot;Fix393&quot;).setAuthor(developerIdent).call();
 * </pre>
 *
 * All mandatory parameters for commands have to be specified in the methods of
 * this class, the optional parameters have to be specified by the
 * setter-methods of the Command class.
 * <p>
 * This class is intended to be used internally (e.g. by JGit tests) or by
 * external components (EGit, third-party tools) when they need exactly the
 * functionality of a GitPorcelain command. There are use-cases where this class
 * is not optimal and where you should use the more low-level JGit classes. The
 * methods in this class may for example offer too much functionality or they
 * offer the functionality with the wrong arguments.
 */
public class Git implements AutoCloseable {
	/** The git repository this class is interacting with */
	private final Repository repo;

	private final boolean closeRepo;

	/**
	 * Open repository
	 *
	 * @param dir
	 *            the repository to open. May be either the GIT_DIR, or the
	 *            working tree directory that contains {@code .git}.
	 * @return a {@link org.eclipse.jgit.api.Git} object for the existing git
	 *         repository
	 * @throws java.io.IOException
	 */
	public static Git open(File dir) throws IOException {
		return open(dir, FS.DETECTED);
	}

	/**
	 * Open repository
	 *
	 * @param dir
	 *            the repository to open. May be either the GIT_DIR, or the
	 *            working tree directory that contains {@code .git}.
	 * @param fs
	 *            filesystem abstraction to use when accessing the repository.
	 * @return a {@link org.eclipse.jgit.api.Git} object for the existing git
	 *         repository. Closing this instance will close the repo.
	 * @throws java.io.IOException
	 */
	public static Git open(File dir, FS fs) throws IOException {
		RepositoryCache.FileKey key;

		key = RepositoryCache.FileKey.lenient(dir, fs);
		Repository db = new RepositoryBuilder().setFS(fs).setGitDir(key.getFile())
				.setMustExist(true).build();
		return new Git(db, true);
	}

	/**
	 * Wrap repository
	 *
	 * @param repo
	 *            the git repository this class is interacting with;
	 *            {@code null} is not allowed.
	 * @return a {@link org.eclipse.jgit.api.Git} object for the existing git
	 *         repository. The caller is responsible for closing the repository;
	 *         {@link #close()} on this instance does not close the repo.
	 */
	public static Git wrap(Repository repo) {
		return new Git(repo);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Free resources associated with this instance.
	 * <p>
	 * If the repository was opened by a static factory method in this class,
	 * then this method calls {@link Repository#close()} on the underlying
	 * repository instance. (Whether this actually releases underlying
	 * resources, such as file handles, may vary; see {@link Repository} for
	 * more details.)
	 * <p>
	 * If the repository was created by a caller and passed into
	 * {@link #Git(Repository)} or a static factory method in this class, then
	 * this method does not call close on the underlying repository.
	 * <p>
	 * In all cases, after calling this method you should not use this
	 * {@link Git} instance anymore.
	 *
	 * @since 3.2
	 */
	@Override
	public void close() {
		if (closeRepo)
			repo.close();
	}

	/**
	 * Return a command object to execute a {@code clone} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-clone.html"
	 *      >Git documentation about clone</a>
	 * @return a {@link org.eclipse.jgit.api.CloneCommand} used to collect all
	 *         optional parameters and to finally execute the {@code clone}
	 *         command
	 */
	public static CloneCommand cloneRepository() {
		return new CloneCommand();
	}

	/**
	 * Return a command to list remote branches/tags without a local repository.
	 *
	 * @return a {@link org.eclipse.jgit.api.LsRemoteCommand}
	 * @since 3.1
	 */
	public static LsRemoteCommand lsRemoteRepository() {
		return new LsRemoteCommand(null);
	}

	/**
	 * Return a command object to execute a {@code init} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-init.html" >Git
	 *      documentation about init</a>
	 * @return a {@link org.eclipse.jgit.api.InitCommand} used to collect all
	 *         optional parameters and to finally execute the {@code init}
	 *         command
	 */
	public static InitCommand init() {
		return new InitCommand();
	}

	/**
	 * Shutdown JGit and release resources it holds like NLS and thread pools
	 * @since 5.8
	 */
	public static void shutdown() {
		WorkQueue.getExecutor().shutdownNow();
		NLS.clear();
	}

	/**
	 * Construct a new {@link org.eclipse.jgit.api.Git} object which can
	 * interact with the specified git repository.
	 * <p>
	 * All command classes returned by methods of this class will always
	 * interact with this git repository.
	 * <p>
	 * The caller is responsible for closing the repository; {@link #close()} on
	 * this instance does not close the repo.
	 *
	 * @param repo
	 *            the git repository this class is interacting with;
	 *            {@code null} is not allowed.
	 */
	public Git(Repository repo) {
		this(repo, false);
	}

	Git(Repository repo, boolean closeRepo) {
		this.repo = requireNonNull(repo);
		this.closeRepo = closeRepo;
	}

	/**
	 * Return a command object to execute a {@code Commit} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-commit.html"
	 *      >Git documentation about Commit</a>
	 * @return a {@link org.eclipse.jgit.api.CommitCommand} used to collect all
	 *         optional parameters and to finally execute the {@code Commit}
	 *         command
	 */
	public CommitCommand commit() {
		return new CommitCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code Log} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-log.html" >Git
	 *      documentation about Log</a>
	 * @return a {@link org.eclipse.jgit.api.LogCommand} used to collect all
	 *         optional parameters and to finally execute the {@code Log}
	 *         command
	 */
	public LogCommand log() {
		return new LogCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code Merge} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-merge.html"
	 *      >Git documentation about Merge</a>
	 * @return a {@link org.eclipse.jgit.api.MergeCommand} used to collect all
	 *         optional parameters and to finally execute the {@code Merge}
	 *         command
	 */
	public MergeCommand merge() {
		return new MergeCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code Pull} command
	 *
	 * @return a {@link org.eclipse.jgit.api.PullCommand}
	 */
	public PullCommand pull() {
		return new PullCommand(repo);
	}

	/**
	 * Return a command object used to create branches
	 *
	 * @return a {@link org.eclipse.jgit.api.CreateBranchCommand}
	 */
	public CreateBranchCommand branchCreate() {
		return new CreateBranchCommand(repo);
	}

	/**
	 * Return a command object used to delete branches
	 *
	 * @return a {@link org.eclipse.jgit.api.DeleteBranchCommand}
	 */
	public DeleteBranchCommand branchDelete() {
		return new DeleteBranchCommand(repo);
	}

	/**
	 * Return a command object used to list branches
	 *
	 * @return a {@link org.eclipse.jgit.api.ListBranchCommand}
	 */
	public ListBranchCommand branchList() {
		return new ListBranchCommand(repo);
	}

	/**
	 *
	 * Return a command object used to list tags
	 *
	 * @return a {@link org.eclipse.jgit.api.ListTagCommand}
	 */
	public ListTagCommand tagList() {
		return new ListTagCommand(repo);
	}

	/**
	 * Return a command object used to rename branches
	 *
	 * @return a {@link org.eclipse.jgit.api.RenameBranchCommand}
	 */
	public RenameBranchCommand branchRename() {
		return new RenameBranchCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code Add} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-add.html" >Git
	 *      documentation about Add</a>
	 * @return a {@link org.eclipse.jgit.api.AddCommand} used to collect all
	 *         optional parameters and to finally execute the {@code Add}
	 *         command
	 */
	public AddCommand add() {
		return new AddCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code Tag} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-tag.html" >Git
	 *      documentation about Tag</a>
	 * @return a {@link org.eclipse.jgit.api.TagCommand} used to collect all
	 *         optional parameters and to finally execute the {@code Tag}
	 *         command
	 */
	public TagCommand tag() {
		return new TagCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code Fetch} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-fetch.html"
	 *      >Git documentation about Fetch</a>
	 * @return a {@link org.eclipse.jgit.api.FetchCommand} used to collect all
	 *         optional parameters and to finally execute the {@code Fetch}
	 *         command
	 */
	public FetchCommand fetch() {
		return new FetchCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code Push} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-push.html" >Git
	 *      documentation about Push</a>
	 * @return a {@link org.eclipse.jgit.api.PushCommand} used to collect all
	 *         optional parameters and to finally execute the {@code Push}
	 *         command
	 */
	public PushCommand push() {
		return new PushCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code cherry-pick} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-cherry-pick.html"
	 *      >Git documentation about cherry-pick</a>
	 * @return a {@link org.eclipse.jgit.api.CherryPickCommand} used to collect
	 *         all optional parameters and to finally execute the
	 *         {@code cherry-pick} command
	 */
	public CherryPickCommand cherryPick() {
		return new CherryPickCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code revert} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-revert.html"
	 *      >Git documentation about reverting changes</a>
	 * @return a {@link org.eclipse.jgit.api.RevertCommand} used to collect all
	 *         optional parameters and to finally execute the
	 *         {@code cherry-pick} command
	 */
	public RevertCommand revert() {
		return new RevertCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code Rebase} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-rebase.html"
	 *      >Git documentation about rebase</a>
	 * @return a {@link org.eclipse.jgit.api.RebaseCommand} used to collect all
	 *         optional parameters and to finally execute the {@code rebase}
	 *         command
	 */
	public RebaseCommand rebase() {
		return new RebaseCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code rm} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-rm.html" >Git
	 *      documentation about rm</a>
	 * @return a {@link org.eclipse.jgit.api.RmCommand} used to collect all
	 *         optional parameters and to finally execute the {@code rm} command
	 */
	public RmCommand rm() {
		return new RmCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code checkout} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-checkout.html"
	 *      >Git documentation about checkout</a>
	 * @return a {@link org.eclipse.jgit.api.CheckoutCommand} used to collect
	 *         all optional parameters and to finally execute the
	 *         {@code checkout} command
	 */
	public CheckoutCommand checkout() {
		return new CheckoutCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code reset} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-reset.html"
	 *      >Git documentation about reset</a>
	 * @return a {@link org.eclipse.jgit.api.ResetCommand} used to collect all
	 *         optional parameters and to finally execute the {@code reset}
	 *         command
	 */
	public ResetCommand reset() {
		return new ResetCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code status} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-status.html"
	 *      >Git documentation about status</a>
	 * @return a {@link org.eclipse.jgit.api.StatusCommand} used to collect all
	 *         optional parameters and to finally execute the {@code status}
	 *         command
	 */
	public StatusCommand status() {
		return new StatusCommand(repo);
	}

	/**
	 * Return a command to create an archive from a tree
	 *
	 * @return a {@link org.eclipse.jgit.api.ArchiveCommand}
	 * @since 3.1
	 */
	public ArchiveCommand archive() {
		return new ArchiveCommand(repo);
	}

	/**
	 * Return a command to add notes to an object
	 *
	 * @return a {@link org.eclipse.jgit.api.AddNoteCommand}
	 */
	public AddNoteCommand notesAdd() {
		return new AddNoteCommand(repo);
	}

	/**
	 * Return a command to remove notes on an object
	 *
	 * @return a {@link org.eclipse.jgit.api.RemoveNoteCommand}
	 */
	public RemoveNoteCommand notesRemove() {
		return new RemoveNoteCommand(repo);
	}

	/**
	 * Return a command to list all notes
	 *
	 * @return a {@link org.eclipse.jgit.api.ListNotesCommand}
	 */
	public ListNotesCommand notesList() {
		return new ListNotesCommand(repo);
	}

	/**
	 * Return a command to show notes on an object
	 *
	 * @return a {@link org.eclipse.jgit.api.ShowNoteCommand}
	 */
	public ShowNoteCommand notesShow() {
		return new ShowNoteCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code ls-remote} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-ls-remote.html"
	 *      >Git documentation about ls-remote</a>
	 * @return a {@link org.eclipse.jgit.api.LsRemoteCommand} used to collect
	 *         all optional parameters and to finally execute the {@code status}
	 *         command
	 */
	public LsRemoteCommand lsRemote() {
		return new LsRemoteCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code clean} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-clean.html"
	 *      >Git documentation about Clean</a>
	 * @return a {@link org.eclipse.jgit.api.CleanCommand} used to collect all
	 *         optional parameters and to finally execute the {@code clean}
	 *         command
	 */
	public CleanCommand clean() {
		return new CleanCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code blame} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-blame.html"
	 *      >Git documentation about Blame</a>
	 * @return a {@link org.eclipse.jgit.api.BlameCommand} used to collect all
	 *         optional parameters and to finally execute the {@code blame}
	 *         command
	 */
	public BlameCommand blame() {
		return new BlameCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code reflog} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-reflog.html"
	 *      >Git documentation about reflog</a>
	 * @return a {@link org.eclipse.jgit.api.ReflogCommand} used to collect all
	 *         optional parameters and to finally execute the {@code reflog}
	 *         command
	 */
	public ReflogCommand reflog() {
		return new ReflogCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code diff} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-diff.html" >Git
	 *      documentation about diff</a>
	 * @return a {@link org.eclipse.jgit.api.DiffCommand} used to collect all
	 *         optional parameters and to finally execute the {@code diff}
	 *         command
	 */
	public DiffCommand diff() {
		return new DiffCommand(repo);
	}

	/**
	 * Return a command object used to delete tags
	 *
	 * @return a {@link org.eclipse.jgit.api.DeleteTagCommand}
	 */
	public DeleteTagCommand tagDelete() {
		return new DeleteTagCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code submodule add} command
	 *
	 * @return a {@link org.eclipse.jgit.api.SubmoduleAddCommand} used to add a
	 *         new submodule to a parent repository
	 */
	public SubmoduleAddCommand submoduleAdd() {
		return new SubmoduleAddCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code submodule init} command
	 *
	 * @return a {@link org.eclipse.jgit.api.SubmoduleInitCommand} used to
	 *         initialize the repository's config with settings from the
	 *         .gitmodules file in the working tree
	 */
	public SubmoduleInitCommand submoduleInit() {
		return new SubmoduleInitCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code submodule deinit} command
	 *
	 * @return a {@link org.eclipse.jgit.api.SubmoduleDeinitCommand} used to
	 *         remove a submodule's working tree manifestation
	 * @since 4.10
	 */
	public SubmoduleDeinitCommand submoduleDeinit() {
		return new SubmoduleDeinitCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code submodule status} command
	 *
	 * @return a {@link org.eclipse.jgit.api.SubmoduleStatusCommand} used to
	 *         report the status of a repository's configured submodules
	 */
	public SubmoduleStatusCommand submoduleStatus() {
		return new SubmoduleStatusCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code submodule sync} command
	 *
	 * @return a {@link org.eclipse.jgit.api.SubmoduleSyncCommand} used to
	 *         update the URL of a submodule from the parent repository's
	 *         .gitmodules file
	 */
	public SubmoduleSyncCommand submoduleSync() {
		return new SubmoduleSyncCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code submodule update} command
	 *
	 * @return a {@link org.eclipse.jgit.api.SubmoduleUpdateCommand} used to
	 *         update the submodules in a repository to the configured revision
	 */
	public SubmoduleUpdateCommand submoduleUpdate() {
		return new SubmoduleUpdateCommand(repo);
	}

	/**
	 * Return a command object used to list stashed commits
	 *
	 * @return a {@link org.eclipse.jgit.api.StashListCommand}
	 */
	public StashListCommand stashList() {
		return new StashListCommand(repo);
	}

	/**
	 * Return a command object used to create a stashed commit
	 *
	 * @return a {@link org.eclipse.jgit.api.StashCreateCommand}
	 * @since 2.0
	 */
	public StashCreateCommand stashCreate() {
		return new StashCreateCommand(repo);
	}

	/**
	 * Returs a command object used to apply a stashed commit
	 *
	 * @return a {@link org.eclipse.jgit.api.StashApplyCommand}
	 * @since 2.0
	 */
	public StashApplyCommand stashApply() {
		return new StashApplyCommand(repo);
	}

	/**
	 * Return a command object used to drop a stashed commit
	 *
	 * @return a {@link org.eclipse.jgit.api.StashDropCommand}
	 * @since 2.0
	 */
	public StashDropCommand stashDrop() {
		return new StashDropCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code apply} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-apply.html"
	 *      >Git documentation about apply</a>
	 * @return a {@link org.eclipse.jgit.api.ApplyCommand} used to collect all
	 *         optional parameters and to finally execute the {@code apply}
	 *         command
	 * @since 2.0
	 */
	public ApplyCommand apply() {
		return new ApplyCommand(repo);
	}

	/**
	 * Return a command object to execute a {@code gc} command
	 *
	 * @see <a href=
	 *      "http://www.kernel.org/pub/software/scm/git/docs/git-gc.html" >Git
	 *      documentation about gc</a>
	 * @return a {@link org.eclipse.jgit.api.GarbageCollectCommand} used to
	 *         collect all optional parameters and to finally execute the
	 *         {@code gc} command
	 * @since 2.2
	 */
	public GarbageCollectCommand gc() {
		return new GarbageCollectCommand(repo);
	}

	/**
	 * Return a command object to find human-readable names of revisions.
	 *
	 * @return a {@link org.eclipse.jgit.api.NameRevCommand}.
	 * @since 3.0
	 */
	public NameRevCommand nameRev() {
		return new NameRevCommand(repo);
	}

	/**
	 * Return a command object to come up with a short name that describes a
	 * commit in terms of the nearest git tag.
	 *
	 * @return a {@link org.eclipse.jgit.api.DescribeCommand}.
	 * @since 3.2
	 */
	public DescribeCommand describe() {
		return new DescribeCommand(repo);
	}

	/**
	 * Return a command used to list the available remotes.
	 *
	 * @return a {@link org.eclipse.jgit.api.RemoteListCommand}
	 * @since 4.2
	 */
	public RemoteListCommand remoteList() {
		return new RemoteListCommand(repo);
	}

	/**
	 * Return a command used to add a new remote.
	 *
	 * @return a {@link org.eclipse.jgit.api.RemoteAddCommand}
	 * @since 4.2
	 */
	public RemoteAddCommand remoteAdd() {
		return new RemoteAddCommand(repo);
	}

	/**
	 * Return a command used to remove an existing remote.
	 *
	 * @return a {@link org.eclipse.jgit.api.RemoteRemoveCommand}
	 * @since 4.2
	 */
	public RemoteRemoveCommand remoteRemove() {
		return new RemoteRemoveCommand(repo);
	}

	/**
	 * Return a command used to change the URL of an existing remote.
	 *
	 * @return a {@link org.eclipse.jgit.api.RemoteSetUrlCommand}
	 * @since 4.2
	 */
	public RemoteSetUrlCommand remoteSetUrl() {
		return new RemoteSetUrlCommand(repo);
	}

	/**
	 * Get repository
	 *
	 * @return the git repository this class is interacting with; see
	 *         {@link #close()} for notes on closing this repository.
	 */
	public Repository getRepository() {
		return repo;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return "Git[" + repo + "]"; //$NON-NLS-1$//$NON-NLS-2$
	}
}

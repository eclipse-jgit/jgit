/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
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

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.RepositoryCache;
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
public class Git {
	/** The git repository this class is interacting with */
	private final Repository repo;

	/**
	 * @param dir
	 *            the repository to open. May be either the GIT_DIR, or the
	 *            working tree directory that contains {@code .git}.
	 * @return a {@link Git} object for the existing git repository
	 * @throws IOException
	 */
	public static Git open(File dir) throws IOException {
		return open(dir, FS.DETECTED);
	}

	/**
	 * @param dir
	 *            the repository to open. May be either the GIT_DIR, or the
	 *            working tree directory that contains {@code .git}.
	 * @param fs
	 *            filesystem abstraction to use when accessing the repository.
	 * @return a {@link Git} object for the existing git repository
	 * @throws IOException
	 */
	public static Git open(File dir, FS fs) throws IOException {
		RepositoryCache.FileKey key;

		key = RepositoryCache.FileKey.lenient(dir, fs);
		return wrap(new RepositoryBuilder().setFS(fs).setGitDir(key.getFile())
				.setMustExist(true).build());
	}

	/**
	 * @param repo
	 *            the git repository this class is interacting with.
	 *            {@code null} is not allowed
	 * @return a {@link Git} object for the existing git repository
	 */
	public static Git wrap(Repository repo) {
		return new Git(repo);
	}

	/**
	 * Returns a command object to execute a {@code clone} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-clone.html"
	 *      >Git documentation about clone</a>
	 * @return a {@link CloneCommand} used to collect all optional parameters
	 *         and to finally execute the {@code clone} command
	 */
	public static CloneCommand cloneRepository() {
		return new CloneCommand();
	}

	/**
	 * Returns a command to list remote branches/tags without a local
	 * repository.
	 *
	 * @return a {@link LsRemoteCommand}
	 * @since 3.1
	 */
	public static LsRemoteCommand lsRemoteRepository() {
		return new LsRemoteCommand(null);
	}

	/**
	 * Returns a command object to execute a {@code init} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-init.html"
	 *      >Git documentation about init</a>
	 * @return a {@link InitCommand} used to collect all optional parameters and
	 *         to finally execute the {@code init} command
	 */
	public static InitCommand init() {
		return new InitCommand();
	}

	/**
	 * Constructs a new {@link Git} object which can interact with the specified
	 * git repository. All command classes returned by methods of this class
	 * will always interact with this git repository.
	 *
	 * @param repo
	 *            the git repository this class is interacting with.
	 *            {@code null} is not allowed
	 */
	public Git(Repository repo) {
		if (repo == null)
			throw new NullPointerException();
		this.repo = repo;
	}

	/**
	 * Returns a command object to execute a {@code Commit} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-commit.html"
	 *      >Git documentation about Commit</a>
	 * @return a {@link CommitCommand} used to collect all optional parameters
	 *         and to finally execute the {@code Commit} command
	 */
	public CommitCommand commit() {
		return new CommitCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code Log} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-log.html"
	 *      >Git documentation about Log</a>
	 * @return a {@link LogCommand} used to collect all optional parameters and
	 *         to finally execute the {@code Log} command
	 */
	public LogCommand log() {
		return new LogCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code Merge} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-merge.html"
	 *      >Git documentation about Merge</a>
	 * @return a {@link MergeCommand} used to collect all optional parameters
	 *         and to finally execute the {@code Merge} command
	 */
	public MergeCommand merge() {
		return new MergeCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code Pull} command
	 *
	 * @return a {@link PullCommand}
	 */
	public PullCommand pull() {
		return new PullCommand(repo);
	}

	/**
	 * Returns a command object used to create branches
	 *
	 * @return a {@link CreateBranchCommand}
	 */
	public CreateBranchCommand branchCreate() {
		return new CreateBranchCommand(repo);
	}

	/**
	 * Returns a command object used to delete branches
	 *
	 * @return a {@link DeleteBranchCommand}
	 */
	public DeleteBranchCommand branchDelete() {
		return new DeleteBranchCommand(repo);
	}

	/**
	 * Returns a command object used to list branches
	 *
	 * @return a {@link ListBranchCommand}
	 */
	public ListBranchCommand branchList() {
		return new ListBranchCommand(repo);
	}

	/**
	 *
	 * Returns a command object used to list tags
	 *
	 * @return a {@link ListTagCommand}
	 */
	public ListTagCommand tagList() {
		return new ListTagCommand(repo);
	}

	/**
	 * Returns a command object used to rename branches
	 *
	 * @return a {@link RenameBranchCommand}
	 */
	public RenameBranchCommand branchRename() {
		return new RenameBranchCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code Add} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-add.html"
	 *      >Git documentation about Add</a>
	 * @return a {@link AddCommand} used to collect all optional parameters and
	 *         to finally execute the {@code Add} command
	 */
	public AddCommand add() {
		return new AddCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code Tag} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-tag.html"
	 *      >Git documentation about Tag</a>
	 * @return a {@link TagCommand} used to collect all optional parameters and
	 *         to finally execute the {@code Tag} command
	 */
	public TagCommand tag() {
		return new TagCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code Fetch} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-fetch.html"
	 *      >Git documentation about Fetch</a>
	 * @return a {@link FetchCommand} used to collect all optional parameters
	 *         and to finally execute the {@code Fetch} command
	 */
	public FetchCommand fetch() {
		return new FetchCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code Push} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-push.html"
	 *      >Git documentation about Push</a>
	 * @return a {@link PushCommand} used to collect all optional parameters and
	 *         to finally execute the {@code Push} command
	 */
	public PushCommand push() {
		return new PushCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code cherry-pick} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-cherry-pick.html"
	 *      >Git documentation about cherry-pick</a>
	 * @return a {@link CherryPickCommand} used to collect all optional
	 *         parameters and to finally execute the {@code cherry-pick} command
	 */
	public CherryPickCommand cherryPick() {
		return new CherryPickCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code revert} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-revert.html"
	 *      >Git documentation about reverting changes</a>
	 * @return a {@link RevertCommand} used to collect all optional parameters
	 *         and to finally execute the {@code cherry-pick} command
	 */
	public RevertCommand revert() {
		return new RevertCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code Rebase} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-rebase.html"
	 *      >Git documentation about rebase</a>
	 * @return a {@link RebaseCommand} used to collect all optional parameters
	 *         and to finally execute the {@code rebase} command
	 */
	public RebaseCommand rebase() {
		return new RebaseCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code rm} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-rm.html"
	 *      >Git documentation about rm</a>
	 * @return a {@link RmCommand} used to collect all optional parameters and
	 *         to finally execute the {@code rm} command
	 */
	public RmCommand rm() {
		return new RmCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code checkout} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-checkout.html"
	 *      >Git documentation about checkout</a>
	 * @return a {@link CheckoutCommand} used to collect all optional parameters
	 *         and to finally execute the {@code checkout} command
	 */
	public CheckoutCommand checkout() {
		return new CheckoutCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code reset} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-reset.html"
	 *      >Git documentation about reset</a>
	 * @return a {@link ResetCommand} used to collect all optional parameters
	 *         and to finally execute the {@code reset} command
	 */
	public ResetCommand reset() {
		return new ResetCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code status} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-status.html"
	 *      >Git documentation about status</a>
	 * @return a {@link StatusCommand} used to collect all optional parameters
	 *         and to finally execute the {@code status} command
	 */
	public StatusCommand status() {
		return new StatusCommand(repo);
	}

	/**
	 * Returns a command to create an archive from a tree
	 *
	 * @return a {@link ArchiveCommand}
	 * @since 3.1
	 */
	public ArchiveCommand archive() {
		return new ArchiveCommand(repo);
	}

	/**
	 * Returns a command to add notes to an object
	 *
	 * @return a {@link AddNoteCommand}
	 */
	public AddNoteCommand notesAdd() {
		return new AddNoteCommand(repo);
	}

	/**
	 * Returns a command to remove notes on an object
	 *
	 * @return a {@link RemoveNoteCommand}
	 */
	public RemoveNoteCommand notesRemove() {
		return new RemoveNoteCommand(repo);
	}

	/**
	 * Returns a command to list all notes
	 *
	 * @return a {@link ListNotesCommand}
	 */
	public ListNotesCommand notesList() {
		return new ListNotesCommand(repo);
	}

	/**
	 * Returns a command to show notes on an object
	 *
	 * @return a {@link ShowNoteCommand}
	 */
	public ShowNoteCommand notesShow() {
		return new ShowNoteCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code ls-remote} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-ls-remote.html"
	 *      >Git documentation about ls-remote</a>
	 * @return a {@link LsRemoteCommand} used to collect all optional parameters
	 *         and to finally execute the {@code status} command
	 */
	public LsRemoteCommand lsRemote() {
		return new LsRemoteCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code clean} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-clean.html"
	 *      >Git documentation about Clean</a>
	 * @return a {@link CleanCommand} used to collect all optional parameters
	 *         and to finally execute the {@code clean} command
	 */
	public CleanCommand clean() {
		return new CleanCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code blame} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-blame.html"
	 *      >Git documentation about Blame</a>
	 * @return a {@link BlameCommand} used to collect all optional parameters
	 *         and to finally execute the {@code blame} command
	 */
	public BlameCommand blame() {
		return new BlameCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code reflog} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-reflog.html"
	 *      >Git documentation about reflog</a>
	 * @return a {@link ReflogCommand} used to collect all optional parameters
	 *         and to finally execute the {@code reflog} command
	 */
	public ReflogCommand reflog() {
		return new ReflogCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code diff} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-diff.html"
	 *      >Git documentation about diff</a>
	 * @return a {@link DiffCommand} used to collect all optional parameters and
	 *         to finally execute the {@code diff} command
	 */
	public DiffCommand diff() {
		return new DiffCommand(repo);
	}

	/**
	 * Returns a command object used to delete tags
	 *
	 * @return a {@link DeleteTagCommand}
	 */
	public DeleteTagCommand tagDelete() {
		return new DeleteTagCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code submodule add} command
	 *
	 * @return a {@link SubmoduleAddCommand} used to add a new submodule to a
	 *         parent repository
	 */
	public SubmoduleAddCommand submoduleAdd() {
		return new SubmoduleAddCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code submodule init} command
	 *
	 * @return a {@link SubmoduleInitCommand} used to initialize the
	 *         repository's config with settings from the .gitmodules file in
	 *         the working tree
	 */
	public SubmoduleInitCommand submoduleInit() {
		return new SubmoduleInitCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code submodule status} command
	 *
	 * @return a {@link SubmoduleStatusCommand} used to report the status of a
	 *         repository's configured submodules
	 */
	public SubmoduleStatusCommand submoduleStatus() {
		return new SubmoduleStatusCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code submodule sync} command
	 *
	 * @return a {@link SubmoduleSyncCommand} used to update the URL of a
	 *         submodule from the parent repository's .gitmodules file
	 */
	public SubmoduleSyncCommand submoduleSync() {
		return new SubmoduleSyncCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code submodule update} command
	 *
	 * @return a {@link SubmoduleUpdateCommand} used to update the submodules in
	 *         a repository to the configured revision
	 */
	public SubmoduleUpdateCommand submoduleUpdate() {
		return new SubmoduleUpdateCommand(repo);
	}

	/**
	 * Returns a command object used to list stashed commits
	 *
	 * @return a {@link StashListCommand}
	 */
	public StashListCommand stashList() {
		return new StashListCommand(repo);
	}

	/**
	 * Returns a command object used to create a stashed commit
	 *
	 * @return a {@link StashCreateCommand}
	 * @since 2.0
	 */
	public StashCreateCommand stashCreate() {
		return new StashCreateCommand(repo);
	}

	/**
	 * Returns a command object used to apply a stashed commit
	 *
	 * @return a {@link StashApplyCommand}
	 * @since 2.0
	 */
	public StashApplyCommand stashApply() {
		return new StashApplyCommand(repo);
	}

	/**
	 * Returns a command object used to drop a stashed commit
	 *
	 * @return a {@link StashDropCommand}
	 * @since 2.0
	 */
	public StashDropCommand stashDrop() {
		return new StashDropCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code apply} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-apply.html"
	 *      >Git documentation about apply</a>
	 *
	 * @return a {@link ApplyCommand} used to collect all optional parameters
	 *         and to finally execute the {@code apply} command
	 * @since 2.0
	 */
	public ApplyCommand apply() {
		return new ApplyCommand(repo);
	}

	/**
	 * Returns a command object to execute a {@code gc} command
	 *
	 * @see <a
	 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-gc.html"
	 *      >Git documentation about gc</a>
	 *
	 * @return a {@link GarbageCollectCommand} used to collect all optional
	 *         parameters and to finally execute the {@code gc} command
	 * @since 2.2
	 */
	public GarbageCollectCommand gc() {
		return new GarbageCollectCommand(repo);
	}

	/**
	 * Returns a command object to find human-readable names of revisions.
	 *
	 * @return a {@link NameRevCommand}.
	 * @since 3.0
	 */
	public NameRevCommand nameRev() {
		return new NameRevCommand(repo);
	}

	/**
	 * @return the git repository this class is interacting with
	 */
	public Repository getRepository() {
		return repo;
	}

}

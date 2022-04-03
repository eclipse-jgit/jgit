/*
 * Copyright (C) 2010-2012, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.EmptyCommitException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.ServiceUnavailableException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.UnsupportedSigningFormatException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuildIterator;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.hooks.CommitMsgHook;
import org.eclipse.jgit.hooks.Hooks;
import org.eclipse.jgit.hooks.PostCommitHook;
import org.eclipse.jgit.hooks.PreCommitHook;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.CommitConfig;
import org.eclipse.jgit.lib.CommitConfig.CleanupMode;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.GpgConfig.GpgFormat;
import org.eclipse.jgit.lib.GpgObjectSigner;
import org.eclipse.jgit.lib.GpgSigner;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk.OperationType;
import org.eclipse.jgit.util.ChangeIdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A class used to execute a {@code Commit} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command.
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-commit.html"
 *      >Git documentation about Commit</a>
 */
public class CommitCommand extends GitCommand<RevCommit> {
	private static final Logger log = LoggerFactory
			.getLogger(CommitCommand.class);

	private PersonIdent author;

	private PersonIdent committer;

	private String message;

	private boolean all;

	private List<String> only = new ArrayList<>();

	private boolean[] onlyProcessed;

	private boolean amend;

	private boolean insertChangeId;

	/**
	 * parents this commit should have. The current HEAD will be in this list
	 * and also all commits mentioned in .git/MERGE_HEAD
	 */
	private List<ObjectId> parents = new LinkedList<>();

	private String reflogComment;

	private boolean useDefaultReflogMessage = true;

	/**
	 * Setting this option bypasses the pre-commit and commit-msg hooks.
	 */
	private boolean noVerify;

	private HashMap<String, PrintStream> hookOutRedirect = new HashMap<>(3);

	private HashMap<String, PrintStream> hookErrRedirect = new HashMap<>(3);

	private Boolean allowEmpty;

	private Boolean signCommit;

	private String signingKey;

	private GpgSigner gpgSigner;

	private GpgConfig gpgConfig;

	private CredentialsProvider credentialsProvider;

	private @NonNull CleanupMode cleanupMode = CleanupMode.VERBATIM;

	private boolean cleanDefaultIsStrip = true;

	private Character commentChar;

	/**
	 * Constructor for CommitCommand
	 *
	 * @param repo
	 *            the {@link org.eclipse.jgit.lib.Repository}
	 */
	protected CommitCommand(Repository repo) {
		super(repo);
		this.credentialsProvider = CredentialsProvider.getDefault();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Executes the {@code commit} command with all the options and parameters
	 * collected by the setter methods of this class. Each instance of this
	 * class should only be used for one invocation of the command (means: one
	 * call to {@link #call()})
	 *
	 * @throws ServiceUnavailableException
	 *             if signing service is not available e.g. since it isn't
	 *             installed
	 */
	@Override
	public RevCommit call() throws GitAPIException, AbortedByHookException,
			ConcurrentRefUpdateException, NoHeadException, NoMessageException,
			ServiceUnavailableException, UnmergedPathsException,
			WrongRepositoryStateException {
		checkCallable();
		Collections.sort(only);

		try (RevWalk rw = new RevWalk(repo)) {
			RepositoryState state = repo.getRepositoryState();
			if (!state.canCommit())
				throw new WrongRepositoryStateException(MessageFormat.format(
						JGitText.get().cannotCommitOnARepoWithState,
						state.name()));

			if (!noVerify) {
				Hooks.preCommit(repo, hookOutRedirect.get(PreCommitHook.NAME),
						hookErrRedirect.get(PreCommitHook.NAME))
						.call();
			}

			processOptions(state, rw);

			if (all && !repo.isBare()) {
				try (Git git = new Git(repo)) {
					git.add().addFilepattern(".") //$NON-NLS-1$
							.setUpdate(true).call();
				} catch (NoFilepatternException e) {
					// should really not happen
					throw new JGitInternalException(e.getMessage(), e);
				}
			}

			Ref head = repo.exactRef(Constants.HEAD);
			if (head == null)
				throw new NoHeadException(
						JGitText.get().commitOnRepoWithoutHEADCurrentlyNotSupported);

			// determine the current HEAD and the commit it is referring to
			ObjectId headId = repo.resolve(Constants.HEAD + "^{commit}"); //$NON-NLS-1$
			if (headId == null && amend)
				throw new WrongRepositoryStateException(
						JGitText.get().commitAmendOnInitialNotPossible);

			if (headId != null) {
				if (amend) {
					RevCommit previousCommit = rw.parseCommit(headId);
					for (RevCommit p : previousCommit.getParents())
						parents.add(p.getId());
					if (author == null)
						author = previousCommit.getAuthorIdent();
				} else {
					parents.add(0, headId);
				}
			}
			if (!noVerify) {
				message = Hooks
						.commitMsg(repo,
								hookOutRedirect.get(CommitMsgHook.NAME),
								hookErrRedirect.get(CommitMsgHook.NAME))
						.setCommitMessage(message).call();
			}

			CommitConfig config = null;
			if (CleanupMode.DEFAULT.equals(cleanupMode)) {
				config = repo.getConfig().get(CommitConfig.KEY);
				cleanupMode = config.resolve(cleanupMode, cleanDefaultIsStrip);
			}
			char comments = (char) 0;
			if (CleanupMode.STRIP.equals(cleanupMode)
					|| CleanupMode.SCISSORS.equals(cleanupMode)) {
				if (commentChar == null) {
					if (config == null) {
						config = repo.getConfig().get(CommitConfig.KEY);
					}
					if (config.isAutoCommentChar()) {
						// We're supposed to pick a character that isn't used,
						// but then cleaning up won't remove any lines. So don't
						// bother.
						comments = (char) 0;
						cleanupMode = CleanupMode.WHITESPACE;
					} else {
						comments = config.getCommentChar();
					}
				} else {
					comments = commentChar.charValue();
				}
			}
			message = CommitConfig.cleanText(message, cleanupMode, comments);

			RevCommit revCommit;
			DirCache index = repo.lockDirCache();
			try (ObjectInserter odi = repo.newObjectInserter()) {
				if (!only.isEmpty())
					index = createTemporaryIndex(headId, index, rw);

				// Write the index as tree to the object database. This may
				// fail for example when the index contains unmerged paths
				// (unresolved conflicts)
				ObjectId indexTreeId = index.writeTree(odi);

				if (insertChangeId)
					insertChangeId(indexTreeId);

				checkIfEmpty(rw, headId, indexTreeId);

				// Create a Commit object, populate it and write it
				CommitBuilder commit = new CommitBuilder();
				commit.setCommitter(committer);
				commit.setAuthor(author);
				commit.setMessage(message);
				commit.setParentIds(parents);
				commit.setTreeId(indexTreeId);

				if (signCommit.booleanValue()) {
					sign(commit);
				}

				ObjectId commitId = odi.insert(commit);
				odi.flush();
				revCommit = rw.parseCommit(commitId);

				updateRef(state, headId, revCommit, commitId);
			} finally {
				index.unlock();
			}
			try {
				Hooks.postCommit(repo, hookOutRedirect.get(PostCommitHook.NAME),
						hookErrRedirect.get(PostCommitHook.NAME)).call();
			} catch (Exception e) {
				log.error(MessageFormat.format(
						JGitText.get().postCommitHookFailed, e.getMessage()),
						e);
			}
			return revCommit;
		} catch (UnmergedPathException e) {
			throw new UnmergedPathsException(e);
		} catch (IOException e) {
			throw new JGitInternalException(
					JGitText.get().exceptionCaughtDuringExecutionOfCommitCommand, e);
		}
	}

	private void checkIfEmpty(RevWalk rw, ObjectId headId, ObjectId indexTreeId)
			throws EmptyCommitException, MissingObjectException,
			IncorrectObjectTypeException, IOException {
		if (headId != null && !allowEmpty.booleanValue()) {
			RevCommit headCommit = rw.parseCommit(headId);
			headCommit.getTree();
			if (indexTreeId.equals(headCommit.getTree())) {
				throw new EmptyCommitException(JGitText.get().emptyCommit);
			}
		}
	}

	private void sign(CommitBuilder commit) throws ServiceUnavailableException,
			CanceledException, UnsupportedSigningFormatException {
		if (gpgSigner == null) {
			gpgSigner = GpgSigner.getDefault();
			if (gpgSigner == null) {
				throw new ServiceUnavailableException(
						JGitText.get().signingServiceUnavailable);
			}
		}
		if (signingKey == null) {
			signingKey = gpgConfig.getSigningKey();
		}
		if (gpgSigner instanceof GpgObjectSigner) {
			((GpgObjectSigner) gpgSigner).signObject(commit,
					signingKey, committer, credentialsProvider,
					gpgConfig);
		} else {
			if (gpgConfig.getKeyFormat() != GpgFormat.OPENPGP) {
				throw new UnsupportedSigningFormatException(JGitText
						.get().onlyOpenPgpSupportedForSigning);
			}
			gpgSigner.sign(commit, signingKey, committer,
					credentialsProvider);
		}
	}

	private void updateRef(RepositoryState state, ObjectId headId,
			RevCommit revCommit, ObjectId commitId)
			throws ConcurrentRefUpdateException, IOException {
		RefUpdate ru = repo.updateRef(Constants.HEAD);
		ru.setNewObjectId(commitId);
		if (!useDefaultReflogMessage) {
			ru.setRefLogMessage(reflogComment, false);
		} else {
			String prefix = amend ? "commit (amend): " //$NON-NLS-1$
					: parents.isEmpty() ? "commit (initial): " //$NON-NLS-1$
							: "commit: "; //$NON-NLS-1$
			ru.setRefLogMessage(prefix + revCommit.getShortMessage(),
					false);
		}
		if (headId != null) {
			ru.setExpectedOldObjectId(headId);
		} else {
			ru.setExpectedOldObjectId(ObjectId.zeroId());
		}
		Result rc = ru.forceUpdate();
		switch (rc) {
		case NEW:
		case FORCED:
		case FAST_FORWARD: {
			setCallable(false);
			if (state == RepositoryState.MERGING_RESOLVED
					|| isMergeDuringRebase(state)) {
				// Commit was successful. Now delete the files
				// used for merge commits
				repo.writeMergeCommitMsg(null);
				repo.writeMergeHeads(null);
			} else if (state == RepositoryState.CHERRY_PICKING_RESOLVED) {
				repo.writeMergeCommitMsg(null);
				repo.writeCherryPickHead(null);
			} else if (state == RepositoryState.REVERTING_RESOLVED) {
				repo.writeMergeCommitMsg(null);
				repo.writeRevertHead(null);
			}
			break;
		}
		case REJECTED:
		case LOCK_FAILURE:
			throw new ConcurrentRefUpdateException(
					JGitText.get().couldNotLockHEAD, ru.getRef(), rc);
		default:
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().updatingRefFailed, Constants.HEAD,
					commitId.toString(), rc));
		}
	}

	private void insertChangeId(ObjectId treeId) {
		ObjectId firstParentId = null;
		if (!parents.isEmpty())
			firstParentId = parents.get(0);
		ObjectId changeId = ChangeIdUtil.computeChangeId(treeId, firstParentId,
				author, committer, message);
		message = ChangeIdUtil.insertId(message, changeId);
		if (changeId != null)
			message = message.replaceAll("\nChange-Id: I" //$NON-NLS-1$
					+ ObjectId.zeroId().getName() + "\n", "\nChange-Id: I" //$NON-NLS-1$ //$NON-NLS-2$
					+ changeId.getName() + "\n"); //$NON-NLS-1$
	}

	private DirCache createTemporaryIndex(ObjectId headId, DirCache index,
			RevWalk rw)
			throws IOException {
		ObjectInserter inserter = null;

		// get DirCacheBuilder for existing index
		DirCacheBuilder existingBuilder = index.builder();

		// get DirCacheBuilder for newly created in-core index to build a
		// temporary index for this commit
		DirCache inCoreIndex = DirCache.newInCore();
		DirCacheBuilder tempBuilder = inCoreIndex.builder();

		onlyProcessed = new boolean[only.size()];
		boolean emptyCommit = true;

		try (TreeWalk treeWalk = new TreeWalk(repo)) {
			treeWalk.setOperationType(OperationType.CHECKIN_OP);
			int dcIdx = treeWalk
					.addTree(new DirCacheBuildIterator(existingBuilder));
			FileTreeIterator fti = new FileTreeIterator(repo);
			fti.setDirCacheIterator(treeWalk, 0);
			int fIdx = treeWalk.addTree(fti);
			int hIdx = -1;
			if (headId != null)
				hIdx = treeWalk.addTree(rw.parseTree(headId));
			treeWalk.setRecursive(true);

			String lastAddedFile = null;
			while (treeWalk.next()) {
				String path = treeWalk.getPathString();
				// check if current entry's path matches a specified path
				int pos = lookupOnly(path);

				CanonicalTreeParser hTree = null;
				if (hIdx != -1)
					hTree = treeWalk.getTree(hIdx, CanonicalTreeParser.class);

				DirCacheIterator dcTree = treeWalk.getTree(dcIdx,
						DirCacheIterator.class);

				if (pos >= 0) {
					// include entry in commit

					FileTreeIterator fTree = treeWalk.getTree(fIdx,
							FileTreeIterator.class);

					// check if entry refers to a tracked file
					boolean tracked = dcTree != null || hTree != null;
					if (!tracked)
						continue;

					// for an unmerged path, DirCacheBuildIterator will yield 3
					// entries, we only want to add one
					if (path.equals(lastAddedFile))
						continue;

					lastAddedFile = path;

					if (fTree != null) {
						// create a new DirCacheEntry with data retrieved from
						// disk
						final DirCacheEntry dcEntry = new DirCacheEntry(path);
						long entryLength = fTree.getEntryLength();
						dcEntry.setLength(entryLength);
						dcEntry.setLastModified(fTree.getEntryLastModifiedInstant());
						dcEntry.setFileMode(fTree.getIndexFileMode(dcTree));

						boolean objectExists = (dcTree != null
								&& fTree.idEqual(dcTree))
								|| (hTree != null && fTree.idEqual(hTree));
						if (objectExists) {
							dcEntry.setObjectId(fTree.getEntryObjectId());
						} else {
							if (FileMode.GITLINK.equals(dcEntry.getFileMode()))
								dcEntry.setObjectId(fTree.getEntryObjectId());
							else {
								// insert object
								if (inserter == null)
									inserter = repo.newObjectInserter();
								long contentLength = fTree
										.getEntryContentLength();
								try (InputStream inputStream = fTree
										.openEntryStream()) {
									dcEntry.setObjectId(inserter.insert(
											Constants.OBJ_BLOB, contentLength,
											inputStream));
								}
							}
						}

						// add to existing index
						existingBuilder.add(dcEntry);
						// add to temporary in-core index
						tempBuilder.add(dcEntry);

						if (emptyCommit
								&& (hTree == null || !hTree.idEqual(fTree)
										|| hTree.getEntryRawMode() != fTree
												.getEntryRawMode()))
							// this is a change
							emptyCommit = false;
					} else {
						// if no file exists on disk, neither add it to
						// index nor to temporary in-core index

						if (emptyCommit && hTree != null)
							// this is a change
							emptyCommit = false;
					}

					// keep track of processed path
					onlyProcessed[pos] = true;
				} else {
					// add entries from HEAD for all other paths
					if (hTree != null) {
						// create a new DirCacheEntry with data retrieved from
						// HEAD
						final DirCacheEntry dcEntry = new DirCacheEntry(path);
						dcEntry.setObjectId(hTree.getEntryObjectId());
						dcEntry.setFileMode(hTree.getEntryFileMode());

						// add to temporary in-core index
						tempBuilder.add(dcEntry);
					}

					// preserve existing entry in index
					if (dcTree != null)
						existingBuilder.add(dcTree.getDirCacheEntry());
				}
			}
		}

		// there must be no unprocessed paths left at this point; otherwise an
		// untracked or unknown path has been specified
		for (int i = 0; i < onlyProcessed.length; i++)
			if (!onlyProcessed[i])
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().entryNotFoundByPath, only.get(i)));

		// there must be at least one change
		if (emptyCommit && !allowEmpty.booleanValue())
			// Would like to throw a EmptyCommitException. But this would break the API
			// TODO(ch): Change this in the next release
			throw new JGitInternalException(JGitText.get().emptyCommit);

		// update index
		existingBuilder.commit();
		// finish temporary in-core index used for this commit
		tempBuilder.finish();
		return inCoreIndex;
	}

	/**
	 * Look an entry's path up in the list of paths specified by the --only/ -o
	 * option
	 *
	 * In case the complete (file) path (e.g. "d1/d2/f1") cannot be found in
	 * <code>only</code>, lookup is also tried with (parent) directory paths
	 * (e.g. "d1/d2" and "d1").
	 *
	 * @param pathString
	 *            entry's path
	 * @return the item's index in <code>only</code>; -1 if no item matches
	 */
	private int lookupOnly(String pathString) {
		String p = pathString;
		while (true) {
			int position = Collections.binarySearch(only, p);
			if (position >= 0)
				return position;
			int l = p.lastIndexOf('/');
			if (l < 1)
				break;
			p = p.substring(0, l);
		}
		return -1;
	}

	/**
	 * Sets default values for not explicitly specified options. Then validates
	 * that all required data has been provided.
	 *
	 * @param state
	 *            the state of the repository we are working on
	 * @param rw
	 *            the RevWalk to use
	 *
	 * @throws NoMessageException
	 *             if the commit message has not been specified
	 * @throws UnsupportedSigningFormatException
	 *             if the configured gpg.format is not supported
	 */
	private void processOptions(RepositoryState state, RevWalk rw)
			throws NoMessageException, UnsupportedSigningFormatException {
		if (committer == null)
			committer = new PersonIdent(repo);
		if (author == null && !amend)
			author = committer;
		if (allowEmpty == null)
			// JGit allows empty commits by default. Only when pathes are
			// specified the commit should not be empty. This behaviour differs
			// from native git but can only be adapted in the next release.
			// TODO(ch) align the defaults with native git
			allowEmpty = (only.isEmpty()) ? Boolean.TRUE : Boolean.FALSE;

		// when doing a merge commit parse MERGE_HEAD and MERGE_MSG files
		if (state == RepositoryState.MERGING_RESOLVED
				|| isMergeDuringRebase(state)) {
			try {
				parents = repo.readMergeHeads();
				if (parents != null)
					for (int i = 0; i < parents.size(); i++) {
						RevObject ro = rw.parseAny(parents.get(i));
						if (ro instanceof RevTag)
							parents.set(i, rw.peel(ro));
					}
			} catch (IOException e) {
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().exceptionOccurredDuringReadingOfGIT_DIR,
						Constants.MERGE_HEAD, e), e);
			}
			if (message == null) {
				try {
					message = repo.readMergeCommitMsg();
				} catch (IOException e) {
					throw new JGitInternalException(MessageFormat.format(
							JGitText.get().exceptionOccurredDuringReadingOfGIT_DIR,
							Constants.MERGE_MSG, e), e);
				}
			}
		} else if (state == RepositoryState.SAFE && message == null) {
			try {
				message = repo.readSquashCommitMsg();
				if (message != null)
					repo.writeSquashCommitMsg(null /* delete */);
			} catch (IOException e) {
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().exceptionOccurredDuringReadingOfGIT_DIR,
						Constants.MERGE_MSG, e), e);
			}

		}
		if (message == null)
			// as long as we don't support -C option we have to have
			// an explicit message
			throw new NoMessageException(JGitText.get().commitMessageNotSpecified);

		if (gpgConfig == null) {
			gpgConfig = new GpgConfig(repo.getConfig());
		}
		if (signCommit == null) {
			signCommit = gpgConfig.isSignCommits() ? Boolean.TRUE
					: Boolean.FALSE;
		}
	}

	private boolean isMergeDuringRebase(RepositoryState state) {
		if (state != RepositoryState.REBASING_INTERACTIVE
				&& state != RepositoryState.REBASING_MERGE)
			return false;
		try {
			return repo.readMergeHeads() != null;
		} catch (IOException e) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().exceptionOccurredDuringReadingOfGIT_DIR,
					Constants.MERGE_HEAD, e), e);
		}
	}

	/**
	 * Set the commit message
	 *
	 * @param message
	 *            the commit message used for the {@code commit}
	 * @return {@code this}
	 */
	public CommitCommand setMessage(String message) {
		checkCallable();
		this.message = message;
		return this;
	}

	/**
	 * Sets the {@link CleanupMode} to apply to the commit message. If not
	 * called, {@link CommitCommand} applies {@link CleanupMode#VERBATIM}.
	 *
	 * @param mode
	 *            {@link CleanupMode} to set
	 * @return {@code this}
	 * @since 6.1
	 */
	public CommitCommand setCleanupMode(@NonNull CleanupMode mode) {
		checkCallable();
		this.cleanupMode = mode;
		return this;
	}

	/**
	 * Sets the default clean mode if {@link #setCleanupMode(CleanupMode)
	 * setCleanupMode(CleanupMode.DEFAULT)} is set and git config
	 * {@code commit.cleanup = default} or is not set.
	 *
	 * @param strip
	 *            if {@code true}, default to {@link CleanupMode#STRIP};
	 *            otherwise default to {@link CleanupMode#WHITESPACE}
	 * @return {@code this}
	 * @since 6.1
	 */
	public CommitCommand setDefaultClean(boolean strip) {
		checkCallable();
		this.cleanDefaultIsStrip = strip;
		return this;
	}

	/**
	 * Sets the comment character to apply when cleaning a commit message. If
	 * {@code null} (the default) and the {@link #setCleanupMode(CleanupMode)
	 * clean-up mode} is {@link CleanupMode#STRIP} or
	 * {@link CleanupMode#SCISSORS}, the value of git config
	 * {@code core.commentChar} will be used.
	 *
	 * @param commentChar
	 *            the comment character, or {@code null} to use the value from
	 *            the git config
	 * @return {@code this}
	 * @since 6.1
	 */
	public CommitCommand setCommentCharacter(Character commentChar) {
		checkCallable();
		this.commentChar = commentChar;
		return this;
	}

	/**
	 * Set whether to allow to create an empty commit
	 *
	 * @param allowEmpty
	 *            whether it should be allowed to create a commit which has the
	 *            same tree as it's sole predecessor (a commit which doesn't
	 *            change anything). By default when creating standard commits
	 *            (without specifying paths) JGit allows to create such commits.
	 *            When this flag is set to false an attempt to create an "empty"
	 *            standard commit will lead to an EmptyCommitException.
	 *            <p>
	 *            By default when creating a commit containing only specified
	 *            paths an attempt to create an empty commit leads to a
	 *            {@link org.eclipse.jgit.api.errors.JGitInternalException}. By
	 *            setting this flag to <code>true</code> this exception will not
	 *            be thrown.
	 * @return {@code this}
	 * @since 4.2
	 */
	public CommitCommand setAllowEmpty(boolean allowEmpty) {
		this.allowEmpty = Boolean.valueOf(allowEmpty);
		return this;
	}

	/**
	 * Get the commit message
	 *
	 * @return the commit message used for the <code>commit</code>
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Sets the committer for this {@code commit}. If no committer is explicitly
	 * specified because this method is never called or called with {@code null}
	 * value then the committer will be deduced from config info in repository,
	 * with current time.
	 *
	 * @param committer
	 *            the committer used for the {@code commit}
	 * @return {@code this}
	 */
	public CommitCommand setCommitter(PersonIdent committer) {
		checkCallable();
		this.committer = committer;
		return this;
	}

	/**
	 * Sets the committer for this {@code commit}. If no committer is explicitly
	 * specified because this method is never called then the committer will be
	 * deduced from config info in repository, with current time.
	 *
	 * @param name
	 *            the name of the committer used for the {@code commit}
	 * @param email
	 *            the email of the committer used for the {@code commit}
	 * @return {@code this}
	 */
	public CommitCommand setCommitter(String name, String email) {
		checkCallable();
		return setCommitter(new PersonIdent(name, email));
	}

	/**
	 * Get the committer
	 *
	 * @return the committer used for the {@code commit}. If no committer was
	 *         specified {@code null} is returned and the default
	 *         {@link org.eclipse.jgit.lib.PersonIdent} of this repo is used
	 *         during execution of the command
	 */
	public PersonIdent getCommitter() {
		return committer;
	}

	/**
	 * Sets the author for this {@code commit}. If no author is explicitly
	 * specified because this method is never called or called with {@code null}
	 * value then the author will be set to the committer or to the original
	 * author when amending.
	 *
	 * @param author
	 *            the author used for the {@code commit}
	 * @return {@code this}
	 */
	public CommitCommand setAuthor(PersonIdent author) {
		checkCallable();
		this.author = author;
		return this;
	}

	/**
	 * Sets the author for this {@code commit}. If no author is explicitly
	 * specified because this method is never called then the author will be set
	 * to the committer or to the original author when amending.
	 *
	 * @param name
	 *            the name of the author used for the {@code commit}
	 * @param email
	 *            the email of the author used for the {@code commit}
	 * @return {@code this}
	 */
	public CommitCommand setAuthor(String name, String email) {
		checkCallable();
		return setAuthor(new PersonIdent(name, email));
	}

	/**
	 * Get the author
	 *
	 * @return the author used for the {@code commit}. If no author was
	 *         specified {@code null} is returned and the default
	 *         {@link org.eclipse.jgit.lib.PersonIdent} of this repo is used
	 *         during execution of the command
	 */
	public PersonIdent getAuthor() {
		return author;
	}

	/**
	 * If set to true the Commit command automatically stages files that have
	 * been modified and deleted, but new files not known by the repository are
	 * not affected. This corresponds to the parameter -a on the command line.
	 *
	 * @param all
	 *            whether to auto-stage all files that have been modified and
	 *            deleted
	 * @return {@code this}
	 * @throws JGitInternalException
	 *             in case of an illegal combination of arguments/ options
	 */
	public CommitCommand setAll(boolean all) {
		checkCallable();
		if (all && !only.isEmpty())
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().illegalCombinationOfArguments, "--all", //$NON-NLS-1$
					"--only")); //$NON-NLS-1$
		this.all = all;
		return this;
	}

	/**
	 * Used to amend the tip of the current branch. If set to {@code true}, the
	 * previous commit will be amended. This is equivalent to --amend on the
	 * command line.
	 *
	 * @param amend
	 *            whether to amend the tip of the current branch
	 * @return {@code this}
	 */
	public CommitCommand setAmend(boolean amend) {
		checkCallable();
		this.amend = amend;
		return this;
	}

	/**
	 * Commit dedicated path only.
	 * <p>
	 * This method can be called several times to add multiple paths. Full file
	 * paths are supported as well as directory paths; in the latter case this
	 * commits all files/directories below the specified path.
	 *
	 * @param only
	 *            path to commit (with <code>/</code> as separator)
	 * @return {@code this}
	 */
	public CommitCommand setOnly(String only) {
		checkCallable();
		if (all)
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().illegalCombinationOfArguments, "--only", //$NON-NLS-1$
					"--all")); //$NON-NLS-1$
		String o = only.endsWith("/") ? only.substring(0, only.length() - 1) //$NON-NLS-1$
				: only;
		// ignore duplicates
		if (!this.only.contains(o))
			this.only.add(o);
		return this;
	}

	/**
	 * If set to true a change id will be inserted into the commit message
	 *
	 * An existing change id is not replaced. An initial change id (I000...)
	 * will be replaced by the change id.
	 *
	 * @param insertChangeId
	 *            whether to insert a change id
	 * @return {@code this}
	 */
	public CommitCommand setInsertChangeId(boolean insertChangeId) {
		checkCallable();
		this.insertChangeId = insertChangeId;
		return this;
	}

	/**
	 * Override the message written to the reflog
	 *
	 * @param reflogComment
	 *            the comment to be written into the reflog or <code>null</code>
	 *            to specify that no reflog should be written
	 * @return {@code this}
	 */
	public CommitCommand setReflogComment(String reflogComment) {
		this.reflogComment = reflogComment;
		useDefaultReflogMessage = false;
		return this;
	}

	/**
	 * Sets the {@link #noVerify} option on this commit command.
	 * <p>
	 * Both the pre-commit and commit-msg hooks can block a commit by their
	 * return value; setting this option to <code>true</code> will bypass these
	 * two hooks.
	 * </p>
	 *
	 * @param noVerify
	 *            Whether this commit should be verified by the pre-commit and
	 *            commit-msg hooks.
	 * @return {@code this}
	 * @since 3.7
	 */
	public CommitCommand setNoVerify(boolean noVerify) {
		this.noVerify = noVerify;
		return this;
	}

	/**
	 * Set the output stream for all hook scripts executed by this command
	 * (pre-commit, commit-msg, post-commit). If not set it defaults to
	 * {@code System.out}.
	 *
	 * @param hookStdOut
	 *            the output stream for hook scripts executed by this command
	 * @return {@code this}
	 * @since 3.7
	 */
	public CommitCommand setHookOutputStream(PrintStream hookStdOut) {
		setHookOutputStream(PreCommitHook.NAME, hookStdOut);
		setHookOutputStream(CommitMsgHook.NAME, hookStdOut);
		setHookOutputStream(PostCommitHook.NAME, hookStdOut);
		return this;
	}

	/**
	 * Set the error stream for all hook scripts executed by this command
	 * (pre-commit, commit-msg, post-commit). If not set it defaults to
	 * {@code System.err}.
	 *
	 * @param hookStdErr
	 *            the error stream for hook scripts executed by this command
	 * @return {@code this}
	 * @since 5.6
	 */
	public CommitCommand setHookErrorStream(PrintStream hookStdErr) {
		setHookErrorStream(PreCommitHook.NAME, hookStdErr);
		setHookErrorStream(CommitMsgHook.NAME, hookStdErr);
		setHookErrorStream(PostCommitHook.NAME, hookStdErr);
		return this;
	}

	/**
	 * Set the output stream for a selected hook script executed by this command
	 * (pre-commit, commit-msg, post-commit). If not set it defaults to
	 * {@code System.out}.
	 *
	 * @param hookName
	 *            name of the hook to set the output stream for
	 * @param hookStdOut
	 *            the output stream to use for the selected hook
	 * @return {@code this}
	 * @since 4.5
	 */
	public CommitCommand setHookOutputStream(String hookName,
			PrintStream hookStdOut) {
		if (!(PreCommitHook.NAME.equals(hookName)
				|| CommitMsgHook.NAME.equals(hookName)
				|| PostCommitHook.NAME.equals(hookName))) {
			throw new IllegalArgumentException(
					MessageFormat.format(JGitText.get().illegalHookName,
							hookName));
		}
		hookOutRedirect.put(hookName, hookStdOut);
		return this;
	}

	/**
	 * Set the error stream for a selected hook script executed by this command
	 * (pre-commit, commit-msg, post-commit). If not set it defaults to
	 * {@code System.err}.
	 *
	 * @param hookName
	 *            name of the hook to set the output stream for
	 * @param hookStdErr
	 *            the output stream to use for the selected hook
	 * @return {@code this}
	 * @since 5.6
	 */
	public CommitCommand setHookErrorStream(String hookName,
			PrintStream hookStdErr) {
		if (!(PreCommitHook.NAME.equals(hookName)
				|| CommitMsgHook.NAME.equals(hookName)
				|| PostCommitHook.NAME.equals(hookName))) {
			throw new IllegalArgumentException(MessageFormat
					.format(JGitText.get().illegalHookName, hookName));
		}
		hookErrRedirect.put(hookName, hookStdErr);
		return this;
	}

	/**
	 * Sets the signing key
	 * <p>
	 * Per spec of user.signingKey: this will be sent to the GPG program as is,
	 * i.e. can be anything supported by the GPG program.
	 * </p>
	 * <p>
	 * Note, if none was set or <code>null</code> is specified a default will be
	 * obtained from the configuration.
	 * </p>
	 *
	 * @param signingKey
	 *            signing key (maybe <code>null</code>)
	 * @return {@code this}
	 * @since 5.3
	 */
	public CommitCommand setSigningKey(String signingKey) {
		checkCallable();
		this.signingKey = signingKey;
		return this;
	}

	/**
	 * Sets whether the commit should be signed.
	 *
	 * @param sign
	 *            <code>true</code> to sign, <code>false</code> to not sign and
	 *            <code>null</code> for default behavior (read from
	 *            configuration)
	 * @return {@code this}
	 * @since 5.3
	 */
	public CommitCommand setSign(Boolean sign) {
		checkCallable();
		this.signCommit = sign;
		return this;
	}

	/**
	 * Sets the {@link GpgSigner} to use if the commit is to be signed.
	 *
	 * @param signer
	 *            to use; if {@code null}, the default signer will be used
	 * @return {@code this}
	 * @since 5.11
	 */
	public CommitCommand setGpgSigner(GpgSigner signer) {
		checkCallable();
		this.gpgSigner = signer;
		return this;
	}

	/**
	 * Sets an external {@link GpgConfig} to use. Whether it will be used is at
	 * the discretion of the {@link #setGpgSigner(GpgSigner)}.
	 *
	 * @param config
	 *            to set; if {@code null}, the config will be loaded from the
	 *            git config of the repository
	 * @return {@code this}
	 * @since 5.11
	 */
	public CommitCommand setGpgConfig(GpgConfig config) {
		checkCallable();
		this.gpgConfig = config;
		return this;
	}

	/**
	 * Sets a {@link CredentialsProvider}
	 *
	 * @param credentialsProvider
	 *            the provider to use when querying for credentials (eg., during
	 *            signing)
	 * @return {@code this}
	 * @since 6.0
	 */
	public CommitCommand setCredentialsProvider(
			CredentialsProvider credentialsProvider) {
		this.credentialsProvider = credentialsProvider;
		return this;
	}
}

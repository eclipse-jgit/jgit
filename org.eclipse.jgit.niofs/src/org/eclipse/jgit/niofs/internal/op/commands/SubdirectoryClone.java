/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.commands;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.RemoteRemoveCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.internal.ketch.KetchLeaderCache;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotEmpty;
import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotNull;

/**
 * Copies a single subdirectory of a repository, preserving all relevant
 * history.
 */
public class SubdirectoryClone {

	private final File repoDir;
	private final String origin;
	private final CredentialsProvider credentialsProvider;
	private final KetchLeaderCache leaders;
	private final File hookDir;
	private final boolean sslVerify;

	private Logger logger = LoggerFactory.getLogger(SubdirectoryClone.class);
	private List<String> branches;
	private String subdirectory;

	/**
	 * @param directory           Directory for the local target repository (created
	 *                            by this command). Must not be null.
	 * @param origin              URI for the repository being cloned. Must not be
	 *                            null.
	 * @param subdirectory        The subdirectory within the origin being copied.
	 *                            Must not be null.
	 * @param branches            The branches that should be copied. Must not be
	 *                            null.
	 * @param credentialsProvider Provides credentials for the initial cloning of
	 *                            the origin. May be null.
	 * @param leaders             Used for initial cloning. May be null.
	 * @param hookDir             Used to specify the directory containing the Git
	 *                            Hooks to add to the repository. May be null.
	 */
	public SubdirectoryClone(final File directory, final String origin, final String subdirectory,
			final List<String> branches, final CredentialsProvider credentialsProvider, final KetchLeaderCache leaders,
			final File hookDir) {
		this(directory, origin, subdirectory, branches, credentialsProvider, leaders, hookDir,
				JGitFileSystemProviderConfiguration.DEFAULT_GIT_HTTP_SSL_VERIFY);
	}

	/**
	 * @param directory           Directory for the local target repository (created
	 *                            by this command). Must not be null.
	 * @param origin              URI for the repository being cloned. Must not be
	 *                            null.
	 * @param subdirectory        The subdirectory within the origin being copied.
	 *                            Must not be null.
	 * @param branches            The branches that should be copied. Must not be
	 *                            null.
	 * @param credentialsProvider Provides credentials for the initial cloning of
	 *                            the origin. May be null.
	 * @param leaders             Used for initial cloning. May be null.
	 * @param hookDir             Used to specify the directory containing the Git
	 *                            Hooks to add to the repository. May be null.
	 * @param sslVerify           Used to disable http ssl verify on the repository
	 */
	public SubdirectoryClone(final File directory, final String origin, final String subdirectory,
			final List<String> branches, final CredentialsProvider credentialsProvider, final KetchLeaderCache leaders,
			final File hookDir, final boolean sslVerify) {
		this.subdirectory = ensureTrailingSlash(subdirectory);
		this.branches = branches;
		this.repoDir = checkNotNull("directory", directory);
		this.origin = checkNotEmpty("origin", origin);
		this.credentialsProvider = credentialsProvider;
		this.leaders = leaders;
		this.hookDir = hookDir;
		this.sslVerify = sslVerify;
	}

	private static String ensureTrailingSlash(String subdirectory) {
		if (subdirectory.endsWith("/")) {
			return subdirectory;
		} else {
			return subdirectory + "/";
		}
	}

	public Git execute() throws IOException {
		final Git git = new Clone(repoDir, origin, false, branches, credentialsProvider, leaders, hookDir, sslVerify)
				.execute().get();
		final Repository repository = git.getRepository();

		try (final ObjectReader reader = repository.newObjectReader();
				final ObjectInserter inserter = repository.newObjectInserter()) {
			// Map all transformed commits that are non-empty so that we can properly map
			// parents
			final Map<ObjectId, ObjectId> commitMap = new HashMap<>();
			final RevWalk revWalk = createRevWalk(repository, reader);
			transformBranches(repository, reader, inserter, revWalk, commitMap);
			overrideBranchNames(repository, revWalk, commitMap);

			removeOriginRemote(repository);

			return git;
		} catch (Exception e) {
			String message = String.format("Error cloning origin <%s> with subdirectory <%s>.", origin, subdirectory);
			logger.error(message);
			cleanupDir(git.getRepository().getDirectory());
			throw new Clone.CloneException(message, e);
		}
	}

	private void removeOriginRemote(Repository repository) throws GitAPIException {
		final org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.wrap(repository);
		final RemoteRemoveCommand cmd = git.remoteRemove();
		cmd.setRemoteName(origin);
		cmd.call();
	}

	private void overrideBranchNames(final Repository repository, final RevWalk revWalk,
			final Map<ObjectId, ObjectId> commitMap)
			throws AmbiguousObjectException, IncorrectObjectTypeException, IOException, MissingObjectException,
			GitAPIException, RefAlreadyExistsException, RefNotFoundException, InvalidRefNameException {
		for (String branchName : branches) {
			if (branchName.equals("HEAD")) {
				continue;
			}

			final ObjectId oldBranchTipId = repository.resolve(branchName);
			final ObjectId newBranchTipId = closestMappedAncestorOrSelf(commitMap,
					revWalk.parseCommit(oldBranchTipId))[0];
			final RevCommit newBranchTip = revWalk.parseCommit(newBranchTipId);
			org.eclipse.jgit.api.Git.wrap(repository).branchCreate().setName(branchName).setForce(true)
					.setStartPoint(newBranchTip).setUpstreamMode(SetupUpstreamMode.NOTRACK).call();
		}
	}

	private void transformBranches(final Repository repository, final ObjectReader reader,
			final ObjectInserter inserter, final RevWalk revWalk, final Map<ObjectId, ObjectId> commitMap)
			throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, IOException,
			UnmergedPathException {
		for (final RevCommit commit : revWalk) {
			try {
				final Optional<ObjectId> oNewCommitTree = filterCommitTree(reader, inserter, commit);
				if (oNewCommitTree.isPresent()) {
					final ObjectId newCommitTree = oNewCommitTree.get();
					final CommitBuilder commitBuilder = generateNewCommit(commitMap, commit, newCommitTree);
					final ObjectId newCommitId = inserter.insert(commitBuilder);

					/*
					 * We want to prune empty commits (i.e. no changes from parent), which will
					 * exist whenever an origin commit did not touch files in the subdirectory.
					 *
					 * But we don't want to remove merge commits.
					 */
					if (isOrphanCommit(commitBuilder) || isMergeCommit(commitBuilder)
							|| isDifferentFromParent(revWalk, commitBuilder)) {
						commitMap.put(commit.getId(), newCommitId);
					}
				}
			} catch (Throwable t) {
				throw new RuntimeException(String.format("Problem occurred for commit [%s].", commit.getId().name()),
						t);
			}
		}
	}

	private boolean isOrphanCommit(final CommitBuilder commitBuilder) {
		return commitBuilder.getParentIds().length == 0;
	}

	private boolean isDifferentFromParent(final RevWalk revWalk, final CommitBuilder commitBuilder)
			throws MissingObjectException, IncorrectObjectTypeException, IOException {
		final ObjectId parentId = commitBuilder.getParentIds()[0];
		final RevCommit parentCommit = revWalk.parseCommit(parentId);
		final ObjectId parentTreeId = parentCommit.getTree().getId();
		final ObjectId commitTreeId = commitBuilder.getTreeId();
		// A commit with the same tree as its parent has no changes.
		return !commitTreeId.equals(parentTreeId);
	}

	private boolean isMergeCommit(final CommitBuilder commitBuilder) {
		return commitBuilder.getParentIds().length > 1;
	}

	private Optional<ObjectId> filterCommitTree(final ObjectReader reader, final ObjectInserter inserter,
			final RevCommit commit) throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException,
			IOException, UnmergedPathException {
		final DirCache dc = DirCache.newInCore();
		final DirCacheEditor editor = dc.editor();
		@SuppressWarnings("resource")
		final TreeWalk treeWalk = new TreeWalk(reader);
		int treeId = treeWalk.addTree(commit.getTree());
		treeWalk.setRecursive(true);
		boolean empty = true;
		while (treeWalk.next()) {
			final String pathString = treeWalk.getPathString();
			final CanonicalTreeParser treeParser = treeWalk.getTree(treeId, CanonicalTreeParser.class);
			if (inSubdirectory(pathString)) {
				moveFromSubdirectoryToRoot(editor, pathString, treeParser);
				empty = false;
			}
		}
		editor.finish();

		if (empty) {
			return Optional.empty();
		} else {
			return Optional.of(dc.writeTree(inserter));
		}
	}

	private RevWalk createRevWalk(final Repository repository, final ObjectReader reader)
			throws MissingObjectException, IncorrectObjectTypeException, IOException {
		final RevWalk revWalk = new RevWalk(reader);
		final List<RevCommit> branchTips = getBranchCommits(repository, revWalk);
		// So that we traverse all branch histories at once
		revWalk.markStart(branchTips);

		// Gets parents before children
		revWalk.sort(RevSort.TOPO, true);
		revWalk.sort(RevSort.REVERSE, true);

		revWalk.setRevFilter(RevFilter.ALL);
		revWalk.setTreeFilter(TreeFilter.ALL);

		return revWalk;
	}

	private List<RevCommit> getBranchCommits(final Repository repository, final RevWalk revWalk) {
		final List<RevCommit> branchTips = branches.stream().map(b -> {
			try {
				return revWalk.parseCommit(repository.resolve(b));
			} catch (IOException ioe) {
				throw new IllegalArgumentException(
						format("Unable to parse branch [%s] in repository [%s].", b, repository.getDirectory()));
			}
		}).collect(toList());
		return branchTips;
	}

	private CommitBuilder generateNewCommit(final Map<ObjectId, ObjectId> commitMap, final RevCommit commit,
			final ObjectId newCommitTree) {
		final CommitBuilder commitBuilder = new CommitBuilder();
		commitBuilder.setAuthor(commit.getAuthorIdent());
		commitBuilder.setCommitter(commit.getCommitterIdent());
		commitBuilder.setTreeId(newCommitTree);
		commitBuilder.setMessage(commit.getFullMessage());
		commitBuilder.setEncoding(commit.getEncoding());
		final ObjectId[] newParentIds = closestMappedAncestorOrSelf(commitMap, commit.getParents());
		if (newParentIds.length > 0) {
			commitBuilder.setParentIds(newParentIds);
		}

		return commitBuilder;
	}

	private ObjectId[] closestMappedAncestorOrSelf(final Map<ObjectId, ObjectId> commitMap, final RevCommit... start) {
		final Queue<RevCommit> commitQueue = new LinkedList<>();
		final Set<ObjectId> processed = new HashSet<>();
		commitQueue.addAll(Arrays.asList(start));

		final List<ObjectId> results = new ArrayList<>();

		while (!commitQueue.isEmpty()) {
			final RevCommit cur = commitQueue.poll();
			if (!processed.contains(cur.getId())) {
				final ObjectId mappedId = commitMap.get(cur.getId());

				// Ancestors not mapped must have been empty commits, that we ignore.
				if (mappedId != null) {
					results.add(mappedId);
				} else {
					Arrays.stream(cur.getParents()).forEach(p -> commitQueue.add(p));
				}
				processed.add(cur.getId());
			}
		}

		return results.toArray(new ObjectId[results.size()]);
	}

	private void moveFromSubdirectoryToRoot(final DirCacheEditor editor, final String pathString,
			final CanonicalTreeParser treeParser) {
		final String newPath = pathString.substring(subdirectory.length());
		final ObjectId entryObjectId = treeParser.getEntryObjectId();
		final FileMode entryFileMode = treeParser.getEntryFileMode();
		editor.add(new DirCacheEditor.PathEdit(new DirCacheEntry(newPath)) {

			@Override
			public void apply(DirCacheEntry ent) {
				ent.setObjectId(entryObjectId);
				ent.setFileMode(entryFileMode);
			}
		});
	}

	private boolean inSubdirectory(final String pathString) {
		return pathString.startsWith(subdirectory);
	}

	private void cleanupDir(final File gitDir) throws IOException {
		try {
			if (System.getProperty("os.name").toLowerCase().contains("windows")) {
				// this operation forces a cache clean freeing any lock -> windows only issue!
				new WindowCacheConfig().install();
			}
			FileUtils.delete(gitDir, FileUtils.RECURSIVE | FileUtils.RETRY);
		} catch (java.io.IOException e) {
			throw new IOException("Failed to remove the git repository.", e);
		}
	}
}

/*
 * Copyright (C) 2011, GitHub Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FileUtils;

/**
 * A class used to execute a {@code worktree add} command.
 */
public class WorktreeAddCommand extends GitCommand<Worktree> {
	private String path;
	private String branch;
	private boolean force;
	private boolean detach;
	private boolean checkout = true;
	private boolean lock;
	private String lockReason;
	private boolean orphan;
	private String newBranch;
	private boolean forceNewBranch;
	private Boolean guessRemote;
	private boolean track = true;

	/**
	 * Constructor for WorktreeAddCommand.
	 *
	 * @param repo
	 *            the repository
	 */
	protected WorktreeAddCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Set the path to the new worktree.
	 *
	 * @param path
	 *            path to the new worktree
	 * @return {@code this}
	 */
	public WorktreeAddCommand setPath(String path) {
		this.path = path;
		return this;
	}

	/**
	 * Set the branch to checkout in the new worktree.
	 *
	 * @param branch
	 *            branch to checkout
	 * @return {@code this}
	 */
	public WorktreeAddCommand setBranch(String branch) {
		this.branch = branch;
		return this;
	}

	/**
	 * Set whether to force creation.
	 *
	 * @param force
	 *            true to force creation
	 * @return {@code this}
	 */
	public WorktreeAddCommand setForce(boolean force) {
		this.force = force;
		return this;
	}

	/**
	 * Set whether to detach HEAD.
	 *
	 * @param detach
	 *            true to detach HEAD
	 * @return {@code this}
	 */
	public WorktreeAddCommand setDetach(boolean detach) {
		this.detach = detach;
		return this;
	}

	/**
	 * Set whether to checkout files.
	 *
	 * @param checkout
	 *            true to checkout files
	 * @return {@code this}
	 */
	public WorktreeAddCommand setCheckout(boolean checkout) {
		this.checkout = checkout;
		return this;
	}

	/**
	 * Set whether to lock the worktree.
	 *
	 * @param lock
	 *            true to lock the worktree
	 * @return {@code this}
	 */
	public WorktreeAddCommand setLock(boolean lock) {
		this.lock = lock;
		return this;
	}

	/**
	 * Set the reason for locking.
	 *
	 * @param lockReason
	 *            reason for locking
	 * @return {@code this}
	 */
	public WorktreeAddCommand setLockReason(String lockReason) {
		this.lockReason = lockReason;
		return this;
	}

	/**
	 * Set whether to create an orphan branch.
	 *
	 * @param orphan
	 *            true to create an orphan branch
	 * @return {@code this}
	 */
	public WorktreeAddCommand setOrphan(boolean orphan) {
		this.orphan = orphan;
		return this;
	}

	/**
	 * Set the name of the new branch to create.
	 *
	 * @param newBranch
	 *            name of the new branch
	 * @return {@code this}
	 */
	public WorktreeAddCommand setNewBranch(String newBranch) {
		this.newBranch = newBranch;
		return this;
	}

	/**
	 * Set whether to force creation of the new branch.
	 *
	 * @param forceNewBranch
	 *            true to force creation of the new branch
	 * @return {@code this}
	 */
	public WorktreeAddCommand setForceNewBranch(boolean forceNewBranch) {
		this.forceNewBranch = forceNewBranch;
		return this;
	}

	/**
	 * Set whether to guess remote tracking branches.
	 *
	 * @param guessRemote
	 *            true to guess
	 * @return {@code this}
	 * @since 7.7
	 */
	public WorktreeAddCommand setGuessRemote(Boolean guessRemote) {
		this.guessRemote = guessRemote;
		return this;
	}

	/**
	 * Set whether to setup tracking.
	 *
	 * @param track
	 *            true to setup tracking
	 * @return {@code this}
	 * @since 7.7
	 */
	public WorktreeAddCommand setTrack(boolean track) {
		this.track = track;
		return this;
	}

	@Override
	public Worktree call() throws GitAPIException {
		checkCallable();
		if (path == null) {
			throw new IllegalArgumentException(JGitText.get().worktreePathRequired);
		}

		File worktreeDir = new File(path);
		if (worktreeDir.exists() && !force) {
			throw new IllegalStateException(MessageFormat.format(JGitText.get().worktreeDirectoryAlreadyExists, path));
		}

		String commitish = branch;
		String branchToCheckout = null;
		String startPointForNewBranch = null;
		boolean needCreateBranch = false;

		boolean guess = guessRemote != null ? guessRemote.booleanValue() : repo.getConfig().getBoolean("worktree", "guessRemote", true); //$NON-NLS-1$ //$NON-NLS-2$

		if (orphan) {
			branchToCheckout = (newBranch != null ? newBranch : worktreeDir.getName());
		} else if (detach) {
			if (commitish == null) {
				commitish = Constants.HEAD;
			}
		} else if (newBranch != null || forceNewBranch) {
			branchToCheckout = newBranch != null ? newBranch : worktreeDir.getName();
			startPointForNewBranch = commitish != null ? commitish : Constants.HEAD;
			needCreateBranch = true;
		} else {
			if (commitish != null) {
				if (commitish.equals("-")) { //$NON-NLS-1$
					commitish = resolvePreviousBranch();
				}

				try {
					Ref localRef = repo.exactRef(Constants.R_HEADS + commitish);
					if (localRef != null) {
						branchToCheckout = commitish;
					} else {
						Ref tagRef = repo.exactRef(Constants.R_TAGS + commitish);
						if (tagRef != null) {
							detach = true;
						} else {
							ObjectId id = repo.resolve(commitish);
							if (id != null && (commitish.length() == 40 || ObjectId.isId(commitish))) {
								detach = true;
							} else if (guess) {
								String remoteTracking = findUniqueRemoteTracking(commitish);
								if (remoteTracking != null) {
									branchToCheckout = commitish;
									startPointForNewBranch = remoteTracking;
									needCreateBranch = true;
								}
							}

							if (branchToCheckout == null && !detach) {
								if (id != null) {
									detach = true;
								} else {
									throw new RefNotFoundException(MessageFormat.format(JGitText.get().refNotResolved, commitish));
								}
							}
						}
					}
				} catch (IOException e) {
					throw new JGitInternalException(e.getMessage(), e);
				}
			} else {
				String derivedBranch = worktreeDir.getName();
				try {
					Ref localRef = repo.exactRef(Constants.R_HEADS + derivedBranch);
					if (localRef != null) {
						branchToCheckout = derivedBranch;
					} else {
						String remoteTracking = guess ? findUniqueRemoteTracking(derivedBranch) : null;
						branchToCheckout = derivedBranch;
						startPointForNewBranch = remoteTracking != null ? remoteTracking : Constants.HEAD;
						needCreateBranch = true;
					}
				} catch (IOException e) {
					throw new JGitInternalException(e.getMessage(), e);
				}
			}
		}

		ObjectId commitId = null;
		if (!orphan) {
			String resolveTarget = startPointForNewBranch != null ? startPointForNewBranch : (branchToCheckout != null ? branchToCheckout : commitish);
			try {
				commitId = repo.resolve(resolveTarget);
			} catch (IOException e) {
				throw new JGitInternalException(e.getMessage(), e);
			}
			if (commitId == null) {
				throw new IllegalStateException(MessageFormat.format(JGitText.get().worktreeCannotResolveStartPoint, resolveTarget));
			}
		}

		if (!force && branchToCheckout != null) {
			Collection<Worktree> existingWorktrees = new WorktreeListCommand(repo).call();
			for (Worktree wt : existingWorktrees) {
				if ((Constants.R_HEADS + branchToCheckout).equals(wt.getBranch())) {
					throw new IllegalStateException(MessageFormat.format(JGitText.get().worktreeBranchCheckedOut, branchToCheckout, wt.getPath()));
				}
			}
		}

		String name = worktreeDir.getName();
		File mainGitDir = repo.getCommonDirectory();
		File worktreesAdminDir = new File(mainGitDir, "worktrees"); //$NON-NLS-1$
		File specificAdminDir = new File(worktreesAdminDir, name);

		if (specificAdminDir.exists() && !force) {
			throw new IllegalStateException(MessageFormat.format(JGitText.get().worktreeAdminDirAlreadyExists, name));
		}

		try {
			FileUtils.mkdirs(worktreeDir, true);
			FileUtils.mkdirs(specificAdminDir, true);

			File dotGitFile = new File(worktreeDir, Constants.DOT_GIT);
			String gitdirContent = "gitdir: " + specificAdminDir.getAbsolutePath(); //$NON-NLS-1$
			Files.write(dotGitFile.toPath(), gitdirContent.getBytes());

			File gitdirAdminFile = new File(specificAdminDir, "gitdir"); //$NON-NLS-1$
			Files.write(gitdirAdminFile.toPath(), dotGitFile.getAbsolutePath().getBytes());

			File headFile = new File(specificAdminDir, Constants.HEAD);
			String headContent;

			if (orphan) {
				headContent = "ref: " + Constants.R_HEADS + branchToCheckout; //$NON-NLS-1$
			} else if (detach) {
				if (commitId == null) {
					throw new IllegalStateException("commitId is null on detach"); //$NON-NLS-1$
				}
				headContent = commitId.name();
			} else if (branchToCheckout != null) {
				headContent = "ref: " + Constants.R_HEADS + branchToCheckout; //$NON-NLS-1$
			} else {
				if (commitish == null) {
					throw new IllegalStateException("commitish is null"); //$NON-NLS-1$
				}
				headContent = "ref: " + (commitish.startsWith(Constants.R_REFS) ? commitish : Constants.R_HEADS + commitish); //$NON-NLS-1$
			}
			Files.write(headFile.toPath(), headContent.getBytes());

			// Create commondir for git CLI compatibility
			File commondirFile = new File(specificAdminDir, "commondir"); //$NON-NLS-1$
			String commondirContent = "../.."; //$NON-NLS-1$
			Files.write(commondirFile.toPath(), commondirContent.getBytes());

			if (lock) {
				File lockedFile = new File(specificAdminDir, "locked"); //$NON-NLS-1$
				String content = lockReason != null ? lockReason : ""; //$NON-NLS-1$
				Files.write(lockedFile.toPath(), content.getBytes());
			}

			Repository newRepo = new BaseRepositoryBuilder().setFS(repo.getFS()).setGitDir(specificAdminDir).setGitCommonDir(repo.getCommonDirectory()).setWorkTree(worktreeDir).build();

			if (checkout && !orphan) {
				if (needCreateBranch) {
					try (Git git = new Git(repo)) {
						git.branchCreate().setName(branchToCheckout).setStartPoint(startPointForNewBranch).setForce(forceNewBranch)
						   .setUpstreamMode(track ? CreateBranchCommand.SetupUpstreamMode.TRACK : CreateBranchCommand.SetupUpstreamMode.NOTRACK)
						   .call();
					}
				}

				try (Git git = new Git(newRepo)) {
					if (branchToCheckout != null) {
						git.checkout().setName(branchToCheckout).call();
					} else if (detach && commitId != null) {
						git.checkout().setName(commitId.name()).call();
					}
				}
			}

			return new Worktree(worktreeDir, branchToCheckout != null ? Constants.R_HEADS + branchToCheckout : null, commitId);
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	private String findUniqueRemoteTracking(String branchName) {
		try {
			List<Ref> matches = new ArrayList<>();
			for (Ref ref : repo.getRefDatabase().getRefsByPrefix(Constants.R_REMOTES)) {
				String name = ref.getName().substring(Constants.R_REMOTES.length());
				int slash = name.indexOf('/');
				if (slash > 0) {
					String remoteBranch = name.substring(slash + 1);
					if (remoteBranch.equals(branchName)) {
						matches.add(ref);
					}
				}
			}

			if (matches.size() == 1) {
				return matches.get(0).getName();
			} else if (matches.size() > 1) {
				String defaultRemote = repo.getConfig().getString("checkout", null, "defaultRemote"); //$NON-NLS-1$ //$NON-NLS-2$
				if (defaultRemote != null) {
					String target = Constants.R_REMOTES + defaultRemote + "/" + branchName; //$NON-NLS-1$
					for (Ref r : matches) {
						if (r.getName().equals(target)) {
							return target;
						}
					}
				}
			}
		} catch (IOException e) {
			// Ignore
		}
		return null;
	}

	private String resolvePreviousBranch() throws GitAPIException {
		try {
			org.eclipse.jgit.lib.ReflogReader reader = repo.getRefDatabase().getReflogReader(Constants.HEAD);
			if (reader != null) {
				for (org.eclipse.jgit.lib.ReflogEntry entry : reader.getReverseEntries()) {
					String comment = entry.getComment();
					if (comment != null && comment.startsWith("checkout: moving from ")) { //$NON-NLS-1$
						String p = comment.substring("checkout: moving from ".length()); //$NON-NLS-1$
						int to = p.indexOf(" to "); //$NON-NLS-1$
						if (to > 0) {
							return p.substring(0, to);
						}
					}
				}
			}
			throw new RefNotFoundException(JGitText.get().refNotResolved);
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}
}

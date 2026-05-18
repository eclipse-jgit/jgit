/*
 * Copyright (C) 2011, GitHub Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.kohsuke.args4j.Argument;

@Command(common = false, usage = "usage_Worktree")
class Worktree extends TextBuiltin {

	@Argument(index = 0, metaVar = "metaVar_command")
	private String command;

	@Argument(index = 1, metaVar = "metaVar_arg")
	private String arg1;

	@Argument(index = 2, metaVar = "metaVar_arg")
	private String arg2;

	@org.kohsuke.args4j.Option(name = "-f", aliases = { "--force" })
	private boolean force;

	@org.kohsuke.args4j.Option(name = "--detach")
	private boolean detach;

	@org.kohsuke.args4j.Option(name = "--no-checkout")
	private boolean noCheckout;

	@org.kohsuke.args4j.Option(name = "--lock")
	private boolean lock;

	@org.kohsuke.args4j.Option(name = "--reason", metaVar = "reason")
	private String lockReason;

	@org.kohsuke.args4j.Option(name = "--orphan")
	private boolean orphan;

	@org.kohsuke.args4j.Option(name = "-b", metaVar = "branch")
	private String newBranch;

	@org.kohsuke.args4j.Option(name = "-B", metaVar = "branch")
	private String forceNewBranchStr;

	@org.kohsuke.args4j.Option(name = "-v", aliases = { "--verbose" })
	private boolean verbose;

	@org.kohsuke.args4j.Option(name = "-n", aliases = { "--dry-run" })
	private boolean dryRun;

	@org.kohsuke.args4j.Option(name = "--expire", metaVar = "time")
	private String expireStr;

	@org.kohsuke.args4j.Option(name = "--guess-remote")
	private Boolean guessRemote;

	@org.kohsuke.args4j.Option(name = "--no-guess-remote")
	private boolean noGuessRemote;

	@org.kohsuke.args4j.Option(name = "--track")
	private boolean track = true;

	@org.kohsuke.args4j.Option(name = "--no-track")
	private boolean noTrack;

	@org.kohsuke.args4j.Option(name = "--porcelain")
	private boolean porcelain;

	@org.kohsuke.args4j.Option(name = "-z")
	private boolean nullTerminated;

	@Override
	protected void run() {
		try (Git git = new Git(db)) {
			if (command == null) {
				throw die("Subcommand required: list, add, remove, move, lock, unlock, prune, repair"); //$NON-NLS-1$
			}

			switch (command) {
			case "list": //$NON-NLS-1$
				org.eclipse.jgit.api.WorktreeListCommand listCmd = git.worktreeList();
				if (expireStr != null) {
					try {
						listCmd.setExpireTime(org.eclipse.jgit.util.GitTimeParser.parseInstant(expireStr));
					} catch (java.text.ParseException e) {
						throw die("Cannot parse expire time", e);
					}
				}
				Collection<org.eclipse.jgit.api.Worktree> worktrees = listCmd.call();
				String sep = nullTerminated ? "\0" : System.lineSeparator();
				int maxPathLen = 0;
				if (!porcelain) {
					for (org.eclipse.jgit.api.Worktree wt : worktrees) {
						int len = wt.getPath().getAbsolutePath().length();
						if (len > maxPathLen) {
							maxPathLen = len;
						}
					}
				}

				for (org.eclipse.jgit.api.Worktree wt : worktrees) {
					if (porcelain) {
						outw.print("worktree " + wt.getPath().getAbsolutePath() + sep);
						if (wt.isBare()) {
							outw.print("bare" + sep);
						} else {
							if (wt.getCommit() != null) {
								outw.print("HEAD " + wt.getCommit().name() + sep);
							}
							if (wt.getBranch() != null) {
								outw.print("branch " + wt.getBranch() + sep);
							} else {
								outw.print("detached" + sep);
							}
						}
						if (wt.isLocked()) {
							outw.print("locked" + (wt.getLockReason() != null && !wt.getLockReason().isEmpty() ? " " + wt.getLockReason() : "") + sep);
						}
						if (wt.isPrunable()) {
							outw.print("prunable" + (wt.getPrunableReason() != null && !wt.getPrunableReason().isEmpty() ? " " + wt.getPrunableReason() : "") + sep);
						}
						outw.print(sep); // empty line at end of record
					} else {
						StringBuilder sb = new StringBuilder();
						String path = wt.getPath().getAbsolutePath();
						sb.append(path);
						for (int i = path.length(); i < maxPathLen + 2; i++) {
							sb.append(' ');
						}
						if (wt.isBare()) {
							sb.append("(bare)");
						} else {
							sb.append(wt.getCommit() != null ? wt.getCommit().abbreviate(7).name() : "").append(" ");
							sb.append(wt.getBranch() != null ? "[" + org.eclipse.jgit.lib.Repository.shortenRefName(wt.getBranch()) + "]" : "(detached HEAD)");
						}

						StringBuilder ann = new StringBuilder();
						if (wt.isLocked()) {
							if (verbose && wt.getLockReason() != null && !wt.getLockReason().isEmpty()) {
								ann.append(System.lineSeparator()).append("\tlocked: ").append(wt.getLockReason());
							} else {
								sb.append(" locked");
							}
						}
						if (wt.isPrunable()) {
							if (verbose && wt.getPrunableReason() != null && !wt.getPrunableReason().isEmpty()) {
								ann.append(System.lineSeparator()).append("\tprunable: ").append(wt.getPrunableReason());
							} else {
								sb.append(" prunable");
							}
						}
						sb.append(ann);
						outw.print(sb.toString() + sep);
					}
				}
				break;
			case "add": //$NON-NLS-1$
				if (arg1 == null) {
					throw die("Usage: jgit worktree add <path> [<commit-ish>]"); //$NON-NLS-1$
				}
				org.eclipse.jgit.api.WorktreeAddCommand addCmd = git.worktreeAdd().setPath(arg1).setBranch(arg2);
				addCmd.setForce(force);
				addCmd.setDetach(detach);
				addCmd.setCheckout(!noCheckout);
				addCmd.setLock(lock);
				addCmd.setLockReason(lockReason);
				addCmd.setOrphan(orphan);
				if (forceNewBranchStr != null) {
					addCmd.setNewBranch(forceNewBranchStr).setForceNewBranch(true);
				} else if (newBranch != null) {
					addCmd.setNewBranch(newBranch);
				}
				if (noGuessRemote) {
					addCmd.setGuessRemote(Boolean.FALSE);
				} else if (guessRemote != null) {
					addCmd.setGuessRemote(guessRemote);
				}
				if (noTrack) {
					addCmd.setTrack(false);
				} else {
					addCmd.setTrack(track);
				}
				addCmd.call();
				break;
			case "remove": //$NON-NLS-1$
				if (arg1 == null) {
					throw die("Usage: jgit worktree remove <path>"); //$NON-NLS-1$
				}
				org.eclipse.jgit.api.WorktreeRemoveCommand rmCmd = git.worktreeRemove().setPath(arg1);
				rmCmd.setForce(force);
				rmCmd.setForceLocked(force);
				rmCmd.call();
				break;
			case "move": //$NON-NLS-1$
				if (arg1 == null || arg2 == null) {
					throw die("Usage: jgit worktree move <path> <newPath>"); //$NON-NLS-1$
				}
				git.worktreeMove().setPath(arg1).setNewPath(arg2).call();
				break;
			case "lock": //$NON-NLS-1$
				if (arg1 == null) {
					throw die("Usage: jgit worktree lock <path> [<reason>]"); //$NON-NLS-1$
				}
				git.worktreeLock().setPath(arg1).setReason(arg2).call();
				break;
			case "unlock": //$NON-NLS-1$
				if (arg1 == null) {
					throw die("Usage: jgit worktree unlock <path>"); //$NON-NLS-1$
				}
				git.worktreeUnlock().setPath(arg1).call();
				break;
			case "prune": //$NON-NLS-1$
				org.eclipse.jgit.api.WorktreePruneCommand pruneCmd = git.worktreePrune();
				pruneCmd.setDryRun(dryRun);
				if (expireStr != null) {
					try {
						pruneCmd.setExpireTime(org.eclipse.jgit.util.GitTimeParser.parseInstant(expireStr));
					} catch (java.text.ParseException e) {
						throw die("Cannot parse expire time", e);
					}
				}
				Collection<String> pruned = pruneCmd.call();
				if (verbose || dryRun) {
					for (String p : pruned) {
						outw.println("Pruned: " + p); //$NON-NLS-1$
					}
				}
				break;
			case "repair": //$NON-NLS-1$
				Collection<String> repaired = git.worktreeRepair().call();
				for (String r : repaired) {
					outw.println("Repaired: " + r); //$NON-NLS-1$
				}
				break;
			default:
				throw die(MessageFormat.format(CLIText.get().unknownSubcommand,
						command));
			}
		} catch (GitAPIException | IOException e) {
			throw die(e.getMessage(), e);
		}
	}
}

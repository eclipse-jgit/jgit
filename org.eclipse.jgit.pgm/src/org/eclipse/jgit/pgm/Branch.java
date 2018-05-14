/*
 * Copyright (C) 2007-2008, Charles O'Farrell <charleso@charleso.org>
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

package org.eclipse.jgit.pgm;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefComparator;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.pgm.opt.OptionWithValuesListHandler;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_listCreateOrDeleteBranches")
class Branch extends TextBuiltin {

	private String otherBranch;
	private boolean createForce;
	private boolean rename;

	@Option(name = "--remote", aliases = { "-r" }, usage = "usage_actOnRemoteTrackingBranches")
	private boolean remote = false;

	@Option(name = "--all", aliases = { "-a" }, usage = "usage_listBothRemoteTrackingAndLocalBranches")
	private boolean all = false;

	@Option(name = "--contains", metaVar = "metaVar_commitish", usage = "usage_printOnlyBranchesThatContainTheCommit")
	private String containsCommitish;

	private List<String> delete;

	/**
	 * Delete branches
	 *
	 * @param names
	 *            a {@link java.util.List} of branch names.
	 */
	@Option(name = "--delete", aliases = {
			"-d" }, metaVar = "metaVar_branchNames", usage = "usage_deleteFullyMergedBranch", handler = OptionWithValuesListHandler.class)
	public void delete(List<String> names) {
		if (names.isEmpty()) {
			throw die(CLIText.get().branchNameRequired);
		}
		delete = names;
	}

	private List<String> deleteForce;

	/**
	 * Forcefully delete branches
	 *
	 * @param names
	 *            a {@link java.util.List} of branch names.
	 */
	@Option(name = "--delete-force", aliases = {
			"-D" }, metaVar = "metaVar_branchNames", usage = "usage_deleteBranchEvenIfNotMerged", handler = OptionWithValuesListHandler.class)
	public void deleteForce(List<String> names) {
		if (names.isEmpty()) {
			throw die(CLIText.get().branchNameRequired);
		}
		deleteForce = names;
	}

	/**
	 * Forcefully create a list of branches
	 *
	 * @param branchAndStartPoint
	 *            a branch name and a start point
	 */
	@Option(name = "--create-force", aliases = {
			"-f" }, metaVar = "metaVar_branchAndStartPoint", usage = "usage_forceCreateBranchEvenExists", handler = OptionWithValuesListHandler.class)
	public void createForce(List<String> branchAndStartPoint) {
		createForce = true;
		if (branchAndStartPoint.isEmpty()) {
			throw die(CLIText.get().branchNameRequired);
		}
		if (branchAndStartPoint.size() > 2) {
			throw die(CLIText.get().tooManyRefsGiven);
		}
		if (branchAndStartPoint.size() == 1) {
			branch = branchAndStartPoint.get(0);
		} else {
			branch = branchAndStartPoint.get(0);
			otherBranch = branchAndStartPoint.get(1);
		}
	}

	/**
	 * Move or rename a branch
	 *
	 * @param currentAndNew
	 *            the current and the new branch name
	 */
	@Option(name = "--move", aliases = {
			"-m" }, metaVar = "metaVar_oldNewBranchNames", usage = "usage_moveRenameABranch", handler = OptionWithValuesListHandler.class)
	public void moveRename(List<String> currentAndNew) {
		rename = true;
		if (currentAndNew.isEmpty()) {
			throw die(CLIText.get().branchNameRequired);
		}
		if (currentAndNew.size() > 2) {
			throw die(CLIText.get().tooManyRefsGiven);
		}
		if (currentAndNew.size() == 1) {
			branch = currentAndNew.get(0);
		} else {
			branch = currentAndNew.get(0);
			otherBranch = currentAndNew.get(1);
		}
	}

	@Option(name = "--verbose", aliases = { "-v" }, usage = "usage_beVerbose")
	private boolean verbose = false;

	@Argument(metaVar = "metaVar_name")
	private String branch;

	private final Map<String, Ref> printRefs = new LinkedHashMap<>();

	/** Only set for verbose branch listing at-the-moment */
	private RevWalk rw;

	private int maxNameLength;

	/** {@inheritDoc} */
	@Override
	protected void run() throws Exception {
		if (delete != null || deleteForce != null) {
			if (delete != null) {
				delete(delete, false);
			}
			if (deleteForce != null) {
				delete(deleteForce, true);
			}
		} else {
			if (rename) {
				String src, dst;
				if (otherBranch == null) {
					final Ref head = db.exactRef(Constants.HEAD);
					if (head != null && head.isSymbolic()) {
						src = head.getLeaf().getName();
					} else {
						throw die(CLIText.get().cannotRenameDetachedHEAD);
					}
					dst = branch;
				} else {
					src = branch;
					final Ref old = db.findRef(src);
					if (old == null)
						throw die(MessageFormat.format(CLIText.get().doesNotExist, src));
					if (!old.getName().startsWith(Constants.R_HEADS))
						throw die(MessageFormat.format(CLIText.get().notABranch, src));
					src = old.getName();
					dst = otherBranch;
				}

				if (!dst.startsWith(Constants.R_HEADS))
					dst = Constants.R_HEADS + dst;
				if (!Repository.isValidRefName(dst))
					throw die(MessageFormat.format(CLIText.get().notAValidRefName, dst));

				RefRename r = db.renameRef(src, dst);
				if (r.rename() != Result.RENAMED)
					throw die(MessageFormat.format(CLIText.get().cannotBeRenamed, src));

			} else if (createForce || branch != null) {
				String newHead = branch;
				String startBranch;
				if (createForce) {
					startBranch = otherBranch;
				} else {
					startBranch = Constants.HEAD;
				}
				Ref startRef = db.findRef(startBranch);
				ObjectId startAt = db.resolve(startBranch + "^0"); //$NON-NLS-1$
				if (startRef != null) {
					startBranch = startRef.getName();
				} else if (startAt != null) {
					startBranch = startAt.name();
				} else {
					throw die(MessageFormat.format(
							CLIText.get().notAValidCommitName, startBranch));
				}
				startBranch = Repository.shortenRefName(startBranch);
				String newRefName = newHead;
				if (!newRefName.startsWith(Constants.R_HEADS)) {
					newRefName = Constants.R_HEADS + newRefName;
				}
				if (!Repository.isValidRefName(newRefName)) {
					throw die(MessageFormat.format(CLIText.get().notAValidRefName, newRefName));
				}
				if (!createForce && db.resolve(newRefName) != null) {
					throw die(MessageFormat.format(CLIText.get().branchAlreadyExists, newHead));
				}
				RefUpdate updateRef = db.updateRef(newRefName);
				updateRef.setNewObjectId(startAt);
				updateRef.setForceUpdate(createForce);
				updateRef.setRefLogMessage(MessageFormat.format(CLIText.get().branchCreatedFrom, startBranch), false);
				Result update = updateRef.update();
				if (update == Result.REJECTED) {
					throw die(MessageFormat.format(CLIText.get().couldNotCreateBranch, newHead, update.toString()));
				}
			} else {
				if (verbose) {
					rw = new RevWalk(db);
				}
				list();
			}
		}
	}

	private void list() throws Exception {
		Ref head = db.exactRef(Constants.HEAD);
		// This can happen if HEAD is stillborn
		if (head != null) {
			String current = head.getLeaf().getName();
			try (Git git = new Git(db)) {
				ListBranchCommand command = git.branchList();
				if (all)
					command.setListMode(ListMode.ALL);
				else if (remote)
					command.setListMode(ListMode.REMOTE);

				if (containsCommitish != null)
					command.setContains(containsCommitish);

				List<Ref> refs = command.call();
				for (Ref ref : refs) {
					if (ref.getName().equals(Constants.HEAD))
						addRef("(no branch)", head); //$NON-NLS-1$
				}

				addRefs(refs, Constants.R_HEADS);
				addRefs(refs, Constants.R_REMOTES);

				try (ObjectReader reader = db.newObjectReader()) {
					for (final Entry<String, Ref> e : printRefs.entrySet()) {
						final Ref ref = e.getValue();
						printHead(reader, e.getKey(),
								current.equals(ref.getName()), ref);
					}
				}
			}
		}
	}

	private void addRefs(Collection<Ref> refs, String prefix) {
		for (final Ref ref : RefComparator.sort(refs)) {
			final String name = ref.getName();
			if (name.startsWith(prefix))
				addRef(name.substring(name.indexOf('/', 5) + 1), ref);
		}
	}

	private void addRef(String name, Ref ref) {
		printRefs.put(name, ref);
		maxNameLength = Math.max(maxNameLength, name.length());
	}

	private void printHead(final ObjectReader reader, final String ref,
			final boolean isCurrent, final Ref refObj) throws Exception {
		outw.print(isCurrent ? '*' : ' ');
		outw.print(' ');
		outw.print(ref);
		if (verbose) {
			final int spaces = maxNameLength - ref.length() + 1;
			outw.format("%" + spaces + "s", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			final ObjectId objectId = refObj.getObjectId();
			outw.print(reader.abbreviate(objectId).name());
			outw.print(' ');
			outw.print(rw.parseCommit(objectId).getShortMessage());
		}
		outw.println();
	}

	private void delete(List<String> branches, boolean force)
			throws IOException {
		String current = db.getBranch();
		ObjectId head = db.resolve(Constants.HEAD);
		for (String b : branches) {
			if (b.equals(current)) {
				throw die(MessageFormat.format(CLIText.get().cannotDeleteTheBranchWhichYouAreCurrentlyOn, b));
			}
			RefUpdate update = db.updateRef((remote ? Constants.R_REMOTES
					: Constants.R_HEADS)
					+ b);
			update.setNewObjectId(head);
			update.setForceUpdate(force || remote);
			Result result = update.delete();
			if (result == Result.REJECTED) {
				throw die(MessageFormat.format(CLIText.get().branchIsNotAnAncestorOfYourCurrentHEAD, b));
			} else if (result == Result.NEW)
				throw die(MessageFormat.format(CLIText.get().branchNotFound, b));
			if (remote)
				outw.println(MessageFormat.format(CLIText.get().deletedRemoteBranch, b));
			else if (verbose)
				outw.println(MessageFormat.format(CLIText.get().deletedBranch, b));
		}
	}
}

/*
 * Copyright (C) 2011, 2014 Christian Halstrick <christian.halstrick@sap.com> and others
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
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.merge.ContentMergeStrategy;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import static org.eclipse.jgit.lib.Constants.OBJECT_ID_ABBREV_LENGTH;

@Command(common = true, usage = "usage_MergesTwoDevelopmentHistories")
class Merge extends TextBuiltin {

	@Option(name = "--strategy", aliases = { "-s" }, usage = "usage_mergeStrategy")
	private String strategyName;

	@Option(name = "--squash", usage = "usage_squash")
	private boolean squash;

	@Option(name = "--no-commit", usage = "usage_noCommit")
	private boolean noCommit = false;

	private MergeStrategy mergeStrategy = MergeStrategy.RECURSIVE;

	@Argument(required = true, metaVar = "metaVar_ref", usage = "usage_mergeRef")
	private String ref;

	private FastForwardMode ff = FastForwardMode.FF;

	@Option(name = "--ff", usage = "usage_mergeFf")
	void ff(@SuppressWarnings("unused") final boolean ignored) {
		ff = FastForwardMode.FF;
	}

	@Option(name = "--no-ff", usage = "usage_mergeNoFf")
	void noff(@SuppressWarnings("unused") final boolean ignored) {
		ff = FastForwardMode.NO_FF;
	}

	@Option(name = "--ff-only", usage = "usage_mergeFfOnly")
	void ffonly(@SuppressWarnings("unused") final boolean ignored) {
		ff = FastForwardMode.FF_ONLY;
	}

	@Option(name = "-m", usage = "usage_message")
	private String message;

	private ContentMergeStrategy contentStrategy = null;

	@Option(name = "--strategy-option", aliases = { "-X" },
			metaVar = "metaVar_extraArgument", usage = "usage_extraArgument")
	void extraArg(String name) {
		if (ContentMergeStrategy.OURS.name().equalsIgnoreCase(name)) {
			contentStrategy = ContentMergeStrategy.OURS;
		} else if (ContentMergeStrategy.THEIRS.name().equalsIgnoreCase(name)) {
			contentStrategy = ContentMergeStrategy.THEIRS;
		} else {
			throw die(MessageFormat.format(CLIText.get().unknownExtraArgument, name));
		}
	}

	/** {@inheritDoc} */
	@Override
	protected void run() {
		if (squash && ff == FastForwardMode.NO_FF) {
			throw die(CLIText.get().cannotCombineSquashWithNoff);
		}
		// determine the merge strategy
		if (strategyName != null) {
			mergeStrategy = MergeStrategy.get(strategyName);
			if (mergeStrategy == null) {
				throw die(MessageFormat.format(
						CLIText.get().unknownMergeStrategy, strategyName));
			}
		}

		try {
			// determine the other revision we want to merge with HEAD
			final Ref srcRef = db.findRef(ref);
			final ObjectId src = db.resolve(ref + "^{commit}"); //$NON-NLS-1$
			if (src == null) {
				throw die(MessageFormat
						.format(CLIText.get().refDoesNotExistOrNoCommit, ref));
			}

			Ref oldHead = getOldHead();
			MergeResult result;
			try (Git git = new Git(db)) {
				MergeCommand mergeCmd = git.merge()
						.setStrategy(mergeStrategy)
						.setContentMergeStrategy(contentStrategy)
						.setSquash(squash)
						.setFastForward(ff)
						.setCommit(!noCommit);
				if (srcRef != null) {
					mergeCmd.include(srcRef);
				} else {
					mergeCmd.include(src);
				}

				if (message != null) {
					mergeCmd.setMessage(message);
				}

				try {
					result = mergeCmd.call();
				} catch (CheckoutConflictException e) {
					result = new MergeResult(e.getConflictingPaths()); // CHECKOUT_CONFLICT
				}
			}

			switch (result.getMergeStatus()) {
			case ALREADY_UP_TO_DATE:
				if (squash) {
					outw.print(CLIText.get().nothingToSquash);
				}
				outw.println(CLIText.get().alreadyUpToDate);
				break;
			case FAST_FORWARD:
				ObjectId oldHeadId = oldHead.getObjectId();
				if (oldHeadId != null) {
					String oldId = oldHeadId.abbreviate(OBJECT_ID_ABBREV_LENGTH).name();
					String newId = result.getNewHead().abbreviate(OBJECT_ID_ABBREV_LENGTH).name();
					outw.println(MessageFormat.format(CLIText.get().updating,
							oldId, newId));
				}
				outw.println(result.getMergeStatus().toString());
				break;
			case CHECKOUT_CONFLICT:
				outw.println(CLIText.get().mergeCheckoutConflict);
				for (String collidingPath : result.getCheckoutConflicts()) {
					outw.println("\t" + collidingPath); //$NON-NLS-1$
				}
				outw.println(CLIText.get().mergeCheckoutFailed);
				break;
			case CONFLICTING:
				for (String collidingPath : result.getConflicts().keySet())
					outw.println(MessageFormat.format(
							CLIText.get().mergeConflict, collidingPath));
				outw.println(CLIText.get().mergeFailed);
				break;
			case FAILED:
				for (Map.Entry<String, MergeFailureReason> entry : result
						.getFailingPaths().entrySet())
					switch (entry.getValue()) {
					case DIRTY_WORKTREE:
					case DIRTY_INDEX:
						outw.println(CLIText.get().dontOverwriteLocalChanges);
						outw.println("        " + entry.getKey()); //$NON-NLS-1$
						break;
					case COULD_NOT_DELETE:
						outw.println(CLIText.get().cannotDeleteFile);
						outw.println("        " + entry.getKey()); //$NON-NLS-1$
						break;
					}
				break;
			case MERGED:
				MergeStrategy strategy = isMergedInto(oldHead, src)
						? MergeStrategy.RECURSIVE
						: mergeStrategy;
				outw.println(MessageFormat.format(CLIText.get().mergeMadeBy,
						strategy.getName()));
				break;
			case MERGED_NOT_COMMITTED:
				outw.println(
						CLIText.get().mergeWentWellStoppedBeforeCommitting);
				break;
			case MERGED_SQUASHED:
			case FAST_FORWARD_SQUASHED:
			case MERGED_SQUASHED_NOT_COMMITTED:
				outw.println(CLIText.get().mergedSquashed);
				outw.println(
						CLIText.get().mergeWentWellStoppedBeforeCommitting);
				break;
			case ABORTED:
				throw die(CLIText.get().ffNotPossibleAborting);
			case NOT_SUPPORTED:
				outw.println(MessageFormat.format(
						CLIText.get().unsupportedOperation, result.toString()));
			}
		} catch (GitAPIException | IOException e) {
			throw die(e.getMessage(), e);
		}

	}

	private Ref getOldHead() throws IOException {
		Ref oldHead = db.exactRef(Constants.HEAD);
		if (oldHead == null) {
			throw die(CLIText.get().onBranchToBeBorn);
		}
		return oldHead;
	}

	private boolean isMergedInto(Ref oldHead, AnyObjectId src)
			throws IOException {
		try (RevWalk revWalk = new RevWalk(db)) {
			ObjectId oldHeadObjectId = oldHead.getPeeledObjectId();
			if (oldHeadObjectId == null)
				oldHeadObjectId = oldHead.getObjectId();
			RevCommit oldHeadCommit = revWalk.lookupCommit(oldHeadObjectId);
			RevCommit srcCommit = revWalk.lookupCommit(src);
			return revWalk.isMergedInto(oldHeadCommit, srcCommit);
		}
	}
}

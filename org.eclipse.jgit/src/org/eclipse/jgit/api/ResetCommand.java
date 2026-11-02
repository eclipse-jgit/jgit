/*
 * Copyright (C) 2011-2013, Chris Aniszczyk <caniszczyk@gmail.com> and others
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
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.Checkout;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuildIterator;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheCheckout.CheckoutMetadata;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.IndexWriteException;
import org.eclipse.jgit.events.WorkingTreeModifiedEvent;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.NameConflictTreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;

/**
 * A class used to execute a {@code Reset} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command. Each instance of this class should only be
 * used for one invocation of the command (means: one call to {@link #call()})
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-reset.html"
 *      >Git documentation about Reset</a>
 */
public class ResetCommand extends GitCommand<Ref> {

	/**
	 * Kind of reset
	 */
	public enum ResetType {
		/**
		 * Just change the ref, the index and workdir are not changed.
		 */
		SOFT,

		/**
		 * Change the ref and the index, the workdir is not changed.
		 */
		MIXED,

		/**
		 * Change the ref, the index and the workdir
		 */
		HARD,

		/**
		 * Resets the index and updates the files in the working tree that are
		 * different between respective commit and HEAD, but keeps those which
		 * are different between the index and working tree. If a file that is
		 * different between the commit and the index has unstaged changes, the
		 * reset is aborted with a
		 * {@link org.eclipse.jgit.api.errors.CheckoutConflictException}. The
		 * reset is also aborted if it would overwrite an untracked file that is
		 * not ignored. Unmerged index entries are reset unconditionally.
		 */
		MERGE,

		/**
		 * Change the ref, the index and the workdir that are different between
		 * respective commit and HEAD
		 */
		KEEP // TODO not implemented yet
	}

	// We need to be able to distinguish whether the caller set the ref
	// explicitly or not, so we apply the default (HEAD) only later.
	private String ref = null;

	private ResetType mode;

	private Collection<String> filepaths = new ArrayList<>();

	private boolean isReflogDisabled;

	private ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

	/**
	 * <p>
	 * Constructor for ResetCommand.
	 * </p>
	 *
	 * @param repo
	 *            the {@link org.eclipse.jgit.lib.Repository}
	 */
	public ResetCommand(Repository repo) {
		super(repo);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Executes the {@code Reset} command. Each instance of this class should
	 * only be used for one invocation of the command. Don't call this method
	 * twice on an instance.
	 */
	@Override
	public Ref call() throws GitAPIException, CheckoutConflictException {
		checkCallable();

		try {
			RepositoryState state = repo.getRepositoryState();
			final boolean merging = state.equals(RepositoryState.MERGING)
					|| state.equals(RepositoryState.MERGING_RESOLVED);
			final boolean cherryPicking = state
					.equals(RepositoryState.CHERRY_PICKING)
					|| state.equals(RepositoryState.CHERRY_PICKING_RESOLVED);
			final boolean reverting = state.equals(RepositoryState.REVERTING)
					|| state.equals(RepositoryState.REVERTING_RESOLVED);

			final ObjectId commitId = resolveRefToCommitId();
			// When ref is explicitly specified, it has to resolve
			if (ref != null && commitId == null) {
				// @TODO throw an InvalidRefNameException. We can't do that
				// now because this would break the API
				throw new JGitInternalException(MessageFormat
						.format(JGitText.get().invalidRefName, ref));
			}

			final ObjectId commitTree;
			if (commitId != null)
				commitTree = parseCommit(commitId).getTree();
			else
				commitTree = null;

			if (!filepaths.isEmpty()) {
				// reset [commit] -- paths
				resetIndexForPaths(commitTree);
				setCallable(false);
				return repo.exactRef(Constants.HEAD);
			}

			if (mode == ResetType.MERGE) {
				// Update index and working tree before moving HEAD so that HEAD
				// is untouched if the reset fails.
				mergeIndex(commitTree);
			}

			final Ref result;
			if (commitId != null) {
				// write the ref
				final RefUpdate ru = repo.updateRef(Constants.HEAD);
				ru.setNewObjectId(commitId);

				String refName = Repository.shortenRefName(getRefOrHEAD());
				if (isReflogDisabled) {
					ru.disableRefLog();
				} else {
					String message = refName + ": updating " + Constants.HEAD; //$NON-NLS-1$
					ru.setRefLogMessage(message, false);
				}
				if (ru.forceUpdate() == RefUpdate.Result.LOCK_FAILURE)
					throw new JGitInternalException(MessageFormat.format(
							JGitText.get().cannotLock, ru.getName()));

				ObjectId origHead = ru.getOldObjectId();
				if (origHead != null)
					repo.writeOrigHead(origHead);
			}
			result = repo.exactRef(Constants.HEAD);

			if (mode == null)
				mode = ResetType.MIXED;

			switch (mode) {
				case HARD:
					checkoutIndex(commitTree);
					break;
				case MIXED:
					resetIndex(commitTree);
					break;
				case SOFT: // do nothing, only the ref was changed
					break;
				case MERGE: // handled earlier
					break;
				case KEEP: // TODO
					throw new UnsupportedOperationException();

			}

			if (mode != ResetType.SOFT) {
				if (merging)
					resetMerge();
				else if (cherryPicking)
					resetCherryPick();
				else if (reverting)
					resetRevert();
				else if (repo.readSquashCommitMsg() != null)
					repo.writeSquashCommitMsg(null /* delete */);
			}

			setCallable(false);
			return result;
		} catch (IOException e) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().exceptionCaughtDuringExecutionOfResetCommand,
					e.getMessage()), e);
		}
	}

	private RevCommit parseCommit(ObjectId commitId) {
		try (RevWalk rw = new RevWalk(repo)) {
			return rw.parseCommit(commitId);
		} catch (IOException e) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().cannotReadCommit, commitId.toString()), e);
		}
	}

	private ObjectId resolveRefToCommitId() {
		try {
			return repo.resolve(getRefOrHEAD() + "^{commit}"); //$NON-NLS-1$
		} catch (IOException e) {
			throw new JGitInternalException(
					MessageFormat.format(JGitText.get().cannotRead, getRefOrHEAD()),
					e);
		}
	}

	/**
	 * Set the name of the <code>Ref</code> to reset to
	 *
	 * @param ref
	 *            the ref to reset to, defaults to HEAD if not specified
	 * @return this instance
	 */
	public ResetCommand setRef(String ref) {
		this.ref = ref;
		return this;
	}

	/**
	 * Set the reset mode
	 *
	 * @param mode
	 *            the mode of the reset command
	 * @return this instance
	 */
	public ResetCommand setMode(ResetType mode) {
		if (!filepaths.isEmpty())
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().illegalCombinationOfArguments,
					"[--mixed | --soft | --hard | --merge]", "<paths>...")); //$NON-NLS-1$ //$NON-NLS-2$
		this.mode = mode;
		return this;
	}

	/**
	 * Repository relative path of file or directory to reset
	 *
	 * @param path
	 *            repository-relative path of file/directory to reset (with
	 *            <code>/</code> as separator)
	 * @return this instance
	 */
	public ResetCommand addPath(String path) {
		if (mode != null)
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().illegalCombinationOfArguments, "<paths>...", //$NON-NLS-1$
					"[--mixed | --soft | --hard | --merge]")); //$NON-NLS-1$
		filepaths.add(path);
		return this;
	}

	/**
	 * Whether to disable reflog
	 *
	 * @param disable
	 *            if {@code true} disables writing a reflog entry for this reset
	 *            command
	 * @return this instance
	 * @since 4.5
	 */
	public ResetCommand disableRefLog(boolean disable) {
		this.isReflogDisabled = disable;
		return this;
	}

	/**
	 * Whether reflog is disabled
	 *
	 * @return {@code true} if writing reflog is disabled for this reset command
	 * @since 4.5
	 */
	public boolean isReflogDisabled() {
		return this.isReflogDisabled;
	}

	private String getRefOrHEAD() {
		if (ref != null) {
			return ref;
		}
		return Constants.HEAD;
	}

	/**
	 * The progress monitor associated with the reset operation. By default,
	 * this is set to <code>NullProgressMonitor</code>
	 *
	 * @see NullProgressMonitor
	 * @param monitor
	 *            a {@link org.eclipse.jgit.lib.ProgressMonitor}
	 * @return {@code this}
	 * @since 4.11
	 */
	public ResetCommand setProgressMonitor(ProgressMonitor monitor) {
		if (monitor == null) {
			monitor = NullProgressMonitor.INSTANCE;
		}
		this.monitor = monitor;
		return this;
	}

	private void resetIndexForPaths(ObjectId commitTree) {
		DirCache dc = null;
		try (TreeWalk tw = new TreeWalk(repo)) {
			dc = repo.lockDirCache();
			DirCacheBuilder builder = dc.builder();

			tw.addTree(new DirCacheBuildIterator(builder));
			if (commitTree != null)
				tw.addTree(commitTree);
			else
				tw.addTree(new EmptyTreeIterator());
			tw.setFilter(PathFilterGroup.createFromStrings(filepaths));
			tw.setRecursive(true);

			while (tw.next()) {
				final CanonicalTreeParser tree = tw.getTree(1,
						CanonicalTreeParser.class);
				// only keep file in index if it's in the commit
				if (tree != null) {
				    // revert index to commit
					DirCacheEntry entry = new DirCacheEntry(tw.getRawPath());
					entry.setFileMode(tree.getEntryFileMode());
					entry.setObjectId(tree.getEntryObjectId());
					builder.add(entry);
				}
			}

			builder.commit();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (dc != null)
				dc.unlock();
		}
	}

	private void resetIndex(ObjectId commitTree) throws IOException {
		DirCache dc = repo.lockDirCache();
		try (TreeWalk walk = new TreeWalk(repo)) {
			DirCacheBuilder builder = dc.builder();

			if (commitTree != null)
				walk.addTree(commitTree);
			else
				walk.addTree(new EmptyTreeIterator());
			walk.addTree(new DirCacheIterator(dc));
			walk.setRecursive(true);

			while (walk.next()) {
				AbstractTreeIterator cIter = walk.getTree(0,
						AbstractTreeIterator.class);
				if (cIter == null) {
					// Not in commit, don't add to new index
					continue;
				}

				final DirCacheEntry entry = new DirCacheEntry(walk.getRawPath());
				entry.setFileMode(cIter.getEntryFileMode());
				entry.setObjectIdFromRaw(cIter.idBuffer(), cIter.idOffset());

				DirCacheIterator dcIter = walk.getTree(1,
						DirCacheIterator.class);
				if (dcIter != null && dcIter.idEqual(cIter)) {
					DirCacheEntry indexEntry = dcIter.getDirCacheEntry();
					entry.setLastModified(indexEntry.getLastModifiedInstant());
					entry.setLength(indexEntry.getLength());
				}

				builder.add(entry);
			}

			builder.commit();
		} finally {
			dc.unlock();
		}
	}

	private void mergeIndex(ObjectId commitTree)
			throws IOException, CheckoutConflictException {
		DirCache dc = repo.lockDirCache();
		List<String> modified = new ArrayList<>();
		List<String> removed = new ArrayList<>();
		try (NameConflictTreeWalk walk = new NameConflictTreeWalk(repo)) {
			DirCacheBuilder builder = dc.builder();
			if (commitTree != null) {
				walk.addTree(commitTree);
			} else {
				walk.addTree(new EmptyTreeIterator());
			}
			walk.addTree(new DirCacheIterator(dc));
			FileTreeIterator workingTree = new FileTreeIterator(repo);
			walk.addTree(workingTree);
			workingTree.setDirCacheIterator(walk, 1);

			List<String> conflicts = new ArrayList<>();
			Map<DirCacheEntry, CheckoutMetadata> toCheckout = new LinkedHashMap<>();
			List<String> toDelete = new ArrayList<>();
			String previousPath = null;
			// Depth of a working tree directory that will be replaced by a
			// file, or -1 if the walk is not inside such a directory.
			int replacedDirDepth = -1;
			while (walk.next()) {
				if (replacedDirDepth >= 0
						&& walk.getDepth() <= replacedDirDepth) {
					replacedDirDepth = -1;
				}
				boolean insideReplacedDir = replacedDirDepth >= 0;

				String path = walk.getPathString();
				if (path.equals(previousPath)) {
					// further stage of an unmerged path
					continue;
				}
				previousPath = path;

				AbstractTreeIterator cIter = walk.getTree(0,
						AbstractTreeIterator.class);
				DirCacheIterator dcIter = walk.getTree(1,
						DirCacheIterator.class);
				WorkingTreeIterator wtIter = walk.getTree(2,
						WorkingTreeIterator.class);
				boolean cIsTree = isTree(cIter);
				boolean dcIsTree = isTree(dcIter);
				boolean wtIsTree = isTree(wtIter);
				AbstractTreeIterator cFileIter = cIsTree ? null : cIter;
				DirCacheEntry dce = dcIter != null && !dcIsTree
						? dcIter.getDirCacheEntry()
						: null;
				WorkingTreeIterator wtFileIter = wtIsTree ? null : wtIter;

				boolean replacesDir = false;
				if (dce != null && dce.isMerged() && cFileIter != null
						&& cFileIter.idEqual(dcIter)
						&& cFileIter.getEntryRawMode() == dce.getRawMode()) {
					// unchanged, keep unstaged changes
					builder.add(dce);
				} else if (dce != null && dce.isMerged() && wtFileIter != null
						&& !(isGitlink(dce) && isGitlink(wtFileIter))
						&& wtFileIter.isModified(dce, true,
								walk.getObjectReader())) {
					// unstaged changes would be lost
					conflicts.add(path);
				} else if (dce == null && wtFileIter != null
						&& !wtFileIter.isEntryIgnored() && (cFileIter != null
								|| cIsTree || insideReplacedDir)) {
					// untracked file would be overwritten or removed
					conflicts.add(path);
				} else if (cFileIter != null && wtFileIter != null
						&& isGitlink(wtFileIter) && !isGitlink(cFileIter)) {
					// don't delete a submodule repository
					conflicts.add(path);
				} else if (cFileIter != null) {
					DirCacheEntry newEntry = new DirCacheEntry(
							walk.getRawPath());
					newEntry.setFileMode(cFileIter.getEntryFileMode());
					newEntry.setObjectIdFromRaw(cFileIter.idBuffer(),
							cFileIter.idOffset());
					builder.add(newEntry);
					toCheckout.put(newEntry,
							new CheckoutMetadata(
									walk.getCheckoutEolStreamType(0),
									walk.getSmudgeCommand(0)));
					replacesDir = wtIsTree && !isGitlink(cFileIter);
				} else if (wtFileIter != null && (dce != null || cIsTree)) {
					// tracked file not in the commit, or ignored file in the
					// way of a directory
					toDelete.add(path);
				}
				if (replacesDir) {
					replacedDirDepth = walk.getDepth();
				}
				if (cIsTree || dcIsTree
						|| (wtIsTree && (replacesDir || insideReplacedDir)
								&& !wtIter.isEntryIgnored())) {
					walk.enterSubtree();
				}
			}

			if (!conflicts.isEmpty()) {
				throw new CheckoutConflictException(conflicts,
						new org.eclipse.jgit.errors.CheckoutConflictException(
								conflicts.toArray(
										new String[conflicts.size()])));
			}

			FS fs = repo.getFS();
			for (String path : toDelete) {
				File file = new File(repo.getWorkTree(), path);
				if (!file.delete() && fs.exists(file)) {
					// a non-empty submodule directory is kept
					if (!fs.isDirectory(file)) {
						throw new IOException(MessageFormat.format(
								JGitText.get().deleteFileFailed,
								file.getAbsolutePath()));
					}
					continue;
				}
				removeEmptyParents(file);
				removed.add(path);
			}

			// Directories in the way of files have been verified above to
			// contain only content that may be deleted.
			Checkout checkout = new Checkout(repo).setRecursiveDeletion(true);
			for (Map.Entry<DirCacheEntry, CheckoutMetadata> entry : toCheckout
					.entrySet()) {
				DirCacheEntry dce = entry.getKey();
				if (isGitlink(dce)) {
					// A file in the way has been verified above to be
					// deletable.
					Path target = new File(repo.getWorkTree(),
							dce.getPathString()).toPath();
					if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
							&& !Files.isDirectory(target,
									LinkOption.NOFOLLOW_LINKS)) {
						FileUtils.delete(target.toFile());
					}
					checkout.checkoutGitlink(dce, null);
				} else {
					checkout.checkout(dce, entry.getValue(),
							walk.getObjectReader(), null);
				}
				modified.add(dce.getPathString());
			}
			if (!builder.commit()) {
				throw new IndexWriteException();
			}
		} finally {
			try {
				dc.unlock();
			} finally {
				WorkingTreeModifiedEvent event = new WorkingTreeModifiedEvent(
						modified, removed);
				if (!event.isEmpty()) {
					repo.fireEvent(event);
				}
			}
		}
	}

	private static boolean isTree(AbstractTreeIterator iter) {
		return iter != null && FileMode.TREE.equals(iter.getEntryRawMode());
	}

	private static boolean isGitlink(DirCacheEntry entry) {
		return FileMode.GITLINK.equals(entry.getRawMode());
	}

	private static boolean isGitlink(AbstractTreeIterator iter) {
		return FileMode.GITLINK.equals(iter.getEntryRawMode());
	}

	private void removeEmptyParents(File file) {
		File parent = file.getParentFile();
		while (parent != null && !parent.equals(repo.getWorkTree())) {
			if (!parent.delete()) {
				break;
			}
			parent = parent.getParentFile();
		}
	}

	private void checkoutIndex(ObjectId commitTree) throws IOException,
			GitAPIException {
		DirCache dc = repo.lockDirCache();
		try {
			DirCacheCheckout checkout = new DirCacheCheckout(repo, dc,
					commitTree);
			checkout.setFailOnConflict(false);
			checkout.setProgressMonitor(monitor);
			try {
				checkout.checkout();
			} catch (org.eclipse.jgit.errors.CheckoutConflictException cce) {
				throw new CheckoutConflictException(checkout.getConflicts(),
						cce);
			}
		} finally {
			dc.unlock();
		}
	}

	private void resetMerge() throws IOException {
		repo.writeMergeHeads(null);
		repo.writeMergeCommitMsg(null);
	}

	private void resetCherryPick() throws IOException {
		repo.writeCherryPickHead(null);
		repo.writeMergeCommitMsg(null);
	}

	private void resetRevert() throws IOException {
		repo.writeRevertHead(null);
		repo.writeMergeCommitMsg(null);
	}

	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return "ResetCommand [repo=" + repo + ", ref=" + ref + ", mode=" + mode
				+ ", isReflogDisabled=" + isReflogDisabled + ", filepaths="
				+ filepaths + "]";
	}

}

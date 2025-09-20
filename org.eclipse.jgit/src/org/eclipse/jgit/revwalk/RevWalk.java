/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2014, Gustaf Lundh <gustaf.lundh@sonymobile.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revwalk;

import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraph.EMPTY;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevWalkException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.AsyncObjectLoaderQueue;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.References;
import org.eclipse.jgit.util.SystemReader;

/**
 * Walks a commit graph and produces the matching commits in order.
 * <p>
 * A RevWalk instance can only be used once to generate results. Running a
 * second time requires creating a new RevWalk instance, or invoking
 * {@link #reset()} before starting again. Resetting an existing instance may be
 * faster for some applications as commit body parsing can be avoided on the
 * later invocations.
 * <p>
 * RevWalk instances are not thread-safe. Applications must either restrict
 * usage of a RevWalk instance to a single thread, or implement their own
 * synchronization at a higher level.
 * <p>
 * Multiple simultaneous RevWalk instances per
 * {@link org.eclipse.jgit.lib.Repository} are permitted, even from concurrent
 * threads. Equality of {@link org.eclipse.jgit.revwalk.RevCommit}s from two
 * different RevWalk instances is never true, even if their
 * {@link org.eclipse.jgit.lib.ObjectId}s are equal (and thus they describe the
 * same commit).
 * <p>
 * The offered iterator is over the list of RevCommits described by the
 * configuration of this instance. Applications should restrict themselves to
 * using either the provided Iterator or {@link #next()}, but never use both on
 * the same RevWalk at the same time. The Iterator may buffer RevCommits, while
 * {@link #next()} does not.
 */
public class RevWalk implements Iterable<RevCommit>, AutoCloseable {
	private static final int MB = 1 << 20;

	/**
	 * Set on objects whose important header data has been loaded.
	 * <p>
	 * For a RevCommit this indicates we have pulled apart the tree and parent
	 * references from the raw bytes available in the repository and translated
	 * those to our own local RevTree and RevCommit instances. The raw buffer is
	 * also available for message and other header filtering.
	 * <p>
	 * For a RevTag this indicates we have pulled part the tag references to
	 * find out who the tag refers to, and what that object's type is.
	 */
	static final int PARSED = 1 << 0;

	/**
	 * Set on RevCommit instances added to our {@link #pending} queue.
	 * <p>
	 * We use this flag to avoid adding the same commit instance twice to our
	 * queue, especially if we reached it by more than one path.
	 */
	static final int SEEN = 1 << 1;

	/**
	 * Set on RevCommit instances the caller does not want output.
	 * <p>
	 * We flag commits as uninteresting if the caller does not want commits
	 * reachable from a commit given to {@link #markUninteresting(RevCommit)}.
	 * This flag is always carried into the commit's parents and is a key part
	 * of the "rev-list B --not A" feature; A is marked UNINTERESTING.
	 */
	static final int UNINTERESTING = 1 << 2;

	/**
	 * Set on a RevCommit that can collapse out of the history.
	 * <p>
	 * If the {@link #treeFilter} concluded that this commit matches his
	 * parents' for all of the paths that the filter is interested in then we
	 * mark the commit REWRITE. Later we can rewrite the parents of a REWRITE
	 * child to remove chains of REWRITE commits before we produce the child to
	 * the application.
	 *
	 * @see RewriteGenerator
	 */
	static final int REWRITE = 1 << 3;

	/**
	 * Temporary mark for use within generators or filters.
	 * <p>
	 * This mark is only for local use within a single scope. If someone sets
	 * the mark they must unset it before any other code can see the mark.
	 */
	static final int TEMP_MARK = 1 << 4;

	/**
	 * Temporary mark for use within {@link TopoSortGenerator}.
	 * <p>
	 * This mark indicates the commit could not produce when it wanted to, as at
	 * least one child was behind it. Commits with this flag are delayed until
	 * all children have been output first.
	 */
	static final int TOPO_DELAY = 1 << 5;

	/**
	 * Temporary mark for use within {@link TopoNonIntermixSortGenerator}.
	 * <p>
	 * This mark indicates the commit has been queued for emission in
	 * {@link TopoSortGenerator} and can be produced. This mark is removed when
	 * the commit has been produced.
	 */
	static final int TOPO_QUEUED = 1 << 6;

	/**
	 * Set on a RevCommit when a {@link TreeRevFilter} has been applied.
	 * <p>
	 * This flag is processed by the {@link RewriteGenerator} to check if a
	 * {@link TreeRevFilter} has been applied.
	 *
	 * @see TreeRevFilter
	 * @see RewriteGenerator
	 */
	static final int TREE_REV_FILTER_APPLIED = 1 << 7;

	/**
	 * Set on a RevObject marked for being unshallowed.
	 * <p>
	 * This flag is used by the RevWalk's generators for keeping track
	 * that some objects have been marked uninteresting, however, they
	 * need to allow the navigation to continue for managing the unshallow
	 * of a shallow clone.
	 *
	 * @see DepthGenerator
	 */
	static final int UNSHALLOW = 1 << 8;

	/**
	 * Number of flag bits we keep internal for our own use. See above flags.
	 */
	static final int RESERVED_FLAGS = 9;

	private static final int APP_FLAGS = -1 & ~((1 << RESERVED_FLAGS) - 1);

	final ObjectReader reader;

	private final boolean closeReader;

	final MutableObjectId idBuffer;

	ObjectIdOwnerMap<RevObject> objects;

	int freeFlags = APP_FLAGS;

	private int delayFreeFlags;

	private int retainOnReset;

	int carryFlags = UNINTERESTING;

	final ArrayList<RevCommit> roots;

	AbstractRevQueue queue;

	Generator pending;

	private final EnumSet<RevSort> sorting;

	private RevFilter filter;

	private TreeFilter treeFilter;

	private CommitGraph commitGraph;

	private boolean retainBody = true;

	private boolean rewriteParents = true;

	private boolean firstParent;

	boolean shallowCommitsInitialized;

	private enum GetMergedIntoStrategy {
		RETURN_ON_FIRST_FOUND, RETURN_ON_FIRST_NOT_FOUND, EVALUATE_ALL
	}

	/**
	 * Create a new revision walker for a given repository.
	 *
	 * @param repo
	 *            the repository the walker will obtain data from. An
	 *            ObjectReader will be created by the walker, and will be closed
	 *            when the walker is closed.
	 */
	public RevWalk(Repository repo) {
		this(repo.newObjectReader(), true);
	}

	/**
	 * Create a new revision walker for a given repository.
	 *
	 * @param or
	 *            the reader the walker will obtain data from. The reader is not
	 *            closed when the walker is closed (but is closed by
	 *            {@link #dispose()}.
	 */
	public RevWalk(ObjectReader or) {
		this(or, false);
	}

	RevWalk(ObjectReader or, boolean closeReader) {
		reader = or;
		idBuffer = new MutableObjectId();
		objects = new ObjectIdOwnerMap<>();
		roots = new ArrayList<>();
		queue = newDateRevQueue(false);
		pending = new StartGenerator(this);
		sorting = EnumSet.of(RevSort.NONE);
		filter = RevFilter.ALL;
		treeFilter = TreeFilter.ALL;
		this.closeReader = closeReader;
		commitGraph = null;
	}

	static AbstractRevQueue newDateRevQueue(boolean firstParent) {
		if(usePriorityQueue()) {
			return new DateRevPriorityQueue(firstParent);
		}

		return new DateRevQueue(firstParent);
	}

	static DateRevQueue newDateRevQueue(Generator g) throws IOException {
		if(usePriorityQueue()) {
			return new DateRevPriorityQueue(g);
		}

		return new DateRevQueue(g);
	}

	private static boolean usePriorityQueue() {
		return Boolean.parseBoolean(SystemReader.getInstance()
				.getProperty("REVWALK_USE_PRIORITY_QUEUE")); //$NON-NLS-1$
	}

	/**
	 * Get the reader this walker is using to load objects.
	 *
	 * @return the reader this walker is using to load objects.
	 */
	public ObjectReader getObjectReader() {
		return reader;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Release any resources used by this walker's reader.
	 * <p>
	 * A walker that has been released can be used again, but may need to be
	 * released after the subsequent usage.
	 *
	 * @since 4.0
	 */
	@Override
	public void close() {
		if (closeReader) {
			reader.close();
		}
	}

	/**
	 * Mark a commit to start graph traversal from.
	 * <p>
	 * Callers are encouraged to use {@link #parseCommit(AnyObjectId)} to obtain
	 * the commit reference, rather than {@link #lookupCommit(AnyObjectId)}, as
	 * this method requires the commit to be parsed before it can be added as a
	 * root for the traversal.
	 * <p>
	 * The method will automatically parse an unparsed commit, but error
	 * handling may be more difficult for the application to explain why a
	 * RevCommit is not actually a commit. The object pool of this walker would
	 * also be 'poisoned' by the non-commit RevCommit.
	 *
	 * @param c
	 *            the commit to start traversing from. The commit passed must be
	 *            from this same revision walker.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             the commit supplied is not available from the object
	 *             database. This usually indicates the supplied commit is
	 *             invalid, but the reference was constructed during an earlier
	 *             invocation to {@link #lookupCommit(AnyObjectId)}.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             the object was not parsed yet and it was discovered during
	 *             parsing that it is not actually a commit. This usually
	 *             indicates the caller supplied a non-commit SHA-1 to
	 *             {@link #lookupCommit(AnyObjectId)}.
	 * @throws java.io.IOException
	 *             a pack file or loose object could not be read.
	 */
	public void markStart(RevCommit c) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		if ((c.flags & SEEN) != 0)
			return;
		if ((c.flags & PARSED) == 0)
			c.parseHeaders(this);
		c.flags |= SEEN;
		roots.add(c);
		queue.add(c);
	}

	/**
	 * Mark commits to start graph traversal from.
	 *
	 * @param list
	 *            commits to start traversing from. The commits passed must be
	 *            from this same revision walker.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             one of the commits supplied is not available from the object
	 *             database. This usually indicates the supplied commit is
	 *             invalid, but the reference was constructed during an earlier
	 *             invocation to {@link #lookupCommit(AnyObjectId)}.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             the object was not parsed yet and it was discovered during
	 *             parsing that it is not actually a commit. This usually
	 *             indicates the caller supplied a non-commit SHA-1 to
	 *             {@link #lookupCommit(AnyObjectId)}.
	 * @throws java.io.IOException
	 *             a pack file or loose object could not be read.
	 */
	public void markStart(Collection<RevCommit> list)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		for (RevCommit c : list)
			markStart(c);
	}

	/**
	 * Mark a commit to not produce in the output.
	 * <p>
	 * Uninteresting commits denote not just themselves but also their entire
	 * ancestry chain, back until the merge base of an uninteresting commit and
	 * an otherwise interesting commit.
	 * <p>
	 * Callers are encouraged to use {@link #parseCommit(AnyObjectId)} to obtain
	 * the commit reference, rather than {@link #lookupCommit(AnyObjectId)}, as
	 * this method requires the commit to be parsed before it can be added as a
	 * root for the traversal.
	 * <p>
	 * The method will automatically parse an unparsed commit, but error
	 * handling may be more difficult for the application to explain why a
	 * RevCommit is not actually a commit. The object pool of this walker would
	 * also be 'poisoned' by the non-commit RevCommit.
	 *
	 * @param c
	 *            the commit to start traversing from. The commit passed must be
	 *            from this same revision walker.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             the commit supplied is not available from the object
	 *             database. This usually indicates the supplied commit is
	 *             invalid, but the reference was constructed during an earlier
	 *             invocation to {@link #lookupCommit(AnyObjectId)}.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             the object was not parsed yet and it was discovered during
	 *             parsing that it is not actually a commit. This usually
	 *             indicates the caller supplied a non-commit SHA-1 to
	 *             {@link #lookupCommit(AnyObjectId)}.
	 * @throws java.io.IOException
	 *             a pack file or loose object could not be read.
	 */
	public void markUninteresting(RevCommit c) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		c.flags |= UNINTERESTING;
		carryFlagsImpl(c);
		markStart(c);
	}

	/**
	 * Determine if a commit is reachable from another commit.
	 * <p>
	 * A commit <code>base</code> is an ancestor of <code>tip</code> if we can
	 * find a path of commits that leads from <code>tip</code> and ends at
	 * <code>base</code>.
	 * <p>
	 * This utility function resets the walker, inserts the two supplied
	 * commits, and then executes a walk until an answer can be obtained.
	 * Currently allocated RevFlags that have been added to RevCommit instances
	 * will be retained through the reset.
	 *
	 * @param base
	 *            commit the caller thinks is reachable from <code>tip</code>.
	 * @param tip
	 *            commit to start iteration from, and which is most likely a
	 *            descendant (child) of <code>base</code>.
	 * @return true if there is a path directly from <code>tip</code> to
	 *         <code>base</code> (and thus <code>base</code> is fully merged
	 *         into <code>tip</code>); false otherwise.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             one or more of the next commit's parents are not available
	 *             from the object database, but were thought to be candidates
	 *             for traversal. This usually indicates a broken link.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             one or more of the next commit's parents are not actually
	 *             commit objects.
	 * @throws java.io.IOException
	 *             a pack file or loose object could not be read.
	 */
	public boolean isMergedInto(RevCommit base, RevCommit tip)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		final RevFilter oldRF = filter;
		final TreeFilter oldTF = treeFilter;
		try {
			finishDelayedFreeFlags();
			reset(~freeFlags & APP_FLAGS);
			filter = RevFilter.MERGE_BASE;
			treeFilter = TreeFilter.ALL;
			markStart(tip);
			markStart(base);
			RevCommit mergeBase;
			while ((mergeBase = next()) != null) {
				if (References.isSameObject(mergeBase, base)) {
					return true;
				}
			}
			return false;
		} finally {
			filter = oldRF;
			treeFilter = oldTF;
		}
	}

	/**
	 * Determine the Refs into which a commit is merged.
	 * <p>
	 * A commit is merged into a ref if we can find a path of commits that leads
	 * from that specific ref and ends at <code>commit</code>.
	 *
	 * @param commit
	 *            commit the caller thinks is reachable from <code>refs</code>.
	 * @param refs
	 *            refs to start iteration from, and which is most likely a
	 *            descendant (child) of <code>commit</code>.
	 * @return list of refs that are reachable from <code>commit</code>.
	 * @throws java.io.IOException
	 *             a pack file or loose object could not be read.
	 * @since 5.12
	 */
	public List<Ref> getMergedInto(RevCommit commit, Collection<Ref> refs)
			throws IOException {
		return getMergedInto(commit, refs, NullProgressMonitor.INSTANCE);
	}

	/**
	 * Determine the Refs into which a commit is merged.
	 * <p>
	 * A commit is merged into a ref if we can find a path of commits that leads
	 * from that specific ref and ends at <code>commit</code>.
	 *
	 * @param commit
	 *            commit the caller thinks is reachable from <code>refs</code>.
	 * @param refs
	 *            refs to start iteration from, and which is most likely a
	 *            descendant (child) of <code>commit</code>.
	 * @param monitor
	 *            the callback for progress and cancellation
	 * @return list of refs that are reachable from <code>commit</code>.
	 * @throws java.io.IOException
	 *             a pack file or loose object could not be read.
	 * @since 5.12
	 */
	public List<Ref> getMergedInto(RevCommit commit, Collection<Ref> refs,
			ProgressMonitor monitor) throws IOException {
		return getMergedInto(commit, refs, GetMergedIntoStrategy.EVALUATE_ALL,
				monitor);
	}

	/**
	 * Determine if a <code>commit</code> is merged into any of the given
	 * <code>refs</code>.
	 *
	 * @param commit
	 *            commit the caller thinks is reachable from <code>refs</code>.
	 * @param refs
	 *            refs to start iteration from, and which is most likely a
	 *            descendant (child) of <code>commit</code>.
	 * @return true if commit is merged into any of the refs; false otherwise.
	 * @throws java.io.IOException
	 *             a pack file or loose object could not be read.
	 * @since 5.12
	 */
	public boolean isMergedIntoAny(RevCommit commit, Collection<Ref> refs)
			throws IOException {
		return getMergedInto(commit, refs,
				GetMergedIntoStrategy.RETURN_ON_FIRST_FOUND,
				NullProgressMonitor.INSTANCE).size() > 0;
	}

	/**
	 * Determine if a <code>commit</code> is merged into any of the given
	 * <code>revs</code>.
	 *
	 * @param commit
	 *            commit the caller thinks is reachable from <code>revs</code>.
	 * @param revs
	 *            commits to start iteration from, and which is most likely a
	 *            descendant (child) of <code>commit</code>.
	 * @return true if commit is merged into any of the revs; false otherwise.
	 * @throws java.io.IOException
	 *             a pack file or loose object could not be read.
	 * @since 6.10.1
	 */
	public boolean isMergedIntoAnyCommit(RevCommit commit, Collection<RevCommit> revs)
			throws IOException {
		return getCommitsMergedInto(commit, revs,
				GetMergedIntoStrategy.RETURN_ON_FIRST_FOUND,
				NullProgressMonitor.INSTANCE).size() > 0;
	}

	/**
	 * Determine if a <code>commit</code> is merged into all of the given
	 * <code>refs</code>.
	 *
	 * @param commit
	 *            commit the caller thinks is reachable from <code>refs</code>.
	 * @param refs
	 *            refs to start iteration from, and which is most likely a
	 *            descendant (child) of <code>commit</code>.
	 * @return true if commit is merged into all of the refs; false otherwise.
	 * @throws java.io.IOException
	 *             a pack file or loose object could not be read.
	 * @since 5.12
	 */
	public boolean isMergedIntoAll(RevCommit commit, Collection<Ref> refs)
			throws IOException {
		return getMergedInto(commit, refs,
				GetMergedIntoStrategy.RETURN_ON_FIRST_NOT_FOUND,
				NullProgressMonitor.INSTANCE).size() == refs.size();
	}

	private List<Ref> getMergedInto(RevCommit needle, Collection<Ref> haystacks,
			Enum returnStrategy, ProgressMonitor monitor) throws IOException {
		Map<RevCommit, List<Ref>> refsByCommit = new HashMap<>();
		for (Ref r : haystacks) {
			RevObject o = peel(parseAny(r.getObjectId()));
			if (!(o instanceof RevCommit)) {
				continue;
			}
			refsByCommit.computeIfAbsent((RevCommit) o, c -> new ArrayList<>()).add(r);
		}
		monitor.update(1);
		List<Ref> result = new ArrayList<>();
		for (RevCommit c : getCommitsMergedInto(needle, refsByCommit.keySet(),
				returnStrategy, monitor)) {
			result.addAll(refsByCommit.get(c));
		}
		return result;
	}

	private Set<RevCommit> getCommitsMergedInto(RevCommit needle, Collection<RevCommit> haystacks,
			Enum returnStrategy, ProgressMonitor monitor) throws IOException {
		Set<RevCommit> result = new HashSet<>();
		List<RevCommit> uninteresting = new ArrayList<>();
		List<RevCommit> marked = new ArrayList<>();
		RevFilter oldRF = filter;
		TreeFilter oldTF = treeFilter;
		try {
			finishDelayedFreeFlags();
			reset(~freeFlags & APP_FLAGS);
			filter = RevFilter.ALL;
			treeFilter = TreeFilter.ALL;

			// Make sure commit is parsed from commit-graph
			if ((needle.flags & PARSED) == 0) {
				needle.parseHeaders(this);
			}
			int cutoff = needle.getGeneration();
			for (RevCommit c : haystacks) {
				if (monitor.isCancelled()) {
					return result;
				}
				monitor.update(1);
				reset(UNINTERESTING | TEMP_MARK);
				markStart(c);
				boolean commitFound = false;
				RevCommit next;
				while ((next = next()) != null) {
					if (next.getGeneration() < cutoff) {
						markUninteresting(next);
						uninteresting.add(next);
					}
					if (References.isSameObject(next, needle)
							|| (next.flags & TEMP_MARK) != 0) {
						result.add(c);
						if (returnStrategy == GetMergedIntoStrategy.RETURN_ON_FIRST_FOUND) {
							return result;
						}
						commitFound = true;
						c.flags |= TEMP_MARK;
						marked.add(c);
						break;
					}
				}
				if (!commitFound) {
					markUninteresting(c);
					uninteresting.add(c);
					if (returnStrategy == GetMergedIntoStrategy.RETURN_ON_FIRST_NOT_FOUND) {
						return result;
					}
				}
			}
		} finally {
			roots.addAll(uninteresting);
			filter = oldRF;
			treeFilter = oldTF;
			for (RevCommit c : marked) {
				c.flags &= ~TEMP_MARK;
			}
		}
		return result;
	}

	/**
	 * Pop the next most recent commit.
	 *
	 * @return next most recent commit; null if traversal is over.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             one or more of the next commit's parents are not available
	 *             from the object database, but were thought to be candidates
	 *             for traversal. This usually indicates a broken link.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             one or more of the next commit's parents are not actually
	 *             commit objects.
	 * @throws java.io.IOException
	 *             a pack file or loose object could not be read.
	 */
	public RevCommit next() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		return pending.next();
	}

	/**
	 * Obtain the sort types applied to the commits returned.
	 *
	 * @return the sorting strategies employed. At least one strategy is always
	 *         used, but that strategy may be
	 *         {@link org.eclipse.jgit.revwalk.RevSort#NONE}.
	 */
	public EnumSet<RevSort> getRevSort() {
		return sorting.clone();
	}

	/**
	 * Check whether the provided sorting strategy is enabled.
	 *
	 * @param sort
	 *            a sorting strategy to look for.
	 * @return true if this strategy is enabled, false otherwise
	 */
	public boolean hasRevSort(RevSort sort) {
		return sorting.contains(sort);
	}

	/**
	 * Select a single sorting strategy for the returned commits.
	 * <p>
	 * Disables all sorting strategies, then enables only the single strategy
	 * supplied by the caller.
	 *
	 * @param s
	 *            a sorting strategy to enable.
	 */
	public void sort(RevSort s) {
		assertNotStarted();
		sorting.clear();
		sorting.add(s);
	}

	/**
	 * Add or remove a sorting strategy for the returned commits.
	 * <p>
	 * Multiple strategies can be applied at once, in which case some strategies
	 * may take precedence over others. As an example,
	 * {@link org.eclipse.jgit.revwalk.RevSort#TOPO} must take precedence over
	 * {@link org.eclipse.jgit.revwalk.RevSort#COMMIT_TIME_DESC}, otherwise it
	 * cannot enforce its ordering.
	 *
	 * @param s
	 *            a sorting strategy to enable or disable.
	 * @param use
	 *            true if this strategy should be used, false if it should be
	 *            removed.
	 */
	public void sort(RevSort s, boolean use) {
		assertNotStarted();
		if (use)
			sorting.add(s);
		else
			sorting.remove(s);

		if (sorting.size() > 1)
			sorting.remove(RevSort.NONE);
		else if (sorting.isEmpty())
			sorting.add(RevSort.NONE);
	}

	/**
	 * Get the currently configured commit filter.
	 *
	 * @return the current filter. Never null as a filter is always needed.
	 */
	@NonNull
	public RevFilter getRevFilter() {
		return filter;
	}

	/**
	 * Set the commit filter for this walker.
	 * <p>
	 * Multiple filters may be combined by constructing an arbitrary tree of
	 * <code>AndRevFilter</code> or <code>OrRevFilter</code> instances to
	 * describe the boolean expression required by the application. Custom
	 * filter implementations may also be constructed by applications.
	 * <p>
	 * Note that filters are not thread-safe and may not be shared by concurrent
	 * RevWalk instances. Every RevWalk must be supplied its own unique filter,
	 * unless the filter implementation specifically states it is (and always
	 * will be) thread-safe. Callers may use
	 * {@link org.eclipse.jgit.revwalk.filter.RevFilter#clone()} to create a
	 * unique filter tree for this RevWalk instance.
	 *
	 * @param newFilter
	 *            the new filter. If null the special
	 *            {@link org.eclipse.jgit.revwalk.filter.RevFilter#ALL} filter
	 *            will be used instead, as it matches every commit.
	 * @see org.eclipse.jgit.revwalk.filter.AndRevFilter
	 * @see org.eclipse.jgit.revwalk.filter.OrRevFilter
	 */
	public void setRevFilter(RevFilter newFilter) {
		assertNotStarted();
		filter = newFilter != null ? newFilter : RevFilter.ALL;
	}

	/**
	 * Get the tree filter used to simplify commits by modified paths.
	 *
	 * @return the current filter. Never null as a filter is always needed. If
	 *         no filter is being applied
	 *         {@link org.eclipse.jgit.treewalk.filter.TreeFilter#ALL} is
	 *         returned.
	 */
	@NonNull
	public TreeFilter getTreeFilter() {
		return treeFilter;
	}

	/**
	 * Set the tree filter used to simplify commits by modified paths.
	 * <p>
	 * If null or {@link org.eclipse.jgit.treewalk.filter.TreeFilter#ALL} the
	 * path limiter is removed. Commits will not be simplified.
	 * <p>
	 * If non-null and not
	 * {@link org.eclipse.jgit.treewalk.filter.TreeFilter#ALL} then the tree
	 * filter will be installed. Commits will have their ancestry simplified to
	 * hide commits that do not contain tree entries matched by the filter,
	 * unless {@code setRewriteParents(false)} is called.
	 * <p>
	 * Usually callers should be inserting a filter graph including
	 * {@link org.eclipse.jgit.treewalk.filter.TreeFilter#ANY_DIFF} along with
	 * one or more {@link org.eclipse.jgit.treewalk.filter.PathFilter}
	 * instances.
	 *
	 * @param newFilter
	 *            new filter. If null the special
	 *            {@link org.eclipse.jgit.treewalk.filter.TreeFilter#ALL} filter
	 *            will be used instead, as it matches everything.
	 * @see org.eclipse.jgit.treewalk.filter.PathFilter
	 */
	public void setTreeFilter(TreeFilter newFilter) {
		assertNotStarted();
		treeFilter = newFilter != null ? newFilter : TreeFilter.ALL;
	}

	/**
	 * Set whether to rewrite parent pointers when filtering by modified paths.
	 * <p>
	 * By default, when {@link #setTreeFilter(TreeFilter)} is called with non-
	 * null and non-{@link org.eclipse.jgit.treewalk.filter.TreeFilter#ALL}
	 * filter, commits will have their ancestry simplified and parents rewritten
	 * to hide commits that do not match the filter.
	 * <p>
	 * This behavior can be bypassed by passing false to this method.
	 *
	 * @param rewrite
	 *            whether to rewrite parents; defaults to true.
	 * @since 3.4
	 */
	public void setRewriteParents(boolean rewrite) {
		rewriteParents = rewrite;
	}

	boolean getRewriteParents() {
		return rewriteParents;
	}

	/**
	 * Should the body of a commit or tag be retained after parsing its headers?
	 * <p>
	 * Usually the body is always retained, but some application code might not
	 * care and would prefer to discard the body of a commit as early as
	 * possible, to reduce memory usage.
	 * <p>
	 * True by default on {@link org.eclipse.jgit.revwalk.RevWalk} and false by
	 * default for {@link org.eclipse.jgit.revwalk.ObjectWalk}.
	 *
	 * @return true if the body should be retained; false it is discarded.
	 */
	public boolean isRetainBody() {
		return retainBody;
	}

	/**
	 * Set whether or not the body of a commit or tag is retained.
	 * <p>
	 * If a body of a commit or tag is not retained, the application must call
	 * {@link #parseBody(RevObject)} before the body can be safely accessed
	 * through the type specific access methods.
	 * <p>
	 * True by default on {@link org.eclipse.jgit.revwalk.RevWalk} and false by
	 * default for {@link org.eclipse.jgit.revwalk.ObjectWalk}.
	 *
	 * @param retain
	 *            true to retain bodies; false to discard them early.
	 */
	public void setRetainBody(boolean retain) {
		retainBody = retain;
	}

	/**
	 * Whether only first-parent links should be followed when walking
	 *
	 * @return whether only first-parent links should be followed when walking.
	 *
	 * @since 5.5
	 */
	public boolean isFirstParent() {
		return firstParent;
	}

	/**
	 * Set whether or not only first parent links should be followed.
	 * <p>
	 * If set, second- and higher-parent links are not traversed at all.
	 * <p>
	 * This must be called prior to {@link #markStart(RevCommit)}.
	 *
	 * @param enable
	 *            true to walk only first-parent links.
	 *
	 * @since 5.5
	 */
	public void setFirstParent(boolean enable) {
		assertNotStarted();
		assertNoCommitsMarkedStart();
		firstParent = enable;
		queue = newDateRevQueue(firstParent);
		pending = new StartGenerator(this);
	}

	/**
	 * Locate a reference to a blob without loading it.
	 * <p>
	 * The blob may or may not exist in the repository. It is impossible to tell
	 * from this method's return value.
	 *
	 * @param id
	 *            name of the blob object.
	 * @return reference to the blob object. Never null.
	 */
	@NonNull
	public RevBlob lookupBlob(AnyObjectId id) {
		RevBlob c = (RevBlob) objects.get(id);
		if (c == null) {
			c = new RevBlob(id);
			objects.add(c);
		}
		return c;
	}

	/**
	 * Locate a reference to a tree without loading it.
	 * <p>
	 * The tree may or may not exist in the repository. It is impossible to tell
	 * from this method's return value.
	 *
	 * @param id
	 *            name of the tree object.
	 * @return reference to the tree object. Never null.
	 */
	@NonNull
	public RevTree lookupTree(AnyObjectId id) {
		RevTree c = (RevTree) objects.get(id);
		if (c == null) {
			c = new RevTree(id);
			objects.add(c);
		}
		return c;
	}

	/**
	 * Locate a reference to a commit without loading it.
	 * <p>
	 * The commit may or may not exist in the repository. It is impossible to
	 * tell from this method's return value.
	 * <p>
	 * See {@link #parseHeaders(RevObject)} and {@link #parseBody(RevObject)}
	 * for loading contents.
	 *
	 * @param id
	 *            name of the commit object.
	 * @return reference to the commit object. Never null.
	 */
	@NonNull
	public RevCommit lookupCommit(AnyObjectId id) {
		RevCommit c = (RevCommit) objects.get(id);
		if (c == null) {
			c = createCommit(id);
			objects.add(c);
		}
		return c;
	}

	/**
	 * This method is intended to be invoked only by {@link RevCommitCG}, in
	 * order to give commit the correct graphPosition before accessing the
	 * commit-graph. In this way, the headers of the commit can be obtained in
	 * constant time.
	 *
	 * @param id
	 *            name of the commit object.
	 * @param graphPos
	 *            the position in the commit-graph of the object.
	 * @return reference to the commit object. Never null.
	 * @since 6.5
	 */
	@NonNull
	protected RevCommit lookupCommit(AnyObjectId id, int graphPos) {
		RevCommit c = (RevCommit) objects.get(id);
		if (c == null) {
			c = createCommit(id, graphPos);
			objects.add(c);
		}
		return c;
	}

	/**
	 * Locate a reference to a tag without loading it.
	 * <p>
	 * The tag may or may not exist in the repository. It is impossible to tell
	 * from this method's return value.
	 *
	 * @param id
	 *            name of the tag object.
	 * @return reference to the tag object. Never null.
	 */
	@NonNull
	public RevTag lookupTag(AnyObjectId id) {
		RevTag c = (RevTag) objects.get(id);
		if (c == null) {
			c = new RevTag(id);
			objects.add(c);
		}
		return c;
	}

	/**
	 * Locate a reference to any object without loading it.
	 * <p>
	 * The object may or may not exist in the repository. It is impossible to
	 * tell from this method's return value.
	 *
	 * @param id
	 *            name of the object.
	 * @param type
	 *            type of the object. Must be a valid Git object type.
	 * @return reference to the object. Never null.
	 */
	@NonNull
	public RevObject lookupAny(AnyObjectId id, int type) {
		RevObject r = objects.get(id);
		if (r == null) {
			switch (type) {
			case Constants.OBJ_COMMIT:
				r = createCommit(id);
				break;
			case Constants.OBJ_TREE:
				r = new RevTree(id);
				break;
			case Constants.OBJ_BLOB:
				r = new RevBlob(id);
				break;
			case Constants.OBJ_TAG:
				r = new RevTag(id);
				break;
			default:
				throw new IllegalArgumentException(MessageFormat.format(
						JGitText.get().invalidGitType, Integer.valueOf(type)));
			}
			objects.add(r);
		}
		return r;
	}

	/**
	 * Locate an object that was previously allocated in this walk.
	 *
	 * @param id
	 *            name of the object.
	 * @return reference to the object if it has been previously located;
	 *         otherwise null.
	 */
	public RevObject lookupOrNull(AnyObjectId id) {
		return objects.get(id);
	}

	/**
	 * Locate a reference to a commit and immediately parse its content.
	 * <p>
	 * Unlike {@link #lookupCommit(AnyObjectId)} this method only returns
	 * successfully if the commit object exists, is verified to be a commit, and
	 * was parsed without error.
	 *
	 * @param id
	 *            name of the commit object.
	 * @return reference to the commit object. Never null.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             the supplied commit does not exist.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             the supplied id is not a commit or an annotated tag.
	 * @throws java.io.IOException
	 *             a pack file or loose object could not be read.
	 */
	@NonNull
	public RevCommit parseCommit(AnyObjectId id) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		RevObject c = peel(parseAny(id));
		if (!(c instanceof RevCommit))
			throw new IncorrectObjectTypeException(id.toObjectId(),
					Constants.TYPE_COMMIT);
		return (RevCommit) c;
	}

	/**
	 * Locate a reference to a tree.
	 * <p>
	 * This method only returns successfully if the tree object exists, is
	 * verified to be a tree.
	 *
	 * @param id
	 *            name of the tree object, or a commit or annotated tag that may
	 *            reference a tree.
	 * @return reference to the tree object. Never null.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             the supplied tree does not exist.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             the supplied id is not a tree, a commit or an annotated tag.
	 * @throws java.io.IOException
	 *             a pack file or loose object could not be read.
	 */
	@NonNull
	public RevTree parseTree(AnyObjectId id) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		RevObject c = peel(parseAny(id));

		final RevTree t;
		if (c instanceof RevCommit)
			t = ((RevCommit) c).getTree();
		else if (!(c instanceof RevTree))
			throw new IncorrectObjectTypeException(id.toObjectId(),
					Constants.TYPE_TREE);
		else
			t = (RevTree) c;
		parseHeaders(t);
		return t;
	}

	/**
	 * Locate a reference to an annotated tag and immediately parse its content.
	 * <p>
	 * Unlike {@link #lookupTag(AnyObjectId)} this method only returns
	 * successfully if the tag object exists, is verified to be a tag, and was
	 * parsed without error.
	 *
	 * @param id
	 *            name of the tag object.
	 * @return reference to the tag object. Never null.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             the supplied tag does not exist.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             the supplied id is not a tag or an annotated tag.
	 * @throws java.io.IOException
	 *             a pack file or loose object could not be read.
	 */
	@NonNull
	public RevTag parseTag(AnyObjectId id) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		RevObject c = parseAny(id);
		if (!(c instanceof RevTag))
			throw new IncorrectObjectTypeException(id.toObjectId(),
					Constants.TYPE_TAG);
		return (RevTag) c;
	}

	/**
	 * Locate a reference to any object and immediately parse its headers.
	 * <p>
	 * This method only returns successfully if the object exists and was parsed
	 * without error. Parsing an object can be expensive as the type must be
	 * determined. For blobs this may mean the blob content was unpacked
	 * unnecessarily, and thrown away.
	 *
	 * @param id
	 *            name of the object.
	 * @return reference to the object. Never null.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             the supplied does not exist.
	 * @throws java.io.IOException
	 *             a pack file or loose object could not be read.
	 */
	@NonNull
	public RevObject parseAny(AnyObjectId id)
			throws MissingObjectException, IOException {
		RevObject r = objects.get(id);
		if (r == null)
			r = parseNew(id, reader.open(id));
		else
			parseHeaders(r);
		return r;
	}

	private RevObject parseNew(AnyObjectId id, ObjectLoader ldr)
			throws LargeObjectException, CorruptObjectException,
			MissingObjectException, IOException {
		RevObject r;
		int type = ldr.getType();
		switch (type) {
		case Constants.OBJ_COMMIT: {
			final RevCommit c = createCommit(id);
			c.parseCanonical(this, getCachedBytes(c, ldr));
			r = c;
			break;
		}
		case Constants.OBJ_TREE: {
			r = new RevTree(id);
			r.flags |= PARSED;
			break;
		}
		case Constants.OBJ_BLOB: {
			r = new RevBlob(id);
			r.flags |= PARSED;
			break;
		}
		case Constants.OBJ_TAG: {
			final RevTag t = new RevTag(id);
			t.parseCanonical(this, getCachedBytes(t, ldr));
			r = t;
			break;
		}
		default:
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().badObjectType, Integer.valueOf(type)));
		}
		objects.add(r);
		return r;
	}

	byte[] getCachedBytes(RevObject obj) throws LargeObjectException,
			MissingObjectException, IncorrectObjectTypeException, IOException {
		return getCachedBytes(obj, reader.open(obj, obj.getType()));
	}

	byte[] getCachedBytes(RevObject obj, ObjectLoader ldr)
			throws LargeObjectException, MissingObjectException, IOException {
		try {
			return ldr.getCachedBytes(5 * MB);
		} catch (LargeObjectException tooBig) {
			tooBig.setObjectId(obj);
			throw tooBig;
		}
	}

	/**
	 * Get the commit-graph.
	 *
	 * @return the commit-graph. Never null.
	 * @since 6.5
	 */
	@NonNull
	CommitGraph commitGraph() {
		if (commitGraph == null) {
			try {
				commitGraph = reader != null
						? reader.getCommitGraph().orElse(EMPTY)
						: EMPTY;
			} catch (IOException e) {
				commitGraph = EMPTY;
			}
		}
		return commitGraph;
	}

	/**
	 * Asynchronous object parsing.
	 *
	 * @param <T>
	 *            Type of returned {@code ObjectId}
	 * @param objectIds
	 *            objects to open from the object store. The supplied collection
	 *            must not be modified until the queue has finished.
	 * @param reportMissing
	 *            if true missing objects are reported by calling failure with a
	 *            MissingObjectException. This may be more expensive for the
	 *            implementation to guarantee. If false the implementation may
	 *            choose to report MissingObjectException, or silently skip over
	 *            the object with no warning.
	 * @return queue to read the objects from.
	 */
	public <T extends ObjectId> AsyncRevObjectQueue parseAny(
			Iterable<T> objectIds, boolean reportMissing) {
		List<T> need = new ArrayList<>();
		List<RevObject> have = new ArrayList<>();
		for (T id : objectIds) {
			RevObject r = objects.get(id);
			if (r != null && (r.flags & PARSED) != 0)
				have.add(r);
			else
				need.add(id);
		}

		final Iterator<RevObject> objItr = have.iterator();
		if (need.isEmpty()) {
			return new AsyncRevObjectQueue() {
				@Override
				public RevObject next() {
					return objItr.hasNext() ? objItr.next() : null;
				}

				@Override
				public boolean cancel(boolean mayInterruptIfRunning) {
					return true;
				}

				@Override
				public void release() {
					// In-memory only, no action required.
				}
			};
		}

		final AsyncObjectLoaderQueue<T> lItr = reader.open(need, reportMissing);
		return new AsyncRevObjectQueue() {
			@Override
			public RevObject next() throws MissingObjectException,
					IncorrectObjectTypeException, IOException {
				if (objItr.hasNext())
					return objItr.next();
				if (!lItr.next())
					return null;

				ObjectId id = lItr.getObjectId();
				ObjectLoader ldr = lItr.open();
				RevObject r = objects.get(id);
				if (r == null)
					r = parseNew(id, ldr);
				else if (r instanceof RevCommit) {
					byte[] raw = ldr.getCachedBytes();
					((RevCommit) r).parseCanonical(RevWalk.this, raw);
				} else if (r instanceof RevTag) {
					byte[] raw = ldr.getCachedBytes();
					((RevTag) r).parseCanonical(RevWalk.this, raw);
				} else
					r.flags |= PARSED;
				return r;
			}

			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				return lItr.cancel(mayInterruptIfRunning);
			}

			@Override
			public void release() {
				lItr.release();
			}
		};
	}

	/**
	 * Ensure the object's critical headers have been parsed.
	 * <p>
	 * This method only returns successfully if the object exists and was parsed
	 * without error.
	 *
	 * @param obj
	 *            the object the caller needs to be parsed.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             the supplied does not exist.
	 * @throws java.io.IOException
	 *             a pack file or loose object could not be read.
	 */
	public void parseHeaders(RevObject obj)
			throws MissingObjectException, IOException {
		if ((obj.flags & PARSED) == 0)
			obj.parseHeaders(this);
	}

	/**
	 * Ensure the object's full body content is available.
	 * <p>
	 * This method only returns successfully if the object exists and was parsed
	 * without error.
	 *
	 * @param obj
	 *            the object the caller needs to be parsed.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             the supplied does not exist.
	 * @throws java.io.IOException
	 *             a pack file or loose object could not be read.
	 */
	public void parseBody(RevObject obj)
			throws MissingObjectException, IOException {
		obj.parseBody(this);
	}

	/**
	 * Peel back annotated tags until a non-tag object is found.
	 *
	 * @param obj
	 *            the starting object.
	 * @return If {@code obj} is not an annotated tag, {@code obj}. Otherwise
	 *         the first non-tag object that {@code obj} references. The
	 *         returned object's headers have been parsed.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             a referenced object cannot be found.
	 * @throws java.io.IOException
	 *             a pack file or loose object could not be read.
	 */
	public RevObject peel(RevObject obj)
			throws MissingObjectException, IOException {
		while (obj instanceof RevTag) {
			parseHeaders(obj);
			obj = ((RevTag) obj).getObject();
		}
		parseHeaders(obj);
		return obj;
	}

	/**
	 * Create a new flag for application use during walking.
	 * <p>
	 * Applications are only assured to be able to create 24 unique flags on any
	 * given revision walker instance. Any flags beyond 24 are offered only if
	 * the implementation has extra free space within its internal storage.
	 *
	 * @param name
	 *            description of the flag, primarily useful for debugging.
	 * @return newly constructed flag instance.
	 * @throws java.lang.IllegalArgumentException
	 *             too many flags have been reserved on this revision walker.
	 */
	public RevFlag newFlag(String name) {
		final int m = allocFlag();
		return new RevFlag(this, name, m);
	}

	int allocFlag() {
		if (freeFlags == 0)
			throw new IllegalArgumentException(
					MessageFormat.format(JGitText.get().flagsAlreadyCreated,
							Integer.valueOf(32 - RESERVED_FLAGS)));
		final int m = Integer.lowestOneBit(freeFlags);
		freeFlags &= ~m;
		return m;
	}

	/**
	 * Automatically carry a flag from a child commit to its parents.
	 * <p>
	 * A carried flag is copied from the child commit onto its parents when the
	 * child commit is popped from the lowest level of walk's internal graph.
	 *
	 * @param flag
	 *            the flag to carry onto parents, if set on a descendant.
	 */
	public void carry(RevFlag flag) {
		if ((freeFlags & flag.mask) != 0)
			throw new IllegalArgumentException(MessageFormat
					.format(JGitText.get().flagIsDisposed, flag.name));
		if (flag.walker != this)
			throw new IllegalArgumentException(MessageFormat
					.format(JGitText.get().flagNotFromThis, flag.name));
		carryFlags |= flag.mask;
	}

	/**
	 * Automatically carry flags from a child commit to its parents.
	 * <p>
	 * A carried flag is copied from the child commit onto its parents when the
	 * child commit is popped from the lowest level of walk's internal graph.
	 *
	 * @param set
	 *            the flags to carry onto parents, if set on a descendant.
	 */
	public void carry(Collection<RevFlag> set) {
		for (RevFlag flag : set)
			carry(flag);
	}

	/**
	 * Preserve a RevFlag during all {@code reset} methods.
	 * <p>
	 * Calling {@code retainOnReset(flag)} avoids needing to pass the flag
	 * during each {@code resetRetain()} invocation on this instance.
	 * <p>
	 * Clearing flags marked retainOnReset requires disposing of the flag with
	 * {@code #disposeFlag(RevFlag)} or disposing of the entire RevWalk by
	 * {@code #dispose()}.
	 *
	 * @param flag
	 *            the flag to retain during all resets.
	 * @since 3.6
	 */
	public final void retainOnReset(RevFlag flag) {
		if ((freeFlags & flag.mask) != 0)
			throw new IllegalArgumentException(MessageFormat
					.format(JGitText.get().flagIsDisposed, flag.name));
		if (flag.walker != this)
			throw new IllegalArgumentException(MessageFormat
					.format(JGitText.get().flagNotFromThis, flag.name));
		retainOnReset |= flag.mask;
	}

	/**
	 * Preserve a set of RevFlags during all {@code reset} methods.
	 * <p>
	 * Calling {@code retainOnReset(set)} avoids needing to pass the flags
	 * during each {@code resetRetain()} invocation on this instance.
	 * <p>
	 * Clearing flags marked retainOnReset requires disposing of the flag with
	 * {@code #disposeFlag(RevFlag)} or disposing of the entire RevWalk by
	 * {@code #dispose()}.
	 *
	 * @param flags
	 *            the flags to retain during all resets.
	 * @since 3.6
	 */
	public final void retainOnReset(Collection<RevFlag> flags) {
		for (RevFlag f : flags)
			retainOnReset(f);
	}

	/**
	 * Allow a flag to be recycled for a different use.
	 * <p>
	 * Recycled flags always come back as a different Java object instance when
	 * assigned again by {@link #newFlag(String)}.
	 * <p>
	 * If the flag was previously being carried, the carrying request is
	 * removed. Disposing of a carried flag while a traversal is in progress has
	 * an undefined behavior.
	 *
	 * @param flag
	 *            the to recycle.
	 */
	public void disposeFlag(RevFlag flag) {
		freeFlag(flag.mask);
	}

	void freeFlag(int mask) {
		retainOnReset &= ~mask;
		if (isNotStarted()) {
			freeFlags |= mask;
			carryFlags &= ~mask;
		} else {
			delayFreeFlags |= mask;
		}
	}

	private void finishDelayedFreeFlags() {
		if (delayFreeFlags != 0) {
			freeFlags |= delayFreeFlags;
			carryFlags &= ~delayFreeFlags;
			delayFreeFlags = 0;
		}
	}

	/**
	 * Resets internal state and allows this instance to be used again.
	 * <p>
	 * Unlike {@link #dispose()} previously acquired RevObject (and RevCommit)
	 * instances are not invalidated. RevFlag instances are not invalidated, but
	 * are removed from all RevObjects.
	 */
	public final void reset() {
		reset(0);
	}

	/**
	 * Resets internal state and allows this instance to be used again.
	 * <p>
	 * Unlike {@link #dispose()} previously acquired RevObject (and RevCommit)
	 * instances are not invalidated. RevFlag instances are not invalidated, but
	 * are removed from all RevObjects.
	 *
	 * @param retainFlags
	 *            application flags that should <b>not</b> be cleared from
	 *            existing commit objects.
	 */
	public final void resetRetain(RevFlagSet retainFlags) {
		reset(retainFlags.mask);
	}

	/**
	 * Resets internal state and allows this instance to be used again.
	 * <p>
	 * Unlike {@link #dispose()} previously acquired RevObject (and RevCommit)
	 * instances are not invalidated. RevFlag instances are not invalidated, but
	 * are removed from all RevObjects.
	 * <p>
	 * See {@link #retainOnReset(RevFlag)} for an alternative that does not
	 * require passing the flags during each reset.
	 *
	 * @param retainFlags
	 *            application flags that should <b>not</b> be cleared from
	 *            existing commit objects.
	 */
	public final void resetRetain(RevFlag... retainFlags) {
		int mask = 0;
		for (RevFlag flag : retainFlags)
			mask |= flag.mask;
		reset(mask);
	}

	/**
	 * Resets internal state and allows this instance to be used again.
	 * <p>
	 * Unlike {@link #dispose()} previously acquired RevObject (and RevCommit)
	 * instances are not invalidated. RevFlag instances are not invalidated, but
	 * are removed from all RevObjects. The value of {@code firstParent} is
	 * retained.
	 *
	 * @param retainFlags
	 *            application flags that should <b>not</b> be cleared from
	 *            existing commit objects.
	 */
	protected void reset(int retainFlags) {
		finishDelayedFreeFlags();
		retainFlags |= PARSED | retainOnReset;
		final int clearFlags = ~retainFlags;

		final FIFORevQueue q = new FIFORevQueue();
		for (RevCommit c : roots) {
			if ((c.flags & clearFlags) == 0)
				continue;
			c.flags &= retainFlags;
			c.reset();
			q.add(c);
		}

		for (;;) {
			final RevCommit c = q.next();
			if (c == null)
				break;
			if (c.getParents() == null)
				continue;
			for (RevCommit p : c.getParents()) {
				if ((p.flags & clearFlags) == 0)
					continue;
				p.flags &= retainFlags;
				p.reset();
				q.add(p);
			}
		}

		roots.clear();
		queue = newDateRevQueue(firstParent);
		pending = new StartGenerator(this);
	}

	/**
	 * Dispose all internal state and invalidate all RevObject instances.
	 * <p>
	 * All RevObject (and thus RevCommit, etc.) instances previously acquired
	 * from this RevWalk are invalidated by a dispose call. Applications must
	 * not retain or use RevObject instances obtained prior to the dispose call.
	 * All RevFlag instances are also invalidated, and must not be reused.
	 */
	public void dispose() {
		reader.close();
		freeFlags = APP_FLAGS;
		delayFreeFlags = 0;
		retainOnReset = 0;
		carryFlags = UNINTERESTING;
		firstParent = false;
		objects.clear();
		roots.clear();
		queue = newDateRevQueue(firstParent);
		pending = new StartGenerator(this);
		shallowCommitsInitialized = false;
	}

	/**
	 * Like {@link #next()}, but if a checked exception is thrown during the
	 * walk it is rethrown as a {@link RevWalkException}.
	 *
	 * @throws RevWalkException
	 *             if an {@link IOException} was thrown.
	 * @return next most recent commit; null if traversal is over.
	 */
	@Nullable
	private RevCommit nextForIterator() {
		try {
			return next();
		} catch (IOException e) {
			throw new RevWalkException(e);
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Returns an Iterator over the commits of this walker.
	 * <p>
	 * The returned iterator is only useful for one walk. If this RevWalk gets
	 * reset a new iterator must be obtained to walk over the new results.
	 * <p>
	 * Applications must not use both the Iterator and the {@link #next()} API
	 * at the same time. Pick one API and use that for the entire walk.
	 * <p>
	 * If a checked exception is thrown during the walk (see {@link #next()}) it
	 * is rethrown from the Iterator as a {@link RevWalkException}.
	 *
	 * @see RevWalkException
	 */
	@Override
	public Iterator<RevCommit> iterator() {
		RevCommit first = nextForIterator();

		return new Iterator<>() {
			RevCommit next = first;

			@Override
			public boolean hasNext() {
				return next != null;
			}

			@Override
			public RevCommit next() {
				RevCommit r = next;
				next = nextForIterator();
				return r;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	/**
	 * Throws an exception if we have started producing output.
	 */
	protected void assertNotStarted() {
		if (isNotStarted())
			return;
		throw new IllegalStateException(
				JGitText.get().outputHasAlreadyBeenStarted);
	}

	/**
	 * Throws an exception if any commits have been marked as start.
	 * <p>
	 * If {@link #markStart(RevCommit)} has already been called,
	 * {@link #reset()} can be called to satisfy this condition.
	 *
	 * @since 5.5
	 */
	protected void assertNoCommitsMarkedStart() {
		if (roots.isEmpty())
			return;
		throw new IllegalStateException(
				JGitText.get().commitsHaveAlreadyBeenMarkedAsStart);
	}

	private boolean isNotStarted() {
		return pending instanceof StartGenerator;
	}

	/**
	 * Create and return an {@link org.eclipse.jgit.revwalk.ObjectWalk} using
	 * the same objects.
	 * <p>
	 * Prior to using this method, the caller must reset this RevWalk to clean
	 * any flags that were used during the last traversal.
	 * <p>
	 * The returned ObjectWalk uses the same ObjectReader, internal object pool,
	 * and free RevFlags. Once the ObjectWalk is created, this RevWalk should
	 * not be used anymore.
	 *
	 * @return a new walk, using the exact same object pool.
	 */
	public ObjectWalk toObjectWalkWithSameObjects() {
		ObjectWalk ow = new ObjectWalk(reader);
		RevWalk rw = ow;
		rw.objects = objects;
		rw.freeFlags = freeFlags;
		return ow;
	}

	/**
	 * Construct a new unparsed commit for the given object.
	 *
	 * @param id
	 *            the object this walker requires a commit reference for.
	 * @return a new unparsed reference for the object.
	 */
	protected RevCommit createCommit(AnyObjectId id) {
		return createCommit(id, commitGraph().findGraphPosition(id));
	}

	private RevCommit createCommit(AnyObjectId id, int graphPos) {
		if (graphPos >= 0) {
			return new RevCommitCG(id, graphPos);
		}
		return new RevCommit(id);
	}

	void carryFlagsImpl(RevCommit c) {
		final int carry = c.flags & carryFlags;
		if (carry != 0)
			RevCommit.carryFlags(c, carry);
	}

	/**
	 * Assume additional commits are shallow (have no parents).
	 * <p>
	 * This method is a No-op if the collection is empty.
	 *
	 * @param ids
	 *            commits that should be treated as shallow commits, in addition
	 *            to any commits already known to be shallow by the repository.
	 * @since 3.3
	 */
	public void assumeShallow(Collection<? extends ObjectId> ids) {
		for (ObjectId id : ids)
			lookupCommit(id).parents = RevCommit.NO_PARENTS;
	}

	/**
	 * Reads the "shallow" file and applies it by setting the parents of shallow
	 * commits to an empty array.
	 * <p>
	 * There is a sequencing problem if the first commit being parsed is a
	 * shallow commit, since {@link RevCommit#parseCanonical(RevWalk, byte[])}
	 * calls this method before its callers add the new commit to the
	 * {@link RevWalk#objects} map. That means a call from this method to
	 * {@link #lookupCommit(AnyObjectId)} fails to find that commit and creates
	 * a new one, which is promptly discarded.
	 * <p>
	 * To avoid that, {@link RevCommit#parseCanonical(RevWalk, byte[])} passes
	 * its commit to this method, so that this method can apply the shallow
	 * state to it directly and avoid creating the duplicate commit object.
	 *
	 * @param rc
	 *            the initial commit being parsed
	 * @throws IOException
	 *             if the shallow commits file can't be read
	 */
	void initializeShallowCommits(RevCommit rc) throws IOException {
		if (shallowCommitsInitialized) {
			throw new IllegalStateException(
					JGitText.get().shallowCommitsAlreadyInitialized);
		}

		shallowCommitsInitialized = true;

		if (reader == null) {
			return;
		}

		for (ObjectId id : reader.getShallowCommits()) {
			if (id.equals(rc.getId())) {
				rc.parents = RevCommit.NO_PARENTS;
			} else {
				lookupCommit(id).parents = RevCommit.NO_PARENTS;
			}
		}
	}
}

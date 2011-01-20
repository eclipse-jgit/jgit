/*
 * Copyright (C) 2011, Garmin International
 * Copyright (C) 2011, Jesse Greenwald <jesse.greenwald@gmail.com>
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

package org.eclipse.jgit.subtree;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * This class provides functions and operations for dealing with repositories
 * that use sub-trees.
 * <p>
 * This class provides
 * <ul>
 * <li>
 * Utility functions for find sub-trees that have been merged in to a commit or
 * somewhere in that commit's history.
 * <li>
 * Splitting out changes to sub-trees into their own commits that live in the
 * sub-tree's ancestory.
 * <li>
 * After splitting out sub-tree commits, rewriting main-line commits to bring
 * the sub-tree commits.
 * </ul>
 */
public class SubtreeSplitter {

	static final FooterKey SUBTREE_FOOTER_KEY = new FooterKey(
			"Sub-Tree");

	private static final String SUBTREE_CONFIG = ".gitsubtree";

	/**
	 * Id of .gitconfig subtree sections
	 */
	public final static String SUBTREE_SECTION = "subtree";

	/**
	 * Id of the .gitconfig path config value.
	 */
	public static String SUBTREE_PATH_PROP = "path";

	/**
	 * Id of the .gitconfig url config value.
	 */
	public static final String SUBTREE_URL_PROP = "url";

	/**
	 * Marker object to indicate that no subtree was available for a particular
	 * subtree id.
	 */
	public static final RevCommit NO_SUBTREE = new RevCommit(ObjectId.zeroId()) {
		public String toString() {
			return "NO_SUBTREE";
		}
	};

	/**
	 * If used for the toRewrite on splitSubtrees, all mainline commits will be
	 * rewritten.
	 */
	public static final Set<ObjectId> REWRITE_ALL = Collections
			.unmodifiableSet(new HashSet<ObjectId>());

	private Repository db;

	private RevWalk revWalk;

	private List<SubtreeContext> subtreeContexts;

	private Map<ObjectId, RevCommit> rewrittenCommits;

	/**
	 * @param db
	 * @param revWalk
	 */
	public SubtreeSplitter(Repository db, RevWalk revWalk) {
		this.db = db;
		if (revWalk == null) {
			revWalk = new RevWalk(db);
		}
		this.revWalk = revWalk;
	}

	/**
	 * Split out sub-trees and rewrite specified commits to use the new splits.
	 *
	 * @param start
	 *            The commit to start the walk at.
	 * @param pathContexts
	 *            Path based subtrees to split out. May be <code>null</code>.
	 * @param toRewrite
	 *            A list of commits that may be rewritten.
	 * @throws IOException
	 */
	public void splitSubtrees(ObjectId start,
			List<PathBasedContext> pathContexts, Set<ObjectId> toRewrite)
			throws IOException {

		Config conf = loadSubtreeConfig(db, start);
		subtreeContexts = new ArrayList<SubtreeContext>();
		for (String name : conf.getSubsections(SUBTREE_SECTION)) {
			subtreeContexts.add(new NameBasedSubtreeContext(name));
		}
		if (pathContexts != null) {
			subtreeContexts.addAll(pathContexts);
		}

		RevFilter oldFilter = revWalk.getRevFilter();
		ObjectInserter inserter = null;
		SubtreeRevFilter filter = null;
		try {
			inserter = db.newObjectInserter();
			filter = new SubtreeRevFilter(db);
			ArrayList<RevCommit> mainlineList = splitSubtrees(inserter, start,
					filter);
			if (toRewrite != null) {
				rewrittenCommits = rewriteMainlineCommits(inserter,
						mainlineList, toRewrite, filter);
			} else {
				rewrittenCommits = null;
			}
		} finally {
			if (filter != null) {
				try {
					filter.flushCache(revWalk);
				} catch (Exception e) {
					// oh well..
				}
				filter.release();
			}
			revWalk.reset();
			revWalk.setRevFilter(oldFilter);
			if (inserter != null) {
				inserter.release();
			}
		}
	}

	ArrayList<RevCommit> splitSubtrees(ObjectInserter inserter,
			ObjectId startCommit, SubtreeRevFilter filter)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {

		// Set up the walker
		revWalk.reset();
		revWalk.markStart(revWalk.parseCommit(startCommit));
		filter.setSplitters(subtreeContexts);
		revWalk.setRevFilter(filter);
		revWalk.sort(RevSort.TOPO);
		revWalk.sort(RevSort.REVERSE, true);

		ArrayList<RevCommit> mainlineList = new ArrayList<RevCommit>();

		ObjectReader or = revWalk.getObjectReader();

		RevCommit curCommit = null;
		while ((curCommit = revWalk.next()) != null) {

			mainlineList.add(curCommit);
			RevCommit[] parents = curCommit.getParents();
			Config conf = loadSubtreeConfig(db, curCommit);
			RevTree curTree = curCommit.getTree();

			for (SubtreeContext context : subtreeContexts) {

				if (context.getSplitCommit(curCommit) != null) {
					// Technically this may be possible if someone has merged in
					// a commit as both a main line commit and a subtree. For
					// now this is not allowed.
					throw new IOException("Tree walked out of order");
				}

				// Find the path that the subtree should be at.
				String path = context.getPath(conf);
				if (path == null) {
					// There is no subtree spec for this splitter on this
					// commit. This can happen when there is no entry for the
					// named subtree in the config file.
					context.setSplitCommit(curCommit, NO_SUBTREE);
					continue;
				}

				// Find the tree object at the path
				ObjectId tree = TreeWalk.findObject(curTree, path, or);
				if (tree == null) {
					// This commit doesn't have the subtree
					// TODO: should this be an error case? The subtree is
					// specified in the config file and there is nothing at the
					// specified path.
					context.setSplitCommit(curCommit, NO_SUBTREE);
					continue;
				}

				CommitBuilder cb = new CommitBuilder();

				HashSet<RevCommit> commitParents = new HashSet<RevCommit>();
				RevCommit identicalParent = null;
				for (ObjectId parent : parents) {

					// Get the mapped subtree commit for the parent.
					RevCommit newParent = context.getSplitCommit(parent);
					if (newParent == NO_SUBTREE) {
						continue;
					}

					// If this tree object matches a parent, then just use the
					// parent. TODO: technically, we should only use the parent
					// commit if the tree matches *AND* there are no other
					// commit objects. However, this can create a bunch of
					// commits with no changes and shouldn't really happen too
					// often.
					if (newParent.getTree().equals(tree)) {
						identicalParent = newParent;
					}

					if (!commitParents.contains(newParent)) {
						commitParents.add(newParent);
						cb.addParentId(newParent);
					}

				}

				RevCommit newRev = null;
				if (identicalParent != null) {
					// There was an identical parent, so just use it.
					newRev = identicalParent;
				} else {
					// Create a new commit for the split subtree.
					cb.setAuthor(curCommit.getAuthorIdent());
					cb.setCommitter(curCommit.getCommitterIdent());
					cb.setEncoding(curCommit.getEncoding());
					cb.setTreeId(tree);
					String msg = curCommit.getFullMessage();
					cb.setMessage(msg);
					newRev = revWalk.parseCommit(inserter.insert(cb));
					// Store off the tree object of the new split
				}
				context.setSplitCommit(curCommit, newRev);

			}

		}

		return mainlineList;
	}

	Map<ObjectId, RevCommit> rewriteMainlineCommits(ObjectInserter inserter,
			List<RevCommit> mainlineList, Set<ObjectId> toRewrite,
			SubtreeRevFilter filter) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {

		// Keep track of the mappings between mainline commits and their
		// rewritten commits.
		Map<ObjectId, RevCommit> mainlineMap = new HashMap<ObjectId, RevCommit>();

		for (RevCommit curCommit : mainlineList) {
			// Figure out if this parent should be "rewritten"
			boolean rewriteCommit = false;
			if (toRewrite == REWRITE_ALL || toRewrite.contains(curCommit)) {
				rewriteCommit = true;
			} else {
				for (RevCommit parent : curCommit.getParents()) {
					if (toRewrite.contains(parent)) {
						rewriteCommit = true;
					}
				}
			}

			if (rewriteCommit) {
				ObjectId newCommitId = rewriteMainlineCommit(inserter,
						mainlineMap, curCommit, filter);
				RevCommit newCommit = revWalk.parseCommit(newCommitId);
				mainlineMap.put(curCommit, newCommit);
			}
		}

		return mainlineMap;

	}

	/**
	 * Rewrite a commit to use new sub-tree splits.
	 *
	 * @param inserter
	 *            The inserter to reuse.
	 * @param mainlineMap
	 *            Map of commits that have been rewritten to their rewritten
	 *            commits.
	 * @param commitId
	 *            The id of the commit to rewrite.
	 * @param filter
	 *            The subtree filter to reuse.
	 * @return The id of the rewritten commit.
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws IOException
	 */
	ObjectId rewriteMainlineCommit(ObjectInserter inserter, Map<ObjectId, RevCommit> mainlineMap,
			ObjectId commitId, SubtreeRevFilter filter)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {

		RevCommit commit = revWalk.parseCommit(commitId);

		CommitBuilder cb = new CommitBuilder();

		// Use author, commiter, and encoding as is from existing commit.
		cb.setAuthor(commit.getAuthorIdent());
		cb.setCommitter(commit.getCommitterIdent());
		cb.setEncoding(commit.getEncoding());

		List<RevCommit> subtreeParents = new ArrayList<RevCommit>();

		// Look for valid subtree parent commits.
		for (SubtreeContext context : subtreeContexts) {

			ObjectId subtreeParentCandidate = context.getSplitCommit(commit);

			if (subtreeParentCandidate == null
					|| subtreeParentCandidate == NO_SUBTREE) {
				continue;
			}

			// Already listed as a parent
			if (subtreeParents.contains(subtreeParentCandidate)) {
				continue;
			}

			RevCommit subtreeParentCandidateRc = revWalk
					.parseCommit(subtreeParentCandidate);

			// See if this subtree parent is already reachable.
			// NOTE: we need to use the rewritten mainline commits here, so
			// iterate
			// through each parent and use a rewritten version if available.
			boolean reachable = false;
			for (RevCommit parent : commit.getParents()) {
				RevCommit mappedParent = mainlineMap.get(parent);
				RevCommit commitToTest = mappedParent != null ? mappedParent
						: parent;
				if (isSubtreeMergedInto(subtreeParentCandidateRc,
						commitToTest, filter)) {
					reachable = true;
					break;
				}
			}

			if (reachable) {
				continue;
			}

			// This is a valid new parent
			subtreeParents.add(subtreeParentCandidateRc);
		}

		List<RevCommit> newParents = new ArrayList<RevCommit>();

		// Add main-line parents back in.
		for (RevCommit parentCandidate : commit.getParents()) {

			if (newParents.contains(parentCandidate)) {
				continue;
			}

			boolean parentAlreadyReachable = false;
			for (RevCommit newParent : subtreeParents) {
				for (RevCommit newParentParent : newParent.getParents()) {
					if (newParentParent.equals(parentCandidate)) {
						parentAlreadyReachable = true;
						break;
					}
				}
				if (parentAlreadyReachable) {
					break;
				}
			}
			if (parentAlreadyReachable) {
				continue;
			}

			RevCommit mappedParent = mainlineMap.get(parentCandidate);
			if (mappedParent != null) {
				cb.addParentId(mappedParent);
			} else {
				cb.addParentId(parentCandidate);
			}
		}

		// Added subtree parents after mainline parents to try and preserve
		// parent ordering. The initial parent is really the important one.
		for (RevCommit subtreeParent : subtreeParents) {
			// TODO: filter to make sure it's not already added?
			cb.addParentId(subtreeParent);
		}

		// Update the subtree config
		cb.setTreeId(updateSubtreeConfig(inserter, commit));

		cb.setMessage(commit.getFullMessage());

		return inserter.insert(cb);

	}

	/**
	 * This is similar to RevWalk.isMergedInto, but the walker doesn't go past
	 * sub tree parents.
	 *
	 * @param base
	 *            commit the caller thinks is reachable from <code>tip</code>.
	 * @param tip
	 *            commit to start iteration from, and which is most likely a
	 *            descendant (child) of <code>base</code>.
	 * @param filter
	 *            The subtree filter to reuse.
	 * @return if there is a path directly from <code>tip</code> to
	 *         <code>base</code> (and thus <code>base</code> is fully merged
	 *         into <code>tip</code>); false otherwise.
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws IOException
	 */
	boolean isSubtreeMergedInto(RevCommit base, RevCommit tip,
			SubtreeRevFilter filter) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {

		revWalk.reset();
		RevFilter oldFilter = revWalk.getRevFilter();
		filter.reset();
		filter.setIncludeBoundarySubtrees(true);
		revWalk.setRevFilter(filter);
		revWalk.sort(RevSort.TOPO);
		revWalk.markStart(tip);

		try {
			for (RevCommit c = revWalk.next(); c != null; c = revWalk.next()) {
				if (base.equals(c)) {
					return true;
				}
			}
			return false;
		} finally {
			revWalk.reset();
			revWalk.setRevFilter(oldFilter);
		}
	}

	/**
	 * Update a .gitsubtree config file to match the current state of the split
	 * contexts.
	 *
	 * @param inserter
	 *            ObjectInserter to reuse.
	 * @param commit
	 *            The commit to load the tree and config file from.
	 * @return An updated Tree that can be used to rewrite a commit with.
	 * @throws IOException
	 */
	ObjectId updateSubtreeConfig(ObjectInserter inserter, RevCommit commit)
			throws IOException {

		// load existing config
		Config config = loadSubtreeConfig(db, commit);

		// update config for each split context
		boolean madeChange = false;
		for (SubtreeContext context : subtreeContexts) {
			String path = context.getPath(config);
			String existingPath = config.getString(SUBTREE_SECTION,
					context.getId(), SUBTREE_PATH_PROP);

			if (path == null || path.trim().isEmpty()) {
				if (existingPath != null && !existingPath.trim().isEmpty()) {
					madeChange = true;
					config.unset(SUBTREE_SECTION, context.getId(),
							SUBTREE_PATH_PROP);
				}
			} else {
				if (!path.equals(existingPath)) {
					madeChange = true;
					config.setString(SUBTREE_SECTION, context.getId(),
							SUBTREE_PATH_PROP, path);
				}
			}

		}

		if (!madeChange) {
			return commit.getTree();
		}

		// create the config blob
		final ObjectId configId = inserter.insert(Constants.OBJ_BLOB,
				Constants.encode(config.toText()));

		// Load in the existing tree
		DirCache dirCache = DirCache.newInCore();
		DirCacheBuilder builder = dirCache.builder();
		builder.addTree(new byte[0], 0, revWalk.getObjectReader(),
				commit.getTree());
		builder.finish();

		// Add the updated .gitsubtree file
		DirCacheEditor editor = dirCache.editor();
		editor.add(new PathEdit(SUBTREE_CONFIG) {
			@Override
			public void apply(DirCacheEntry ent) {
				ent.setObjectId(configId);
				ent.setFileMode(FileMode.REGULAR_FILE);
			}
		});
		editor.finish();

		// Done modifying the tree
		return editor.getDirCache().writeTree(inserter);
	}

	/**
	 * Parse the .gitsubtree config file for a commit.
	 * ".gitsubtree files are in this format":
	 *
	 * <pre>
	 * {@code
	 * [subtree "<subtree-id>"]
	 *     path = <path-in-tree>
	 *     url = <upstream-url>
	 * }
	 * </pre>
	 *
	 * For example:
	 *
	 * <pre>
	 * {@code
	 * [subtree "gerrit"]
	 *     path = gerrit
	 * }
	 * </pre>
	 *
	 * @param db
	 *            The repository to load data from.
	 * @param commit
	 *            The commit containing the desired subtree config.
	 * @return The parsed config.
	 * @throws IOException
	 *
	 */
	static Config loadSubtreeConfig(Repository db, ObjectId commit)
			throws IOException {
		try {
			return new BlobBasedConfig(null, db, commit, SUBTREE_CONFIG);
		} catch (FileNotFoundException e) {
			// Couldn't find the file, so no config
		} catch (IOException e) {
			throw new IOException("Unable to load " + SUBTREE_CONFIG
					+ " config file for commit " + commit.name());
		} catch (ConfigInvalidException e) {
			// TODO: throw stronger typed message?
			throw new IOException("Invalid " + SUBTREE_CONFIG
					+ " config file for commit " + commit.name());
		}
		return new Config();
	}

	/**
	 * @return The resulting subtree contexts from the split operation.
	 */
	public List<SubtreeContext> getSubtreeContexts() {
		return subtreeContexts;
	}

	/**
	 * @return A map of commits that were rewritten to the commits they were
	 *         rewritten to.
	 */
	public Map<ObjectId, RevCommit> getRewrittenCommits() {
		return rewrittenCommits;
	}

}

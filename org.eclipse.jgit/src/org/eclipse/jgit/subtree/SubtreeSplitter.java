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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
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
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
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

	private Map<String, SubtreeContext> subtreeContexts;

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
			List<PathBasedContext> pathContexts,
			Set<? extends ObjectId> toRewrite)
			throws IOException {

		subtreeContexts = new HashMap<String, SubtreeContext>();
		for (PathBasedContext pbc : pathContexts) {
			subtreeContexts.put(pbc.getId(), pbc);
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

		ArrayList<RevCommit> superList = new ArrayList<RevCommit>();

		ObjectReader or = revWalk.getObjectReader();

		for (RevCommit superCommit : revWalk) {

			superList.add(superCommit);
			RevCommit[] superParents = superCommit.getParents();
			RevTree superTree = superCommit.getTree();
			Config conf = loadSubtreeConfig(db, superCommit);
			Map<String, RevCommit> allSubtreeParents = filter
					.getSubtreeAnalyzer().getSubtreeParents(superCommit,
							revWalk);

			for (SubtreeContext context : subtreeContexts.values()) {

				if (context.getSplitCommit(superCommit) != null) {
					// Technically this may be possible if someone has merged in
					// a commit as both a main line commit and a subtree. For
					// now this is not allowed.
					throw new IOException("Tree walked out of order");
				}

				// Find the path that the subtree should be at.
				String subtreePath = context.getPath(conf);
				if (subtreePath == null) {
					// There is no subtree spec for this splitter on this
					// commit. This can happen when there is no entry for the
					// named subtree in the config file.
					context.setSplitCommit(superCommit, NO_SUBTREE);
					continue;
				}

				// Find the tree object at the path
				TreeWalk treeWalk = TreeWalk
						.forPath(or, subtreePath, superTree);
				if (treeWalk == null) {
					// This commit doesn't have the subtree
					// TODO: should this be an error case? The subtree is
					// specified in the config file and there is nothing at the
					// specified path.
					context.setSplitCommit(superCommit, NO_SUBTREE);
					continue;
				}
				ObjectId tree = treeWalk.getObjectId(0);

				// If the tree matches the merged in subtree, just "reset" to
				// the parent commit.
				RevCommit contextParent = allSubtreeParents
						.get(context.getId());
				if (contextParent != null
						&& tree.equals(contextParent.getTree())) {
					context.setSplitCommit(superCommit, contextParent);
					continue;
				}

				// Find the corresponding subtree state for each of the parent
				// commits.
				ArrayList<RevCommit> subParents = new ArrayList<RevCommit>();
				for (ObjectId parent : superParents) {
					// Get the mapped subtree commit for the parent.
					RevCommit newParent = context.getSplitCommit(parent);
					if (newParent != NO_SUBTREE && newParent != null
							&& !subParents.contains(newParent)) {
						subParents.add(newParent);
					}
				}

				// If there are multiple parents, try to exclude parents that
				// are reachable from other parents. This is to remove pointless
				// "spurs" from the history.
				if (subParents.size() > 1) {
					RevWalk rw2 = new RevWalk(revWalk.getObjectReader());
					rw2.sort(RevSort.TOPO);
					RevFlag REACHABLE = rw2.newFlag("REACHABLE");
					for (RevCommit parent : subParents) {
						for (RevCommit parentParent : rw2.parseCommit(parent)
								.getParents()) {
							rw2.markStart(parentParent);
							parentParent.add(REACHABLE);
						}
					}
					rw2.carry(REACHABLE);
					while (rw2.next() != null) {
						// Just make sure to walk everything.
					}
					ListIterator<RevCommit> i = subParents.listIterator();
					while (i.hasNext()) {
						if (rw2.parseCommit(i.next()).has(REACHABLE)) {
							i.remove();
						}
					}
				}

				// If at this point there is a single subtree parent left and
				// its tree matches, just use it.
				if (subParents.size() == 1) {
					RevCommit singleParent = subParents.iterator().next();
					if (singleParent.getTree().equals(tree)) {
						context.setSplitCommit(superCommit,
								revWalk.parseCommit(singleParent));
						continue;
					}
				}

				// Create a new commit for the split subtree.
				CommitBuilder cb = new CommitBuilder();
				for (RevCommit parent : subParents) {
					cb.addParentId(parent);
				}
				cb.setAuthor(superCommit.getAuthorIdent());
				cb.setCommitter(superCommit.getCommitterIdent());
				cb.setEncoding(superCommit.getEncoding());
				cb.setTreeId(tree);
				String msg = superCommit.getFullMessage();
				cb.setMessage(msg);
				context.setSplitCommit(superCommit,
						revWalk.parseCommit(inserter.insert(cb)));

			}

		}

		return superList;
	}

	Map<ObjectId, RevCommit> rewriteMainlineCommits(ObjectInserter inserter,
			List<RevCommit> mainlineList, Set<? extends ObjectId> toRewrite,
			SubtreeRevFilter filter)
			throws MissingObjectException,
			IncorrectObjectTypeException, IOException {

		// Keep track of the mappings between mainline commits and their
		// rewritten commits.
		Map<ObjectId, RevCommit> mainlineMap = new HashMap<ObjectId, RevCommit>();
		SubtreeAnalyzer sa = filter.getSubtreeAnalyzer();

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
						mainlineMap, curCommit, sa);
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
	 * @param sa
	 * @return The id of the rewritten commit.
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws IOException
	 */
	ObjectId rewriteMainlineCommit(ObjectInserter inserter,
			Map<ObjectId, RevCommit> mainlineMap, ObjectId commitId,
			SubtreeAnalyzer sa)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {

		RevCommit commit = revWalk.parseCommit(commitId);
		CommitBuilder cb = new CommitBuilder();

		// Add in any non-subtree parents.
		Map<String, RevCommit> subtreeParents = sa.getSubtreeParents(commit,
				revWalk);
		for (RevCommit parent : commit.getParents()) {
			if (!subtreeParents.containsValue(parent)) {
				cb.addParentId(mainlineMap.containsKey(parent) ? mainlineMap
						.get(parent) : parent);
			}
		}

		// Go through each subtree context looking for new subtree commits. Only
		// include "new" subtree commits as parents to this rewritten commit.
		for (SubtreeContext sc : subtreeContexts.values()) {

			RevCommit subtreeParentCandidate = sc.getSplitCommit(commit);
			if (subtreeParentCandidate == null
					|| subtreeParentCandidate == NO_SUBTREE) {
				// No subtree for this context...
				continue;
			}

			boolean match = false;
			for (RevCommit parent : commit.getParents()) {
				if (subtreeParents.containsValue(parent)) {
					// The subtree analyzer has identified this parent as being
					// a subtree. So, go ahead and add it as a parent to the
					// rewritten commit.
					continue;
				}
				if (subtreeParentCandidate.equals(sc.getSplitCommit(parent))) {
					// The subtree candidate isn't new, so don't bother adding
					// it as a parent.
					match = true;
					break;
				}
			}
			if (!match) {
				cb.addParentId(subtreeParentCandidate);
			}
		}

		// Update the subtree config
		cb.setTreeId(updateSubtreeConfig(db, revWalk, subtreeContexts.values(),
				inserter, commit));

		// Try to determine if any changes were actually made. Compare the tree
		// and parent set.
		if (cb.getTreeId().equals(commit.getTree())) {
			HashSet<ObjectId> parents1 = new HashSet<ObjectId>();
			for (ObjectId parent1 : commit.getParents())
				parents1.add(parent1);
			HashSet<ObjectId> parents2 = new HashSet<ObjectId>();
			for (ObjectId parent2 : cb.getParentIds())
				parents2.add(parent2);
			if (parents1.equals(parents2)) {
				return commit;
			}
		}

		// Use author, commiter, message, etc as is from existing commit.
		cb.setAuthor(commit.getAuthorIdent());
		cb.setCommitter(commit.getCommitterIdent());
		cb.setEncoding(commit.getEncoding());
		cb.setMessage(commit.getFullMessage());
		return inserter.insert(cb);
	}

	/**
	 * Update a .gitsubtree config file to match the current state of the split
	 * contexts.
	 *
	 * @param db
	 * @param revWalk
	 * @param subtreeContexts
	 *
	 * @param inserter
	 *            ObjectInserter to reuse.
	 * @param treeish
	 *            The commit or tree to load the config from.
	 * @return An updated Tree that can be used to rewrite a commit with.
	 * @throws IOException
	 */
	static ObjectId updateSubtreeConfig(Repository db, RevWalk revWalk,
			Collection<SubtreeContext> subtreeContexts,
			ObjectInserter inserter, ObjectId treeish)
			throws IOException {


		RevObject obj = revWalk.parseAny(treeish);
		RevTree tree;
		if (obj instanceof RevTree) {
			tree = (RevTree) obj;
		} else if (obj instanceof RevCommit) {
			tree = ((RevCommit) obj).getTree();
		} else {
			throw new IOException("Can't handle type: " + obj);
		}

		// load existing config
		Config config = loadSubtreeConfig(db, tree);

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

		if (!madeChange)
			return tree;

		// create the config blob
		final ObjectId configId = inserter.insert(Constants.OBJ_BLOB,
				Constants.encode(config.toText()));

		// Load in the existing tree
		DirCache dirCache = DirCache.newInCore();
		DirCacheBuilder builder = dirCache.builder();
		builder.addTree(new byte[0], 0, revWalk.getObjectReader(), tree);
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
	 * @param treeish
	 *            the tree (or commit) that contains the subtree config.
	 * @return The parsed config.
	 * @throws IOException
	 *
	 */
	static Config loadSubtreeConfig(Repository db, ObjectId treeish)
			throws IOException {
		try {
			return new BlobBasedConfig(null, db, treeish, SUBTREE_CONFIG);
		} catch (FileNotFoundException e) {
			// Couldn't find the file, so no config
		} catch (IOException e) {
			throw new IOException("Unable to load " + SUBTREE_CONFIG
					+ " config file for commit " + treeish.name());
		} catch (ConfigInvalidException e) {
			// TODO: throw stronger typed message?
			throw new IOException("Invalid " + SUBTREE_CONFIG
					+ " config file for commit " + treeish.name());
		}
		return new Config();
	}

	/**
	 * @return The resulting subtree contexts from the split operation.
	 */
	public Map<String, SubtreeContext> getSubtreeContexts() {
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

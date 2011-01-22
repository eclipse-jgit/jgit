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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * Maps parents of commits to the subtrees they most likely belong to.
 */
class SubtreeAnalyzer {

	/**
	 * Return entries that match across trees.
	 *
	 * If the TreeWalk is recursive, however, entries that are trees may be
	 * returned when they do not match. This is because those tree entries need
	 * to be recursively walked to see if they contain any files that match.
	 */
	private static final class AllSameFilter extends TreeFilter {
		private static final int baseTree = 0;

		@Override
		public boolean include(TreeWalk walker) throws MissingObjectException,
				IncorrectObjectTypeException, IOException {
			final int n = walker.getTreeCount();
			if (n == 1)
				return true;

			boolean allSame = true;
			boolean allTrees = true;
			final int m = walker.getRawMode(baseTree);
			for (int i = 1; i < n; i++) {
				if (walker.getRawMode(i) != FileMode.TYPE_TREE) {
					allTrees = false;
				}

				if (walker.getRawMode(i) != m || !walker.idEqual(i, baseTree)) {
					allSame = false;
				}
			}

			return (walker.isRecursive() && allTrees) ? true : allSame;
		}

		@Override
		public boolean shouldBeRecursive() {
			return false;
		}

		@Override
		public TreeFilter clone() {
			return this;
		}

		@Override
		public String toString() {
			return "ALL_SAME";
		}

	}

	private static final String NOTES_REF = "refs/notes/subtrees";

	private static final TreeFilter ALL_SAME_TREE_FILTER = new AllSameFilter();

	private TreeWalk tw;

	private Repository repo;

	private Map<RevCommit, Map<String, RevCommit>> subtreeCache = new HashMap<RevCommit, Map<String, RevCommit>>();

	private NoteMap noteMap;

	private ObjectReader reader;

	private ObjectId notesCommitId;

	private ObjectInserter inserter;

	SubtreeAnalyzer(Repository db) {
		repo = db;
		reader = repo.newObjectReader();
		inserter = repo.newObjectInserter();
		tw = new TreeWalk(reader);
	}

	void release() {
		tw.release();
		reader.release();
		inserter.release();
	}

	/**
	 * Get the subtree parents of the given commit. This may load the data from
	 * a cache, perform the subtree analysis, etc.
	 *
	 * @param cmit
	 * @param walker
	 * @return A map of subtree ids to parent commits.
	 * @throws IOException
	 */
	Map<String, RevCommit> getSubtreeParents(RevCommit cmit,
			RevWalk walker) throws IOException {
		Map<String, RevCommit> subtreeParents = null;

		// See if we've already have a cached version in memory.
		subtreeParents = subtreeCache.get(cmit);

		// Still no parents? See if the note map has an entry.
		if (subtreeParents == null) {
			subtreeParents = parseNote(cmit, walker);
			// Still no parents? Try reloading the notemap to see if it's
			// changed in the background and now has an entry.
			if (subtreeParents == null) {
				if (loadNoteMap(walker)) {
					subtreeParents = parseNote(cmit, walker);
				}
			}
		}

		// Still no cache entry? Go ahead and do the subtree analysis.
		if (subtreeParents == null) {
			subtreeParents = analyzeSubtrees(cmit, walker);
		}

		subtreeCache.put(cmit, subtreeParents);

		return subtreeParents;
	}

	/**
	 * For each parent commit of a given commit, try to detect if the parent is
	 * part of the super project or belongs to a sub project. This is done by
	 * analyzing the similarity between the parent's Tree and each of the
	 * subtree's Trees within the given commit.
	 *
	 * @param cmit
	 *            The commit to perform the analysis on.
	 * @param walker
	 *            The RevWalk to reuse.
	 * @return A map of subtree ids to parent commits.
	 *
	 * @throws IOException
	 */
	private HashMap<String, RevCommit> analyzeSubtrees(RevCommit cmit,
			RevWalk walker) throws IOException {
		HashMap<String, RevCommit> subtreeParents = new HashMap<String, RevCommit>();
		Config cfg = SubtreeSplitter.loadSubtreeConfig(repo, cmit);
		if (cfg != null) {

			for (RevCommit parent : cmit.getParents()) {
				walker.parseBody(parent);

				// First, compare the root and use it as a baseline.
				int bestScore = scoreTrees(parent.getTree(), cmit.getTree());
				String bestId = null;

				// Next, go through each known subtree to see if it is a
				// better match.
				Set<String> subtreeIds = cfg
						.getSubsections(SubtreeSplitter.SUBTREE_SECTION);
				for (String curId : subtreeIds) {

					String curPath = cfg.getString(
							SubtreeSplitter.SUBTREE_SECTION, curId,
							SubtreeSplitter.SUBTREE_PATH_PROP);

					// If the path is not set properly in the config file.
					if (curPath == null) {
						continue;
					}

					// Find the subtree's Tree object.
					ObjectId subtree = TreeWalk.findObject(cmit.getTree(),
							curPath, walker.getObjectReader());

					// Does the subtree Tree exist?
					if (subtree == null) {
						continue;
					}

					// If this subtree's score is better than the previous best,
					// use this subtree as the match.
					int curScore = scoreTrees(subtree, parent.getTree());
					if (curScore > bestScore) {
						bestScore = curScore;
						bestId = curId;
					}
				}

				// Was this parent determined to be a subtree?
				if (bestId != null) {
					subtreeParents.put(bestId, parent);
				}
			}

		}
		return subtreeParents;
	}

	/**
	 * Simple test to see how many files match between the two trees. Later on,
	 * it may be necessary to do a something more complex. For example, test the
	 * mergability of the commits/trees.
	 *
	 * @param tree1
	 * @param tree2
	 * @return A relative score for the similarity of the provided trees. The
	 *         higher the number, the more similar the trees.
	 * @throws IOException
	 */
	private int scoreTrees(ObjectId tree1, ObjectId tree2) throws IOException {
		tw.reset();
		tw.setRecursive(true);
		tw.setFilter(ALL_SAME_TREE_FILTER);
		tw.addTree(tree1);
		tw.addTree(tree2);

		int score = 0;
		while (tw.next()) {
			if (tw.getFileMode(0) != FileMode.TREE) {
				// Because the walk may return trees that don't match.
				score++;
			}
		}
		return score;
	}

	/**
	 * Fetch the subtree footer note for a given commit and try to parse it.
	 *
	 * @param cmit
	 *            The commit to find the note for.
	 * @param walker
	 *            The walker to load data from.
	 * @return The results of parsing the note map. <code>null</code> if noteMap
	 *         is <code>null</code> or there is no note for the given commit.
	 * @throws MissingObjectException
	 * @throws IOException
	 */
	private HashMap<String, RevCommit> parseNote(RevCommit cmit, RevWalk walker)
			throws MissingObjectException, IOException {
		if (noteMap != null) {
			ObjectId note = noteMap.get(cmit);
			if (note != null) {
				RevObject object = walker.parseAny(note);
				if (object instanceof RevBlob) {
					RevBlob noteBlob = (RevBlob) object;
					ObjectLoader loader = reader.open(noteBlob.getId(),
							Constants.OBJ_BLOB);
					String footers = new String(loader.getBytes());

					String footerKey = SubtreeSplitter.SUBTREE_FOOTER_KEY
							.getName();
					int footerLen = footerKey.length();

					// Parse the note, line by line.
					StringReader sr = new StringReader(footers.trim());
					BufferedReader br = new BufferedReader(sr);
					ArrayList<String> lines = new ArrayList<String>();
					String line = null;
					while ((line = br.readLine()) != null) {

						// Does the line start with the subtree footer?
						if (line.startsWith(footerKey)) {

							// trim the "Subtree:" prefix.
							int idx = footerLen;
							while (line.charAt(idx) != ':') {
								idx++;
							}
							idx++;
							if (idx < line.length()) {
								lines.add(line.substring(idx).trim());
							}
						}
					}

					// Parse the footer lines.
					return parseSubtreeFooters(repo, cmit, lines);
				}
			}
		}

		return null;
	}

	/**
	 * Reload {@link #noteMap} from the db.
	 *
	 * @param walker
	 * @return <code>true</code> if {@link #noteMap} was updated,
	 *         <code>false</code> otherwise.
	 * @throws IOException
	 */
	private boolean loadNoteMap(RevWalk walker) throws IOException {
		ObjectId id = repo.resolve(NOTES_REF);
		if (id != null && !id.equals(notesCommitId)) {
			RevCommit noteCommit = walker.parseCommit(id);
			if (noteCommit != null) {
				notesCommitId = id;
				noteMap = NoteMap.read(reader, noteCommit);
				return true;
			}
		}
		return false;
	}

	/**
	 * Parse the Sub-Tree footers.
	 *
	 * The Sub-Tree footer lines should be in this format:
	 * <p>
	 * <code><pre>{@code
	 * Sub-Tree: <sha1> <subtree-id>
	 * }</pre></code>
	 * <p/>
	 * <p>
	 * The sha1 is the sha1 of the commit that owns the subtree. The subtree-id
	 * refers to an entry in the .gitsubtree file. For example:
	 * </p>
	 * <p>
	 * <code><pre>{@code
	 * Sub-Tree: 8dc6ca89e881048fc72d80ee214beab46a123675 gerrit
	 * }</pre></code>
	 * </p>
	 *
	 * @param db
	 *            The repository containing the data.
	 * @param commit
	 *            The commit whose commit message will be parsed.
	 * @param footerLines
	 *            The collection of footer that correspond to the commit.
	 * @return Map of subtree ids to commit object ids.
	 * @throws IOException
	 *
	 */
	private HashMap<String, RevCommit> parseSubtreeFooters(Repository db,
			RevCommit commit, Collection<String> footerLines)
			throws IOException {

		HashMap<String, RevCommit> result = new HashMap<String, RevCommit>();

		Config config = SubtreeSplitter.loadSubtreeConfig(db, commit);
		RevCommit[] parents = commit.getParents();

		for (String line : footerLines) {
			int len = line.length();
			int idx = 0;

			// skip whitespace
			while (idx < len && Character.isWhitespace(line.charAt(idx))) {
				idx++;
			}
			int shaStart = idx;
			while (idx < len && !Character.isWhitespace(line.charAt(idx))) {
				idx++;
			}

			// parse the SHA1
			ObjectId commitId = null;
			String sha1 = line.substring(shaStart, idx);
			try {
				commitId = ObjectId.fromString(sha1);
			} catch (IllegalArgumentException iae) {
				throw new SubtreeFooterException("Can't parse ObjectId: "
						+ sha1 + " in footer of " + commit.name());
			}

			// Make sure the sub tree is actually a parent of this commit
			RevCommit parentRevCommit = null;
			for (RevCommit parent : parents) {
				if (parent.equals(commitId)) {
					parentRevCommit = parent;
					break;
				}
			}
			if (parentRevCommit == null) {
				throw new SubtreeFooterException("Sub-Tree \""
						+ commitId.name() + "\" is not a parent of \""
						+ commit.name() + "\"");
			}

			// skip whitespace
			while (idx < len && Character.isWhitespace(line.charAt(idx))) {
				idx++;
			}

			String subtreeId = line.substring(idx).trim();
			if (subtreeId.isEmpty()) {
				throw new SubtreeFooterException("Sub-Tree id not specified \""
						+ line + "\" in footer of " + commit.name());
			}

			if (config == null
					|| !config.getSubsections("subtree").contains(subtreeId)) {
				throw new SubtreeFooterException("Sub-Tree \"" + subtreeId
						+ "\" does not exist in .gitsubtree file for commit "
						+ commit.name());
			}

			result.put(subtreeId, parentRevCommit);
		}

		return result;
	}

	/**
	 * Update {@link #noteMap} to include contents of {@link #subtreeCache},
	 * commit the contents of {@link #noteMap}, and update the subtree notes
	 * ref.
	 *
	 * @param walker
	 *            Used to reload the {@link #noteMap} if necessary.
	 * @return The commit id of the written note (possibly the commit id of the
	 *         old note map if no changes were made).
	 * @throws IOException
	 */
	ObjectId flushCache(RevWalk walker) throws IOException {

		for (int retryCount = 0; retryCount < 3; retryCount++) {

			loadNoteMap(walker);
			boolean madeUpdate = false;
			for (RevCommit cmit : subtreeCache.keySet()) {
				Map<String, RevCommit> subtrees = subtreeCache.get(cmit);
				if (updateNoteMap(cmit, subtrees)) {
					madeUpdate = true;
				}
			}

			if (!madeUpdate) {
				return notesCommitId;
			}

			CommitBuilder cb = new CommitBuilder();
			cb.setTreeId(noteMap.writeTree(inserter));
			cb.setAuthor(new PersonIdent("Subtree Splitter", "dev@null.org"));
			cb.setCommitter(new PersonIdent("Subtree Splitter", "dev@null.org"));
			cb.setMessage("subtree cache");
			ObjectId commitId = inserter.insert(cb);

			RefUpdate update = repo.updateRef(NOTES_REF);
			update.setForceUpdate(true);
			update.setExpectedOldObjectId(notesCommitId);
			update.setNewObjectId(commitId);

			switch (update.update()) {
			case FORCED:
				return commitId;
			default:
				// try again.
				break;
			}

		}
		return null;

	}

	/**
	 * Update {@link #noteMap} to include a note for the given commit and
	 * subtree parents data.
	 *
	 * @param cmit
	 *            The commit the note will point to.
	 * @param subtreeParents
	 *            The subtree parent data to put in the note.
	 * @return <code>true</code> if {@link #noteMap} was updated.
	 *         <code>false</code> if {@link #noteMap} already as an entry for
	 *         the given commit.
	 * @throws IOException
	 */
	private boolean updateNoteMap(RevCommit cmit,
			Map<String, RevCommit> subtreeParents) throws IOException {
		if (noteMap == null) {
			noteMap = NoteMap.newEmptyMap();
		}

		if (!noteMap.contains(cmit)) {
			StringBuilder footers = new StringBuilder();
			String footerKey = SubtreeSplitter.SUBTREE_FOOTER_KEY.getName();
			for (String subtreeId : subtreeParents.keySet()) {
				String sha1 = subtreeParents.get(subtreeId).name();
				footers.append(footerKey).append(": ").append(sha1).append(" ")
						.append(subtreeId).append('\n');
			}

			noteMap.set(cmit, footers.toString(), inserter);

			return true;
		} else {
			return false;
		}
	}

}

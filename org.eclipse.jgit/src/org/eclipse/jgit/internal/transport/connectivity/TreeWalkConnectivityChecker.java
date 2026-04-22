/*
 * Copyright (C) 2026, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.connectivity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdSubclassMap;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.ObjectReachabilityChecker;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.ReachabilityChecker;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ConnectivityChecker;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * A connectivity checker that avoids the object allocations that occur when
 * doing standard graph coloring via {@code ObjectWalk}.
 *
 * {@code TreeWalkConnectivityChecker} requires the {@code PackParser} to report
 * new objects in the pack, and will fail immediately if that is not configured.
 *
 * The {@code ObjectWalk}-based algorithm marks objects interesting and
 * uninteresting and parses new subtrees and blobs to propagate those states.
 * Each propagation of state to a child node requires a Java object allocation.
 * This algorithm is closer to the diff algorithm. It parses tree objects at
 * common paths and compares hash values in those trees. Objects only need to be
 * created for subtrees that differ.
 *
 * The tree connectivity part of this algorithm always creates O(commits + tree
 * objects in the new pack) Java objects, independent of where the parent commit
 * is in the graph, and independent of the number of references. (Caveat, it
 * does a standard reachability check if a parent commit is not in advertised
 * "haves", and a standard object reachability check for any base objects
 * referenced in a thin pack.) The {@code ObjectWalk}-based algorithm is
 * equivalent to this algorithm when the parents of new commits are all in the
 * advertised "haves", creating O(commit + tree objects in the new pack) Java
 * objects. It is much less efficient otherwise, creating either O(tree objects
 * in checkout) or O(all objects in checkout) Java objects.
 *
 * This algorithm first validates that the commits in the commands (new branch
 * tips) are connected. Starting with those commits, it walks back until a
 * commit not in the pack is found, or until a commit with no parents is found.
 * If a parent commit id is not in the database, connectivity fails. If the
 * parent commit is in the database and was present in the "haves" advertised
 * for the client, it moves on to verifying tree connectivity. Otherwise, it
 * performs a reachability check to make sure the client has access to the
 * unadvertised parent commit, and proceeds to verifying tree connectivity if
 * that succeeds.
 *
 * Tree connectivity is verified for every commit in the receive pack that was
 * visited when checking commit connectivity. For each commit, get its root tree
 * and the root trees of its parents. For each path segment in the child
 * commit's root tree, do the following (includes recursing into differing
 * subtrees, where the same actions are applied):
 * <ul>
 * <li>if a blob or subtree's id is identical to one of the parent's blob or
 * subtree's ids, continue/li>
 * <li>if a new blob id is not present in the database, connectivity fails</li>
 * <li>if a new subtree id is present in the pack, traverse into the subtree to
 * continue the check, performing the same actions in this list</li>
 * <li>if the new subtree id is not in the pack but is present in the database,
 * continue/li>
 * <li>if the new subtree id is not in either the pack or the database,
 * connectivity fails</li>
 * </ul>
 */
public class TreeWalkConnectivityChecker implements ConnectivityChecker {
	private ObjectIdSubclassMap<ObjectId> objectsInPack;

	private Set<ObjectId> advertisedHaves;

	@Override
	public void checkConnectivity(ConnectivityCheckInfo connectivityCheckInfo,
			Set<ObjectId> haves, ProgressMonitor pm) throws IOException {

		if (!connectivityCheckInfo.getParser().needNewObjectIds()) {
			throw new IllegalStateException(
					"PackParser.setNeedNewObjectIds(true) must be set"); //$NON-NLS-1$
		}

		Repository repo = connectivityCheckInfo.getRepository();
		RevWalk rw = connectivityCheckInfo.getWalk();
		this.objectsInPack = connectivityCheckInfo.getParser()
				.getNewObjectIds();
		this.advertisedHaves = haves;

		Set<RevCommit> nonAdvertisedParentCommitsOutsidePack = new HashSet<>();
		List<RevCommit> newCommitsToVerify = extractCommitsToVerify(rw, repo,
				connectivityCheckInfo.getCommands(),
				nonAdvertisedParentCommitsOutsidePack, pm);

		/*
		 * This class underreports the full set of objects parsed and does not
		 * advance the progress monitor in the checkReachability() and
		 * checkThinPackBases() methods. These methods invoke {@code
		 * ReachabilityChecker} and @{code ObjectReachabilityChecker}, neither
		 * of which take a progress monitor. Pushes are generally based on
		 * commits near branch tips, so the checkers normally do minimal walks.
		 * Setup for these checkers involves at a minimum marking all "haves" as
		 * starts, so we capture that in the progress monitor.
		 */
		if (!nonAdvertisedParentCommitsOutsidePack.isEmpty()) {
			pm.update(advertisedHaves.size());
			checkReachability(rw, repo, nonAdvertisedParentCommitsOutsidePack);
		}

		checkThinPackBases(rw, connectivityCheckInfo);

		verifyTreeConnectivity(rw, repo, newCommitsToVerify,
				connectivityCheckInfo.isCheckObjects(), pm);
	}

	/**
	 * Extracts commits that need to be verified and identifies parent commits
	 * outside the pack.
	 *
	 * @param rw
	 *            the RevWalk to use
	 * @param repo
	 *            the repository
	 * @param commands
	 *            the receive commands
	 * @param nonAdvertisedParentCommitsOutsidePack
	 *            set to populate with parent commits outside the pack and not
	 *            in the advertised set (haves)
	 * @param pm
	 *            progress monitor
	 * @return list of new commits in the pack that require verification
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	private List<RevCommit> extractCommitsToVerify(RevWalk rw, Repository repo,
			List<ReceiveCommand> commands,
			Set<RevCommit> nonAdvertisedParentCommitsOutsidePack,
			ProgressMonitor pm) throws IOException {

		List<RevCommit> newCommitsToVerify = new ArrayList<>();

		// Gather new commits from the receive commands
		for (ReceiveCommand cmd : commands) {
			if (cmd.getType() != ReceiveCommand.Type.DELETE
					&& !advertisedHaves.contains(cmd.getNewId())) {
				rw.markStart(rw.parseCommit(cmd.getNewId()));
			}
		}

		// Walk commits in the receive pack, recording both commits in the pack
		// and parent commits outside the pack
		RevCommit commit;
		while ((commit = rw.next()) != null) {
			pm.update(1);
			if (objectsInPack.contains(commit)) {
				newCommitsToVerify.add(commit);
			} else {
				// Commit not in pack, check object database
				if (!repo.getObjectDatabase().has(commit)) {
					throw new MissingObjectException(commit.getId(),
							Constants.TYPE_COMMIT);
				}
				// Advertised parents are reachable, only track non-advertised
				if (!advertisedHaves.contains(commit.getId())) {
					nonAdvertisedParentCommitsOutsidePack.add(commit);
				}
				rw.markUninteresting(commit);
			}
		}

		return newCommitsToVerify;
	}

	/**
	 * Performs reachability checks for parent commits outside the pack.
	 *
	 * @param rw
	 *            the RevWalk to use
	 * @param repo
	 *            the repository
	 * @param nonAdvertisedParentCommitsOutsidePack
	 *            parent commits to check visibility for
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	private void checkReachability(RevWalk rw, Repository repo,
			Set<RevCommit> nonAdvertisedParentCommitsOutsidePack)
			throws IOException {

		try {
			// First try with the smaller set of advertised haves
			ReachabilityChecker checker = rw.getObjectReader()
					.createReachabilityChecker(rw);
			Stream<RevCommit> starterCommits = advertisedHaves.stream()
					.map(id -> parseCommitOrNull(rw, id))
					.filter(Objects::nonNull);

			Optional<RevCommit> unreachable = checker.areAllReachable(
					nonAdvertisedParentCommitsOutsidePack, starterCommits);

			if (unreachable.isPresent()) {
				// Fallback to check against full ref database
				Stream<RevCommit> allRefCommits = repo.getRefDatabase()
						.getRefs().stream()
						.map(ref -> parseCommitOrNull(rw, ref.getObjectId()))
						.filter(Objects::nonNull);
				unreachable = checker.areAllReachable(
						nonAdvertisedParentCommitsOutsidePack, allRefCommits);
				if (unreachable.isPresent()) {
					throw new MissingObjectException(unreachable.get().getId(),
							Constants.TYPE_COMMIT);
				}
			}
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	/**
	 * Validates that base objects referenced in a thin pack are reachable.
	 *
	 * @param rw
	 *            the RevWalk to use
	 * @param connectivityCheckInfo
	 *            the connectivity check info
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	private void checkThinPackBases(RevWalk rw,
			ConnectivityCheckInfo connectivityCheckInfo) throws IOException {

		if (!connectivityCheckInfo.isCheckObjects()) {
			return;
		}
		ObjectIdSubclassMap<ObjectId> baseObjectIds = connectivityCheckInfo
				.getParser().getBaseObjectIds();

		try (ObjectWalk ow = new ObjectWalk(rw.getObjectReader())) {
			ObjectReachabilityChecker checker = rw.getObjectReader()
					.createObjectReachabilityChecker(ow);

			List<RevObject> targetObjs = StreamSupport
					.stream(baseObjectIds.spliterator(), false)
					.map(id -> parseAnyUnchecked(ow, id))
					.collect(Collectors.toList());

			// First try with the smaller set of advertised haves
			Stream<RevObject> starterObjs = advertisedHaves.stream()
					.map(id -> parseAnyUnchecked(ow, id))
					.filter(Objects::nonNull);

			Optional<RevObject> unreachable = checker
					.areAllReachable(targetObjs, starterObjs);
			if (unreachable.isPresent()) {
				// Fallback to check against full ref database
				Stream<RevObject> allRefCommits = connectivityCheckInfo
						.getRepository().getRefDatabase().getRefs().stream()
						.map(ref -> parseAnyUnchecked(ow, ref.getObjectId()))
						.filter(Objects::nonNull);
				unreachable = checker.areAllReachable(targetObjs,
						allRefCommits);
				if (unreachable.isPresent()) {
					throw new MissingObjectException(unreachable.get().getId(),
							Constants.TYPE_COMMIT);
				}
			}
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	/**
	 * Verifies tree connectivity for the new commits.
	 *
	 * @param rw
	 *            the RevWalk to use
	 * @param repo
	 *            the repository
	 * @param newCommitsToVerify
	 *            commits to verify
	 * @param checkObjects
	 *            whether to check objects
	 * @param pm
	 *            progress monitor
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	private void verifyTreeConnectivity(RevWalk rw, Repository repo,
			List<RevCommit> newCommitsToVerify, boolean checkObjects,
			ProgressMonitor pm) throws IOException {

		for (RevCommit c : newCommitsToVerify) {
			verifyTreeConnectivityForCommit(c, rw, repo, checkObjects, pm);
		}
	}

	/**
	 * Verifies tree connectivity for a single commit.
	 *
	 * @param c
	 *            the commit
	 * @param rw
	 *            the RevWalk to use
	 * @param repo
	 *            the repository
	 * @param checkObjects
	 *            whether to check objects
	 * @param pm
	 *            progress monitor
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	private void verifyTreeConnectivityForCommit(RevCommit c, RevWalk rw,
			Repository repo, boolean checkObjects, ProgressMonitor pm)
			throws IOException {
		try (TreeWalk tw = new TreeWalk(rw.getObjectReader())) {
			tw.setRecursive(false);
			tw.addTree(c.getTree());
			pm.update(1);
			for (RevCommit p : c.getParents()) {
				tw.addTree(p.getTree());
				pm.update(1);
			}

			while (tw.next()) {
				if (tw.getFileMode(0) == FileMode.MISSING) {
					continue; // Object deleted or moved
				}

				if (tw.getFileMode(0) == FileMode.GITLINK) {
					continue; // Skip submodule entries
				}

				ObjectId newObjId = tw.getObjectId(0);
				if (matchesAnyParent(tw, newObjId)) {
					continue; // matched via object ids
				}

				if (!tw.isSubtree()) {
					/*
					 * Blob case. objectsInPack are already in the object
					 * database, but the map lookup is faster than the database
					 * search, so try the map lookup first.
					 */
					if (!objectsInPack.contains(newObjId) && (checkObjects
							|| !repo.getObjectDatabase().has(newObjId))) {
						throw new MissingObjectException(newObjId,
								tw.getFileMode(0).getObjectType());
					}
				} else {
					// Subtree case
					if (objectsInPack.contains(newObjId)) {
						tw.enterSubtree();
						pm.update(1 + c.getParentCount());
					} else if (checkObjects
							|| !repo.getObjectDatabase().has(newObjId)) {
						throw new MissingObjectException(newObjId,
								FileMode.TREE.getObjectType());
					}
				} // Else object is in the database
			}
		}
	}

	/**
	 * Parses an object, throwing UncheckedIOException on failure. For use with
	 * streams.
	 *
	 * @param rw
	 *            the RevWalk to use
	 * @param id
	 *            the object ID
	 * @return the parsed object
	 */
	private static RevObject parseAnyUnchecked(RevWalk rw, ObjectId id) {
		try {
			return rw.parseAny(id);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Parses an object as a commit, peeling tags if necessary. Returns null if
	 * the object is not a commit or cannot be peeled to a commit. Throws
	 * UncheckedIOException on other I/O failures. For use with streams.
	 *
	 * @param rw
	 *            the RevWalk to use
	 * @param id
	 *            the object ID
	 * @return the parsed commit, or null
	 */
	private static RevCommit parseCommitOrNull(RevWalk rw, ObjectId id) {
		try {
			return rw.parseCommit(id);
		} catch (IncorrectObjectTypeException e) {
			return null;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Checks if the new object ID matches any parent tree's object ID at the
	 * current path.
	 *
	 * @param tw
	 *            the TreeWalk
	 * @param newObjId
	 *            the new object ID
	 * @return true if matches any parent
	 */
	private static boolean matchesAnyParent(TreeWalk tw, ObjectId newObjId) {
		for (int i = 1; i < tw.getTreeCount(); i++) {
			if (tw.getFileMode(i) != FileMode.MISSING
					&& newObjId.equals(tw.getObjectId(i))) {
				return true;
			}
		}
		return false;
	}

}

/*
 * Copyright (C) 2010, Benjamin Muskalla <bmuskalla@eclipsesource.com>
 * Copyright (C) 2008, Manuel Woelker <manuel.woelker@gmail.com>
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
package org.eclipse.jgit.blame;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.IntList;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Origin search strategy looking for a copied and modified file
 *
 */
public class CopyModifiedSearchStrategy implements IOriginSearchStrategy {
	final static double MAX_SCORE = 1000;

	double maxScore = MAX_SCORE;

	double thresholdScore = MAX_SCORE / 5;

	int maxCandidates = 2;

	private Repository repository;

	public Origin[] findOrigins(Origin source) {
		RevCommit commit = source.commit;
		repository = source.repository;
		try {
			ArrayList<Origin> resultList = new ArrayList<Origin>();
			for (RevCommit parent : commit.getParents()) {
				RevWalk revWalk = new RevWalk(repository);
				parent = revWalk.parseCommit(parent);
				resultList.addAll(findOrigins(source, parent));
			}
			return resultList.toArray(new Origin[0]);
		} catch (Exception e) {
			throw new RuntimeException(
					"could not retrieve scapegoats for commit " + commit, e);
		}
	}

	private Collection<Origin> findOrigins(Origin source, RevCommit parent) {
		IDiff diff = new MyersDiffImpl();
		ScoreList<Integer, String> scoreList = new ScoreList<Integer, String>(
				maxCandidates);
		ArrayList<Origin> resultList = new ArrayList<Origin>();
		try {
			// Tree tree = repository.mapTree(parent);
			TreeWalk treeWalk = createTreeWalk(source, parent);
			while (treeWalk.next()) {
				if (!treeWalk.isSubtree()) {
					// file
					ObjectId objectId = treeWalk.getObjectId(0);
					if (objectId == null || objectId.equals(ObjectId.zeroId())) {
						// nothing to see here
						continue;
					}
					ObjectLoader openBlob = repository.open(objectId);
					byte[] parentBytes = openBlob.getBytes();
					byte[] targetBytes = source.getBytes();
					IntList parentLines = RawParseUtils.lineMap(parentBytes, 0,
							parentBytes.length);
					IntList targetLines = RawParseUtils.lineMap(targetBytes, 0,
							targetBytes.length);
					IDifference[] differences = diff.diff(parentBytes,
							parentLines, targetBytes, targetLines);
					int maxLines = Math.max(targetLines.size(), parentLines
							.size()) - 1;
					int totalLines = targetLines.size() + parentLines.size()
							- 2;
					int changedlines = 0;
					for (IDifference difference : differences) {
						changedlines += difference.getLengthA()
								+ difference.getLengthB();
					}
					int commonLines = (totalLines - changedlines) / 2;
					int score = (int) (commonLines * maxScore / maxLines);
					String pathString = treeWalk
					.getPathString();
					if (score > thresholdScore) {
						scoreList.add(Integer.valueOf(score), pathString);
					}
				}
			}
			for (String path : scoreList.getEntries()) {
				resultList.add(new Origin(repository, parent, path));
			}

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return resultList;
	}

	/**
	 * @param source
	 * @param parent
	 * @return tree walk for the origin search
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws CorruptObjectException
	 * @throws IOException
	 */
	protected TreeWalk createTreeWalk(Origin source, RevCommit parent)
			throws MissingObjectException, IncorrectObjectTypeException,
			CorruptObjectException, IOException {
		TreeWalk treeWalk = new TreeWalk(repository);
		treeWalk.reset(parent.getTree());
		treeWalk.setRecursive(true);
		return treeWalk;
	}
}

package org.eclipse.jgit.blame;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;

public class BlameCacheUtil {

	private final RevWalk walk;


	BlameCacheUtil(RevWalk rw) {
		this.walk = rw;
	}

	/**
	 * Rewrite candidate c, assigning all its regions (i.e. unconfirmed lines)
	 * to the commits indicated by the cachedBlame
	 *
	 * @param fullBlame
	 *            blame of all lines of a file at commit C
	 * @param unconfirmed
	 *            candidate with a list of unblamed regions at commit C
	 * @return one or more Candidates with all the regions of "candidate"
	 *         blamed.
	 */
	public Candidate blameFromCache(BlameCache.Entry fullBlame,
			Candidate unconfirmed) throws IOException {
		List<Candidate> confirmed = new ArrayList<>();
		// Split region into single lines with a commit per line
		// So region(resultStart:0, sourceStart:2, lenght: 2) becomes
		// (commit, resultStart:0, sourceStart:2, length: 1)
		// (commit, resultStart:1, sourceStart:3, length: 1)
		Region r = unconfirmed.regionList;
		while (r != null) {
			List<Candidate> candidates = reassignRegion(fullBlame, r);
			confirmed.addAll(candidates);
			r = r.next;
		}

		return toCandidateList(confirmed);
	}

	record CommitLine(String commitId, Region r) {
	}

	private List<Candidate> reassignRegion(BlameCache.Entry fullBlame,
			Region r) throws IOException {
		// This is an unconfirmed region: result positions refer to wherever it
		// started,
		// source position refer to the current copy we are exploring.
		List<Candidate> confirmed = new ArrayList<>();
		String currentCommit = fullBlame.getCommitId(r.sourceStart);
		int regionStart = 0;
		for (int i = 0; i < r.length; i++) {
			String blameCommit = fullBlame.getCommitId(r.sourceStart + i);
			if (!blameCommit.equals(currentCommit)) {
				Candidate c = new Candidate(null, walk.parseCommit(
						ObjectId.fromString(currentCommit)), null);
				c.regionList = new Region(r.resultStart + regionStart, r.sourceStart + regionStart, i - regionStart);
				confirmed.add(c);

				regionStart = i;
				currentCommit = blameCommit;
			}
		}
		Candidate c = new Candidate(null, walk.parseCommit(
				ObjectId.fromString(currentCommit)), null);
		c.regionList = new Region(r.resultStart + regionStart, r.sourceStart + regionStart, r.length - regionStart);
		confirmed.add(c);
		return confirmed;
	}

	private Candidate toCandidateList(List<Candidate> jList) {
		Candidate head = jList.get(0);
		Candidate tail = head;

		for (int i = 1; i < jList.size(); i++) {
			Candidate c = jList.get(i);
			tail.queueNext = c;
			tail = c;
		}
		return head;
	}
}

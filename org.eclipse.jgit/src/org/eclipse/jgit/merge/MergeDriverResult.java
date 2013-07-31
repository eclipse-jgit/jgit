package org.eclipse.jgit.merge;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.diff.Sequence;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;

/**
 *
 * @author lgoubet
 */
public class MergeDriverResult {
	private final Status status;

	private final boolean pruneSubtree;

	private final Set<String> paths;

	private final MergeFailureReason failureReason;

	private final Map<String, MergeResult<? extends Sequence>> lowLevelResults;

	private final Map<String, DirCacheEntry> toBeCheckedOut;

	private final Set<String> toBeDeleted;

	private final Set<String> modifiedFiles;

	/**
	 *
	 * @param status
	 * @param paths
	 */
	public MergeDriverResult(Status status, Set<String> paths) {
		this(status, false, paths, null, Collections
				.<String, MergeResult<? extends Sequence>> emptyMap(),
				Collections.<String, DirCacheEntry> emptyMap(), Collections
						.<String> emptySet(), Collections.<String> emptySet());
	}

	/**
	 *
	 * @param status
	 * @param pruneSubtree
	 * @param paths
	 * @param reason
	 */
	public MergeDriverResult(Status status, boolean pruneSubtree,
			Set<String> paths, MergeFailureReason reason) {
		this(status, pruneSubtree, paths, reason, Collections
				.<String, MergeResult<? extends Sequence>> emptyMap(),
				Collections.<String, DirCacheEntry> emptyMap(), Collections
						.<String> emptySet(), Collections.<String> emptySet());
	}

	/**
	 *
	 * @param status
	 * @param pruneSubtree
	 * @param paths
	 * @param reason
	 * @param lowLevelResults
	 * @param toBeCheckedOut
	 * @param toBeDeleted
	 * @param modifiedFiles
	 */
	public MergeDriverResult(Status status, boolean pruneSubtree,
			Set<String> paths, MergeFailureReason reason,
			Map<String, MergeResult<? extends Sequence>> lowLevelResults,
			Map<String, DirCacheEntry> toBeCheckedOut, Set<String> toBeDeleted,
			Set<String> modifiedFiles) {
		this.status = status;
		this.pruneSubtree = pruneSubtree;
		this.paths = paths;
		this.failureReason = reason;
		this.lowLevelResults = lowLevelResults;
		this.toBeCheckedOut = toBeCheckedOut;
		this.toBeDeleted = toBeDeleted;
		this.modifiedFiles = modifiedFiles;
	}

	/**
	 *
	 * @return Whether the merger should prune the subtree of the currently
	 *         merged path.
	 */
	public boolean shouldPruneSubtree() {
		return pruneSubtree;
	}

	/**
	 *
	 * @return Status of this merge.
	 */
	public Status getStatus() {
		return status;
	}

	/**
	 *
	 * @return Paths affected by this merge operation.
	 */
	public Set<String> getPaths() {
		return paths;
	}

	/**
	 *
	 * @return Reason of the failure, if any.
	 */
	public MergeFailureReason getFailureReason() {
		return failureReason;
	}

	/**
	 *
	 * @return Low level results of this merge operation.
	 */
	public Map<String, MergeResult<? extends Sequence>> getLowLevelResults() {
		return lowLevelResults;
	}

	/**
	 *
	 * @return Files that are to be checked out after this merge.
	 */
	public Map<String, DirCacheEntry> getToBeCheckedOut() {
		return toBeCheckedOut;
	}

	/**
	 *
	 * @return Files that are to be deleted after this merge.
	 */
	public Set<String> getToBeDeleted() {
		return toBeDeleted;
	}

	/**
	 *
	 * @return Files modified by this merge operation.
	 */
	public Set<String> getModifiedFiles() {
		return modifiedFiles;
	}

	/**
	 *
	 * @author lgoubet
	 */
	public static enum Status {
		/**
		 * Used to indicate that this merge operation failed somehow. See
		 * {@link #failureReason} for more information.
		 */
		FAILED,

		/** Used to indicate that the merge ended successfully. */
		OK,

		/** Used to indicate that conflicts were encountered during this merge. */
		CONFLICT;
	}
}

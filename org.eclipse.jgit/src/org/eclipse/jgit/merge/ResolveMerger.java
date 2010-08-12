package org.eclipse.jgit.merge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuildIterator;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.IndexWriteException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.NameConflictTreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;

/**
 * @author d032780
 *
 */
public class ResolveMerger extends ThreeWayMerger {
	private NameConflictTreeWalk tw;

	private String commitNames[];

	private static final int T_BASE = 0;

	private static final int T_OURS = 1;

	private static final int T_THEIRS = 2;

	private static final int T_INDEX = 3;

	private static final int T_FILE = 4;

	private DirCacheBuilder builder;

	private ObjectId resultTree;

	private List<String> unmergedPathes = new ArrayList<String>();

	private List<String> modifiedFiles = new LinkedList<String>();

	private Map<String, DirCacheEntry> toBeCheckedOut = new HashMap<String, DirCacheEntry>();

	private Map<String, MergeResult> mergeResults = new HashMap<String, MergeResult>();

	private ObjectInserter oi;

	/**
	 * @param local
	 */
	protected ResolveMerger(Repository local) {
		super(local);
		commitNames = new String[] { "BASE", "OURS", "THEIRS" };
		oi = local.newObjectInserter();
	}

	@Override
	protected boolean mergeImpl() throws IOException {
		DirCache dc = getRepository().lockDirCache();
		try {
			builder = dc.builder();
			DirCacheBuildIterator buildIt = new DirCacheBuildIterator(builder);

			tw = new NameConflictTreeWalk(db);
			tw.reset();
			tw.addTree(mergeBase());
			tw.addTree(sourceTrees[0]);
			tw.addTree(sourceTrees[1]);
			tw.addTree(buildIt);
			tw.addTree(new FileTreeIterator(getRepository().getWorkTree(),
					getRepository().getFS()));

			while (tw.next()) {
				if (!processEntry(
						tw.getTree(T_BASE, CanonicalTreeParser.class),
						tw.getTree(T_OURS, CanonicalTreeParser.class),
						tw.getTree(T_THEIRS, CanonicalTreeParser.class),
						tw.getTree(T_INDEX, DirCacheBuildIterator.class),
						tw.getTree(T_FILE, WorkingTreeIterator.class))) {
					cleanUp();
					return false;
				}
				if (tw.isSubtree())
					tw.enterSubtree();
			}

			// All content-merges are successfully done. If we can now write the
			// new
			// index we are on quite safe ground. Even if the checkout of files
			// coming from "theirs" fails the user can work around such failures
			// by
			// checking out the index again.
			if (!builder.commit()) {
				cleanUp();
				throw new IndexWriteException();
			}
			builder = null;

			// No problem found. The only thing left to be done is to checkout
			// all files from "theirs" which have been selected to go into the
			// new index.
			checkout();
			if (getUnmergedPathes().isEmpty()) {
				resultTree = dc.writeTree(oi);
				return true;
			} else {
				resultTree = null;
				return false;
			}
		} finally {
			dc.unlock();
		}
	}

	private void checkout() throws NoWorkTreeException, IOException {
		for (Map.Entry<String, DirCacheEntry> entry : toBeCheckedOut.entrySet()) {
			DirCacheCheckout.checkoutEntry(db,
					new File(db.getWorkTree(), entry.getKey()),
					entry.getValue(), true);
			modifiedFiles.add(entry.getKey());
		}
	}

	/**
	 * Reverts the worktree after an unsuccessful merge. We know that for all
	 * modified files the old content was in the old index and the index
	 * contained only stage 0
	 *
	 * @throws IOException
	 * @throws CorruptObjectException
	 * @throws NoWorkTreeException
	 */
	private void cleanUp() throws NoWorkTreeException, CorruptObjectException, IOException {
		DirCache dc = db.readDirCache();
		ObjectReader or = db.getObjectDatabase().newReader();
		Iterator<String> mpathsIt=modifiedFiles.iterator();
		while(mpathsIt.hasNext()) {
			String mpath=mpathsIt.next();
			DirCacheEntry entry = dc.getEntry(mpath);
			or.open(entry.getObjectId()).copyTo(new FileOutputStream(new File(db.getWorkTree(), mpath)));
			mpathsIt.remove();
		}
	}

	/**
	 * adds a new path with the specified stage to the index builder
	 *
	 * @param path
	 * @param p
	 * @param stage
	 * @return the entry which was added to the index
	 */
	private DirCacheEntry add(byte[] path, CanonicalTreeParser p, int stage) {
		if (p != null) {
			DirCacheEntry e = new DirCacheEntry(path, stage);
			e.setFileMode(p.getEntryFileMode());
			e.setObjectId(p.getEntryObjectId());
			builder.add(e);
			return e;
		}
		return null;
	}

	/**
	 * Processes one path and tries to merge. This method will do all do all
	 * trivial (not content) merges and will also detect if a merge will fail.
	 * The merge will fail when one of the following is true
	 * <ul>
	 * <li>the index entry does not match the entry in ours. When merging one
	 * branch into the current HEAD, ours will point to HEAD and theirs will
	 * point to the other branch. It is assumed that the index matches the HEAD
	 * because it will only not match HEAD if it was populated before the merge
	 * operation. But the merge commit should not accidentally contain
	 * modifications done before the merge. Check the <a href=
	 * "http://www.kernel.org/pub/software/scm/git/docs/git-read-tree.html#_3_way_merge"
	 * >git read-tree</a> documentation for further explanations.</li>
	 * <li>A conflict was detected and the working-tree file is dirty. When a
	 * conflict is detected the content-merge algorithm will try to write a
	 * merged version into the working-tree. If the file is dirty we would
	 * override unsaved data.</li>
	 *
	 * @param base
	 *            the common base for ours and theirs
	 * @param ours
	 *            the ours side of the merge. When merging a branch into the
	 *            HEAD ours will point to HEAD
	 * @param theirs
	 *            the theirs side of the merge. When merging a branch into the
	 *            current HEAD theirs will point to the branch which is merged
	 *            into HEAD.
	 * @param index
	 *            the index entry
	 * @param work
	 *            the file in the working tree
	 * @return <code>false</code> if the merge will fail because the index entry
	 *         didn't match ours or the working-dir file was dirty and a
	 *         conflict occured
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws CorruptObjectException
	 * @throws IOException
	 */
	private boolean processEntry(CanonicalTreeParser base,
			CanonicalTreeParser ours, CanonicalTreeParser theirs,
			DirCacheBuildIterator index, WorkingTreeIterator work)
			throws MissingObjectException, IncorrectObjectTypeException,
			CorruptObjectException, IOException {
		final int modeO = tw.getRawMode(T_OURS);
		final int modeI = tw.getRawMode(T_INDEX);

		// The index entry has to be equal to the ours entry.
		if (!tw.idEqual(T_OURS, T_INDEX) || modeO != modeI) {
			return false;
		}

		final int modeT = tw.getRawMode(T_THEIRS);
		if (modeO == modeT && tw.idEqual(T_OURS, T_THEIRS)) {
			// ours and theirs are equal: it doesn'nt matter
			// which one we choose. OURS is choosen here.
			add(tw.getRawPath(), ours, DirCacheEntry.STAGE_0);
			// no checkout needed!
			return true;
		}

		final int modeB = tw.getRawMode(T_BASE);
		if (modeB == modeO && tw.idEqual(T_BASE, T_OURS)) {
			// OURS was not changed compared to base. All changes must be in
			// THEIRS. Choose THEIRS.
			DirCacheEntry e = add(tw.getRawPath(), theirs,
					DirCacheEntry.STAGE_0);
			// We could checkout the new entry into the worktree already, but
			// all worktree modification is deferred until we did know that the
			// merge will not fail (e.g. because of the index not matching ours.
			toBeCheckedOut.put(tw.getPathString(), e);
			return true;
		}

		if (modeB == modeT && tw.idEqual(T_BASE, T_THEIRS)) {
			// THEIRS was not changed compared to base. All changes must be in
			// OURS. Choose OURS.
			// We checked it in the beginning: ours is equal to index. So we can
			// leave the index entry as it is.
			builder.add((index == null) ? null : index.getDirCacheEntry());
			return true;
		}

		if (tw.isSubtree()) {
			// file/folder conflicts: here I want to detect only file/folder
			// conflict between ours and theirs. file/folder conflicts between
			// base/index/workingTree and something else are not relevant or
			// detected later
			if (nonTree(modeO) && !nonTree(modeT)) {
				if (nonTree(modeB))
					add(tw.getRawPath(), base, DirCacheEntry.STAGE_1);
				add(tw.getRawPath(), ours, DirCacheEntry.STAGE_2);
				unmergedPathes.add(tw.getPathString());
				return true;
			}
			if (nonTree(modeT) && !nonTree(modeO)) {
				if (nonTree(modeB))
					add(tw.getRawPath(), base, DirCacheEntry.STAGE_1);
				add(tw.getRawPath(), theirs, DirCacheEntry.STAGE_3);
				unmergedPathes.add(tw.getPathString());
				return true;
			}

			// ours and theirs are both folders or both files (and subtree
			// tells us we are in a subtree because of index or working-dir).
			// If they are both folders not content-merge is required - we can
			// return here.
			if (!nonTree(modeO))
				return true;

			// ours and theirs are both files, just fall out of the if block
			// and do the content merge
		}

		// We are going to update the worktree. Make sure the worktree is
		// not modified
		if (work.isModified(index.getDirCacheEntry(), true, true, db.getFS()))
			return false;

		if (!contentMerge(base, ours, theirs, index, work)) {
			unmergedPathes.add(tw.getPathString());
		}
		modifiedFiles.add(tw.getPathString());
		return true;
	}

	private boolean contentMerge(CanonicalTreeParser base,
			CanonicalTreeParser ours, CanonicalTreeParser theirs,
			DirCacheBuildIterator index, WorkingTreeIterator work)
			throws FileNotFoundException, IllegalStateException, IOException {
		MergeFormatter fmt = new MergeFormatter();

		// do the merge
		MergeResult result = MergeAlgorithm.merge(
				getRawText(base.getEntryObjectId(), db),
				getRawText(ours.getEntryObjectId(), db),
				getRawText(theirs.getEntryObjectId(), db));

		File of = new File(db.getWorkTree(), tw.getPathString());
		FileOutputStream fos = new FileOutputStream(of);
		try {
			fmt.formatMerge(fos, result, Arrays.asList(commitNames),
					Constants.CHARACTER_ENCODING);
		} finally {
			fos.close();
		}
		if (result.containsConflicts()) {
			// a conflict occured, the file will contain conflict markers
			// the index will be populated with the three stages and only the
			// workdir contains the halfways merged content
			add(tw.getRawPath(), base, DirCacheEntry.STAGE_1);
			add(tw.getRawPath(), ours, DirCacheEntry.STAGE_2);
			add(tw.getRawPath(), theirs, DirCacheEntry.STAGE_3);
			mergeResults.put(tw.getPathString(), result);
			return false;
		} else {
			// no conflict occured, the file will contain fully merged content.
			// the index will be populated with the new merged version
			DirCacheEntry dce = new DirCacheEntry(tw.getPathString());
			dce.setFileMode(tw.getFileMode(0));
			dce.setLastModified(of.lastModified());
			dce.setLength((int) of.length());
			InputStream is = new FileInputStream(of);
			try {
				dce.setObjectId(oi.insert(Constants.OBJ_BLOB, of.length(),
						is));
			} finally {
				is.close();
			}
			builder.add(dce);
			return true;
		}
	}

	private static RawText getRawText(ObjectId id, Repository db)
			throws IOException {
		if (id.equals(ObjectId.zeroId()))
			return new RawText(new byte[] {});
		return new RawText(db.open(id, Constants.OBJ_BLOB).getCachedBytes());
	}

	private static boolean nonTree(final int mode) {
		return mode != 0 && !FileMode.TREE.equals(mode);
	}

	@Override
	public ObjectId getResultTreeId() {
		return (resultTree == null) ? null : resultTree.toObjectId();
	}

	/**
	 * @param commitNames
	 *            the names of the commits as they would appear in conflict
	 *            markers
	 */
	public void setCommitNames(String[] commitNames) {
		this.commitNames = commitNames;
	}

	/**
	 * @return the names of the commits as they would appear in conflict
	 *         markers.
	 */
	public String[] getCommitNames() {
		return commitNames;
	}

	/**
	 * @return the pathes with conflicts. This is a subset of the files listed
	 *         by {@link #getModifiedFiles()}
	 */
	public List<String> getUnmergedPathes() {
		return unmergedPathes;
	}

	/**
	 * @return the pathes of files which have been modified by this merge. A
	 *         file will be modified if a content-merge works on this path or if
	 *         the merge algorithm decides to take the theirs-version. This is a
	 *         superset of the files listed by {@link #getUnmergedPathes()}.
	 */
	public List<String> getModifiedFiles() {
		return modifiedFiles;
	}

	/**
	 * @return a map which maps the pathes of files which have to be checked out
	 *         because the merge created new fully-merged content for this file
	 *         into the index. This means: the merge wrote a new stage 0 entry
	 *         for this path.
	 */
	public Map<String, DirCacheEntry> getToBeCheckedOut() {
		return toBeCheckedOut;
	}

	/**
	 * @return the mergeResults
	 */
	public Map<String, MergeResult> getMergeResults() {
		return mergeResults;
	}
}

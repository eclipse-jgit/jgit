/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008-2009, Johannes E. Schindelin <johannes.schindelin@gmx.de>
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

package org.eclipse.jgit.diff;

import static org.eclipse.jgit.diff.DiffEntry.ChangeType.ADD;
import static org.eclipse.jgit.diff.DiffEntry.ChangeType.COPY;
import static org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE;
import static org.eclipse.jgit.diff.DiffEntry.ChangeType.MODIFY;
import static org.eclipse.jgit.diff.DiffEntry.ChangeType.RENAME;
import static org.eclipse.jgit.diff.DiffEntry.Side.NEW;
import static org.eclipse.jgit.diff.DiffEntry.Side.OLD;
import static org.eclipse.jgit.lib.Constants.encode;
import static org.eclipse.jgit.lib.Constants.encodeASCII;
import static org.eclipse.jgit.lib.FileMode.GITLINK;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.diff.DiffAlgorithm.SupportedAlgorithm;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.FileHeader.PatchType;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.FollowFilter;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.IndexDiffFilter;
import org.eclipse.jgit.treewalk.filter.NotIgnoredFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.QuotedString;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/**
 * Format a Git style patch script.
 */
public class DiffFormatter {
	private static final int DEFAULT_BINARY_FILE_THRESHOLD = PackConfig.DEFAULT_BIG_FILE_THRESHOLD;

	private static final byte[] noNewLine = encodeASCII("\\ No newline at end of file\n"); //$NON-NLS-1$

	/** Magic return content indicating it is empty or no content present. */
	private static final byte[] EMPTY = new byte[] {};

	/** Magic return indicating the content is binary. */
	private static final byte[] BINARY = new byte[] {};

	private final OutputStream out;

	private Repository db;

	private ObjectReader reader;

	private DiffConfig diffCfg;

	private int context = 3;

	private int abbreviationLength = 7;

	private DiffAlgorithm diffAlgorithm;

	private RawTextComparator comparator = RawTextComparator.DEFAULT;

	private int binaryFileThreshold = DEFAULT_BINARY_FILE_THRESHOLD;

	private String oldPrefix = "a/"; //$NON-NLS-1$

	private String newPrefix = "b/"; //$NON-NLS-1$

	private TreeFilter pathFilter = TreeFilter.ALL;

	private RenameDetector renameDetector;

	private ProgressMonitor progressMonitor;

	private ContentSource.Pair source;

	/**
	 * Create a new formatter with a default level of context.
	 *
	 * @param out
	 *            the stream the formatter will write line data to. This stream
	 *            should have buffering arranged by the caller, as many small
	 *            writes are performed to it.
	 */
	public DiffFormatter(OutputStream out) {
		this.out = out;
	}

	/** @return the stream we are outputting data to. */
	protected OutputStream getOutputStream() {
		return out;
	}

	/**
	 * Set the repository the formatter can load object contents from.
	 *
	 * Once a repository has been set, the formatter must be released to ensure
	 * the internal ObjectReader is able to release its resources.
	 *
	 * @param repository
	 *            source repository holding referenced objects.
	 */
	public void setRepository(Repository repository) {
		if (reader != null)
			reader.release();

		db = repository;
		reader = db.newObjectReader();
		diffCfg = db.getConfig().get(DiffConfig.KEY);

		ContentSource cs = ContentSource.create(reader);
		source = new ContentSource.Pair(cs, cs);

		DiffConfig dc = db.getConfig().get(DiffConfig.KEY);
		if (dc.isNoPrefix()) {
			setOldPrefix(""); //$NON-NLS-1$
			setNewPrefix(""); //$NON-NLS-1$
		}
		setDetectRenames(dc.isRenameDetectionEnabled());

		diffAlgorithm = DiffAlgorithm.getAlgorithm(db.getConfig().getEnum(
				ConfigConstants.CONFIG_DIFF_SECTION, null,
				ConfigConstants.CONFIG_KEY_ALGORITHM,
				SupportedAlgorithm.HISTOGRAM));

	}

	/**
	 * Change the number of lines of context to display.
	 *
	 * @param lineCount
	 *            number of lines of context to see before the first
	 *            modification and after the last modification within a hunk of
	 *            the modified file.
	 */
	public void setContext(final int lineCount) {
		if (lineCount < 0)
			throw new IllegalArgumentException(
					JGitText.get().contextMustBeNonNegative);
		context = lineCount;
	}

	/**
	 * Change the number of digits to show in an ObjectId.
	 *
	 * @param count
	 *            number of digits to show in an ObjectId.
	 */
	public void setAbbreviationLength(final int count) {
		if (count < 0)
			throw new IllegalArgumentException(
					JGitText.get().abbreviationLengthMustBeNonNegative);
		abbreviationLength = count;
	}

	/**
	 * Set the algorithm that constructs difference output.
	 *
	 * @param alg
	 *            the algorithm to produce text file differences.
	 * @see HistogramDiff
	 */
	public void setDiffAlgorithm(DiffAlgorithm alg) {
		diffAlgorithm = alg;
	}

	/**
	 * Set the line equivalence function for text file differences.
	 *
	 * @param cmp
	 *            The equivalence function used to determine if two lines of
	 *            text are identical. The function can be changed to ignore
	 *            various types of whitespace.
	 * @see RawTextComparator#DEFAULT
	 * @see RawTextComparator#WS_IGNORE_ALL
	 * @see RawTextComparator#WS_IGNORE_CHANGE
	 * @see RawTextComparator#WS_IGNORE_LEADING
	 * @see RawTextComparator#WS_IGNORE_TRAILING
	 */
	public void setDiffComparator(RawTextComparator cmp) {
		comparator = cmp;
	}

	/**
	 * Set maximum file size for text files.
	 *
	 * Files larger than this size will be treated as though they are binary and
	 * not text. Default is {@value #DEFAULT_BINARY_FILE_THRESHOLD} .
	 *
	 * @param threshold
	 *            the limit, in bytes. Files larger than this size will be
	 *            assumed to be binary, even if they aren't.
	 */
	public void setBinaryFileThreshold(int threshold) {
		this.binaryFileThreshold = threshold;
	}

	/**
	 * Set the prefix applied in front of old file paths.
	 *
	 * @param prefix
	 *            the prefix in front of old paths. Typically this is the
	 *            standard string {@code "a/"}, but may be any prefix desired by
	 *            the caller. Must not be null. Use the empty string to have no
	 *            prefix at all.
	 */
	public void setOldPrefix(String prefix) {
		oldPrefix = prefix;
	}

	/**
	 * Get the prefix applied in front of old file paths.
	 *
	 * @return the prefix
	 * @since 2.0
	 */
	public String getOldPrefix() {
		return this.oldPrefix;
	}

	/**
	 * Set the prefix applied in front of new file paths.
	 *
	 * @param prefix
	 *            the prefix in front of new paths. Typically this is the
	 *            standard string {@code "b/"}, but may be any prefix desired by
	 *            the caller. Must not be null. Use the empty string to have no
	 *            prefix at all.
	 */
	public void setNewPrefix(String prefix) {
		newPrefix = prefix;
	}

	/**
	 * Get the prefix applied in front of new file paths.
	 *
	 * @return the prefix
	 * @since 2.0
	 */
	public String getNewPrefix() {
		return this.newPrefix;
	}

	/** @return true if rename detection is enabled. */
	public boolean isDetectRenames() {
		return renameDetector != null;
	}

	/**
	 * Enable or disable rename detection.
	 *
	 * Before enabling rename detection the repository must be set with
	 * {@link #setRepository(Repository)}. Once enabled the detector can be
	 * configured away from its defaults by obtaining the instance directly from
	 * {@link #getRenameDetector()} and invoking configuration.
	 *
	 * @param on
	 *            if rename detection should be enabled.
	 */
	public void setDetectRenames(boolean on) {
		if (on && renameDetector == null) {
			assertHaveRepository();
			renameDetector = new RenameDetector(db);
		} else if (!on)
			renameDetector = null;
	}

	/** @return the rename detector if rename detection is enabled. */
	public RenameDetector getRenameDetector() {
		return renameDetector;
	}

	/**
	 * Set the progress monitor for long running rename detection.
	 *
	 * @param pm
	 *            progress monitor to receive rename detection status through.
	 */
	public void setProgressMonitor(ProgressMonitor pm) {
		progressMonitor = pm;
	}

	/**
	 * Set the filter to produce only specific paths.
	 *
	 * If the filter is an instance of {@link FollowFilter}, the filter path
	 * will be updated during successive scan or format invocations. The updated
	 * path can be obtained from {@link #getPathFilter()}.
	 *
	 * @param filter
	 *            the tree filter to apply.
	 */
	public void setPathFilter(TreeFilter filter) {
		pathFilter = filter != null ? filter : TreeFilter.ALL;
	}

	/** @return the current path filter. */
	public TreeFilter getPathFilter() {
		return pathFilter;
	}

	/**
	 * Flush the underlying output stream of this formatter.
	 *
	 * @throws IOException
	 *             the stream's own flush method threw an exception.
	 */
	public void flush() throws IOException {
		out.flush();
	}

	/** Release the internal ObjectReader state. */
	public void release() {
		if (reader != null)
			reader.release();
	}

	/**
	 * Determine the differences between two trees.
	 *
	 * No output is created, instead only the file paths that are different are
	 * returned. Callers may choose to format these paths themselves, or convert
	 * them into {@link FileHeader} instances with a complete edit list by
	 * calling {@link #toFileHeader(DiffEntry)}.
	 *
	 * @param a
	 *            the old (or previous) side.
	 * @param b
	 *            the new (or updated) side.
	 * @return the paths that are different.
	 * @throws IOException
	 *             trees cannot be read or file contents cannot be read.
	 */
	public List<DiffEntry> scan(AnyObjectId a, AnyObjectId b)
			throws IOException {
		assertHaveRepository();

		RevWalk rw = new RevWalk(reader);
		RevTree aTree = a != null ? rw.parseTree(a) : null;
		RevTree bTree = b != null ? rw.parseTree(b) : null;
		return scan(aTree, bTree);
	}

	/**
	 * Determine the differences between two trees.
	 *
	 * No output is created, instead only the file paths that are different are
	 * returned. Callers may choose to format these paths themselves, or convert
	 * them into {@link FileHeader} instances with a complete edit list by
	 * calling {@link #toFileHeader(DiffEntry)}.
	 *
	 * @param a
	 *            the old (or previous) side.
	 * @param b
	 *            the new (or updated) side.
	 * @return the paths that are different.
	 * @throws IOException
	 *             trees cannot be read or file contents cannot be read.
	 */
	public List<DiffEntry> scan(RevTree a, RevTree b) throws IOException {
		assertHaveRepository();

		AbstractTreeIterator aIterator;
		if (a != null) {
			CanonicalTreeParser aParser = new CanonicalTreeParser();
			aParser.reset(reader, a);
			aIterator = aParser;
		} else
			aIterator = new EmptyTreeIterator();
		AbstractTreeIterator bIterator;
		if (b != null) {
			CanonicalTreeParser bParser = new CanonicalTreeParser();
			bParser.reset(reader, b);
			bIterator = bParser;
		} else
			bIterator = new EmptyTreeIterator();
		return scan(aIterator, bIterator);
	}

	/**
	 * Determine the differences between two trees.
	 *
	 * No output is created, instead only the file paths that are different are
	 * returned. Callers may choose to format these paths themselves, or convert
	 * them into {@link FileHeader} instances with a complete edit list by
	 * calling {@link #toFileHeader(DiffEntry)}.
	 *
	 * @param a
	 *            the old (or previous) side.
	 * @param b
	 *            the new (or updated) side.
	 * @return the paths that are different.
	 * @throws IOException
	 *             trees cannot be read or file contents cannot be read.
	 */
	public List<DiffEntry> scan(AbstractTreeIterator a, AbstractTreeIterator b)
			throws IOException {
		assertHaveRepository();

		TreeWalk walk = new TreeWalk(reader);
		walk.addTree(a);
		walk.addTree(b);
		walk.setRecursive(true);

		TreeFilter filter = getDiffTreeFilterFor(a, b);
		if (pathFilter instanceof FollowFilter) {
			walk.setFilter(AndTreeFilter.create(
					PathFilter.create(((FollowFilter) pathFilter).getPath()),
					filter));
		} else {
			walk.setFilter(AndTreeFilter.create(pathFilter, filter));
		}

		source = new ContentSource.Pair(source(a), source(b));

		List<DiffEntry> files = DiffEntry.scan(walk);
		if (pathFilter instanceof FollowFilter && isAdd(files)) {
			// The file we are following was added here, find where it
			// came from so we can properly show the rename or copy,
			// then continue digging backwards.
			//
			a.reset();
			b.reset();
			walk.reset();
			walk.addTree(a);
			walk.addTree(b);
			walk.setFilter(filter);

			if (renameDetector == null)
				setDetectRenames(true);
			files = updateFollowFilter(detectRenames(DiffEntry.scan(walk)));

		} else if (renameDetector != null)
			files = detectRenames(files);

		return files;
	}

	private static TreeFilter getDiffTreeFilterFor(AbstractTreeIterator a,
			AbstractTreeIterator b) {
		if (a instanceof DirCacheIterator && b instanceof WorkingTreeIterator)
			return new IndexDiffFilter(0, 1);

		if (a instanceof WorkingTreeIterator && b instanceof DirCacheIterator)
			return new IndexDiffFilter(1, 0);

		TreeFilter filter = TreeFilter.ANY_DIFF;
		if (a instanceof WorkingTreeIterator)
			filter = AndTreeFilter.create(new NotIgnoredFilter(0), filter);
		if (b instanceof WorkingTreeIterator)
			filter = AndTreeFilter.create(new NotIgnoredFilter(1), filter);
		return filter;
	}

	private ContentSource source(AbstractTreeIterator iterator) {
		if (iterator instanceof WorkingTreeIterator)
			return ContentSource.create((WorkingTreeIterator) iterator);
		return ContentSource.create(reader);
	}

	private List<DiffEntry> detectRenames(List<DiffEntry> files)
			throws IOException {
		renameDetector.reset();
		renameDetector.addAll(files);
		return renameDetector.compute(reader, progressMonitor);
	}

	private boolean isAdd(List<DiffEntry> files) {
		String oldPath = ((FollowFilter) pathFilter).getPath();
		for (DiffEntry ent : files) {
			if (ent.getChangeType() == ADD && ent.getNewPath().equals(oldPath))
				return true;
		}
		return false;
	}

	private List<DiffEntry> updateFollowFilter(List<DiffEntry> files) {
		String oldPath = ((FollowFilter) pathFilter).getPath();
		for (DiffEntry ent : files) {
			if (isRename(ent) && ent.getNewPath().equals(oldPath)) {
				pathFilter = FollowFilter.create(ent.getOldPath(), diffCfg);
				return Collections.singletonList(ent);
			}
		}
		return Collections.emptyList();
	}

	private static boolean isRename(DiffEntry ent) {
		return ent.getChangeType() == RENAME || ent.getChangeType() == COPY;
	}

	/**
	 * Format the differences between two trees.
	 *
	 * The patch is expressed as instructions to modify {@code a} to make it
	 * {@code b}.
	 *
	 * @param a
	 *            the old (or previous) side.
	 * @param b
	 *            the new (or updated) side.
	 * @throws IOException
	 *             trees cannot be read, file contents cannot be read, or the
	 *             patch cannot be output.
	 */
	public void format(AnyObjectId a, AnyObjectId b) throws IOException {
		format(scan(a, b));
	}

	/**
	 * Format the differences between two trees.
	 *
	 * The patch is expressed as instructions to modify {@code a} to make it
	 * {@code b}.
	 *
	 * @param a
	 *            the old (or previous) side.
	 * @param b
	 *            the new (or updated) side.
	 * @throws IOException
	 *             trees cannot be read, file contents cannot be read, or the
	 *             patch cannot be output.
	 */
	public void format(RevTree a, RevTree b) throws IOException {
		format(scan(a, b));
	}

	/**
	 * Format the differences between two trees.
	 *
	 * The patch is expressed as instructions to modify {@code a} to make it
	 * {@code b}.
	 *
	 * @param a
	 *            the old (or previous) side.
	 * @param b
	 *            the new (or updated) side.
	 * @throws IOException
	 *             trees cannot be read, file contents cannot be read, or the
	 *             patch cannot be output.
	 */
	public void format(AbstractTreeIterator a, AbstractTreeIterator b)
			throws IOException {
		format(scan(a, b));
	}

	/**
	 * Format a patch script from a list of difference entries. Requires
	 * {@link #scan(AbstractTreeIterator, AbstractTreeIterator)} to have been
	 * called first.
	 *
	 * @param entries
	 *            entries describing the affected files.
	 * @throws IOException
	 *             a file's content cannot be read, or the output stream cannot
	 *             be written to.
	 */
	public void format(List<? extends DiffEntry> entries) throws IOException {
		for (DiffEntry ent : entries)
			format(ent);
	}

	/**
	 * Format a patch script for one file entry.
	 *
	 * @param ent
	 *            the entry to be formatted.
	 * @throws IOException
	 *             a file's content cannot be read, or the output stream cannot
	 *             be written to.
	 */
	public void format(DiffEntry ent) throws IOException {
		FormatResult res = createFormatResult(ent);
		format(res.header, res.a, res.b);
	}

	private static void writeGitLinkDiffText(OutputStream o, DiffEntry ent)
			throws IOException {
		if (ent.getOldMode() == GITLINK) {
			o.write(encodeASCII("-Subproject commit " + ent.getOldId().name() //$NON-NLS-1$
					+ "\n")); //$NON-NLS-1$
		}
		if (ent.getNewMode() == GITLINK) {
			o.write(encodeASCII("+Subproject commit " + ent.getNewId().name() //$NON-NLS-1$
					+ "\n")); //$NON-NLS-1$
		}
	}

	private String format(AbbreviatedObjectId id) {
		if (id.isComplete() && db != null) {
			try {
				id = reader.abbreviate(id.toObjectId(), abbreviationLength);
			} catch (IOException cannotAbbreviate) {
				// Ignore this. We'll report the full identity.
			}
		}
		return id.name();
	}

	private static String quotePath(String name) {
		return QuotedString.GIT_PATH.quote(name);
	}

	/**
	 * Format a patch script, reusing a previously parsed FileHeader.
	 * <p>
	 * This formatter is primarily useful for editing an existing patch script
	 * to increase or reduce the number of lines of context within the script.
	 * All header lines are reused as-is from the supplied FileHeader.
	 *
	 * @param head
	 *            existing file header containing the header lines to copy.
	 * @param a
	 *            text source for the pre-image version of the content. This
	 *            must match the content of {@link FileHeader#getOldId()}.
	 * @param b
	 *            text source for the post-image version of the content. This
	 *            must match the content of {@link FileHeader#getNewId()}.
	 * @throws IOException
	 *             writing to the supplied stream failed.
	 */
	public void format(final FileHeader head, final RawText a, final RawText b)
			throws IOException {
		// Reuse the existing FileHeader as-is by blindly copying its
		// header lines, but avoiding its hunks. Instead we recreate
		// the hunks from the text instances we have been supplied.
		//
		final int start = head.getStartOffset();
		int end = head.getEndOffset();
		if (!head.getHunks().isEmpty())
			end = head.getHunks().get(0).getStartOffset();
		out.write(head.getBuffer(), start, end - start);
		if (head.getPatchType() == PatchType.UNIFIED)
			format(head.toEditList(), a, b);
	}

	/**
	 * Formats a list of edits in unified diff format
	 *
	 * @param edits
	 *            some differences which have been calculated between A and B
	 * @param a
	 *            the text A which was compared
	 * @param b
	 *            the text B which was compared
	 * @throws IOException
	 */
	public void format(final EditList edits, final RawText a, final RawText b)
			throws IOException {
		for (int curIdx = 0; curIdx < edits.size();) {
			Edit curEdit = edits.get(curIdx);
			final int endIdx = findCombinedEnd(edits, curIdx);
			final Edit endEdit = edits.get(endIdx);

			int aCur = Math.max(0, curEdit.getBeginA() - context);
			int bCur = Math.max(0, curEdit.getBeginB() - context);
			final int aEnd = Math.min(a.size(), endEdit.getEndA() + context);
			final int bEnd = Math.min(b.size(), endEdit.getEndB() + context);

			writeHunkHeader(aCur, aEnd, bCur, bEnd);

			while (aCur < aEnd || bCur < bEnd) {
				if (aCur < curEdit.getBeginA() || endIdx + 1 < curIdx) {
					writeContextLine(a, aCur);
					if (isEndOfLineMissing(a, aCur))
						out.write(noNewLine);
					aCur++;
					bCur++;
				} else if (aCur < curEdit.getEndA()) {
					writeRemovedLine(a, aCur);
					if (isEndOfLineMissing(a, aCur))
						out.write(noNewLine);
					aCur++;
				} else if (bCur < curEdit.getEndB()) {
					writeAddedLine(b, bCur);
					if (isEndOfLineMissing(b, bCur))
						out.write(noNewLine);
					bCur++;
				}

				if (end(curEdit, aCur, bCur) && ++curIdx < edits.size())
					curEdit = edits.get(curIdx);
			}
		}
	}

	/**
	 * Output a line of context (unmodified line).
	 *
	 * @param text
	 *            RawText for accessing raw data
	 * @param line
	 *            the line number within text
	 * @throws IOException
	 */
	protected void writeContextLine(final RawText text, final int line)
			throws IOException {
		writeLine(' ', text, line);
	}

	private static boolean isEndOfLineMissing(final RawText text, final int line) {
		return line + 1 == text.size() && text.isMissingNewlineAtEnd();
	}

	/**
	 * Output an added line.
	 *
	 * @param text
	 *            RawText for accessing raw data
	 * @param line
	 *            the line number within text
	 * @throws IOException
	 */
	protected void writeAddedLine(final RawText text, final int line)
			throws IOException {
		writeLine('+', text, line);
	}

	/**
	 * Output a removed line
	 *
	 * @param text
	 *            RawText for accessing raw data
	 * @param line
	 *            the line number within text
	 * @throws IOException
	 */
	protected void writeRemovedLine(final RawText text, final int line)
			throws IOException {
		writeLine('-', text, line);
	}

	/**
	 * Output a hunk header
	 *
	 * @param aStartLine
	 *            within first source
	 * @param aEndLine
	 *            within first source
	 * @param bStartLine
	 *            within second source
	 * @param bEndLine
	 *            within second source
	 * @throws IOException
	 */
	protected void writeHunkHeader(int aStartLine, int aEndLine,
			int bStartLine, int bEndLine) throws IOException {
		out.write('@');
		out.write('@');
		writeRange('-', aStartLine + 1, aEndLine - aStartLine);
		writeRange('+', bStartLine + 1, bEndLine - bStartLine);
		out.write(' ');
		out.write('@');
		out.write('@');
		out.write('\n');
	}

	private void writeRange(final char prefix, final int begin, final int cnt)
			throws IOException {
		out.write(' ');
		out.write(prefix);
		switch (cnt) {
		case 0:
			// If the range is empty, its beginning number must be the
			// line just before the range, or 0 if the range is at the
			// start of the file stream. Here, begin is always 1 based,
			// so an empty file would produce "0,0".
			//
			out.write(encodeASCII(begin - 1));
			out.write(',');
			out.write('0');
			break;

		case 1:
			// If the range is exactly one line, produce only the number.
			//
			out.write(encodeASCII(begin));
			break;

		default:
			out.write(encodeASCII(begin));
			out.write(',');
			out.write(encodeASCII(cnt));
			break;
		}
	}

	/**
	 * Write a standard patch script line.
	 *
	 * @param prefix
	 *            prefix before the line, typically '-', '+', ' '.
	 * @param text
	 *            the text object to obtain the line from.
	 * @param cur
	 *            line number to output.
	 * @throws IOException
	 *             the stream threw an exception while writing to it.
	 */
	protected void writeLine(final char prefix, final RawText text,
			final int cur) throws IOException {
		out.write(prefix);
		text.writeLine(out, cur);
		out.write('\n');
	}

	/**
	 * Creates a {@link FileHeader} representing the given {@link DiffEntry}
	 * <p>
	 * This method does not use the OutputStream associated with this
	 * DiffFormatter instance. It is therefore safe to instantiate this
	 * DiffFormatter instance with a {@link DisabledOutputStream} if this method
	 * is the only one that will be used.
	 *
	 * @param ent
	 *            the DiffEntry to create the FileHeader for
	 * @return a FileHeader representing the DiffEntry. The FileHeader's buffer
	 *         will contain only the header of the diff output. It will also
	 *         contain one {@link HunkHeader}.
	 * @throws IOException
	 *             the stream threw an exception while writing to it, or one of
	 *             the blobs referenced by the DiffEntry could not be read.
	 * @throws CorruptObjectException
	 *             one of the blobs referenced by the DiffEntry is corrupt.
	 * @throws MissingObjectException
	 *             one of the blobs referenced by the DiffEntry is missing.
	 */
	public FileHeader toFileHeader(DiffEntry ent) throws IOException,
			CorruptObjectException, MissingObjectException {
		return createFormatResult(ent).header;
	}

	private static class FormatResult {
		FileHeader header;

		RawText a;

		RawText b;
	}

	private FormatResult createFormatResult(DiffEntry ent) throws IOException,
			CorruptObjectException, MissingObjectException {
		final FormatResult res = new FormatResult();
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		final EditList editList;
		final FileHeader.PatchType type;

		formatHeader(buf, ent);

		if (ent.getOldMode() == GITLINK || ent.getNewMode() == GITLINK) {
			formatOldNewPaths(buf, ent);
			writeGitLinkDiffText(buf, ent);
			editList = new EditList();
			type = PatchType.UNIFIED;

		} else if (ent.getOldId() == null || ent.getNewId() == null) {
			// Content not changed (e.g. only mode, pure rename)
			editList = new EditList();
			type = PatchType.UNIFIED;

		} else {
			assertHaveRepository();

			byte[] aRaw = open(OLD, ent);
			byte[] bRaw = open(NEW, ent);

			if (aRaw == BINARY || bRaw == BINARY //
					|| RawText.isBinary(aRaw) || RawText.isBinary(bRaw)) {
				formatOldNewPaths(buf, ent);
				buf.write(encodeASCII("Binary files differ\n")); //$NON-NLS-1$
				editList = new EditList();
				type = PatchType.BINARY;

			} else {
				res.a = new RawText(aRaw);
				res.b = new RawText(bRaw);
				editList = diff(res.a, res.b);
				type = PatchType.UNIFIED;

				switch (ent.getChangeType()) {
				case RENAME:
				case COPY:
					if (!editList.isEmpty())
						formatOldNewPaths(buf, ent);
					break;

				default:
					formatOldNewPaths(buf, ent);
					break;
				}
			}
		}

		res.header = new FileHeader(buf.toByteArray(), editList, type);
		return res;
	}

	private EditList diff(RawText a, RawText b) {
		return diffAlgorithm.diff(comparator, a, b);
	}

	private void assertHaveRepository() {
		if (db == null)
			throw new IllegalStateException(JGitText.get().repositoryIsRequired);
	}

	private byte[] open(DiffEntry.Side side, DiffEntry entry)
			throws IOException {
		if (entry.getMode(side) == FileMode.MISSING)
			return EMPTY;

		if (entry.getMode(side).getObjectType() != Constants.OBJ_BLOB)
			return EMPTY;

		AbbreviatedObjectId id = entry.getId(side);
		if (!id.isComplete()) {
			Collection<ObjectId> ids = reader.resolve(id);
			if (ids.size() == 1) {
				id = AbbreviatedObjectId.fromObjectId(ids.iterator().next());
				switch (side) {
				case OLD:
					entry.oldId = id;
					break;
				case NEW:
					entry.newId = id;
					break;
				}
			} else if (ids.size() == 0)
				throw new MissingObjectException(id, Constants.OBJ_BLOB);
			else
				throw new AmbiguousObjectException(id, ids);
		}

		try {
			ObjectLoader ldr = source.open(side, entry);
			return ldr.getBytes(binaryFileThreshold);

		} catch (LargeObjectException.ExceedsLimit overLimit) {
			return BINARY;

		} catch (LargeObjectException.ExceedsByteArrayLimit overLimit) {
			return BINARY;

		} catch (LargeObjectException.OutOfMemory tooBig) {
			return BINARY;

		} catch (LargeObjectException tooBig) {
			tooBig.setObjectId(id.toObjectId());
			throw tooBig;
		}
	}

	/**
	 * Output the first header line
	 *
	 * @param o
	 *            The stream the formatter will write the first header line to
	 * @param type
	 *            The {@link ChangeType}
	 * @param oldPath
	 *            old path to the file
	 * @param newPath
	 *            new path to the file
	 * @throws IOException
	 *             the stream threw an exception while writing to it.
	 */
	protected void formatGitDiffFirstHeaderLine(ByteArrayOutputStream o,
			final ChangeType type, final String oldPath, final String newPath)
			throws IOException {
		o.write(encodeASCII("diff --git ")); //$NON-NLS-1$
		o.write(encode(quotePath(oldPrefix + (type == ADD ? newPath : oldPath))));
		o.write(' ');
		o.write(encode(quotePath(newPrefix
				+ (type == DELETE ? oldPath : newPath))));
		o.write('\n');
	}

	private void formatHeader(ByteArrayOutputStream o, DiffEntry ent)
			throws IOException {
		final ChangeType type = ent.getChangeType();
		final String oldp = ent.getOldPath();
		final String newp = ent.getNewPath();
		final FileMode oldMode = ent.getOldMode();
		final FileMode newMode = ent.getNewMode();

		formatGitDiffFirstHeaderLine(o, type, oldp, newp);

		if ((type == MODIFY || type == COPY || type == RENAME)
				&& !oldMode.equals(newMode)) {
			o.write(encodeASCII("old mode ")); //$NON-NLS-1$
			oldMode.copyTo(o);
			o.write('\n');

			o.write(encodeASCII("new mode ")); //$NON-NLS-1$
			newMode.copyTo(o);
			o.write('\n');
		}

		switch (type) {
		case ADD:
			o.write(encodeASCII("new file mode ")); //$NON-NLS-1$
			newMode.copyTo(o);
			o.write('\n');
			break;

		case DELETE:
			o.write(encodeASCII("deleted file mode ")); //$NON-NLS-1$
			oldMode.copyTo(o);
			o.write('\n');
			break;

		case RENAME:
			o.write(encodeASCII("similarity index " + ent.getScore() + "%")); //$NON-NLS-1$ //$NON-NLS-2$
			o.write('\n');

			o.write(encode("rename from " + quotePath(oldp))); //$NON-NLS-1$
			o.write('\n');

			o.write(encode("rename to " + quotePath(newp))); //$NON-NLS-1$
			o.write('\n');
			break;

		case COPY:
			o.write(encodeASCII("similarity index " + ent.getScore() + "%")); //$NON-NLS-1$ //$NON-NLS-2$
			o.write('\n');

			o.write(encode("copy from " + quotePath(oldp))); //$NON-NLS-1$
			o.write('\n');

			o.write(encode("copy to " + quotePath(newp))); //$NON-NLS-1$
			o.write('\n');
			break;

		case MODIFY:
			if (0 < ent.getScore()) {
				o.write(encodeASCII("dissimilarity index " //$NON-NLS-1$
						+ (100 - ent.getScore()) + "%")); //$NON-NLS-1$
				o.write('\n');
			}
			break;
		}

		if (ent.getOldId() != null && !ent.getOldId().equals(ent.getNewId())) {
			formatIndexLine(o, ent);
		}
	}

	/**
	 * @param o
	 *            the stream the formatter will write line data to
	 * @param ent
	 *            the DiffEntry to create the FileHeader for
	 * @throws IOException
	 *             writing to the supplied stream failed.
	 */
	protected void formatIndexLine(OutputStream o, DiffEntry ent)
			throws IOException {
		o.write(encodeASCII("index " // //$NON-NLS-1$
				+ format(ent.getOldId()) //
				+ ".." // //$NON-NLS-1$
				+ format(ent.getNewId())));
		if (ent.getOldMode().equals(ent.getNewMode())) {
			o.write(' ');
			ent.getNewMode().copyTo(o);
		}
		o.write('\n');
	}

	private void formatOldNewPaths(ByteArrayOutputStream o, DiffEntry ent)
			throws IOException {
		if (ent.oldId.equals(ent.newId))
			return;

		final String oldp;
		final String newp;

		switch (ent.getChangeType()) {
		case ADD:
			oldp = DiffEntry.DEV_NULL;
			newp = quotePath(newPrefix + ent.getNewPath());
			break;

		case DELETE:
			oldp = quotePath(oldPrefix + ent.getOldPath());
			newp = DiffEntry.DEV_NULL;
			break;

		default:
			oldp = quotePath(oldPrefix + ent.getOldPath());
			newp = quotePath(newPrefix + ent.getNewPath());
			break;
		}

		o.write(encode("--- " + oldp + "\n")); //$NON-NLS-1$ //$NON-NLS-2$
		o.write(encode("+++ " + newp + "\n")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private int findCombinedEnd(final List<Edit> edits, final int i) {
		int end = i + 1;
		while (end < edits.size()
				&& (combineA(edits, end) || combineB(edits, end)))
			end++;
		return end - 1;
	}

	private boolean combineA(final List<Edit> e, final int i) {
		return e.get(i).getBeginA() - e.get(i - 1).getEndA() <= 2 * context;
	}

	private boolean combineB(final List<Edit> e, final int i) {
		return e.get(i).getBeginB() - e.get(i - 1).getEndB() <= 2 * context;
	}

	private static boolean end(final Edit edit, final int a, final int b) {
		return edit.getEndA() <= a && edit.getEndB() <= b;
	}
}

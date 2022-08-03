/*
 * Copyright (C) 2022, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.patch;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.InflaterInputStream;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.errors.FilterFailedException;
import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.api.errors.PatchFormatException;
import org.eclipse.jgit.attributes.Attributes;
import org.eclipse.jgit.attributes.FilterCommand;
import org.eclipse.jgit.attributes.FilterCommandRegistry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig.EolStreamType;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader.PatchType;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk.OperationType;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.NotIgnoredFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.LfsFactory;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.eclipse.jgit.util.TemporaryBuffer.LocalFile;
import org.eclipse.jgit.util.WorkTreeUpdater;
import org.eclipse.jgit.util.WorkTreeUpdater.StreamLoader;
import org.eclipse.jgit.util.WorkTreeUpdater.StreamSupplier;
import org.eclipse.jgit.util.io.BinaryDeltaInputStream;
import org.eclipse.jgit.util.io.BinaryHunkInputStream;
import org.eclipse.jgit.util.io.EolStreamTypeUtil;
import org.eclipse.jgit.util.sha1.SHA1;

/**
 * Applies a patch to files and/or to the index.
 */
public class PatchApplier {

	private final boolean inCore;
	private WorkTreeUpdater workTreeUpdater;
	@Nullable
	private final RevTree tip;

	@Nullable
	private final Repository repo;

	private final ObjectInserter inserter;
	private final ObjectReader reader;

	/**
	 * @param repo repository to apply the patch in
	 */
	public PatchApplier(Repository repo) {
		this.inCore = false;
		if (repo == null) {
			throw new NullPointerException(JGitText.get().repositoryIsRequired);
		}
		this.repo = repo;
		inserter = repo.newObjectInserter();
		reader = inserter.newReader();
		this.tip = null; // Not needed // NOSUBMIT: why?
	}

	/**
	 * @param repo   repository to apply the patch in
	 * @param tip    of the tree to apply the patch in
	 * @param oi     to be used for modifying objects
	 */
	public PatchApplier(Repository repo, RevTree tip, ObjectInserter oi) {
		this.repo = repo;
		inCore = true;
		this.tip = tip;
		inserter = oi;
		reader = oi.newReader();
	}

	/**
	 * A wrapper for returning both the applied tree ID and the applied files list.
	 */
	public static class ApplyPatchResult {

		/**
		 * The applied tree ID.
		 */
		public ObjectId appliedTree;
		/**
		 * List of the applied paths.
		 */
		public List<String> appliedPaths;
		/**
		 * List of the applied files, only valid when operating on non-bare repositories.
		 */
		public List<File> appliedFiles;
	}

	/**
	 * Applies the given patch
	 *
	 * @param patchInput the patch to apply.
	 * @return the new tree ID if index was updated.
	 * @throws PatchFormatException if the patch cannot be parsed
	 * @throws PatchApplyException  if the patch cannot be applied
	 */
	public ApplyPatchResult applyPatch(InputStream patchInput)
			throws PatchFormatException, PatchApplyException {
		ApplyPatchResult result = new ApplyPatchResult();
		result.appliedFiles = new ArrayList<>();
		try {
			final org.eclipse.jgit.patch.Patch p = new org.eclipse.jgit.patch.Patch();
			p.parse(patchInput);
			if (!p.getErrors().isEmpty()) {
				throw new PatchFormatException(p.getErrors());
			}

			if (inCore) {
				DirCache dc = DirCache.read(inserter.newReader(), tip);
				workTreeUpdater = WorkTreeUpdater.createInCoreWorkTreeUpdater(repo, dc, inserter);
			} else {
				workTreeUpdater =	WorkTreeUpdater.createWorkTreeUpdater(repo, null);
			}
 			DirCache dirCache = workTreeUpdater.getLockedDirCache();
			Set<String> modifiedPaths = new HashSet<>();
			for (org.eclipse.jgit.patch.FileHeader fh : p.getFiles()) {
				ChangeType type = fh.getChangeType();
				File f = null;
				switch (type) {
					case ADD:
						f = getFile(fh.getNewPath(), true);
						apply(fh.getNewPath(), dirCache, f, fh);
						break;
					case MODIFY:
						f = getFile(fh.getOldPath(), false);
						apply(fh.getOldPath(), dirCache, f, fh);
						break;
					case DELETE:
						f = getFile(fh.getOldPath(), false);
						TreeWalk walk = getTreeWalkForFile(fh.getNewPath(), dirCache).walk;
						if (walk != null) {
							walk.next();
							Attributes attributes = walk.getAttributes();
							workTreeUpdater.deleteFile(fh.getOldPath(), f,
									workTreeUpdater.detectCheckoutStreamType(attributes),
									walk.getSmudgeCommand(attributes));
						} else {
							workTreeUpdater.deleteFile(fh.getOldPath(), f,
									workTreeUpdater.detectCheckoutStreamType(new Attributes()), null);
						}

						break;
					case RENAME:
						f = getFile(fh.getOldPath(), false);
						File dest = getFile(fh.getNewPath(), false);
						/* this is odd: we rename the fil on the FS, but apply() will write a fresh stream
						anyway, which will overwrite if there were hunks in the patch. */
						workTreeUpdater.renameFile(f, fh.getOldPath(), dest, fh.getNewPath());
						apply(fh.getOldPath(), dirCache, dest, fh);
						break;
					case COPY:
						f = getFile(fh.getOldPath(), false);
						File target = getFile(fh.getNewPath(), false);
						workTreeUpdater.copyFile(f, target, fh.getNewPath());
						apply(fh.getOldPath(), dirCache, target, fh);
				}
				modifiedPaths.add(fh.getNewPath());
				if (fh.getChangeType() != ChangeType.COPY)
					modifiedPaths.add(fh.getOldPath());

				if (f != null) {
					result.appliedFiles.add(f);
				}
			}
			patchInput.close();
			workTreeUpdater.writeWorkTreeChanges(false);

                        /* We processed the patch. Now add things that weren't changed. */
			for (int i = 0; i < dirCache.getEntryCount(); i++) {
				DirCacheEntry dce = dirCache.getEntry(i);
				if (!modifiedPaths.contains(dce.getPathString()) || dce.getStage() != DirCacheEntry.STAGE_0)
					workTreeUpdater.addExistingToIndex(dce);
			}

			WorkTreeUpdater.Result res = workTreeUpdater.writeIndexChanges();
			result.appliedTree = res.treeId;
			result.appliedPaths = res.modifiedFiles; // NOSUBMIT - should the src of the rename be here?
		} catch (IOException e) {
			throw new PatchApplyException(
					MessageFormat.format(JGitText.get().patchApplyException, e.getMessage()), e);
		}
		return result;
	}

	private File getFile(String path, boolean create) throws PatchApplyException {
		if (inCore) {
			return null;
		}
		File f = new File(workTreeUpdater.nonNullRepo().getWorkTree(), path);
		if (create) {
			try {
				File parent = f.getParentFile();
				FileUtils.mkdirs(parent, true);
				FileUtils.createNewFile(f);
			} catch (IOException e) {
				throw new PatchApplyException(MessageFormat.format(JGitText.get().createNewFileFailed, f),
						e);
			}
		}
		return f;
	}

	private static class FileTreeWalk {

		TreeWalk walk;
		int fileTreeIdx = -1;
		int cacheTreeIdx = -1;
	}

	private FileTreeWalk getTreeWalkForFile(String path, DirCache cache) throws PatchApplyException {
		try {
			FileTreeWalk res = new FileTreeWalk();
			res.walk = inCore ? TreeWalk.forPath(repo, path, tip) : new TreeWalk(repo);
			// Use a TreeWalk with a DirCacheIterator to pick up the correct
			// clean/smudge filters.
			if (!inCore) {
				res.cacheTreeIdx = res.walk.addTree(new DirCacheIterator(cache));
				FileTreeIterator files = new FileTreeIterator(repo);
				res.fileTreeIdx = res.walk.addTree(files);
				res.walk.setFilter(AndTreeFilter.create(PathFilterGroup.createFromStrings(path),
						new NotIgnoredFilter(res.fileTreeIdx)));
				res.walk.setOperationType(OperationType.CHECKIN_OP);
				res.walk.setRecursive(true);
				files.setDirCacheIterator(res.walk, res.cacheTreeIdx);
			}
			return res;
		} catch (IOException e) {
			throw new PatchApplyException(
					MessageFormat.format(JGitText.get().patchApplyException, e.getMessage()), e);
		}
	}

	private void apply(String path, DirCache dirCache, @Nullable File f,
			org.eclipse.jgit.patch.FileHeader fh) throws PatchApplyException {
		if (PatchType.BINARY.equals(fh.getPatchType())) {
			// This patch type just says "something changed". We can't do anything with that.
			// Maybe this should return an error code, though?
			return;
		}
		try {
			FileTreeWalk fileTreeWalk = getTreeWalkForFile(path, dirCache);
			TreeWalk walk = fileTreeWalk.walk;
			boolean loaded = false;
			// CR-LF handling is determined by whether the file or the patch have CR-LF line endings.
			boolean convertCrLf = inCore ? true : needsCrLfConversion(f, fh);
			EolStreamType streamType = convertCrLf ? EolStreamType.TEXT_CRLF : EolStreamType.DIRECT;
			String smudgeFilterCommand = null;
			WorkTreeUpdater.StreamSupplier fileStreamSupplier = null;
			ObjectId fileId = ObjectId.zeroId();
			if (walk != null) {
				if (inCore) {
					// For new files, @TreeWalk.forPath is null.
					fileId = walk.getObjectId(0);
					ObjectLoader loader = LfsFactory.getInstance()
							.applySmudgeFilter(repo, reader.open(fileId, OBJ_BLOB), null);
					fileStreamSupplier = loader::openStream;
					convertCrLf = RawText.isCrLfText(loader.getBytes());
					streamType = convertCrLf ? EolStreamType.TEXT_CRLF : EolStreamType.DIRECT;
					smudgeFilterCommand = walk.getFilterCommand(Constants.ATTR_FILTER_TYPE_SMUDGE);
					loaded = true;
				} else {
					if (walk.next()) {
						// If the file on disk has no newline characters, convertCrLf
						// will be false. In that case we want to honor the normal git
						// settings.
						streamType = convertCrLf ? EolStreamType.TEXT_CRLF
								: walk.getEolStreamType(OperationType.CHECKOUT_OP);
						smudgeFilterCommand = walk.getFilterCommand(Constants.ATTR_FILTER_TYPE_SMUDGE);
						FileTreeIterator file = walk.getTree(fileTreeWalk.fileTreeIdx, FileTreeIterator.class);
						if (file != null) {
							fileId = file.getEntryObjectId();
							fileStreamSupplier = file::openEntryStream;
							loaded = true;
						}
					}
				}
			}
			if (!loaded) {
				fileStreamSupplier = inCore ? InputStream::nullInputStream : () -> new FileInputStream(f);
			}
			FileMode fileMode = fh.getNewMode() != null ? fh.getNewMode() : FileMode.MISSING;
			ContentStreamLoaders resultStreamLoaders;
			boolean safeWrite;
			if (PatchType.GIT_BINARY.equals(fh.getPatchType())) {
				resultStreamLoaders = applyBinary(path, f, fh, fileStreamSupplier, fileId);
				safeWrite = true;
			} else {
				String filterCommand =
						walk != null ? walk.getFilterCommand(Constants.ATTR_FILTER_TYPE_CLEAN) : null;
				RawText raw = getRawText(f, fileStreamSupplier, fileId, path, loaded, filterCommand,
						convertCrLf);
				resultStreamLoaders = applyText(raw, fh);
				safeWrite = false;
				if (!inCore) {
					workTreeUpdater.nonNullRepo().getFS().setExecute(f, fileMode == FileMode.EXECUTABLE_FILE);
				}
			}

			if (resultStreamLoaders != null) {
				workTreeUpdater.updateFileWithContent(resultStreamLoaders.copy1, streamType,
						smudgeFilterCommand, fh.getNewPath(), f, safeWrite);
				Instant lastModified =
						f == null ? null : workTreeUpdater.nonNullRepo().getFS().lastModifiedInstant(f);
				Attributes attributes = walk != null ? walk.getAttributes() : new Attributes();
				workTreeUpdater.insertToIndex(resultStreamLoaders.copy2,
						fh.getNewPath().getBytes(StandardCharsets.UTF_8),
						fileMode, DirCacheEntry.STAGE_0, lastModified,
						(int) resultStreamLoaders.copy2.getSize(), attributes.get(Constants.ATTR_FILTER));

				if (resultStreamLoaders.hash1 != null) {
					if (!fh.getNewId().toObjectId().equals(resultStreamLoaders.hash1.toObjectId())) {
						throw new PatchApplyException(
								MessageFormat.format(JGitText.get().applyBinaryResultOidWrong, path));
					}
				}
				if (resultStreamLoaders.hash2 != null) {
					if (!fh.getNewId().toObjectId().equals(resultStreamLoaders.hash2.toObjectId())) {
						throw new PatchApplyException(
								MessageFormat.format(JGitText.get().applyBinaryResultOidWrong, path));
					}
				}
			}
		} catch (IOException | UnsupportedOperationException e) {
			throw new PatchApplyException(
					MessageFormat.format(JGitText.get().patchApplyException, e.getMessage()), e);
		}
	}

	/**
	 * Gets the raw text of the given file.
	 *
	 * @param file to read from
	 * @param fileStreamSupplier if loaded, the stream of the file content
	 * @param fileId of the file
	 * @param path of the file
	 * @param loaded whether the file was loaded by a {@link TreeWalk}
	 * @param filterCommand for reading the file content
	 * @param convertCrLf whether a CR-LF conversion is needed
	 * @return the result raw text
	 * @throws IOException in case of filtering issues
	 */
	private RawText getRawText(@Nullable File file, StreamSupplier fileStreamSupplier,
			ObjectId fileId, String path, boolean loaded, String filterCommand, boolean convertCrLf)
			throws IOException {
		if (loaded) {
			// Can't use file.openEntryStream() as we cannot control its CR-LF conversion.
			try (InputStream input = filterClean(repo, path, fileStreamSupplier.load(), convertCrLf,
					filterCommand)) {
				return new RawText(org.eclipse.jgit.util.IO.readWholeStream(input, 0).array());
			}
		}
		if (convertCrLf) {
			try (InputStream input = EolStreamTypeUtil.wrapInputStream(fileStreamSupplier.load(),
					EolStreamType.TEXT_LF)) {
				return new RawText(org.eclipse.jgit.util.IO.readWholeStream(input, 0).array());
			}
		}
		if (inCore && fileId.equals(ObjectId.zeroId())) {
			return new RawText(new byte[]{});
		}
		return new RawText(file);
	}

	private InputStream filterClean(Repository repository, String path, InputStream fromFile,
			boolean convertCrLf, String filterCommand) throws IOException {
		InputStream input = fromFile;
		if (convertCrLf) {
			input = EolStreamTypeUtil.wrapInputStream(input, EolStreamType.TEXT_LF);
		}
		if (org.eclipse.jgit.util.StringUtils.isEmptyOrNull(filterCommand)) {
			return input;
		}
		if (FilterCommandRegistry.isRegistered(filterCommand)) {
			LocalFile buffer = new org.eclipse.jgit.util.TemporaryBuffer.LocalFile(null,
					workTreeUpdater.getInCoreFileSizeLimit());
			FilterCommand command = FilterCommandRegistry.createFilterCommand(filterCommand, repository,
					input, buffer);
			while (command.run() != -1) {
				// loop as long as command.run() tells there is work to do
			}
			return buffer.openInputStreamWithAutoDestroy();
		}
		org.eclipse.jgit.util.FS fs = repository.getFS();
		ProcessBuilder filterProcessBuilder = fs.runInShell(filterCommand, new String[0]);
		filterProcessBuilder.directory(repository.getWorkTree());
		filterProcessBuilder.environment()
				.put(Constants.GIT_DIR_KEY, repository.getDirectory().getAbsolutePath());
		ExecutionResult result;
		try {
			result = fs.execute(filterProcessBuilder, input);
		} catch (IOException | InterruptedException e) {
			throw new IOException(new FilterFailedException(e, filterCommand, path));
		}
		int rc = result.getRc();
		if (rc != 0) {
			throw new IOException(
					new FilterFailedException(rc, filterCommand, path, result.getStdout().toByteArray(4096),
							org.eclipse.jgit.util.RawParseUtils.decode(result.getStderr().toByteArray(4096))));
		}
		return result.getStdout().openInputStreamWithAutoDestroy();
	}

	private boolean needsCrLfConversion(File f, org.eclipse.jgit.patch.FileHeader fileHeader)
			throws IOException {
		if (PatchType.GIT_BINARY.equals(fileHeader.getPatchType())) {
			return false;
		}
		if (!hasCrLf(fileHeader)) {
			try (InputStream input = new FileInputStream(f)) {
				return RawText.isCrLfText(input);
			}
		}
		return false;
	}

	private static boolean hasCrLf(org.eclipse.jgit.patch.FileHeader fileHeader) {
		if (PatchType.GIT_BINARY.equals(fileHeader.getPatchType())) {
			return false;
		}
		for (org.eclipse.jgit.patch.HunkHeader header : fileHeader.getHunks()) {
			byte[] buf = header.getBuffer();
			int hunkEnd = header.getEndOffset();
			int lineStart = header.getStartOffset();
			while (lineStart < hunkEnd) {
				int nextLineStart = RawParseUtils.nextLF(buf, lineStart);
				if (nextLineStart > hunkEnd) {
					nextLineStart = hunkEnd;
				}
				if (nextLineStart <= lineStart) {
					break;
				}
				if (nextLineStart - lineStart > 1) {
					char first = (char) (buf[lineStart] & 0xFF);
					if (first == ' ' || first == '-') {
						// It's an old line. Does it end in CR-LF?
						if (buf[nextLineStart - 2] == '\r') {
							return true;
						}
					}
				}
				lineStart = nextLineStart;
			}
		}
		return false;
	}

	private void initHash(SHA1 hash, long size) {
		hash.update(Constants.encodedTypeString(Constants.OBJ_BLOB));
		hash.update((byte) ' ');
		hash.update(Constants.encodeASCII(size));
		hash.update((byte) 0);
	}

	private ObjectId hash(File f) throws IOException {
		SHA1 hash = SHA1.newInstance();
		initHash(hash, f.length());
		try (InputStream input = new FileInputStream(f)) {
			byte[] buf = new byte[8192];
			int n;
			while ((n = input.read(buf)) >= 0) {
				hash.update(buf, 0, n);
			}
		}
		return hash.toObjectId();
	}

	private void checkOid(ObjectId baseId, ObjectId id, ChangeType type, File f, String path)
			throws PatchApplyException, IOException {
		boolean hashOk = false;
		if (id != null) {
			hashOk = baseId.equals(id);
			if (!hashOk && ChangeType.ADD.equals(type) && ObjectId.zeroId().equals(baseId)) {
				// We create a new file. The OID of an empty file is not the zero id!
				hashOk = Constants.EMPTY_BLOB_ID.equals(id);
			}
		} else if (!inCore) {
			if (ObjectId.zeroId().equals(baseId)) {
				// File empty is OK.
				hashOk = !f.exists() || f.length() == 0;
			} else {
				hashOk = baseId.equals(hash(f));
			}
		}
		if (!hashOk) {
			throw new PatchApplyException(
					MessageFormat.format(JGitText.get().applyBinaryBaseOidWrong, path));
		}
	}

	/**
	 * Applying the patch to both the working tree and the index require 2 different stream loaders,
	 * since input streams can't be reused.
	 */
	private static class ContentStreamLoaders {

		StreamLoader copy1;
		StreamLoader copy2;

		// For binary content, we check the content hash.
		SHA1 hash1;
		SHA1 hash2;

		ContentStreamLoaders(StreamLoader copy1, StreamLoader copy2) {
			this.copy1 = copy1;
			this.copy2 = copy2;
		}

		ContentStreamLoaders(StreamLoader copy1, SHA1 hash1, StreamLoader copy2, SHA1 hash2) {
			this(copy1, copy2);
			this.hash1 = hash1;
			this.hash2 = hash2;
		}
	}

	private ContentStreamLoaders applyBinary(String path, File f,
			org.eclipse.jgit.patch.FileHeader fh, StreamSupplier inputSupplier, ObjectId id)
			throws PatchApplyException, IOException, UnsupportedOperationException {
		if (inCore) {
			throw new UnsupportedOperationException(JGitText.get().applyBinaryForInCoreNotSupported);
		}
		if (!fh.getOldId().isComplete() || !fh.getNewId().isComplete()) {
			throw new PatchApplyException(
					MessageFormat.format(JGitText.get().applyBinaryOidTooShort, path));
		}
		org.eclipse.jgit.patch.BinaryHunk hunk = fh.getForwardBinaryHunk();
		// A BinaryHunk has the start at the "literal" or "delta" token. Data
		// starts on the next line.
		int start = RawParseUtils.nextLF(hunk.getBuffer(), hunk.getStartOffset());
		int length = hunk.getEndOffset() - start;
		SHA1 hash1 = SHA1.newInstance();
		SHA1 hash2 = SHA1.newInstance();
		switch (hunk.getType()) {
			case LITERAL_DEFLATED:
				// This just overwrites the file. We need to check the hash of
				// the base.
				checkOid(fh.getOldId().toObjectId(), id, fh.getChangeType(), f, path);
				initHash(hash1, hunk.getSize());
				InputStream inflated1 = new SHA1InputStream(hash1, new InflaterInputStream(
						new BinaryHunkInputStream(new ByteArrayInputStream(hunk.getBuffer(), start, length))));
				initHash(hash2, hunk.getSize());
				InputStream inflated2 = new SHA1InputStream(hash2, new InflaterInputStream(
						new BinaryHunkInputStream(new ByteArrayInputStream(hunk.getBuffer(), start, length))));
				return new ContentStreamLoaders(
						WorkTreeUpdater.createStreamLoader(() -> inflated1, hunk.getSize()), hash1,
						WorkTreeUpdater.createStreamLoader(() -> inflated2, hunk.getSize()), hash2);
			case DELTA_DEFLATED:
				// Unfortunately delta application needs random access to the
				// base to construct the result.
				byte[] base;
				try (InputStream in = inputSupplier.load()) {
					base = IO.readWholeStream(in, 0).array();
				}
				// At least stream the result!
				BinaryDeltaInputStream input1 = new BinaryDeltaInputStream(base, new InflaterInputStream(
						new BinaryHunkInputStream(new ByteArrayInputStream(hunk.getBuffer(), start, length))));
				BinaryDeltaInputStream input2 = new BinaryDeltaInputStream(base, new InflaterInputStream(
						new BinaryHunkInputStream(new ByteArrayInputStream(hunk.getBuffer(), start, length))));
				long finalSize = input1.getExpectedResultSize();
				initHash(hash1, finalSize);
				initHash(hash2, finalSize);
				return new ContentStreamLoaders(
						WorkTreeUpdater.createStreamLoader(() -> new SHA1InputStream(hash1, input1), finalSize),
						hash1,
						WorkTreeUpdater.createStreamLoader(() -> new SHA1InputStream(hash2, input2), finalSize),
						hash2);
			default:
				throw new UnsupportedOperationException(
						MessageFormat.format(JGitText.get().applyBinaryPatchTypeNotSupported,
								hunk.getType().name()));
		}
	}

	private ContentStreamLoaders applyText(RawText rt, org.eclipse.jgit.patch.FileHeader fh)
			throws IOException, PatchApplyException {
		List<ByteBuffer> oldLines = new ArrayList<>(rt.size());
		for (int i = 0; i < rt.size(); i++) {
			oldLines.add(rt.getRawString(i));
		}
		List<ByteBuffer> newLines = new ArrayList<>(oldLines);
		int afterLastHunk = 0;
		int lineNumberShift = 0;
		int lastHunkNewLine = -1;
		for (org.eclipse.jgit.patch.HunkHeader hh : fh.getHunks()) {
			// We assume hunks to be ordered
			if (hh.getNewStartLine() <= lastHunkNewLine) {
				throw new PatchApplyException(MessageFormat.format(JGitText.get().patchApplyException, hh));
			}
			lastHunkNewLine = hh.getNewStartLine();

			byte[] b = new byte[hh.getEndOffset() - hh.getStartOffset()];
			System.arraycopy(hh.getBuffer(), hh.getStartOffset(), b, 0, b.length);
			if (inCore && hasCrLf(fh)) {
				b = new String(b, StandardCharsets.UTF_8).replaceAll("\r\n", "\n")
						.getBytes(StandardCharsets.UTF_8);
			}
			RawText hrt = new RawText(b);

			List<ByteBuffer> hunkLines = new ArrayList<>(hrt.size());
			for (int i = 0; i < hrt.size(); i++) {
				hunkLines.add(hrt.getRawString(i));
			}

			if (hh.getNewStartLine() == 0) {
				// Must be the single hunk for clearing all content
				if (fh.getHunks().size() == 1 && canApplyAt(hunkLines, newLines, 0)) {
					newLines.clear();
					break;
				}
				throw new PatchApplyException(MessageFormat.format(JGitText.get().patchApplyException, hh));
			}
			// Hunk lines as reported by the hunk may be off, so don't rely on
			// them.
			int applyAt = hh.getNewStartLine() - 1 + lineNumberShift;
			// But they definitely should not go backwards.
			if (applyAt < afterLastHunk && lineNumberShift < 0) {
				applyAt = hh.getNewStartLine() - 1;
				lineNumberShift = 0;
			}
			if (applyAt < afterLastHunk) {
				throw new PatchApplyException(MessageFormat.format(JGitText.get().patchApplyException, hh));
			}
			boolean applies = false;
			int oldLinesInHunk = hh.getLinesContext() + hh.getOldImage().getLinesDeleted();
			if (oldLinesInHunk <= 1) {
				// Don't shift hunks without context lines. Just try the
				// position corrected by the current lineNumberShift, and if
				// that fails, the position recorded in the hunk header.
				applies = canApplyAt(hunkLines, newLines, applyAt);
				if (!applies && lineNumberShift != 0) {
					applyAt = hh.getNewStartLine() - 1;
					applies = applyAt >= afterLastHunk && canApplyAt(hunkLines, newLines, applyAt);
				}
			} else {
				int maxShift = applyAt - afterLastHunk;
				for (int shift = 0; shift <= maxShift; shift++) {
					if (canApplyAt(hunkLines, newLines, applyAt - shift)) {
						applies = true;
						applyAt -= shift;
						break;
					}
				}
				if (!applies) {
					// Try shifting the hunk downwards
					applyAt = hh.getNewStartLine() - 1 + lineNumberShift;
					maxShift = newLines.size() - applyAt - oldLinesInHunk;
					for (int shift = 1; shift <= maxShift; shift++) {
						if (canApplyAt(hunkLines, newLines, applyAt + shift)) {
							applies = true;
							applyAt += shift;
							break;
						}
					}
				}
			}
			if (!applies) {
				throw new PatchApplyException(MessageFormat.format(JGitText.get().patchApplyException, hh));
			}
			// Hunk applies at applyAt. Apply it, and update afterLastHunk and
			// lineNumberShift
			lineNumberShift = applyAt - hh.getNewStartLine() + 1;
			int sz = hunkLines.size();
			for (int j = 1; j < sz; j++) {
				ByteBuffer hunkLine = hunkLines.get(j);
				if (!hunkLine.hasRemaining()) {
					// Completely empty line; accept as empty context line
					applyAt++;
					continue;
				}
				switch (hunkLine.array()[hunkLine.position()]) {
					case ' ':
						applyAt++;
						break;
					case '-':
						newLines.remove(applyAt);
						break;
					case '+':
						newLines.add(applyAt++, slice(hunkLine, 1));
						break;
					default:
						break;
				}
			}
			afterLastHunk = applyAt;
		}
		if (!isNoNewlineAtEndOfFile(fh)) {
			newLines.add(null);
		}
		if (!rt.isMissingNewlineAtEnd()) {
			oldLines.add(null);
		}

		// We could check if old == new, but the short-circuiting complicates logic for inCore patching,
		// so just write the new thing regardless.
		TemporaryBuffer buffer = new TemporaryBuffer.LocalFile(null);
		try (OutputStream out = buffer) {
			for (Iterator<ByteBuffer> l = newLines.iterator(); l.hasNext(); ) {
				ByteBuffer line = l.next();
				if (line == null) {
					// Must be the marker for the final newline
					break;
				}
				out.write(line.array(), line.position(), line.remaining());
				if (l.hasNext()) {
					out.write('\n');
				}
			}
			return new ContentStreamLoaders(
					WorkTreeUpdater.createStreamLoader(buffer::openInputStream, buffer.length()),
					WorkTreeUpdater.createStreamLoader(buffer::openInputStream, buffer.length()));
		}
	}

	private boolean canApplyAt(List<ByteBuffer> hunkLines, List<ByteBuffer> newLines, int line) {
		int sz = hunkLines.size();
		int limit = newLines.size();
		int pos = line;
		for (int j = 1; j < sz; j++) {
			ByteBuffer hunkLine = hunkLines.get(j);
			if (!hunkLine.hasRemaining()) {
				// Empty line. Accept as empty context line.
				if (pos >= limit || newLines.get(pos).hasRemaining()) {
					return false;
				}
				pos++;
				continue;
			}
			switch (hunkLine.array()[hunkLine.position()]) {
				case ' ':
				case '-':
					if (pos >= limit || !newLines.get(pos).equals(slice(hunkLine, 1))) {
						return false;
					}
					pos++;
					break;
				default:
					break;
			}
		}
		return true;
	}

	private ByteBuffer slice(ByteBuffer b, int off) {
		int newOffset = b.position() + off;
		return ByteBuffer.wrap(b.array(), newOffset, b.limit() - newOffset);
	}

	private boolean isNoNewlineAtEndOfFile(org.eclipse.jgit.patch.FileHeader fh) {
		List<? extends org.eclipse.jgit.patch.HunkHeader> hunks = fh.getHunks();
		if (hunks == null || hunks.isEmpty()) {
			return false;
		}
		org.eclipse.jgit.patch.HunkHeader lastHunk = hunks.get(hunks.size() - 1);
		byte[] buf = new byte[lastHunk.getEndOffset() - lastHunk.getStartOffset()];
		System.arraycopy(lastHunk.getBuffer(), lastHunk.getStartOffset(), buf, 0, buf.length);
		RawText lhrt = new RawText(buf);
		return lhrt.getString(lhrt.size() - 1).equals("\\ No newline at end of file"); // $NON-NLS-1$
	}

	/**
	 * An {@link InputStream} that updates a {@link SHA1} on every byte read. The hash is supposed to
	 * have been initialized before reading starts.
	 */
	private static class SHA1InputStream extends InputStream {

		private final SHA1 hash;

		private final InputStream in;

		SHA1InputStream(SHA1 hash, InputStream in) {
			this.hash = hash;
			this.in = in;
		}

		@Override
		public int read() throws IOException {
			int b = in.read();
			if (b >= 0) {
				hash.update((byte) b);
			}
			return b;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int n = in.read(b, off, len);
			if (n > 0) {
				hash.update(b, off, n);
			}
			return n;
		}

		@Override
		public void close() throws IOException {
			in.close();
		}
	}
}

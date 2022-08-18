/*
 * Copyright (C) 2022, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.attributes.Attribute;
import org.eclipse.jgit.attributes.Attributes;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuildIterator;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheCheckout.CheckoutMetadata;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.IndexWriteException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig.EolStreamType;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk.OperationType;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;
import org.eclipse.jgit.util.LfsFactory.LfsInputStream;
import org.eclipse.jgit.util.io.EolStreamTypeUtil;

/**
 * Handles work tree updates on both the checkout and the index.
 * <p>
 * You should use a single instance for all of your file changes. In case of an
 * error, make sure your instance is released, and initiate a new one if
 * necessary.
 *
 * @since 6.3
 */
public class WorkTreeUpdater implements Closeable {

	/**
	 * The result of writing the index changes.
	 */
	public static class Result {

		private final List<String> modifiedFiles = new LinkedList<>();


		private final List<String> failedToDelete = new LinkedList<>();

		private ObjectId treeId = null;

		/**
		 * @return Modified tree ID if any, or null otherwise.
		 */
		public ObjectId getTreeId() {
			return treeId;
		}

		/**
		 * @return Files that couldn't be deleted.
		 */
		public List<String> getFailedToDelete() {
			return failedToDelete;
		}

		/**
		 * @return Files modified during this operation.
		 */
		public List<String> getModifiedFiles() {
			return modifiedFiles;
		}
	}

	Result result = new Result();

	/**
	 * The repository this handler operates on.
	 */
	@Nullable
	private final Repository repo;

	/**
	 * Set to true if this operation should work in-memory. The repo's dircache
	 * and workingtree are not touched by this method. Eventually needed files
	 * are created as temporary files and a new empty, in-memory dircache will
	 * be used instead the repo's one. Often used for bare repos where the repo
	 * doesn't even have a workingtree and dircache.
	 */
	private final boolean inCore;

	private final ObjectInserter inserter;

	private final ObjectReader reader;

	private DirCache dirCache;

	private boolean implicitDirCache = false;

	/**
	 * Builder to update the dir cache during this operation.
	 */
	private DirCacheBuilder builder;

	/**
	 * The {@link WorkingTreeOptions} are needed to determine line endings for
	 * affected files.
	 */
	private WorkingTreeOptions workingTreeOptions;

	/**
	 * The size limit (bytes) which controls a file to be stored in {@code Heap}
	 * or {@code LocalFile} during the operation.
	 */
	private int inCoreFileSizeLimit;

	/**
	 * If the operation has nothing to do for a file but check it out at the end
	 * of the operation, it can be added here.
	 */
	private final Map<String, DirCacheEntry> toBeCheckedOut = new HashMap<>();

	/**
	 * Files in this list will be deleted from the local copy at the end of the
	 * operation.
	 */
	private final TreeMap<String, File> toBeDeleted = new TreeMap<>();

	/**
	 * Keeps {@link CheckoutMetadata} for {@link #checkout()}.
	 */
	private Map<String, CheckoutMetadata> checkoutMetadataByPath;

	/**
	 * Keeps {@link CheckoutMetadata} for {@link #revertModifiedFiles()}.
	 */
	private Map<String, CheckoutMetadata> cleanupMetadataByPath;

	/**
	 * Whether the changes were successfully written.
	 */
	private boolean indexChangesWritten;

	/**
	 * @param repo
	 *            the {@link Repository}.
	 * @param dirCache
	 *            if set, use the provided dir cache. Otherwise, use the default
	 *            repository one
	 */
	private WorkTreeUpdater(Repository repo, DirCache dirCache) {
		this.repo = repo;
		this.dirCache = dirCache;

		this.inCore = false;
		this.inserter = repo.newObjectInserter();
		this.reader = inserter.newReader();
		Config config = repo.getConfig();
		this.workingTreeOptions = config.get(WorkingTreeOptions.KEY);
		this.inCoreFileSizeLimit = getInCoreFileSizeLimit(config);
		this.checkoutMetadataByPath = new HashMap<>();
		this.cleanupMetadataByPath = new HashMap<>();
	}

	/**
	 * Creates a new {@link WorkTreeUpdater} for the given repository.
	 *
	 * @param repo
	 *            the {@link Repository}.
	 * @param dirCache
	 *            if set, use the provided dir cache. Otherwise, use the default
	 *            repository one
	 * @return the {@link WorkTreeUpdater}.
	 */
	public static WorkTreeUpdater createWorkTreeUpdater(Repository repo,
			DirCache dirCache) {
		return new WorkTreeUpdater(repo, dirCache);
	}

	/**
	 * @param repo
	 *            the {@link Repository}.
	 * @param dirCache
	 *            if set, use the provided dir cache. Otherwise, creates a new
	 *            one
	 * @param oi
	 *            to use for writing the modified objects with.
	 */
	private WorkTreeUpdater(Repository repo, DirCache dirCache,
			ObjectInserter oi) {
		this.repo = repo;
		this.dirCache = dirCache;
		this.inserter = oi;

		this.inCore = true;
		this.reader = oi.newReader();
		if (repo != null) {
			this.inCoreFileSizeLimit = getInCoreFileSizeLimit(repo.getConfig());
		}
	}

	/**
	 * Creates a new {@link WorkTreeUpdater} that works in memory only.
	 *
	 * @param repo
	 *            the {@link Repository}.
	 * @param dirCache
	 *            if set, use the provided dir cache. Otherwise, creates a new
	 *            one
	 * @param oi
	 *            to use for writing the modified objects with.
	 * @return the {@link WorkTreeUpdater}
	 */
	public static WorkTreeUpdater createInCoreWorkTreeUpdater(Repository repo,
			DirCache dirCache, ObjectInserter oi) {
		return new WorkTreeUpdater(repo, dirCache, oi);
	}

	/**
	 * Something that can supply an {@link InputStream}.
	 */
	public interface StreamSupplier {

		/**
		 * Loads the input stream.
		 *
		 * @return the loaded stream
		 * @throws IOException
		 *             if any reading error occurs
		 */
		InputStream load() throws IOException;
	}

	/**
	 * We want to use DirCacheCheckout for its CR-LF and smudge filters, but DirCacheCheckout needs an
	 * ObjectLoader rather than InputStream. This class provides a bridge between the two.
	 */
	public static class StreamLoader extends ObjectLoader {

		private final StreamSupplier data;

		private final long size;

		private StreamLoader(StreamSupplier data, long length) {
			this.data = data;
			this.size = length;
		}

		@Override
		public int getType() {
			return Constants.OBJ_BLOB;
		}

		@Override
		public long getSize() {
			return size;
		}

		@Override
		public boolean isLarge() {
			return true;
		}

		@Override
		public byte[] getCachedBytes() throws LargeObjectException {
			throw new LargeObjectException();
		}

		@Override
		public ObjectStream openStream() throws IOException {
			return new ObjectStream.Filter(getType(), getSize(),
					new BufferedInputStream(data.load()));
		}
	}

	/**
	 * Creates stream loader for the given supplier.
	 *
	 * @param supplier
	 *            to wrap
	 * @param length
	 *            of the supplied content
	 * @return the result stream loader
	 */
	public static StreamLoader createStreamLoader(StreamSupplier supplier,
			long length) {
		return new StreamLoader(supplier, length);
	}

	private static int getInCoreFileSizeLimit(Config config) {
		return config.getInt(ConfigConstants.CONFIG_MERGE_SECTION,
				ConfigConstants.CONFIG_KEY_IN_CORE_LIMIT, 10 << 20);
	}

	/**
	 * Gets the size limit for in-core files in this config.
	 *
	 * @return the size
	 */
	public int getInCoreFileSizeLimit() {
		return inCoreFileSizeLimit;
	}

	/**
	 * Gets dir cache for the repo. Locked if not inCore.
	 *
	 * @return the result dir cache
	 * @throws IOException
	 *             is case the dir cache cannot be read
	 */
	public DirCache getLockedDirCache() throws IOException {
		if (dirCache == null) {
			implicitDirCache = true;
			if (inCore) {
				dirCache = DirCache.newInCore();
			} else {
				dirCache = nonNullRepo().lockDirCache();
			}
		}
		if (builder == null) {
			builder = dirCache.builder();
		}
		return dirCache;
	}

	/**
	 * Creates a {@link DirCacheBuildIterator} for the builder of this
	 * {@link WorkTreeUpdater}.
	 *
	 * @return the {@link DirCacheBuildIterator}
	 */
	public DirCacheBuildIterator createDirCacheBuildIterator() {
		return new DirCacheBuildIterator(builder);
	}

	/**
	 * Writes the changes to the working tree (but not to the index).
	 *
	 * @param shouldCheckoutTheirs
	 *            before committing the changes
	 * @throws IOException
	 *             if any of the writes fail
	 */
	public void writeWorkTreeChanges(boolean shouldCheckoutTheirs)
			throws IOException {
		handleDeletedFiles();

		if (inCore) {
			builder.finish();
			return;
		}
		if (shouldCheckoutTheirs) {
			// No problem found. The only thing left to be done is to
			// check out all files from "theirs" which have been selected to
			// go into the new index.
			checkout();
		}

		// All content operations are successfully done. If we can now write the
		// new index we are on quite safe ground. Even if the checkout of
		// files coming from "theirs" fails the user can work around such
		// failures by checking out the index again.
		if (!builder.commit()) {
			revertModifiedFiles();
			throw new IndexWriteException();
		}
	}

	/**
	 * Writes the changes to the index.
	 *
	 * @return the {@link Result} of the operation.
	 * @throws IOException
	 *             if any of the writes fail
	 */
	public Result writeIndexChanges() throws IOException {
		result.treeId = getLockedDirCache().writeTree(inserter);
		indexChangesWritten = true;
		return result;
	}

	/**
	 * Adds a {@link DirCacheEntry} for direct checkout and remembers its
	 * {@link CheckoutMetadata}.
	 *
	 * @param path
	 *            of the entry
	 * @param entry
	 *            to add
	 * @param cleanupStreamType
	 *            to use for the cleanup metadata
	 * @param cleanupSmudgeCommand
	 *            to use for the cleanup metadata
	 * @param checkoutStreamType
	 *            to use for the checkout metadata
	 * @param checkoutSmudgeCommand
	 *            to use for the checkout metadata
	 */
	public void addToCheckout(String path, DirCacheEntry entry,
			EolStreamType cleanupStreamType, String cleanupSmudgeCommand,
			EolStreamType checkoutStreamType, String checkoutSmudgeCommand) {
		if (entry != null) {
			// In some cases, we just want to add the metadata.
			toBeCheckedOut.put(path, entry);
		}
		addCheckoutMetadata(cleanupMetadataByPath, path, cleanupStreamType,
				cleanupSmudgeCommand);
		addCheckoutMetadata(checkoutMetadataByPath, path, checkoutStreamType,
				checkoutSmudgeCommand);
	}

	/**
	 * Gets a map which maps the paths of files which have to be checked out
	 * because the operation created new fully-merged content for this file into
	 * the index.
	 * <p>
	 * This means: the operation wrote a new stage 0 entry for this path.
	 * </p>
	 *
	 * @return the map
	 */
	public Map<String, DirCacheEntry> getToBeCheckedOut() {
		return toBeCheckedOut;
	}

	/**
	 * Remembers the given file to be deleted.
	 * <p>
	 * Note the actual deletion is only done in {@link #writeWorkTreeChanges}.
	 *
	 * @param path
	 *            of the file to be deleted
	 * @param file
	 *            to be deleted
	 * @param streamType
	 *            to use for cleanup metadata
	 * @param smudgeCommand
	 *            to use for cleanup metadata
	 */
	public void deleteFile(String path, File file, EolStreamType streamType,
			String smudgeCommand) {
		toBeDeleted.put(path, file);
		if (file != null && file.isFile()) {
			addCheckoutMetadata(cleanupMetadataByPath, path, streamType,
					smudgeCommand);
		}
	}

	/**
	 * Remembers the {@link CheckoutMetadata} for the given path; it may be
	 * needed in {@link #checkout()} or in {@link #revertModifiedFiles()}.
	 *
	 * @param map
	 *            to add the metadata to
	 * @param path
	 *            of the current node
	 * @param streamType
	 *            to use for the metadata
	 * @param smudgeCommand
	 *            to use for the metadata
	 */
	private void addCheckoutMetadata(Map<String, CheckoutMetadata> map,
			String path, EolStreamType streamType, String smudgeCommand) {
		if (inCore || map == null) {
			return;
		}
		map.put(path, new CheckoutMetadata(streamType, smudgeCommand));
	}

	/**
	 * Detects if CRLF conversion has been configured.
	 * <p>
	 * </p>
	 * See {@link EolStreamTypeUtil#detectStreamType} for more info.
	 *
	 * @param attributes
	 *            of the file for which the type is to be detected
	 * @return the detected type
	 */
	public EolStreamType detectCheckoutStreamType(Attributes attributes) {
		if (inCore) {
			return null;
		}
		return EolStreamTypeUtil.detectStreamType(OperationType.CHECKOUT_OP,
				workingTreeOptions, attributes);
	}

	private void handleDeletedFiles() {
		// Iterate in reverse so that "folder/file" is deleted before
		// "folder". Otherwise, this could result in a failing path because
		// of a non-empty directory, for which delete() would fail.
		for (String path : toBeDeleted.descendingKeySet()) {
			File file = inCore ? null : toBeDeleted.get(path);
			if (file != null && !file.delete()) {
				if (!file.isDirectory()) {
					result.failedToDelete.add(path);
				}
			}
		}
	}

	/**
	 * Marks the given path as modified in the operation.
	 *
	 * @param path
	 *            to mark as modified
	 */
	public void markAsModified(String path) {
		result.modifiedFiles.add(path);
	}

	/**
	 * Gets the list of files which were modified in this operation.
	 *
	 * @return the list
	 */
	public List<String> getModifiedFiles() {
		return result.modifiedFiles;
	}

	private void checkout() throws NoWorkTreeException, IOException {
		for (Map.Entry<String, DirCacheEntry> entry : toBeCheckedOut
				.entrySet()) {
			DirCacheEntry dirCacheEntry = entry.getValue();
			if (dirCacheEntry.getFileMode() == FileMode.GITLINK) {
				new File(nonNullRepo().getWorkTree(), entry.getKey())
						.mkdirs();
			} else {
				DirCacheCheckout.checkoutEntry(repo, dirCacheEntry, reader,
						false, checkoutMetadataByPath.get(entry.getKey()),
						workingTreeOptions);
				result.modifiedFiles.add(entry.getKey());
			}
		}
	}

	/**
	 * Reverts any uncommitted changes in the worktree. We know that for all
	 * modified files the old content was in the old index and the index
	 * contained only stage 0. In case of inCore operation just clear the
	 * history of modified files.
	 *
	 * @throws IOException
	 *             in case the cleaning up failed
	 */
	public void revertModifiedFiles() throws IOException {
		if (inCore) {
			result.modifiedFiles.clear();
			return;
		}
		if (indexChangesWritten) {
			return;
		}
		for (String path : result.modifiedFiles) {
			DirCacheEntry entry = dirCache.getEntry(path);
			if (entry != null) {
				DirCacheCheckout.checkoutEntry(repo, entry, reader, false,
						cleanupMetadataByPath.get(path), workingTreeOptions);
			}
		}
	}

	@Override
	public void close() throws IOException {
		if (implicitDirCache) {
			dirCache.unlock();
		}
	}

	/**
	 * Updates the file in the checkout with the given content.
	 *
	 * @param resultStreamLoader
	 *            with the content to be updated
	 * @param streamType
	 *            for parsing the content
	 * @param smudgeCommand
	 *            for formatting the content
	 * @param path
	 *            of the file to be updated
	 * @param file
	 *            to be updated
	 * @param safeWrite
	 *            whether the content should be written to a buffer first
	 * @throws IOException
	 *             if the file cannot be updated
	 */
	public void updateFileWithContent(StreamLoader resultStreamLoader,
			EolStreamType streamType, String smudgeCommand, String path,
			File file, boolean safeWrite) throws IOException {
		if (inCore) {
			return;
		}
		CheckoutMetadata metadata = new CheckoutMetadata(streamType,
				smudgeCommand);
		if (safeWrite) {
			// Write to a buffer and copy to the file only if everything was
			// fine.
			TemporaryBuffer buffer = new TemporaryBuffer.LocalFile(null);
			try {
				try (TemporaryBuffer buf = buffer) {
					DirCacheCheckout.getContent(repo, path, metadata,
							resultStreamLoader, workingTreeOptions, buf);
				}
				try (InputStream bufIn = buffer.openInputStream()) {
					Files.copy(bufIn, file.toPath(),
							StandardCopyOption.REPLACE_EXISTING);
				}
			} finally {
				buffer.destroy();
			}
			return;
		}
		try (OutputStream outputStream = new FileOutputStream(file)) {
			DirCacheCheckout.getContent(repo, path, metadata,
					resultStreamLoader, workingTreeOptions, outputStream);
		}
	}

	/**
	 * Creates a path with the given content, and adds it to the specified stage
	 * to the index builder.
	 *
	 * @param inputStream
	 *            with the content to be updated
	 * @param path
	 *            of the file to be updated
	 * @param fileMode
	 *            of the modified file
	 * @param entryStage
	 *            of the new entry
	 * @param lastModified
	 *            instant of the modified file
	 * @param len
	 *            of the content
	 * @param lfsAttribute
	 *            for checking for LFS enablement
	 * @return the entry which was added to the index
	 * @throws IOException
	 *             if inserting the content fails
	 */
	public DirCacheEntry insertToIndex(InputStream inputStream, byte[] path,
			FileMode fileMode, int entryStage, Instant lastModified, int len,
			Attribute lfsAttribute) throws IOException {
		StreamLoader contentLoader = createStreamLoader(() -> inputStream, len);
		return insertToIndex(contentLoader, path, fileMode, entryStage,
				lastModified, len, lfsAttribute);
	}

	/**
	 * Creates a path with the given content, and adds it to the specified stage
	 * to the index builder.
	 *
	 * @param resultStreamLoader
	 *            with the content to be updated
	 * @param path
	 *            of the file to be updated
	 * @param fileMode
	 *            of the modified file
	 * @param entryStage
	 *            of the new entry
	 * @param lastModified
	 *            instant of the modified file
	 * @param len
	 *            of the content
	 * @param lfsAttribute
	 *            for checking for LFS enablement
	 * @return the entry which was added to the index
	 * @throws IOException
	 *             if inserting the content fails
	 */
	public DirCacheEntry insertToIndex(StreamLoader resultStreamLoader,
			byte[] path, FileMode fileMode, int entryStage,
			Instant lastModified, int len, Attribute lfsAttribute)
			throws IOException {
		return addExistingToIndex(
				insertResult(resultStreamLoader, lfsAttribute), path, fileMode,
				entryStage, lastModified, len);
	}

	/**
	 * Adds a path with the specified stage to the index builder.
	 *
	 * @param objectId
	 *            of the existing object to add
	 * @param path
	 *            of the modified file
	 * @param fileMode
	 *            of the modified file
	 * @param entryStage
	 *            of the new entry
	 * @param lastModified
	 *            instant of the modified file
	 * @param len
	 *            of the modified file content
	 * @return the entry which was added to the index
	 */
	public DirCacheEntry addExistingToIndex(ObjectId objectId, byte[] path,
			FileMode fileMode, int entryStage, Instant lastModified, int len) {
		DirCacheEntry dce = new DirCacheEntry(path, entryStage);
		dce.setFileMode(fileMode);
		if (lastModified != null) {
			dce.setLastModified(lastModified);
		}
		dce.setLength(inCore ? 0 : len);

		dce.setObjectId(objectId);
		builder.add(dce);
		return dce;
	}

	private ObjectId insertResult(StreamLoader resultStreamLoader,
			Attribute lfsAttribute) throws IOException {
		try (LfsInputStream is = LfsFactory.getInstance().applyCleanFilter(repo,
				resultStreamLoader.data.load(), resultStreamLoader.size,
				lfsAttribute)) {
			return inserter.insert(OBJ_BLOB, is.getLength(), is);
		}
	}

	/**
	 * Gets the non-null repository instance of this {@link WorkTreeUpdater}.
	 *
	 * @return non-null repository instance
	 * @throws NullPointerException
	 *             if the handler was constructed without a repository.
	 */
	@NonNull
	private Repository nonNullRepo() throws NullPointerException {
		return Objects.requireNonNull(repo,
				() -> JGitText.get().repositoryIsRequired);
	}
}

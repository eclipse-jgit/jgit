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
import java.util.TreeMap;
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
 * Handles files IO for both local and remote repositories.
 * <p>
 * You should use a single instance for all of your file changes. In case of an error, make sure
 * your instance is released, and initiate a new one if necessary.
 */
public class RepoIOHandler implements Closeable {

	/**
	 * The result of the operations done by this handler.
	 */
	public static class Result {

		/**
		 * Files modified during this operation.
		 */
		public List<String> modifiedFiles = new LinkedList<>();

		/**
		 * Files in this list were failed to be deleted.
		 */
		public List<String> failedToDelete = new LinkedList<>();

		/**
		 * Modified tree ID if any, or null otherwise.
		 */
		public ObjectId treeId = null;
	}

	Result result = new Result();

	/**
	 * The repository this handler operates on.
	 *
	 * <p>Callers that want to assume the repo is not null may use {@link #nonNullRepo()}.
	 */
	@Nullable
	private final Repository repo;

	private final ObjectInserter inserter;
	private final ObjectReader reader;
	private final boolean inCore;
	private DirCache dirCache;
	private boolean implicitDirCache = false;

	/**
	 * Builder to update the dir cache during this operation.
	 */
	private DirCacheBuilder builder = null;

	/**
	 * The {@link WorkingTreeOptions} are needed to determine line endings for affected files.
	 */
	private WorkingTreeOptions workingTreeOptions;

	/**
	 * The size limit (bytes) which controls a file to be stored in {@code Heap} or {@code LocalFile}
	 * during the merge.
	 */
	private int inCoreFileSizeLimit;

	/**
	 * If the operation has nothing to do for a file but check it out at the end of the operation, it
	 * can be added here.
	 */
	private final Map<String, DirCacheEntry> toBeCheckedOut = new HashMap<>();

	/**
	 * Files in this list will be deleted from the local copy at the end of the operation.
	 */
	private final TreeMap<String, File> toBeDeleted = new TreeMap<>();

	/**
	 * Keeps {@link CheckoutMetadata} for {@link #checkout()}.
	 */
	private Map<String, CheckoutMetadata> checkoutMetadata;

	/**
	 * Keeps {@link CheckoutMetadata} for {@link #revertModifiedFiles()}.
	 */
	private Map<String, CheckoutMetadata> cleanupMetadata;

	/**
	 * Whether the changes were successfully written
	 */
	private boolean changesWritten = false;

	/**
	 * @param local    repository
	 * @param dirCache if set, use the provided dir cache. Otherwise, use the default repository one
	 */
	private RepoIOHandler(
			Repository local,
			DirCache dirCache) {
		this.repo = local;
		this.dirCache = dirCache;

		this.inCore = false;
		this.inserter = local.newObjectInserter();
		this.reader = inserter.newReader();
		this.workingTreeOptions = local.getConfig().get(WorkingTreeOptions.KEY);
		this.checkoutMetadata = new HashMap<>();
		this.cleanupMetadata = new HashMap<>();
		this.inCoreFileSizeLimit = setInCoreFileSizeLimit(local.getConfig());
	}

	/**
	 * @param local    repository
	 * @param dirCache if set, use the provided dir cache. Otherwise, use the default repository one
	 * @return an IO handler.
	 */
	public static RepoIOHandler initRepoIOHandlerForLocal(Repository local, DirCache dirCache) {
		return new RepoIOHandler(local, dirCache);
	}

	/**
	 * @param remote   repository
	 * @param dirCache if set, use the provided dir cache. Otherwise, creates a new one
	 * @param oi       to use for writing the modified objects with.
	 */
	private RepoIOHandler(
			Repository remote,
			DirCache dirCache,
			ObjectInserter oi) {
		this.repo = remote;
		this.dirCache = dirCache;
		this.inserter = oi;

		this.inCore = true;
		this.reader = oi.newReader();
		if (remote != null) {
			this.inCoreFileSizeLimit = setInCoreFileSizeLimit(remote.getConfig());
		}
	}

	/**
	 * @param remote   repository
	 * @param dirCache if set, use the provided dir cache. Otherwise, creates a new one
	 * @param oi       to use for writing the modified objects with.
	 * @return an IO handler.
	 */
	public static RepoIOHandler initRepoIOHandlerForRemote(Repository remote, DirCache dirCache,
			ObjectInserter oi) {
		return new RepoIOHandler(remote, dirCache, oi);
	}

	/**
	 * Something that can supply an {@link InputStream}.
	 */
	public interface StreamSupplier {

		/**
		 * Loads the input stream.
		 *
		 * @return the loaded stream
		 * @throws IOException if any reading error occurs
		 */
		InputStream load() throws IOException;
	}

	/**
	 * We write the patch result to a {@link org.eclipse.jgit.util.TemporaryBuffer} and then use
	 * {@link DirCacheCheckout}.getContent() to run the result through the CR-LF and smudge filters.
	 * DirCacheCheckout needs an ObjectLoader, not a TemporaryBuffer, so this class bridges between
	 * the two, making any Stream provided by a {@link StreamSupplier} look like an ordinary git blob
	 * to DirCacheCheckout.
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
			return new ObjectStream.Filter(getType(), getSize(), new BufferedInputStream(data.load()));
		}
	}

	/**
	 * Creates stream loader for the given supplier.
	 *
	 * @param supplier to wrap
	 * @param length   of the supplied content
	 * @return the result stream loader
	 */
	public static StreamLoader createStreamLoader(StreamSupplier supplier, long length) {
		return new StreamLoader(supplier, length);
	}

	private static int setInCoreFileSizeLimit(Config config) {
		return config.getInt(
				ConfigConstants.CONFIG_MERGE_SECTION, ConfigConstants.CONFIG_KEY_IN_CORE_LIMIT, 10 << 20);
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
	 * Gets dir cache for the repo. Locked for local repositories.
	 *
	 * @return the result dir cache
	 * @throws IOException is case the local dir cache cannot be read
	 */
	public DirCache getLockedDirCache() throws IOException {
		if (dirCache == null) {
			implicitDirCache = true;
			if (inCore) {
				dirCache = DirCache.newInCore();
			} else {
				dirCache = nonNullNonBareRepo().lockDirCache();
			}
		}
		if (builder == null) {
			builder = dirCache.builder();
		}
		return dirCache;
	}

	/**
	 * Creates build iterator for the handler's builder.
	 *
	 * @return the iterator
	 */
	public DirCacheBuildIterator createDirCacheBuildIterator() {
		return new DirCacheBuildIterator(builder);
	}

	/**
	 * Finish the dir cache building
	 *
	 * @param shouldCheckoutTheirs before committing the changes
	 * @throws IOException if any of the writes fail
	 */
	public void finishBuilding(boolean shouldCheckoutTheirs) throws IOException {
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
	 * Writes the dir cache changes.
	 *
	 * @return the Result of the operation.
	 * @throws IOException if any of the writes fail
	 */
	public Result writeChanges() throws IOException {
		result.treeId = getLockedDirCache().writeTree(inserter);
		changesWritten = true;
		return result;
	}

	/**
	 * Adds a {@link DirCacheEntry} for direct checkout and remembers its {@link CheckoutMetadata}.
	 *
	 * @param path                  of the entry
	 * @param entry                 to add
	 * @param cleanupAttributes     the {@link Attributes} of the tree that needs cleaning up
	 * @param cleanupSmudgeCommand  to use for the cleanup metadata
	 * @param checkoutAttributes    the {@link Attributes} of the tree that needs checkout
	 * @param checkoutSmudgeCommand to use for the checkout metadata
	 * @since 6.1
	 */
	public void addToCheckout(
			String path, DirCacheEntry entry, Attributes cleanupAttributes,
			String cleanupSmudgeCommand, Attributes checkoutAttributes, String checkoutSmudgeCommand) {
		if (entry != null) {
			// In some cases, we just want to add the metadata.
			toBeCheckedOut.put(path, entry);
		}
		addCheckoutMetadata(cleanupMetadata, path, cleanupAttributes, cleanupSmudgeCommand);
		addCheckoutMetadata(checkoutMetadata, path, checkoutAttributes, checkoutSmudgeCommand);
	}

	/**
	 * Get a map which maps the paths of files which have to be checked out because the merge created
	 * new fully-merged content for this file into the index.
	 *
	 * @return a map which maps the paths of files which have to be checked out because the merge
	 * created new fully-merged content for this file into the index. This means: the merge wrote a
	 * new stage 0 entry for this path.
	 */
	public Map<String, DirCacheEntry> getToBeCheckedOut() {
		return toBeCheckedOut;
	}

	/**
	 * Deletes the given file
	 * <p>
	 * Note the actual deletion is only done in {@link #finishBuilding}
	 *
	 * @param path          of the file to be deleted
	 * @param file          to be deleted
	 * @param attributes    to use for determining the metadata
	 * @param smudgeCommand to use for cleanup metadata
	 * @throws IOException if the file cannot be deleted
	 */
	public void deleteFile(String path, File file, Attributes attributes, String smudgeCommand)
			throws IOException {
		toBeDeleted.put(path, file);
		if (file != null && file.isFile()) {
			addCheckoutMetadata(cleanupMetadata, path, attributes, smudgeCommand);
		}
	}

	/**
	 * Remembers the {@link CheckoutMetadata} for the given path; it may be needed in {@link
	 * #checkout()} or in {@link #revertModifiedFiles()}.
	 *
	 * @param map           to add the metadata to
	 * @param path          of the current node
	 * @param attributes    to use for determining the metadata
	 * @param smudgeCommand to use for the metadata
	 * @since 6.1
	 */
	private void addCheckoutMetadata(
			Map<String, CheckoutMetadata> map, String path, Attributes attributes, String smudgeCommand) {
		if (inCore || map == null) {
			return;
		}
		map.put(path, getMetadata(attributes, smudgeCommand));
	}

	private CheckoutMetadata getMetadata(Attributes attributes, String smudgeCommand) {
		EolStreamType eol =
				EolStreamTypeUtil.detectStreamType(
						OperationType.CHECKOUT_OP, workingTreeOptions, attributes);
		return new CheckoutMetadata(eol, smudgeCommand);
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
			result.modifiedFiles.add(path);
		}
	}

	/**
	 * Marks the given path as modified in the operation.
	 *
	 * @param path to mark as modified
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
		// Iterate in reverse so that "folder/file" is deleted before
		// "folder". Otherwise, this could result in a failing path because
		// of a non-empty directory, for which delete() would fail.
		for (Map.Entry<String, DirCacheEntry> entry : toBeCheckedOut.entrySet()) {
			DirCacheEntry dirCacheEntry = entry.getValue();
			if (dirCacheEntry.getFileMode() == FileMode.GITLINK) {
				new File(nonNullNonBareRepo().getWorkTree(), entry.getKey()).mkdirs();
			} else {
				DirCacheCheckout.checkoutEntry(
						repo, dirCacheEntry, reader, false, checkoutMetadata.get(entry.getKey()));
				result.modifiedFiles.add(entry.getKey());
			}
		}
	}

	/**
	 * Reverts any uncommitted changes in the worktree. We know that for all modified files the
	 * old content was in the old index and the index contained only stage 0. In case if inCore
	 *  operation just clear the history of modified files.
	 *
	 * @throws java.io.IOException in case the cleaning up failed
	 */
	public void revertModifiedFiles() throws IOException {
		if (inCore) {
			result.modifiedFiles.clear();
			return;
		}
		if(changesWritten) {
			return;
		}
		for (String path : result.modifiedFiles) {
			DirCacheEntry entry = dirCache.getEntry(path);
			if (entry != null) {
				DirCacheCheckout.checkoutEntry(
						repo, entry, reader, false, cleanupMetadata.get(path));
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
	 * Updates the file in the filesystem with the given content.
	 *
	 * @param resultStreamLoader with the content to be updated
	 * @param attributes         for parsing the content
	 * @param smudgeCommand      for formatting the content
	 * @param path               of the file to be updated
	 * @param file               to be updated
	 * @param safeWrite          whether the content should be written to a buffer first
	 * @throws IOException if the {@link CheckoutMetadata} cannot be determined
	 */
	public void updateFileWithContent(
			StreamLoader resultStreamLoader,
			Attributes attributes,
			String smudgeCommand,
			String path,
			File file,
			boolean safeWrite)
			throws IOException {
		if (inCore) {
			return;
		}
		CheckoutMetadata checkoutMetadata = getMetadata(attributes, smudgeCommand);
		if (safeWrite) {
			try (org.eclipse.jgit.util.TemporaryBuffer buffer =
					new org.eclipse.jgit.util.TemporaryBuffer.LocalFile(null)) {
				// Write to a buffer and copy to the file only if everything was fine.
				DirCacheCheckout.getContent(
						repo, path, checkoutMetadata, resultStreamLoader, null, buffer);
				InputStream bufIn = buffer.openInputStream();
				Files.copy(bufIn, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
			return;
		}
		OutputStream outputStream = new FileOutputStream(file);
		DirCacheCheckout.getContent(
				repo, path, checkoutMetadata, resultStreamLoader, null, outputStream);

	}

	/**
	 * Creates a path with the given content, and adds it to the specified stage to the index builder
	 *
	 * @param inputStream        with the content to be updated
	 * @param path               of the file to be updated
	 * @param fileMode           of the modified file
	 * @param entryStage         of the new entry
	 * @param lastModified       instant of the modified file
	 * @param len                of the content
	 * @param lfsAttribute       for checking for LFS enablement
	 * @return the entry which was added to the index
	 * @throws IOException if inserting the content fails
	 */
	public DirCacheEntry insertToIndex(
			InputStream inputStream,
			byte[] path,
			FileMode fileMode,
			int entryStage,
			Instant lastModified,
			int len,
			Attribute lfsAttribute) throws IOException {
		StreamLoader contentLoader = createStreamLoader(() -> inputStream, len);
		return insertToIndex(contentLoader, path, fileMode, entryStage, lastModified, len,
				lfsAttribute);
	}

	/**
	 * Creates a path with the given content, and adds it to the specified stage to the index builder
	 *
	 * @param resultStreamLoader with the content to be updated
	 * @param path               of the file to be updated
	 * @param fileMode           of the modified file
	 * @param entryStage         of the new entry
	 * @param lastModified       instant of the modified file
	 * @param len                of the content
	 * @param lfsAttribute       for checking for LFS enablement
	 * @return the entry which was added to the index
	 * @throws IOException if inserting the content fails
	 */
	public DirCacheEntry insertToIndex(
			StreamLoader resultStreamLoader,
			byte[] path,
			FileMode fileMode,
			int entryStage,
			Instant lastModified,
			int len,
			Attribute lfsAttribute) throws IOException {
		return addExistingToIndex(insertResult(resultStreamLoader, lfsAttribute),
				path, fileMode, entryStage, lastModified, len);
	}

	/**
	 * Adds a path with the specified stage to the index builder
	 *
	 * @param objectId     of the existing object to add
	 * @param path         of the modified file
	 * @param fileMode     of the modified file
	 * @param entryStage   of the new entry
	 * @param lastModified instant of the modified file
	 * @param len          of the modified file content
	 * @return the entry which was added to the index
	 */
	public DirCacheEntry addExistingToIndex(
			ObjectId objectId,
			byte[] path,
			FileMode fileMode,
			int entryStage,
			Instant lastModified,
			int len) {
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

	private ObjectId insertResult(StreamLoader resultStreamLoader, Attribute lfsAttribute)
			throws IOException {
		try (LfsInputStream is =
				org.eclipse.jgit.util.LfsFactory.getInstance()
						.applyCleanFilter(
								repo,
								resultStreamLoader.data.load(),
								resultStreamLoader.size,
								lfsAttribute)) {
			return inserter.insert(OBJ_BLOB, is.getLength(), is);
		}
	}

	/**
	 * @return non-null repository instance
	 * @throws java.lang.NullPointerException if the handler was constructed without a repository.
	 *                                        <p>
	 *                                        Get non-null repository instance
	 */
	private Repository nonNullRepo() throws NullPointerException {
		if (repo == null) {
			throw new NullPointerException(JGitText.get().repositoryIsRequired);
		}
		return repo;
	}


	/**
	 * Get non-null and non-bare repository instance
	 *
	 * @return non-null and non-bare repository instance
	 * @throws java.lang.NullPointerException if the handler was constructed without a repository.
	 * @throws NoWorkTreeException            if the handler was constructed with a bare repository
	 */
	private Repository nonNullNonBareRepo() throws NullPointerException, NoWorkTreeException {
		if (nonNullRepo().isBare()) {
			throw new NoWorkTreeException();
		}
		return repo;
	}
}

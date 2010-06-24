/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2006-2010, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.lib;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.SystemReader;

/**
 * Represents a Git repository. A repository holds all objects and refs used for
 * managing source code (could by any type of file, but source code is what
 * SCM's are typically used for).
 *
 * In Git terms all data is stored in GIT_DIR, typically a directory called
 * .git. A work tree is maintained unless the repository is a bare repository.
 * Typically the .git directory is located at the root of the work dir.
 *
 * <ul>
 * <li>GIT_DIR
 * 	<ul>
 * 		<li>objects/ - objects</li>
 * 		<li>refs/ - tags and heads</li>
 * 		<li>config - configuration</li>
 * 		<li>info/ - more configurations</li>
 * 	</ul>
 * </li>
 * </ul>
 * <p>
 * This class is thread-safe.
 * <p>
 * This implementation only handles a subtly undocumented subset of git features.
 *
 */
public class Repository {
	private final AtomicInteger useCnt = new AtomicInteger(1);

	private final File gitDir;

	private final FS fs;

	private final FileBasedConfig userConfig;

	private final RepositoryConfig config;

	private final RefDatabase refs;

	private final ObjectDirectory objectDatabase;

	private GitIndex index;

	private final List<RepositoryListener> listeners = new Vector<RepositoryListener>(); // thread safe
	static private final List<RepositoryListener> allListeners = new Vector<RepositoryListener>(); // thread safe

	private File workDir;

	private File indexFile;

	/**
	 * Construct a representation of a Git repository.
	 *
	 * The work tree, object directory, alternate object directories and index
	 * file locations are deduced from the given git directory and the default
	 * rules.
	 *
	 * @param d
	 *            GIT_DIR (the location of the repository metadata).
	 * @throws IOException
	 *             the repository appears to already exist but cannot be
	 *             accessed.
	 */
	public Repository(final File d) throws IOException {
		this(d, null, null, null, null); // go figure it out
	}

	/**
	 * Construct a representation of a Git repository.
	 *
	 * The work tree, object directory, alternate object directories and index
	 * file locations are deduced from the given git directory and the default
	 * rules.
	 *
	 * @param d
	 *            GIT_DIR (the location of the repository metadata). May be
	 *            null work workTree is set
	 * @param workTree
	 *            GIT_WORK_TREE (the root of the checkout). May be null for
	 *            default value.
	 * @throws IOException
	 *             the repository appears to already exist but cannot be
	 *             accessed.
	 */
	public Repository(final File d, final File workTree) throws IOException {
		this(d, workTree, null, null, null); // go figure it out
	}

	/**
	 * Construct a representation of a Git repository using the given parameters
	 * possibly overriding default conventions.
	 *
	 * @param d
	 *            GIT_DIR (the location of the repository metadata). May be null
	 *            for default value in which case it depends on GIT_WORK_TREE.
	 * @param workTree
	 *            GIT_WORK_TREE (the root of the checkout). May be null for
	 *            default value if GIT_DIR is provided.
	 * @param objectDir
	 *            GIT_OBJECT_DIRECTORY (where objects and are stored). May be
	 *            null for default value. Relative names ares resolved against
	 *            GIT_WORK_TREE.
	 * @param alternateObjectDir
	 *            GIT_ALTERNATE_OBJECT_DIRECTORIES (where more objects are read
	 *            from). May be null for default value. Relative names ares
	 *            resolved against GIT_WORK_TREE.
	 * @param indexFile
	 *            GIT_INDEX_FILE (the location of the index file). May be null
	 *            for default value. Relative names ares resolved against
	 *            GIT_WORK_TREE.
	 * @throws IOException
	 *             the repository appears to already exist but cannot be
	 *             accessed.
	 */
	public Repository(final File d, final File workTree, final File objectDir,
			final File[] alternateObjectDir, final File indexFile) throws IOException {
		this(d, workTree, objectDir, alternateObjectDir, indexFile, FS.DETECTED);
	}

	/**
	 * Construct a representation of a Git repository using the given parameters
	 * possibly overriding default conventions.
	 *
	 * @param d
	 *            GIT_DIR (the location of the repository metadata). May be null
	 *            for default value in which case it depends on GIT_WORK_TREE.
	 * @param workTree
	 *            GIT_WORK_TREE (the root of the checkout). May be null for
	 *            default value if GIT_DIR is provided.
	 * @param objectDir
	 *            GIT_OBJECT_DIRECTORY (where objects and are stored). May be
	 *            null for default value. Relative names ares resolved against
	 *            GIT_WORK_TREE.
	 * @param alternateObjectDir
	 *            GIT_ALTERNATE_OBJECT_DIRECTORIES (where more objects are read
	 *            from). May be null for default value. Relative names ares
	 *            resolved against GIT_WORK_TREE.
	 * @param indexFile
	 *            GIT_INDEX_FILE (the location of the index file). May be null
	 *            for default value. Relative names ares resolved against
	 *            GIT_WORK_TREE.
	 * @param fs
	 *            the file system abstraction which will be necessary to
	 *            perform certain file system operations.
	 * @throws IOException
	 *             the repository appears to already exist but cannot be
	 *             accessed.
	 */
	public Repository(final File d, final File workTree, final File objectDir,
			final File[] alternateObjectDir, final File indexFile, FS fs)
			throws IOException {

		if (workTree != null) {
			workDir = workTree;
			if (d == null)
				gitDir = new File(workTree, Constants.DOT_GIT);
			else
				gitDir = d;
		} else {
			if (d != null)
				gitDir = d;
			else
				throw new IllegalArgumentException(
						JGitText.get().eitherGIT_DIRorGIT_WORK_TREEmustBePassed);
		}

		this.fs = fs;

		userConfig = SystemReader.getInstance().openUserConfig(fs);
		config = new RepositoryConfig(userConfig, fs.resolve(gitDir, "config"));

		loadUserConfig();
		loadConfig();

		if (workDir == null) {
			// if the working directory was not provided explicitly,
			// we need to decide if this is a "bare" repository or not
			// first, we check the working tree configuration
			String workTreeConfig = getConfig().getString(
					ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_WORKTREE);
			if (workTreeConfig != null) {
				// the working tree configuration wins
				workDir = fs.resolve(d, workTreeConfig);
			} else if (getConfig().getString(
					ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_BARE) != null) {
				// we have asserted that a value for the "bare" flag was set
				if (!getConfig().getBoolean(ConfigConstants.CONFIG_CORE_SECTION,
						ConfigConstants.CONFIG_KEY_BARE, true))
					// the "bare" flag is false -> use the parent of the
					// meta data directory
					workDir = gitDir.getParentFile();
				else
					// the "bare" flag is true
					workDir = null;
			} else if (Constants.DOT_GIT.equals(gitDir.getName())) {
				// no value for the "bare" flag, but the meta data directory
				// is named ".git" -> use the parent of the meta data directory
				workDir = gitDir.getParentFile();
			} else {
				workDir = null;
			}
		}

		refs = new RefDirectory(this);
		if (objectDir != null)
			objectDatabase = new ObjectDirectory(fs.resolve(objectDir, ""),
					alternateObjectDir, fs);
		else
			objectDatabase = new ObjectDirectory(fs.resolve(gitDir, "objects"),
					alternateObjectDir, fs);

		if (indexFile != null)
			this.indexFile = indexFile;
		else
			this.indexFile = new File(gitDir, "index");

		if (objectDatabase.exists()) {
			final String repositoryFormatVersion = getConfig().getString(
					ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION);
			if (!"0".equals(repositoryFormatVersion)) {
				throw new IOException(MessageFormat.format(
						JGitText.get().unknownRepositoryFormat2,
						repositoryFormatVersion));
			}
		}
	}

	private void loadUserConfig() throws IOException {
		try {
			userConfig.load();
		} catch (ConfigInvalidException e1) {
			IOException e2 = new IOException(MessageFormat.format(JGitText
					.get().userConfigFileInvalid, userConfig.getFile()
					.getAbsolutePath(), e1));
			e2.initCause(e1);
			throw e2;
		}
	}

	private void loadConfig() throws IOException {
		try {
			config.load();
		} catch (ConfigInvalidException e1) {
			IOException e2 = new IOException(JGitText.get().unknownRepositoryFormat);
			e2.initCause(e1);
			throw e2;
		}
	}

	/**
	 * Create a new Git repository.
	 * <p>
	 * Repository with working tree is created using this method. This method is
	 * the same as {@code create(false)}.
	 *
	 * @throws IOException
	 * @see #create(boolean)
	 */
	public void create() throws IOException {
		create(false);
	}

	/**
	 * Create a new Git repository initializing the necessary files and
	 * directories.
	 *
	 * @param bare
	 *            if true, a bare repository is created.
	 *
	 * @throws IOException
	 *             in case of IO problem
	 */
	public void create(boolean bare) throws IOException {
		final RepositoryConfig cfg = getConfig();
		if (cfg.getFile().exists()) {
			throw new IllegalStateException(MessageFormat.format(
					JGitText.get().repositoryAlreadyExists, gitDir));
		}
		gitDir.mkdirs();
		refs.create();
		objectDatabase.create();

		new File(gitDir, "branches").mkdir();

		RefUpdate head = updateRef(Constants.HEAD);
		head.disableRefLog();
		head.link(Constants.R_HEADS + Constants.MASTER);

		cfg.setInt(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION, 0);
		cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_FILEMODE, true);
		if (bare)
			cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_BARE, true);
		cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES, !bare);
		cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOCRLF, false);
		cfg.save();
	}

	/**
	 * @return GIT_DIR
	 */
	public File getDirectory() {
		return gitDir;
	}

	/**
	 * @return the directory containing the objects owned by this repository.
	 */
	public File getObjectsDirectory() {
		return objectDatabase.getDirectory();
	}

	/**
	 * @return the object database which stores this repository's data.
	 */
	public ObjectDatabase getObjectDatabase() {
		return objectDatabase;
	}

	/** @return the reference database which stores the reference namespace. */
	public RefDatabase getRefDatabase() {
		return refs;
	}

	/**
	 * @return the configuration of this repository
	 */
	public RepositoryConfig getConfig() {
		if (userConfig.isOutdated()) {
			try {
				loadUserConfig();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		if (config.isOutdated()) {
				try {
					loadConfig();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
		}
		return config;
	}

	/**
	 * @return the used file system abstraction
	 */
	public FS getFS() {
		return fs;
	}

	/**
	 * Construct a filename where the loose object having a specified SHA-1
	 * should be stored. If the object is stored in a shared repository the path
	 * to the alternative repo will be returned. If the object is not yet store
	 * a usable path in this repo will be returned. It is assumed that callers
	 * will look for objects in a pack first.
	 *
	 * @param objectId
	 * @return suggested file name
	 */
	public File toFile(final AnyObjectId objectId) {
		return objectDatabase.fileFor(objectId);
	}

	/**
	 * @param objectId
	 * @return true if the specified object is stored in this repo or any of the
	 *         known shared repositories.
	 */
	public boolean hasObject(final AnyObjectId objectId) {
		return objectDatabase.hasObject(objectId);
	}

	/**
	 * @param id
	 *            SHA-1 of an object.
	 *
	 * @return a {@link ObjectLoader} for accessing the data of the named
	 *         object, or null if the object does not exist.
	 * @throws IOException
	 */
	public ObjectLoader openObject(final AnyObjectId id)
			throws IOException {
		final WindowCursor wc = new WindowCursor();
		try {
			return openObject(wc, id);
		} finally {
			wc.release();
		}
	}

	/**
	 * @param curs
	 *            temporary working space associated with the calling thread.
	 * @param id
	 *            SHA-1 of an object.
	 *
	 * @return a {@link ObjectLoader} for accessing the data of the named
	 *         object, or null if the object does not exist.
	 * @throws IOException
	 */
	public ObjectLoader openObject(final WindowCursor curs, final AnyObjectId id)
			throws IOException {
		return objectDatabase.openObject(curs, id);
	}

	/**
	 * Open object in all packs containing specified object.
	 *
	 * @param objectId
	 *            id of object to search for
	 * @param curs
	 *            temporary working space associated with the calling thread.
	 * @return collection of loaders for this object, from all packs containing
	 *         this object
	 * @throws IOException
	 */
	public Collection<PackedObjectLoader> openObjectInAllPacks(
			final AnyObjectId objectId, final WindowCursor curs)
			throws IOException {
		Collection<PackedObjectLoader> result = new LinkedList<PackedObjectLoader>();
		openObjectInAllPacks(objectId, result, curs);
		return result;
	}

	/**
	 * Open object in all packs containing specified object.
	 *
	 * @param objectId
	 *            id of object to search for
	 * @param resultLoaders
	 *            result collection of loaders for this object, filled with
	 *            loaders from all packs containing specified object
	 * @param curs
	 *            temporary working space associated with the calling thread.
	 * @throws IOException
	 */
	void openObjectInAllPacks(final AnyObjectId objectId,
			final Collection<PackedObjectLoader> resultLoaders,
			final WindowCursor curs) throws IOException {
		objectDatabase.openObjectInAllPacks(resultLoaders, curs, objectId);
	}

	/**
	 * @param id
	 *            SHA'1 of a blob
	 * @return an {@link ObjectLoader} for accessing the data of a named blob
	 * @throws IOException
	 */
	public ObjectLoader openBlob(final ObjectId id) throws IOException {
		return openObject(id);
	}

	/**
	 * @param id
	 *            SHA'1 of a tree
	 * @return an {@link ObjectLoader} for accessing the data of a named tree
	 * @throws IOException
	 */
	public ObjectLoader openTree(final ObjectId id) throws IOException {
		return openObject(id);
	}

	/**
	 * Access a Commit object using a symbolic reference. This reference may
	 * be a SHA-1 or ref in combination with a number of symbols translating
	 * from one ref or SHA1-1 to another, such as HEAD^ etc.
	 *
	 * @param revstr a reference to a git commit object
	 * @return a Commit named by the specified string
	 * @throws IOException for I/O error or unexpected object type.
	 *
	 * @see #resolve(String)
	 */
	public Commit mapCommit(final String revstr) throws IOException {
		final ObjectId id = resolve(revstr);
		return id != null ? mapCommit(id) : null;
	}

	/**
	 * Access any type of Git object by id and
	 *
	 * @param id
	 *            SHA-1 of object to read
	 * @param refName optional, only relevant for simple tags
	 * @return The Git object if found or null
	 * @throws IOException
	 */
	public Object mapObject(final ObjectId id, final String refName) throws IOException {
		final ObjectLoader or = openObject(id);
		if (or == null)
			return null;
		final byte[] raw = or.getBytes();
		switch (or.getType()) {
		case Constants.OBJ_TREE:
			return makeTree(id, raw);

		case Constants.OBJ_COMMIT:
			return makeCommit(id, raw);

		case Constants.OBJ_TAG:
			return makeTag(id, refName, raw);

		case Constants.OBJ_BLOB:
			return raw;

		default:
			throw new IncorrectObjectTypeException(id,
				JGitText.get().incorrectObjectType_COMMITnorTREEnorBLOBnorTAG);
		}
	}

	/**
	 * Access a Commit by SHA'1 id.
	 * @param id
	 * @return Commit or null
	 * @throws IOException for I/O error or unexpected object type.
	 */
	public Commit mapCommit(final ObjectId id) throws IOException {
		final ObjectLoader or = openObject(id);
		if (or == null)
			return null;
		final byte[] raw = or.getBytes();
		if (Constants.OBJ_COMMIT == or.getType())
			return new Commit(this, id, raw);
		throw new IncorrectObjectTypeException(id, Constants.TYPE_COMMIT);
	}

	private Commit makeCommit(final ObjectId id, final byte[] raw) {
		Commit ret = new Commit(this, id, raw);
		return ret;
	}

	/**
	 * Access a Tree object using a symbolic reference. This reference may
	 * be a SHA-1 or ref in combination with a number of symbols translating
	 * from one ref or SHA1-1 to another, such as HEAD^{tree} etc.
	 *
	 * @param revstr a reference to a git commit object
	 * @return a Tree named by the specified string
	 * @throws IOException
	 *
	 * @see #resolve(String)
	 */
	public Tree mapTree(final String revstr) throws IOException {
		final ObjectId id = resolve(revstr);
		return id != null ? mapTree(id) : null;
	}

	/**
	 * Access a Tree by SHA'1 id.
	 * @param id
	 * @return Tree or null
	 * @throws IOException for I/O error or unexpected object type.
	 */
	public Tree mapTree(final ObjectId id) throws IOException {
		final ObjectLoader or = openObject(id);
		if (or == null)
			return null;
		final byte[] raw = or.getBytes();
		switch (or.getType()) {
		case Constants.OBJ_TREE:
			return new Tree(this, id, raw);

		case Constants.OBJ_COMMIT:
			return mapTree(ObjectId.fromString(raw, 5));

		default:
			throw new IncorrectObjectTypeException(id, Constants.TYPE_TREE);
		}
	}

	private Tree makeTree(final ObjectId id, final byte[] raw) throws IOException {
		Tree ret = new Tree(this, id, raw);
		return ret;
	}

	private Tag makeTag(final ObjectId id, final String refName, final byte[] raw) {
		Tag ret = new Tag(this, id, refName, raw);
		return ret;
	}

	/**
	 * Access a tag by symbolic name.
	 *
	 * @param revstr
	 * @return a Tag or null
	 * @throws IOException on I/O error or unexpected type
	 */
	public Tag mapTag(String revstr) throws IOException {
		final ObjectId id = resolve(revstr);
		return id != null ? mapTag(revstr, id) : null;
	}

	/**
	 * Access a Tag by SHA'1 id
	 * @param refName
	 * @param id
	 * @return Commit or null
	 * @throws IOException for I/O error or unexpected object type.
	 */
	public Tag mapTag(final String refName, final ObjectId id) throws IOException {
		final ObjectLoader or = openObject(id);
		if (or == null)
			return null;
		final byte[] raw = or.getBytes();
		if (Constants.OBJ_TAG == or.getType())
			return new Tag(this, id, refName, raw);
		return new Tag(this, id, refName, null);
	}

	/**
	 * Create a command to update, create or delete a ref in this repository.
	 *
	 * @param ref
	 *            name of the ref the caller wants to modify.
	 * @return an update command. The caller must finish populating this command
	 *         and then invoke one of the update methods to actually make a
	 *         change.
	 * @throws IOException
	 *             a symbolic ref was passed in and could not be resolved back
	 *             to the base ref, as the symbolic ref could not be read.
	 */
	public RefUpdate updateRef(final String ref) throws IOException {
		return updateRef(ref, false);
	}

	/**
	 * Create a command to update, create or delete a ref in this repository.
	 *
	 * @param ref
	 *            name of the ref the caller wants to modify.
	 * @param detach
	 *            true to create a detached head
	 * @return an update command. The caller must finish populating this command
	 *         and then invoke one of the update methods to actually make a
	 *         change.
	 * @throws IOException
	 *             a symbolic ref was passed in and could not be resolved back
	 *             to the base ref, as the symbolic ref could not be read.
	 */
	public RefUpdate updateRef(final String ref, final boolean detach) throws IOException {
		return refs.newUpdate(ref, detach);
	}

	/**
	 * Create a command to rename a ref in this repository
	 *
	 * @param fromRef
	 *            name of ref to rename from
	 * @param toRef
	 *            name of ref to rename to
	 * @return an update command that knows how to rename a branch to another.
	 * @throws IOException
	 *             the rename could not be performed.
	 *
	 */
	public RefRename renameRef(final String fromRef, final String toRef) throws IOException {
		return refs.newRename(fromRef, toRef);
	}

	/**
	 * Parse a git revision string and return an object id.
	 *
	 * Currently supported is combinations of these.
	 * <ul>
	 * <li>SHA-1 - a SHA-1</li>
	 * <li>refs/... - a ref name</li>
	 * <li>ref^n - nth parent reference</li>
	 * <li>ref~n - distance via parent reference</li>
	 * <li>ref@{n} - nth version of ref</li>
	 * <li>ref^{tree} - tree references by ref</li>
	 * <li>ref^{commit} - commit references by ref</li>
	 * </ul>
	 *
	 * Not supported is:
	 * <ul>
	 * <li>timestamps in reflogs, ref@{full or relative timestamp}</li>
	 * <li>abbreviated SHA-1's</li>
	 * </ul>
	 *
	 * @param revstr
	 *            A git object references expression
	 * @return an ObjectId or null if revstr can't be resolved to any ObjectId
	 * @throws IOException
	 *             on serious errors
	 */
	public ObjectId resolve(final String revstr) throws IOException {
		char[] rev = revstr.toCharArray();
		RevObject ref = null;
		RevWalk rw = new RevWalk(this);
		for (int i = 0; i < rev.length; ++i) {
			switch (rev[i]) {
			case '^':
				if (ref == null) {
					ref = parseSimple(rw, new String(rev, 0, i));
					if (ref == null)
						return null;
				}
				if (i + 1 < rev.length) {
					switch (rev[i + 1]) {
					case '0':
					case '1':
					case '2':
					case '3':
					case '4':
					case '5':
					case '6':
					case '7':
					case '8':
					case '9':
						int j;
						ref = rw.parseCommit(ref);
						for (j = i + 1; j < rev.length; ++j) {
							if (!Character.isDigit(rev[j]))
								break;
						}
						String parentnum = new String(rev, i + 1, j - i - 1);
						int pnum;
						try {
							pnum = Integer.parseInt(parentnum);
						} catch (NumberFormatException e) {
							throw new RevisionSyntaxException(
									JGitText.get().invalidCommitParentNumber,
									revstr);
						}
						if (pnum != 0) {
							RevCommit commit = (RevCommit) ref;
							if (pnum > commit.getParentCount())
								ref = null;
							else
								ref = commit.getParent(pnum - 1);
						}
						i = j - 1;
						break;
					case '{':
						int k;
						String item = null;
						for (k = i + 2; k < rev.length; ++k) {
							if (rev[k] == '}') {
								item = new String(rev, i + 2, k - i - 2);
								break;
							}
						}
						i = k;
						if (item != null)
							if (item.equals("tree")) {
								ref = rw.parseTree(ref);
							} else if (item.equals("commit")) {
								ref = rw.parseCommit(ref);
							} else if (item.equals("blob")) {
								ref = rw.peel(ref);
								if (!(ref instanceof RevBlob))
									throw new IncorrectObjectTypeException(ref,
											Constants.TYPE_BLOB);
							} else if (item.equals("")) {
								ref = rw.peel(ref);
							} else
								throw new RevisionSyntaxException(revstr);
						else
							throw new RevisionSyntaxException(revstr);
						break;
					default:
						ref = rw.parseAny(ref);
						if (ref instanceof RevCommit) {
							RevCommit commit = ((RevCommit) ref);
							if (commit.getParentCount() == 0)
								ref = null;
							else
								ref = commit.getParent(0);
						} else
							throw new IncorrectObjectTypeException(ref,
									Constants.TYPE_COMMIT);

					}
				} else {
					ref = rw.peel(ref);
					if (ref instanceof RevCommit) {
						RevCommit commit = ((RevCommit) ref);
						if (commit.getParentCount() == 0)
							ref = null;
						else
							ref = commit.getParent(0);
					} else
						throw new IncorrectObjectTypeException(ref,
								Constants.TYPE_COMMIT);
				}
				break;
			case '~':
				if (ref == null) {
					ref = parseSimple(rw, new String(rev, 0, i));
					if (ref == null)
						return null;
				}
				ref = rw.peel(ref);
				if (!(ref instanceof RevCommit))
					throw new IncorrectObjectTypeException(ref,
							Constants.TYPE_COMMIT);
				int l;
				for (l = i + 1; l < rev.length; ++l) {
					if (!Character.isDigit(rev[l]))
						break;
				}
				String distnum = new String(rev, i + 1, l - i - 1);
				int dist;
				try {
					dist = Integer.parseInt(distnum);
				} catch (NumberFormatException e) {
					throw new RevisionSyntaxException(
							JGitText.get().invalidAncestryLength, revstr);
				}
				while (dist > 0) {
					RevCommit commit = (RevCommit) ref;
					if (commit.getParentCount() == 0) {
						ref = null;
						break;
					}
					commit = commit.getParent(0);
					rw.parseHeaders(commit);
					ref = commit;
					--dist;
				}
				i = l - 1;
				break;
			case '@':
				int m;
				String time = null;
				for (m = i + 2; m < rev.length; ++m) {
					if (rev[m] == '}') {
						time = new String(rev, i + 2, m - i - 2);
						break;
					}
				}
				if (time != null)
					throw new RevisionSyntaxException(
							JGitText.get().reflogsNotYetSupportedByRevisionParser,
							revstr);
				i = m - 1;
				break;
			default:
				if (ref != null)
					throw new RevisionSyntaxException(revstr);
			}
		}
		return ref != null ? ref.copy() : resolveSimple(revstr);
	}

	private RevObject parseSimple(RevWalk rw, String revstr) throws IOException {
		ObjectId id = resolveSimple(revstr);
		return id != null ? rw.parseAny(id) : null;
	}

	private ObjectId resolveSimple(final String revstr) throws IOException {
		if (ObjectId.isId(revstr))
			return ObjectId.fromString(revstr);
		final Ref r = getRefDatabase().getRef(revstr);
		return r != null ? r.getObjectId() : null;
	}

	/** Increment the use counter by one, requiring a matched {@link #close()}. */
	public void incrementOpen() {
		useCnt.incrementAndGet();
	}

	/**
	 * Close all resources used by this repository
	 */
	public void close() {
		if (useCnt.decrementAndGet() == 0) {
			objectDatabase.close();
			refs.close();
		}
	}

	/**
	 * Add a single existing pack to the list of available pack files.
	 *
	 * @param pack
	 *            path of the pack file to open.
	 * @param idx
	 *            path of the corresponding index file.
	 * @throws IOException
	 *             index file could not be opened, read, or is not recognized as
	 *             a Git pack file index.
	 */
	public void openPack(final File pack, final File idx) throws IOException {
		objectDatabase.openPack(pack, idx);
	}

	public String toString() {
		return "Repository[" + getDirectory() + "]";
	}

	/**
	 * Get the name of the reference that {@code HEAD} points to.
	 * <p>
	 * This is essentially the same as doing:
	 *
	 * <pre>
	 * return getRef(Constants.HEAD).getTarget().getName()
	 * </pre>
	 *
	 * Except when HEAD is detached, in which case this method returns the
	 * current ObjectId in hexadecimal string format.
	 *
	 * @return name of current branch (for example {@code refs/heads/master}) or
	 *         an ObjectId in hex format if the current branch is detached.
	 * @throws IOException
	 */
	public String getFullBranch() throws IOException {
		Ref head = getRef(Constants.HEAD);
		if (head == null)
			return null;
		if (head.isSymbolic())
			return head.getTarget().getName();
		if (head.getObjectId() != null)
			return head.getObjectId().name();
		return null;
	}

	/**
	 * Get the short name of the current branch that {@code HEAD} points to.
	 * <p>
	 * This is essentially the same as {@link #getFullBranch()}, except the
	 * leading prefix {@code refs/heads/} is removed from the reference before
	 * it is returned to the caller.
	 *
	 * @return name of current branch (for example {@code master}), or an
	 *         ObjectId in hex format if the current branch is detached.
	 * @throws IOException
	 */
	public String getBranch() throws IOException {
		String name = getFullBranch();
		if (name != null)
			return shortenRefName(name);
		return name;
	}

	/**
	 * Objects known to exist but not expressed by {@link #getAllRefs()}.
	 * <p>
	 * When a repository borrows objects from another repository, it can
	 * advertise that it safely has that other repository's references, without
	 * exposing any other details about the other repository.  This may help
	 * a client trying to push changes avoid pushing more than it needs to.
	 *
	 * @return unmodifiable collection of other known objects.
	 */
	public Set<ObjectId> getAdditionalHaves() {
		HashSet<ObjectId> r = new HashSet<ObjectId>();
		for (ObjectDatabase d : objectDatabase.getAlternates()) {
			if (d instanceof AlternateRepositoryDatabase) {
				Repository repo;

				repo = ((AlternateRepositoryDatabase) d).getRepository();
				for (Ref ref : repo.getAllRefs().values())
					r.add(ref.getObjectId());
				r.addAll(repo.getAdditionalHaves());
			}
		}
		return r;
	}

	/**
	 * Get a ref by name.
	 *
	 * @param name
	 *            the name of the ref to lookup. May be a short-hand form, e.g.
	 *            "master" which is is automatically expanded to
	 *            "refs/heads/master" if "refs/heads/master" already exists.
	 * @return the Ref with the given name, or null if it does not exist
	 * @throws IOException
	 */
	public Ref getRef(final String name) throws IOException {
		return refs.getRef(name);
	}

	/**
	 * @return mutable map of all known refs (heads, tags, remotes).
	 */
	public Map<String, Ref> getAllRefs() {
		try {
			return refs.getRefs(RefDatabase.ALL);
		} catch (IOException e) {
			return new HashMap<String, Ref>();
		}
	}

	/**
	 * @return mutable map of all tags; key is short tag name ("v1.0") and value
	 *         of the entry contains the ref with the full tag name
	 *         ("refs/tags/v1.0").
	 */
	public Map<String, Ref> getTags() {
		try {
			return refs.getRefs(Constants.R_TAGS);
		} catch (IOException e) {
			return new HashMap<String, Ref>();
		}
	}

	/**
	 * Peel a possibly unpeeled reference to an annotated tag.
	 * <p>
	 * If the ref cannot be peeled (as it does not refer to an annotated tag)
	 * the peeled id stays null, but {@link Ref#isPeeled()} will be true.
	 *
	 * @param ref
	 *            The ref to peel
	 * @return <code>ref</code> if <code>ref.isPeeled()</code> is true; else a
	 *         new Ref object representing the same data as Ref, but isPeeled()
	 *         will be true and getPeeledObjectId will contain the peeled object
	 *         (or null).
	 */
	public Ref peel(final Ref ref) {
		try {
			return refs.peel(ref);
		} catch (IOException e) {
			// Historical accident; if the reference cannot be peeled due
			// to some sort of repository access problem we claim that the
			// same as if the reference was not an annotated tag.
			return ref;
		}
	}

	/**
	 * @return a map with all objects referenced by a peeled ref.
	 */
	public Map<AnyObjectId, Set<Ref>> getAllRefsByPeeledObjectId() {
		Map<String, Ref> allRefs = getAllRefs();
		Map<AnyObjectId, Set<Ref>> ret = new HashMap<AnyObjectId, Set<Ref>>(allRefs.size());
		for (Ref ref : allRefs.values()) {
			ref = peel(ref);
			AnyObjectId target = ref.getPeeledObjectId();
			if (target == null)
				target = ref.getObjectId();
			// We assume most Sets here are singletons
			Set<Ref> oset = ret.put(target, Collections.singleton(ref));
			if (oset != null) {
				// that was not the case (rare)
				if (oset.size() == 1) {
					// Was a read-only singleton, we must copy to a new Set
					oset = new HashSet<Ref>(oset);
				}
				ret.put(target, oset);
				oset.add(ref);
			}
		}
		return ret;
	}

	/**
	 * @return a representation of the index associated with this
	 *         {@link Repository}
	 * @throws IOException
	 *             if the index can not be read
	 * @throws IllegalStateException
	 *             if this is bare (see {@link #isBare()})
	 */
	public GitIndex getIndex() throws IOException, IllegalStateException {
		if (isBare())
			throw new IllegalStateException(
					JGitText.get().bareRepositoryNoWorkdirAndIndex);
		if (index == null) {
			index = new GitIndex(this);
			index.read();
		} else {
			index.rereadIfNecessary();
		}
		return index;
	}

	/**
	 * @return the index file location
	 * @throws IllegalStateException
	 *             if this is bare (see {@link #isBare()})
	 */
	public File getIndexFile() throws IllegalStateException {
		if (isBare())
			throw new IllegalStateException(
					JGitText.get().bareRepositoryNoWorkdirAndIndex);
		return indexFile;
	}

	static byte[] gitInternalSlash(byte[] bytes) {
		if (File.separatorChar == '/')
			return bytes;
		for (int i=0; i<bytes.length; ++i)
			if (bytes[i] == File.separatorChar)
				bytes[i] = '/';
		return bytes;
	}

	/**
	 * @return an important state
	 */
	public RepositoryState getRepositoryState() {
		// Pre Git-1.6 logic
		if (new File(getWorkDir(), ".dotest").exists())
			return RepositoryState.REBASING;
		if (new File(gitDir,".dotest-merge").exists())
			return RepositoryState.REBASING_INTERACTIVE;

		// From 1.6 onwards
		if (new File(getDirectory(),"rebase-apply/rebasing").exists())
			return RepositoryState.REBASING_REBASING;
		if (new File(getDirectory(),"rebase-apply/applying").exists())
			return RepositoryState.APPLY;
		if (new File(getDirectory(),"rebase-apply").exists())
			return RepositoryState.REBASING;

		if (new File(getDirectory(),"rebase-merge/interactive").exists())
			return RepositoryState.REBASING_INTERACTIVE;
		if (new File(getDirectory(),"rebase-merge").exists())
			return RepositoryState.REBASING_MERGE;

		// Both versions
		if (new File(gitDir, "MERGE_HEAD").exists()) {
			// we are merging - now check whether we have unmerged paths
			try {
				if (!DirCache.read(this).hasUnmergedPaths()) {
					// no unmerged paths -> return the MERGING_RESOLVED state
					return RepositoryState.MERGING_RESOLVED;
				}
			} catch (IOException e) {
				// Can't decide whether unmerged paths exists. Return
				// MERGING state to be on the safe side (in state MERGING
				// you are not allow to do anything)
				e.printStackTrace();
			}
			return RepositoryState.MERGING;
		}

		if (new File(gitDir,"BISECT_LOG").exists())
			return RepositoryState.BISECTING;

		return RepositoryState.SAFE;
	}

	/**
	 * Check validity of a ref name. It must not contain character that has
	 * a special meaning in a Git object reference expression. Some other
	 * dangerous characters are also excluded.
	 *
	 * For portability reasons '\' is excluded
	 *
	 * @param refName
	 *
	 * @return true if refName is a valid ref name
	 */
	public static boolean isValidRefName(final String refName) {
		final int len = refName.length();
		if (len == 0)
			return false;
		if (refName.endsWith(".lock"))
			return false;

		int components = 1;
		char p = '\0';
		for (int i = 0; i < len; i++) {
			final char c = refName.charAt(i);
			if (c <= ' ')
				return false;
			switch (c) {
			case '.':
				switch (p) {
				case '\0': case '/': case '.':
					return false;
				}
				if (i == len -1)
					return false;
				break;
			case '/':
				if (i == 0 || i == len - 1)
					return false;
				components++;
				break;
			case '{':
				if (p == '@')
					return false;
				break;
			case '~': case '^': case ':':
			case '?': case '[': case '*':
			case '\\':
				return false;
			}
			p = c;
		}
		return components > 1;
	}

	/**
	 * Strip work dir and return normalized repository path.
	 *
	 * @param workDir Work dir
	 * @param file File whose path shall be stripped of its workdir
	 * @return normalized repository relative path or the empty
	 *         string if the file is not relative to the work directory.
	 */
	public static String stripWorkDir(File workDir, File file) {
		final String filePath = file.getPath();
		final String workDirPath = workDir.getPath();

		if (filePath.length() <= workDirPath.length() ||
		    filePath.charAt(workDirPath.length()) != File.separatorChar ||
		    !filePath.startsWith(workDirPath)) {
			File absWd = workDir.isAbsolute() ? workDir : workDir.getAbsoluteFile();
			File absFile = file.isAbsolute() ? file : file.getAbsoluteFile();
			if (absWd == workDir && absFile == file)
				return "";
			return stripWorkDir(absWd, absFile);
		}

		String relName = filePath.substring(workDirPath.length() + 1);
		if (File.separatorChar != '/')
			relName = relName.replace(File.separatorChar, '/');
		return relName;
	}

	/**
	 * @return the "bare"-ness of this Repository
	 */
	public boolean isBare() {
		return workDir == null;
	}

	/**
	 * @return the workdir file, i.e. where the files are checked out
	 * @throws IllegalStateException
	 *             if the repository is "bare"
	 */
	public File getWorkDir() throws IllegalStateException {
		if (isBare())
			throw new IllegalStateException(
					JGitText.get().bareRepositoryNoWorkdirAndIndex);
		return workDir;
	}

	/**
	 * Override default workdir
	 *
	 * @param workTree
	 *            the work tree directory
	 */
	public void setWorkDir(File workTree) {
		this.workDir = workTree;
	}

	/**
	 * Register a {@link RepositoryListener} which will be notified
	 * when ref changes are detected.
	 *
	 * @param l
	 */
	public void addRepositoryChangedListener(final RepositoryListener l) {
		listeners.add(l);
	}

	/**
	 * Remove a registered {@link RepositoryListener}
	 * @param l
	 */
	public void removeRepositoryChangedListener(final RepositoryListener l) {
		listeners.remove(l);
	}

	/**
	 * Register a global {@link RepositoryListener} which will be notified
	 * when a ref changes in any repository are detected.
	 *
	 * @param l
	 */
	public static void addAnyRepositoryChangedListener(final RepositoryListener l) {
		allListeners.add(l);
	}

	/**
	 * Remove a globally registered {@link RepositoryListener}
	 * @param l
	 */
	public static void removeAnyRepositoryChangedListener(final RepositoryListener l) {
		allListeners.remove(l);
	}

	void fireRefsChanged() {
		final RefsChangedEvent event = new RefsChangedEvent(this);
		List<RepositoryListener> all;
		synchronized (listeners) {
			all = new ArrayList<RepositoryListener>(listeners);
		}
		synchronized (allListeners) {
			all.addAll(allListeners);
		}
		for (final RepositoryListener l : all) {
			l.refsChanged(event);
		}
	}

	void fireIndexChanged() {
		final IndexChangedEvent event = new IndexChangedEvent(this);
		List<RepositoryListener> all;
		synchronized (listeners) {
			all = new ArrayList<RepositoryListener>(listeners);
		}
		synchronized (allListeners) {
			all.addAll(allListeners);
		}
		for (final RepositoryListener l : all) {
			l.indexChanged(event);
		}
	}

	/**
	 * Force a scan for changed refs.
	 *
	 * @throws IOException
	 */
	public void scanForRepoChanges() throws IOException {
		getAllRefs(); // This will look for changes to refs
		if (!isBare())
			getIndex(); // This will detect changes in the index
	}

	/**
	 * @param refName
	 *
	 * @return a more user friendly ref name
	 */
	public String shortenRefName(String refName) {
		if (refName.startsWith(Constants.R_HEADS))
			return refName.substring(Constants.R_HEADS.length());
		if (refName.startsWith(Constants.R_TAGS))
			return refName.substring(Constants.R_TAGS.length());
		if (refName.startsWith(Constants.R_REMOTES))
			return refName.substring(Constants.R_REMOTES.length());
		return refName;
	}

	/**
	 * @param refName
	 * @return a {@link ReflogReader} for the supplied refname, or null if the
	 *         named ref does not exist.
	 * @throws IOException the ref could not be accessed.
	 */
	public ReflogReader getReflogReader(String refName) throws IOException {
		Ref ref = getRef(refName);
		if (ref != null)
			return new ReflogReader(this, ref.getName());
		return null;
	}

	/**
	 * Return the information stored in the file $GIT_DIR/MERGE_MSG. In this
	 * file operations triggering a merge will store a template for the commit
	 * message of the merge commit.
	 *
	 * @return a String containing the content of the MERGE_MSG file or
	 *         {@code null} if this file doesn't exist
	 * @throws IOException
	 */
	public String readMergeCommitMsg() throws IOException {
		File mergeMsgFile = new File(gitDir, Constants.MERGE_MSG);
		try {
			return new String(IO.readFully(mergeMsgFile));
		} catch (FileNotFoundException e) {
			// MERGE_MSG file has disappeared in the meantime
			// ignore it
			return null;
		}
	}

	/**
	 * Return the information stored in the file $GIT_DIR/MERGE_HEAD. In this
	 * file operations triggering a merge will store the IDs of all heads which
	 * should be merged together with HEAD.
	 *
	 * @return a list of {@link Commit}s which IDs are listed in the MERGE_HEAD
	 *         file or {@code null} if this file doesn't exist. Also if the file
	 *         exists but is empty {@code null} will be returned
	 * @throws IOException
	 */
	public List<ObjectId> readMergeHeads() throws IOException {
		File mergeHeadFile = new File(gitDir, Constants.MERGE_HEAD);
		byte[] raw;
		try {
			raw = IO.readFully(mergeHeadFile);
		} catch (FileNotFoundException notFound) {
			return new LinkedList<ObjectId>();
		}

		if (raw.length == 0)
			throw new IOException("MERGE_HEAD file empty: " + mergeHeadFile);

		LinkedList<ObjectId> heads = new LinkedList<ObjectId>();
		for (int p = 0; p < raw.length;) {
			heads.add(ObjectId.fromString(raw, p));
			p = RawParseUtils
					.nextLF(raw, p + Constants.OBJECT_ID_STRING_LENGTH);
		}
		return heads;
	}
}

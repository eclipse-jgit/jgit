/*
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2017, Shawn O. Pearce <spearce@spearce.org>
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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.MutableInteger;

/**
 * Misc. constants used throughout JGit.
 */
@SuppressWarnings("nls")
public final class Constants {
	/** Hash function used natively by Git for all objects. */
	private static final String HASH_FUNCTION = "SHA-1";

	/**
	 * A Git object hash is 160 bits, i.e. 20 bytes.
	 * <p>
	 * Changing this assumption is not going to be as easy as changing this
	 * declaration.
	 */
	public static final int OBJECT_ID_LENGTH = 20;

	/**
	 * A Git object can be expressed as a 40 character string of hexadecimal
	 * digits.
	 *
	 * @see #OBJECT_ID_LENGTH
	 */
	public static final int OBJECT_ID_STRING_LENGTH = OBJECT_ID_LENGTH * 2;

	/** Special name for the "HEAD" symbolic-ref. */
	public static final String HEAD = "HEAD";

	/** Special name for the "FETCH_HEAD" symbolic-ref. */
	public static final String FETCH_HEAD = "FETCH_HEAD";

	/**
	 * Text string that identifies an object as a commit.
	 * <p>
	 * Commits connect trees into a string of project histories, where each
	 * commit is an assertion that the best way to continue is to use this other
	 * tree (set of files).
	 */
	public static final String TYPE_COMMIT = "commit";

	/**
	 * Text string that identifies an object as a blob.
	 * <p>
	 * Blobs store whole file revisions. They are used for any user file, as
	 * well as for symlinks. Blobs form the bulk of any project's storage space.
	 */
	public static final String TYPE_BLOB = "blob";

	/**
	 * Text string that identifies an object as a tree.
	 * <p>
	 * Trees attach object ids (hashes) to names and file modes. The normal use
	 * for a tree is to store a version of a directory and its contents.
	 */
	public static final String TYPE_TREE = "tree";

	/**
	 * Text string that identifies an object as an annotated tag.
	 * <p>
	 * Annotated tags store a pointer to any other object, and an additional
	 * message. It is most commonly used to record a stable release of the
	 * project.
	 */
	public static final String TYPE_TAG = "tag";

	private static final byte[] ENCODED_TYPE_COMMIT = encodeASCII(TYPE_COMMIT);

	private static final byte[] ENCODED_TYPE_BLOB = encodeASCII(TYPE_BLOB);

	private static final byte[] ENCODED_TYPE_TREE = encodeASCII(TYPE_TREE);

	private static final byte[] ENCODED_TYPE_TAG = encodeASCII(TYPE_TAG);

	/** An unknown or invalid object type code. */
	public static final int OBJ_BAD = -1;

	/**
	 * In-pack object type: extended types.
	 * <p>
	 * This header code is reserved for future expansion. It is currently
	 * undefined/unsupported.
	 */
	public static final int OBJ_EXT = 0;

	/**
	 * In-pack object type: commit.
	 * <p>
	 * Indicates the associated object is a commit.
	 * <p>
	 * <b>This constant is fixed and is defined by the Git packfile format.</b>
	 *
	 * @see #TYPE_COMMIT
	 */
	public static final int OBJ_COMMIT = 1;

	/**
	 * In-pack object type: tree.
	 * <p>
	 * Indicates the associated object is a tree.
	 * <p>
	 * <b>This constant is fixed and is defined by the Git packfile format.</b>
	 *
	 * @see #TYPE_BLOB
	 */
	public static final int OBJ_TREE = 2;

	/**
	 * In-pack object type: blob.
	 * <p>
	 * Indicates the associated object is a blob.
	 * <p>
	 * <b>This constant is fixed and is defined by the Git packfile format.</b>
	 *
	 * @see #TYPE_BLOB
	 */
	public static final int OBJ_BLOB = 3;

	/**
	 * In-pack object type: annotated tag.
	 * <p>
	 * Indicates the associated object is an annotated tag.
	 * <p>
	 * <b>This constant is fixed and is defined by the Git packfile format.</b>
	 *
	 * @see #TYPE_TAG
	 */
	public static final int OBJ_TAG = 4;

	/** In-pack object type: reserved for future use. */
	public static final int OBJ_TYPE_5 = 5;

	/**
	 * In-pack object type: offset delta
	 * <p>
	 * Objects stored with this type actually have a different type which must
	 * be obtained from their delta base object. Delta objects store only the
	 * changes needed to apply to the base object in order to recover the
	 * original object.
	 * <p>
	 * An offset delta uses a negative offset from the start of this object to
	 * refer to its delta base. The base object must exist in this packfile
	 * (even in the case of a thin pack).
	 * <p>
	 * <b>This constant is fixed and is defined by the Git packfile format.</b>
	 */
	public static final int OBJ_OFS_DELTA = 6;

	/**
	 * In-pack object type: reference delta
	 * <p>
	 * Objects stored with this type actually have a different type which must
	 * be obtained from their delta base object. Delta objects store only the
	 * changes needed to apply to the base object in order to recover the
	 * original object.
	 * <p>
	 * A reference delta uses a full object id (hash) to reference the delta
	 * base. The base object is allowed to be omitted from the packfile, but
	 * only in the case of a thin pack being transferred over the network.
	 * <p>
	 * <b>This constant is fixed and is defined by the Git packfile format.</b>
	 */
	public static final int OBJ_REF_DELTA = 7;

	/**
	 * Pack file signature that occurs at file header - identifies file as Git
	 * packfile formatted.
	 * <p>
	 * <b>This constant is fixed and is defined by the Git packfile format.</b>
	 */
	public static final byte[] PACK_SIGNATURE = { 'P', 'A', 'C', 'K' };

	/**
	 * Native character encoding for commit messages, file names...
	 *
	 * @deprecated Use {@link java.nio.charset.StandardCharsets#UTF_8} directly
	 *             instead.
	 */
	@Deprecated
	public static final Charset CHARSET;

	/**
	 * Native character encoding for commit messages, file names...
	 *
	 * @deprecated Use {@link java.nio.charset.StandardCharsets#UTF_8} directly
	 *             instead.
	 */
	@Deprecated
	public static final String CHARACTER_ENCODING;

	/** Default main branch name */
	public static final String MASTER = "master";

	/** Default stash branch name */
	public static final String STASH = "stash";

	/**
	 *
	 */
	public static final String FILE_SEPARATOR = "/";

	/**
	 * Name of the folder (inside gitDir) where the branches are stored.
	 *
	 * A slightly deprecated way to store shorthands to be used to specify a URL
	 * to git fetch, git pull and git push. This mechanism is legacy and not
	 * likely to be found in modern repositories.
	 *
	 * This directory is ignored if $GIT_COMMON_DIR is set and
	 * "$GIT_COMMON_DIR/branches" will be used instead.
	 *
	 * @since 5.1
	 */
	public static final String BRANCHES = "branches";

	/**
	 * Name of the file (inside gitDir) where the description of repo is stored.
	 *
	 * @since 5.1
	 */
	public static final String DESCRIPTION = "description";

	/**
	 * Name of the folder where the refs are stored.
	 *
	 * References are stored in subdirectories of this directory. The git prune
	 * command knows to preserve objects reachable from refs found in this
	 * directory and its subdirectories.
	 *
	 * This directory is ignored if $GIT_COMMON_DIR is set and
	 * "$GIT_COMMON_DIR/refs" will be used instead.
	 *
	 * @since 5.1
	 */
	public static final String REFS = "refs";

	/** Prefix for any ref */
	public static final String R_REFS = REFS + FILE_SEPARATOR;

	/** Prefix for branch refs */
	public static final String R_HEADS = R_REFS + "heads" + FILE_SEPARATOR;

	/** Prefix for remotes refs */
	public static final String R_REMOTES = R_REFS + "remotes" + FILE_SEPARATOR;

	/** Prefix for tag refs */
	public static final String R_TAGS = R_REFS + "tags" + FILE_SEPARATOR;

	/** Prefix for notes refs */
	public static final String R_NOTES = R_REFS + "notes" + FILE_SEPARATOR;

	/** Standard notes ref */
	public static final String R_NOTES_COMMITS = R_NOTES + "commits";

	/** Standard stash ref */
	public static final String R_STASH = R_REFS + STASH;

	/**
	 * Packed refs file.
	 *
	 * Records the same information as refs/heads/, refs/tags/ and friends
	 * record in a more efficient way. This file is ignored if $GIT_COMMON_DIR
	 * is set and "$GIT_COMMON_DIR/packed-refs" will be used instead.
	 *
	 */
	public static final String PACKED_REFS = "packed-refs";

	/**
	 * Logs folder name.
	 *
	 * Records of changes made to refs are stored in this directory. This
	 * directory is ignored if $GIT_COMMON_DIR is set and "$GIT_COMMON_DIR/logs"
	 * will be used instead.
	 *
	 */
	public static final String LOGS = "logs";

	/** Prefix for any logs */
	public static final String L_LOGS = LOGS + FILE_SEPARATOR;

	/**
	 * Logs refs folder.
	 *
	 * @since 5.1
	 */
	public static final String LOGS_REFS = L_LOGS + "refs";

	/**
	 * Name of the folder where the info is stored.
	 *
	 * Additional information about the repository is recorded in this
	 * directory. This directory is ignored if $GIT_COMMON_DIR is set and
	 * "$GIT_COMMON_DIR/info" will be used instead.
	 *
	 * @since 5.1
	 */
	public static final String INFO = "info";

	/** Prefix for any info */
	public static final String I_INFO = INFO + FILE_SEPARATOR;

	/**
	 * Info refs folder.
	 */
	public static final String INFO_REFS = I_INFO + "refs";

	/**
	 * Excludes-file
	 *
	 * @since 3.0
	 */
	public static final String INFO_EXCLUDE = I_INFO + "exclude";

	/**
	 * Attributes-override-file
	 *
	 * @since 4.2
	 */
	public static final String INFO_ATTRIBUTES = I_INFO + "attributes";

	/**
	 * Remotes-file
	 *
	 * Stores shorthands for URL and default refnames for use when interacting
	 * with remote repositories via git fetch, git pull and git push commands.
	 * This mechanism is legacy and not likely to be found in modern
	 * repositories. This directory is ignored if $GIT_COMMON_DIR is set and
	 * "$GIT_COMMON_DIR/remotes" will be used instead.
	 *
	 * @since 5.1
	 */
	public static final String REMOTES = "remotes";

	/**
	 * The system property that contains the system user name
	 *
	 * @since 3.6
	 */
	public static final String OS_USER_DIR = "user.dir";

	/** The system property that contains the system user name */
	public static final String OS_USER_NAME_KEY = "user.name";

	/** The environment variable that contains the author's name */
	public static final String GIT_AUTHOR_NAME_KEY = "GIT_AUTHOR_NAME";

	/** The environment variable that contains the author's email */
	public static final String GIT_AUTHOR_EMAIL_KEY = "GIT_AUTHOR_EMAIL";

	/** The environment variable that contains the commiter's name */
	public static final String GIT_COMMITTER_NAME_KEY = "GIT_COMMITTER_NAME";

	/** The environment variable that contains the commiter's email */
	public static final String GIT_COMMITTER_EMAIL_KEY = "GIT_COMMITTER_EMAIL";

	/**
	 * The environment variable that blocks use of the system config file
	 *
	 * @since 3.3
	 */
	public static final String GIT_CONFIG_NOSYSTEM_KEY = "GIT_CONFIG_NOSYSTEM";

	/**
	 * The environment variable that limits how close to the root of the file
	 * systems JGit will traverse when looking for a repository root.
	 */
	public static final String GIT_CEILING_DIRECTORIES_KEY = "GIT_CEILING_DIRECTORIES";

	/**
	 * The environment variable that tells us which directory is the ".git"
	 * directory.
	 *
	 * If the GIT_DIR environment variable is set then it specifies a path to
	 * use instead of the default .git for the base of the repository. The
	 * --git-dir command-line option also sets this value.
	 */
	public static final String GIT_DIR_KEY = "GIT_DIR";

	/**
	 * The environment variable that tells us which directory is the common
	 * ".git" directory.
	 *
	 * If this variable is set to a path, non-worktree files that are normally
	 * in $GIT_DIR will be taken from this path instead. Worktree-specific files
	 * such as HEAD or index are taken from $GIT_DIR. It's related to
	 * git-worktree.
	 *
	 * @since 5.1
	 */
	public static final String GIT_COMMON_DIR_KEY = "GIT_COMMON_DIR";

	/**
	 * The environment variable that tells us which directory is the main
	 * working directory.
	 *
	 * Set the path to the root of the working tree. This can also be controlled
	 * by the --work-tree command-line option and the core.worktree
	 * configuration variable.
	 *
	 */
	public static final String GIT_WORK_TREE_KEY = "GIT_WORK_TREE";

	/**
	 * The environment variable that tells us which file holds the Git index.
	 */
	public static final String GIT_INDEX_FILE_KEY = "GIT_INDEX_FILE";

	/**
	 * The environment variable that tells us where objects are stored
	 */
	public static final String GIT_OBJECT_DIRECTORY_KEY = "GIT_OBJECT_DIRECTORY";

	/**
	 * The environment variable that tells us where to look for objects, besides
	 * the default objects directory.
	 */
	public static final String GIT_ALTERNATE_OBJECT_DIRECTORIES_KEY = "GIT_ALTERNATE_OBJECT_DIRECTORIES";

	/** Default value for the user name if no other information is available */
	public static final String UNKNOWN_USER_DEFAULT = "unknown-user";

	/** Beginning of the common "Signed-off-by: " commit message line */
	public static final String SIGNED_OFF_BY_TAG = "Signed-off-by: ";

	/** A gitignore file name */
	public static final String GITIGNORE_FILENAME = ".gitignore";

	/** Default remote name used by clone, push and fetch operations */
	public static final String DEFAULT_REMOTE_NAME = "origin";

	/** Default name for the Git repository directory or Git link file */
	public static final String DOT_GIT = ".git";

	/**
	 * Default name for the Git repository configuration.
	 *
	 * Repository specific configuration file. This file is ignored if
	 * $GIT_COMMON_DIR is set and "$GIT_COMMON_DIR/config" will be used instead.
	 */
	public static final String CONFIG = "config";

	/** A bare repository typically ends with this string */
	public static final String DOT_GIT_EXT = ".git";

	/**
	 * Name of the attributes file
	 *
	 * @since 3.7
	 */
	public static final String DOT_GIT_ATTRIBUTES = ".gitattributes";

	/**
	 * Key for filters in .gitattributes
	 *
	 * @since 4.2
	 */
	public static final String ATTR_FILTER = "filter";

	/**
	 * clean command name, used to call filter driver
	 *
	 * @since 4.2
	 */
	public static final String ATTR_FILTER_TYPE_CLEAN = "clean";

	/**
	 * smudge command name, used to call filter driver
	 *
	 * @since 4.2
	 */
	public static final String ATTR_FILTER_TYPE_SMUDGE = "smudge";

	/**
	 * Builtin filter commands start with this prefix
	 *
	 * @since 4.6
	 */
	public static final String BUILTIN_FILTER_PREFIX = "jgit://builtin/";

	/** Name of the ignore file */
	public static final String DOT_GIT_IGNORE = ".gitignore";

	/** Name of the submodules file */
	public static final String DOT_GIT_MODULES = ".gitmodules";

	/**
	 * Name of the .git/shallow file.
	 *
	 * This is similar to info/grafts but is internally used and maintained by
	 * shallow clone mechanism. See --depth option to git-clone and git-fetch.
	 * This file is ignored if $GIT_COMMON_DIR is set and
	 * "$GIT_COMMON_DIR/shallow" will be used instead.
	 */
	public static final String SHALLOW = "shallow";

	/**
	 * Prefix of the first line in a ".git" file
	 *
	 * @since 3.6
	 */
	public static final String GITDIR = "gitdir: ";

	/**
	 * Name of the folder (inside gitDir) where submodules are stored
	 *
	 * @since 3.6
	 */
	public static final String MODULES = "modules";

	/**
	 * Name of the folder (inside gitDir) where worktrees are stored.
	 *
	 * .git/worktrees/&lt;worktree-name&gt;
	 *
	 * Contains administrative data for linked working trees. Each subdirectory
	 * contains the working tree-related part of a linked working tree. This
	 * directory is ignored if $GIT_COMMON_DIR is set, in which case
	 * "$GIT_COMMON_DIR/worktrees" will be used instead.
	 *
	 * @since 5.1
	 */
	public static final String WORKTREES = "worktrees";

	/**
	 * Name of the folder (inside gitDir) where the hooks are stored.
	 *
	 * Hooks are customization scripts used by various Git commands.
	 *
	 * This directory is ignored if $GIT_COMMON_DIR is set and
	 * "$GIT_COMMON_DIR/hooks" will be used instead.
	 *
	 * @since 3.7
	 */
	public static final String HOOKS = "hooks";

	/**
	 * Merge attribute.
	 *
	 * @since 4.9
	 */
	public static final String ATTR_MERGE = "merge"; //$NON-NLS-1$

	/**
	 * Diff attribute.
	 *
	 * @since 4.11
	 */
	public static final String ATTR_DIFF = "diff"; //$NON-NLS-1$

	/**
	 * Binary value for custom merger.
	 *
	 * @since 4.9
	 */
	public static final String ATTR_BUILTIN_BINARY_MERGER = "binary"; //$NON-NLS-1$

	/**
	 * Name of the folder (inside gitDir) where the objects are stored.
	 *
	 * This directory is ignored if $GIT_COMMON_DIR is set and
	 * "$GIT_COMMON_DIR/objects" will be used instead.
	 *
	 * @since 5.1
	 */
	public static final String OBJECTS = "objects";

	/**
	 * The current index file (inside gitDir) for the repository.
	 *
	 * @since 5.1
	 */
	public static final String INDEX = "index";

	/**
	 * The current index.lock file (inside gitDir) for the repository.
	 *
	 * @since 5.1
	 */
	public static final String INDEX_LOCK = "index.lock";

	/**
	 * Name of the file (inside gitDir) that has reference to worktree's .git
	 * file (opposite link).
	 *
	 * .git/worktrees/&lt;worktree-name&gt;/gitdir
	 *
	 * A text file containing the absolute path back to the .git file that
	 * points to here. This is used to check if the linked repository has been
	 * manually removed and there is no need to keep this directory any more.
	 * The mtime of this file should be updated every time the linked repository
	 * is accessed.
	 *
	 * @since 5.1
	 */
	public static final String GITDIR_FILE = "gitdir";

	/**
	 * Name of the file (inside gitDir) that shows if worktree is locked.
	 *
	 * .git/worktrees/&lt;worktree-name&gt;/locked
	 *
	 * If this file exists, the linked working tree may be on a portable device
	 * and not available. The presence of this file prevents
	 * worktrees/&lt;id&gt; from being pruned either automatically or manually
	 * by git worktree prune. The file may contain a string explaining why the
	 * repository is locked.
	 *
	 * @since 5.1
	 */
	public static final String LOCKED_FILE = "locked";

	/**
	 * Name of the file (inside gitDir) that has reference to $GIT_COMMON_DIR.
	 *
	 * .git/worktrees/&lt;worktree-name&gt;/commondir
	 *
	 * If this file exists, $GIT_COMMON_DIR will be set to the path specified in
	 * this file if it is not explicitly set. If the specified path is relative,
	 * it is relative to $GIT_DIR. The repository with commondir is incomplete
	 * without the repository pointed by "commondir".
	 *
	 * @since 5.1
	 */
	public static final String COMMONDIR_FILE = "commondir";

	/**
	 * Create a new digest function for objects.
	 *
	 * @return a new digest object.
	 * @throws java.lang.RuntimeException
	 *             this Java virtual machine does not support the required hash
	 *             function. Very unlikely given that JGit uses a hash function
	 *             that is in the Java reference specification.
	 */
	public static MessageDigest newMessageDigest() {
		try {
			return MessageDigest.getInstance(HASH_FUNCTION);
		} catch (NoSuchAlgorithmException nsae) {
			throw new RuntimeException(MessageFormat.format(
					JGitText.get().requiredHashFunctionNotAvailable,
					HASH_FUNCTION), nsae);
		}
	}

	/**
	 * Convert an OBJ_* type constant to a TYPE_* type constant.
	 *
	 * @param typeCode
	 *            the type code, from a pack representation.
	 * @return the canonical string name of this type.
	 */
	public static String typeString(int typeCode) {
		switch (typeCode) {
		case OBJ_COMMIT:
			return TYPE_COMMIT;
		case OBJ_TREE:
			return TYPE_TREE;
		case OBJ_BLOB:
			return TYPE_BLOB;
		case OBJ_TAG:
			return TYPE_TAG;
		default:
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().badObjectType, Integer.valueOf(typeCode)));
		}
	}

	/**
	 * Convert an OBJ_* type constant to an ASCII encoded string constant.
	 * <p>
	 * The ASCII encoded string is often the canonical representation of the
	 * type within a loose object header, or within a tag header.
	 *
	 * @param typeCode
	 *            the type code, from a pack representation.
	 * @return the canonical ASCII encoded name of this type.
	 */
	public static byte[] encodedTypeString(int typeCode) {
		switch (typeCode) {
		case OBJ_COMMIT:
			return ENCODED_TYPE_COMMIT;
		case OBJ_TREE:
			return ENCODED_TYPE_TREE;
		case OBJ_BLOB:
			return ENCODED_TYPE_BLOB;
		case OBJ_TAG:
			return ENCODED_TYPE_TAG;
		default:
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().badObjectType, Integer.valueOf(typeCode)));
		}
	}

	/**
	 * Parse an encoded type string into a type constant.
	 *
	 * @param id
	 *            object id this type string came from; may be null if that is
	 *            not known at the time the parse is occurring.
	 * @param typeString
	 *            string version of the type code.
	 * @param endMark
	 *            character immediately following the type string. Usually ' '
	 *            (space) or '\n' (line feed).
	 * @param offset
	 *            position within <code>typeString</code> where the parse should
	 *            start. Updated with the new position (just past
	 *            <code>endMark</code> when the parse is successful.
	 * @return a type code constant (one of {@link #OBJ_BLOB},
	 *         {@link #OBJ_COMMIT}, {@link #OBJ_TAG}, {@link #OBJ_TREE}.
	 * @throws org.eclipse.jgit.errors.CorruptObjectException
	 *             there is no valid type identified by <code>typeString</code>.
	 */
	public static int decodeTypeString(final AnyObjectId id,
			final byte[] typeString, final byte endMark,
			final MutableInteger offset) throws CorruptObjectException {
		try {
			int position = offset.value;
			switch (typeString[position]) {
			case 'b':
				if (typeString[position + 1] != 'l'
						|| typeString[position + 2] != 'o'
						|| typeString[position + 3] != 'b'
						|| typeString[position + 4] != endMark)
					throw new CorruptObjectException(id,
							JGitText.get().corruptObjectInvalidType);
				offset.value = position + 5;
				return Constants.OBJ_BLOB;

			case 'c':
				if (typeString[position + 1] != 'o'
						|| typeString[position + 2] != 'm'
						|| typeString[position + 3] != 'm'
						|| typeString[position + 4] != 'i'
						|| typeString[position + 5] != 't'
						|| typeString[position + 6] != endMark)
					throw new CorruptObjectException(id,
							JGitText.get().corruptObjectInvalidType);
				offset.value = position + 7;
				return Constants.OBJ_COMMIT;

			case 't':
				switch (typeString[position + 1]) {
				case 'a':
					if (typeString[position + 2] != 'g'
							|| typeString[position + 3] != endMark)
						throw new CorruptObjectException(id,
								JGitText.get().corruptObjectInvalidType);
					offset.value = position + 4;
					return Constants.OBJ_TAG;

				case 'r':
					if (typeString[position + 2] != 'e'
							|| typeString[position + 3] != 'e'
							|| typeString[position + 4] != endMark)
						throw new CorruptObjectException(id,
								JGitText.get().corruptObjectInvalidType);
					offset.value = position + 5;
					return Constants.OBJ_TREE;

				default:
					throw new CorruptObjectException(id,
							JGitText.get().corruptObjectInvalidType);
				}

			default:
				throw new CorruptObjectException(id,
						JGitText.get().corruptObjectInvalidType);
			}
		} catch (ArrayIndexOutOfBoundsException bad) {
			throw new CorruptObjectException(id,
					JGitText.get().corruptObjectInvalidType);
		}
	}

	/**
	 * Convert an integer into its decimal representation.
	 *
	 * @param s
	 *            the integer to convert.
	 * @return a decimal representation of the input integer. The returned array
	 *         is the smallest array that will hold the value.
	 */
	public static byte[] encodeASCII(long s) {
		return encodeASCII(Long.toString(s));
	}

	/**
	 * Convert a string to US-ASCII encoding.
	 *
	 * @param s
	 *            the string to convert. Must not contain any characters over
	 *            127 (outside of 7-bit ASCII).
	 * @return a byte array of the same length as the input string, holding the
	 *         same characters, in the same order.
	 * @throws java.lang.IllegalArgumentException
	 *             the input string contains one or more characters outside of
	 *             the 7-bit ASCII character space.
	 */
	public static byte[] encodeASCII(String s) {
		final byte[] r = new byte[s.length()];
		for (int k = r.length - 1; k >= 0; k--) {
			final char c = s.charAt(k);
			if (c > 127)
				throw new IllegalArgumentException(
						MessageFormat.format(JGitText.get().notASCIIString, s));
			r[k] = (byte) c;
		}
		return r;
	}

	/**
	 * Convert a string to a byte array in the standard character encoding.
	 *
	 * @param str
	 *            the string to convert. May contain any Unicode characters.
	 * @return a byte array representing the requested string, encoded using the
	 *         default character encoding (UTF-8).
	 * @see #CHARACTER_ENCODING
	 */
	public static byte[] encode(String str) {
		final ByteBuffer bb = UTF_8.encode(str);
		final int len = bb.limit();
		if (bb.hasArray() && bb.arrayOffset() == 0) {
			final byte[] arr = bb.array();
			if (arr.length == len)
				return arr;
		}

		final byte[] arr = new byte[len];
		bb.get(arr);
		return arr;
	}

	static {
		if (OBJECT_ID_LENGTH != newMessageDigest().getDigestLength())
			throw new LinkageError(JGitText.get().incorrectOBJECT_ID_LENGTH);
		CHARSET = UTF_8;
		CHARACTER_ENCODING = UTF_8.name();
	}

	/** name of the file containing the commit msg for a merge commit */
	public static final String MERGE_MSG = "MERGE_MSG";

	/** name of the file containing the IDs of the parents of a merge commit */
	public static final String MERGE_HEAD = "MERGE_HEAD";

	/**
	 * name of the file containing the ID of a cherry pick commit in case of
	 * conflicts
	 */
	public static final String CHERRY_PICK_HEAD = "CHERRY_PICK_HEAD";

	/** name of the file containing the commit msg for a squash commit */
	public static final String SQUASH_MSG = "SQUASH_MSG";

	/**
	 * name of the file containing the ID of a revert commit in case of
	 * conflicts
	 */
	public static final String REVERT_HEAD = "REVERT_HEAD";

	/**
	 * name of the ref ORIG_HEAD used by certain commands to store the original
	 * value of HEAD
	 */
	public static final String ORIG_HEAD = "ORIG_HEAD";

	/**
	 * Name of the file in which git commands and hooks store and read the
	 * message prepared for the upcoming commit.
	 *
	 * @since 4.0
	 */
	public static final String COMMIT_EDITMSG = "COMMIT_EDITMSG";

	/**
	 * Well-known object ID for the empty blob.
	 *
	 * @since 0.9.1
	 */
	public static final ObjectId EMPTY_BLOB_ID = ObjectId
			.fromString("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391");

	/**
	 * Well-known object ID for the empty tree.
	 *
	 * @since 5.1
	 */
	public static final ObjectId EMPTY_TREE_ID = ObjectId
			.fromString("4b825dc642cb6eb9a060e54bf8d69288fbee4904");

	/**
	 * Suffix of lock file name
	 *
	 * @since 4.7
	 */
	public static final String LOCK_SUFFIX = ".lock"; //$NON-NLS-1$

	private Constants() {
		// Hide the default constructor
	}
}

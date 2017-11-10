/*
 * Copyright (C) 2017, Markus Duft <markus.duft@ssi-schaefer.com>
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
package org.eclipse.jgit.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;

import org.eclipse.jgit.attributes.Attribute;
import org.eclipse.jgit.attributes.Attributes;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

/**
 * Eases handling of BLOB contents wrt GIT LFS support. Also handles optionality
 * of LFS support.
 */
public class LfsHelper {

	private static final String CLEAN_FILTER_METHOD = "cleanLfsBlob"; //$NON-NLS-1$
	private static final String SMUDGE_FILTER_METHOD = "smudgeLfsBlob"; //$NON-NLS-1$
	private static final String LFS_BLOB_HELPER = "org.eclipse.jgit.lfs.LfsBlobHelper"; //$NON-NLS-1$

	/**
	 * @return whether LFS support is present
	 */
	public static boolean isAvailable() {
		try {
			Class.forName(LFS_BLOB_HELPER);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	/**
	 * Applies smudge filtering to a given loader, potentially redirecting it to
	 * a LFS blob which is downloaded on demand.
	 * <p>
	 * Returns the original loader in case LFS is not applicable.
	 *
	 * @param db
	 *            the repository
	 * @param loader
	 *            the loader for the blob
	 * @param attribute
	 *            the attribute used to check for LFS enablement (i.e. "merge",
	 *            "diff", "filter" from .gitattributes).
	 * @return a loader for the actual data of a blob
	 * @throws IOException
	 */
	public static ObjectLoader getSmudgeFiltered(Repository db,
			ObjectLoader loader, Attribute attribute)
			throws IOException {
		if (isAvailable() && isEnabled(db) && (attribute == null
				|| isEnabled(db, attribute))) {
			try {
				Class<?> pc = Class.forName(LFS_BLOB_HELPER);
				Method provider = pc.getMethod(SMUDGE_FILTER_METHOD,
						Repository.class, ObjectLoader.class);
				return (ObjectLoader) provider.invoke(null, db, loader);
			} catch (ClassNotFoundException | NoSuchMethodException
					| IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				return loader;
			}
		} else {
			return loader;
		}
	}

	/**
	 * Applies clean filtering to the given stream, writing the file content to
	 * the LFS storage if required and returning a stream to the LFS pointer
	 * instead.
	 * <p>
	 * Returns the original stream if LFS is not applicable.
	 *
	 * @param db
	 *            the repository
	 * @param input
	 *            the original input
	 * @param attribute
	 *            the attribute used to check for LFS enablement (i.e. "merge",
	 *            "diff", "filter" from .gitattributes).
	 * @return a stream to the content that should be written to the object
	 *         store.
	 */
	public static InputStream getCleanFiltered(Repository db, InputStream input,
			Attribute attribute) {
		if (isAvailable() && isEnabled(db, attribute)) {
			try {
				Class<?> pc = Class.forName(LFS_BLOB_HELPER);
				Method provider = pc.getMethod(CLEAN_FILTER_METHOD,
						Repository.class, InputStream.class);
				return (InputStream) provider.invoke(null, db, input);
			} catch (ClassNotFoundException | NoSuchMethodException
					| IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				return input;
			}
		} else {
			return input;
		}
	}

	/**
	 * @param db
	 *            the repository
	 * @return whether LFS is requested for the given repo.
	 */
	public static boolean isEnabled(Repository db) {
		if (db == null) {
			return false;
		}
		return db.getConfig().getBoolean(ConfigConstants.CONFIG_FILTER_SECTION,
				"lfs", ConfigConstants.CONFIG_KEY_USEJGITBUILTIN, false); //$NON-NLS-1$
	}

	/**
	 * @param db
	 *            the repository
	 * @param attribute
	 *            the attribute to check
	 * @return whether LFS filter is enabled for the given .gitattribute
	 *         attribute.
	 */
	public static boolean isEnabled(Repository db, Attribute attribute) {
		return isEnabled(db) && "lfs".equals(attribute.getValue()); //$NON-NLS-1$
	}

	/**
	 * @param db
	 *            the repository
	 * @param path
	 *            the path to find attributes for
	 * @return the {@link Attributes} for the given path.
	 * @throws IOException
	 *             in case of an error
	 */
	public static Attributes getAttributesForPath(Repository db, String path)
			throws IOException {
		try (TreeWalk walk = new TreeWalk(db)) {
			walk.addTree(new FileTreeIterator(db));
			PathFilter f = PathFilter.create(path);
			walk.setFilter(f);
			walk.setRecursive(false);
			Attributes attr = null;
			while (walk.next()) {
				if (f.isDone(walk)) {
					attr = walk.getAttributes();
					break;
				} else if (walk.isSubtree()) {
					walk.enterSubtree();
				}
			}
			if (attr == null) {
				throw new IOException(MessageFormat
						.format(JGitText.get().noPathAttributesFound, path));
			}

			return attr;
		}
	}

	/**
	 * @param db
	 *            the repository
	 * @param path
	 *            the path to find attributes for
	 * @param commit
	 *            the commit to inspect.
	 * @return the {@link Attributes} for the given path.
	 * @throws IOException
	 *             in case of an error
	 */
	public static Attributes getAttributesForPath(Repository db, String path,
			RevCommit commit) throws IOException {
		if (commit == null) {
			return getAttributesForPath(db, path);
		}

		try (TreeWalk walk = TreeWalk.forPath(db, path, commit.getTree())) {
			Attributes attr = walk == null ? null : walk.getAttributes();
			if (attr == null) {
				throw new IOException(MessageFormat
						.format(JGitText.get().noPathAttributesFound, path));
			}

			return attr;
		}
	}

}

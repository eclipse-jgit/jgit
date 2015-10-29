/*
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

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.jgit.internal.storage.file.FileSnapshot;
import org.eclipse.jgit.lib.Repository;

/**
 * Git file manager for a repo
 * <p>
 * This instance does lazy loading and caches the detected config files
 * (attributes, ignore files, ...)
 *
 * @author imo
 * @param <T>
 *            config node type
 *
 */
public abstract class AbstractDotFileManager<T> {
	private static final String PATH_GLOBAL = "git:global";//$NON-NLS-1$

	private static final String PATH_INFO = "git:info";//$NON-NLS-1$

	private final File m_root;
	/**
	 * the file system abstraction which will be necessary to perform certain
	 * file system operations.
	 */
	private final FS m_fs;

	private final Map<String, VolatileDotFile<T>> m_dotFiles = new LinkedHashMap<>();

	private final ReentrantReadWriteLock m_lockX = new ReentrantReadWriteLock(
			true);

	private final String m_dotFileName;

	private IFileLocator m_globalFileLocator;

	private IFileLocator m_infoFileLocator;


	/**
	 * @param root
	 * @param fs
	 * @param dotFileName
	 *            for example .gitattributes or .gitignore
	 */
	public AbstractDotFileManager(File root, FS fs, String dotFileName) {
		m_root = root;
		m_fs = fs;
		m_dotFileName = dotFileName;
	}

	/**
	 * @param globalFileLocator
	 */
	protected void setGlobalFileLocator(IFileLocator globalFileLocator) {
		m_globalFileLocator = globalFileLocator;
	}

	/**
	 * @param infoFileLocator
	 *            for example info/attributes or info/excludes
	 */
	protected void setInfoFileLocator(IFileLocator infoFileLocator) {
		m_infoFileLocator = infoFileLocator;
	}

	/**
	 *
	 * clear all caches
	 *
	 * @throws IOException
	 */
	public void clear() throws IOException {
		m_lockX.writeLock().lock();
		try {
			m_dotFiles.clear();
			clearSnapshots();
		} finally {
			m_lockX.writeLock().unlock();
		}
	}

	/**
	 * @return the lock used on all operations on this object
	 */
	protected ReadWriteLock getLock() {
		return m_lockX;
	}

	/**
	 * @return the global node
	 * @throws IOException
	 */
	protected T lookupGlobalNode() throws IOException {
		return readCachedNode(PATH_GLOBAL, m_globalFileLocator);
	}

	/**
	 * @return the INFO node
	 * @throws IOException
	 */
	protected T lookupInfoNode() throws IOException {
		return readCachedNode(PATH_INFO, m_infoFileLocator);
	}

	/**
	 * @return the root node in the {@link Repository#getWorkTree()}
	 * @throws IOException
	 */
	protected T lookupRootNode() throws IOException {
		return lookupWorkTreeNode(""); //$NON-NLS-1$
	}

	/**
	 * @param folderPath
	 *            the path without the fileName and without leading and trailing
	 *            '/'
	 * @return the node represented by the path and backed on the filesystem
	 * @throws IOException
	 */
	protected T lookupWorkTreeNode(final String folderPath) throws IOException {
		return readCachedNode(folderPath, new IFileLocator() {
			@Override
			public File locateFile() throws IOException {
				if (m_root == null)
					return null;
				if (folderPath.isEmpty())
					return m_fs.resolve(m_root, m_dotFileName); // $NON-NLS-1$
				return m_fs.resolve(m_root,
							folderPath + "/" + m_dotFileName); //$NON-NLS-1$
			}
		});
	}

	private T readCachedNode(String id, IFileLocator fileLocator)
			throws IOException {
		VolatileDotFile<T> v;
		m_lockX.readLock().lock();
		try {
			v = m_dotFiles.get(id);
		} finally {
			m_lockX.readLock().unlock();
		}
		if (v != null && !v.isDirty()) {
			return v.getNode();
		}

		if (fileLocator == null) {
			return null;
		}

		v = writeCachedNode(id, fileLocator);
		if (v == null) {
			return null;
		}

		return v.getNode();
	}

	/**
	 * Reload a dot file using the write lock
	 *
	 * @param id
	 * @param fileLocator
	 *            the actual file
	 * @return updated version or null
	 * @throws IOException
	 */
	private VolatileDotFile<T> writeCachedNode(String id,
			IFileLocator fileLocator) throws IOException {
		m_lockX.writeLock().lock();
		try {
			m_dotFiles.remove(id);

			VolatileDotFile<T> v = null;
			File f = fileLocator.locateFile();
			if (f != null && m_fs.exists(f)) {
				v = new VolatileDotFile<>(loadNode(f), f);
				m_dotFiles.put(id, v);
			}
			clearSnapshots();
			return v;
		} finally {
			m_lockX.writeLock().unlock();
		}
	}

	/**
	 * @param f
	 *            existing file
	 * @return the loaded node
	 *         <p>
	 *         This method is always called in the context of a write lock
	 *         {@link #getLock()}
	 * @throws IOException
	 */
	protected abstract T loadNode(File f) throws IOException;

	/**
	 * clear derived cache
	 * <p>
	 * do not load it immediately again, use lazy loading
	 * <p>
	 * This method is always called in the context of the write lock
	 * {@link #getLock()}
	 */
	protected abstract void clearSnapshots();

	/**
	 * Locator for a dot file such as .gitattributes or .gitignore
	 */
	protected interface IFileLocator {
		/**
		 * @return the dot file
		 * @throws IOException
		 */
		File locateFile() throws IOException;
	}

	/**
	 * Attributes or Ignore file node that is loaded from the physical working
	 * tree. The node is assummed volatile and is checked for changes before
	 * every access to it.
	 *
	 * @param <T>
	 *            is the type of node represented by this class
	 */
	private static final class VolatileDotFile<T> {
		private final T m_node;

		private final File m_file;
		private final FileSnapshot m_snapshot;

		/**
		 * @param node
		 * @param file
		 * @throws IOException
		 */
		VolatileDotFile(T node, File file) throws IOException {
			m_node = node;
			m_file=file;
			m_snapshot = m_node != null ? FileSnapshot.save(file)
					: FileSnapshot.MISSING_FILE;
		}

		public T getNode() {
			return m_node;
		}

		/**
		 * @return true if the file was loaded for the first time, was reloaded
		 *         due to changed content, or does not exist anymore
		 * @throws IOException
		 */
		public boolean isDirty() throws IOException {
			return m_snapshot.isModified(m_file);
		}
	}
}

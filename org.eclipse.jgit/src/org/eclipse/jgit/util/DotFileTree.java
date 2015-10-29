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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.jgit.attributes.AttributesHierarchy;
import org.eclipse.jgit.attributes.AttributesNode;
import org.eclipse.jgit.attributes.AttributesRule;
import org.eclipse.jgit.events.ConfigChangedEvent;
import org.eclipse.jgit.events.ConfigChangedListener;
import org.eclipse.jgit.events.DotFileChangedEvent;
import org.eclipse.jgit.events.DotFileChangedListener;
import org.eclipse.jgit.ignore.FastIgnoreRule;
import org.eclipse.jgit.ignore.IgnoreHierarchy;
import org.eclipse.jgit.ignore.IgnoreNode;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;

/**
 * .git* file tree
 */
public class DotFileTree {
	private static final String PATH_GLOBAL_ATTRIBUTES = "global:attributes";//$NON-NLS-1$

	private static final String PATH_GLOBAL_EXCLUDES = "global:ignore";//$NON-NLS-1$

	private static final String PATH_INFO_ATTRIBUTES = "info:attributes";//$NON-NLS-1$

	private static final String PATH_INFO_EXCLUDES = "info:ignore";//$NON-NLS-1$

	private final Repository repo;

	private final Map<String, Object> nodeCache = new HashMap<>();

	private volatile long snapshotId;

	private volatile boolean initialized;

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(
			true);

	/**
	 * The manager for the cascaded .gitattributes inside the workFile tree the
	 * git configs
	 */
	private final AttributesHierarchy attributesHierarchy;

	/**
	 * The manager for the cascaded .gitignore inside the workFile tree and the
	 * git configs
	 */
	private final IgnoreHierarchy ignoreHierarchy;

	/**
	 * @param repo
	 */
	public DotFileTree(Repository repo) {
		this.repo = repo;
		// add dot file and config changed listener
		this.repo.getListenerList().addListener(ConfigChangedListener.class,
				new ConfigChangedListener() {
					@Override
					public void onConfigChanged(ConfigChangedEvent event) {
						handleConfigChanged();
					}
				});
		// add dot file and config changed listener
		this.repo.getListenerList().addListener(DotFileChangedListener.class,
				new DotFileChangedListener() {
					@Override
					public void onDotFileChanged(DotFileChangedEvent event) {
						handleDotFilesChanged(event.getFiles());
					}
				});

		ignoreHierarchy = new IgnoreHierarchy(this);
		attributesHierarchy = new AttributesHierarchy(this);
	}

	/**
	 * @return the {@link AttributesHierarchy} that manages .gitattributes
	 */
	public AttributesHierarchy getAttributesHierarchy() {
		return attributesHierarchy;
	}

	/**
	 * @return the {@link IgnoreHierarchy} that manages .gitignore
	 */
	public IgnoreHierarchy getIgnoreHierarchy() {
		return ignoreHierarchy;
	}

	/**
	 * reset the {@link DotFileTree}
	 * <p>
	 * it will lazy reload at the next node getter
	 */
	public void reset() {
		lock.writeLock().lock();
		try {
			initialized = false;
			snapshotId++;
		} finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * @return the current snapshot id. This id changes on every mutation of
	 *         this {@link DotFileTree}
	 */
	public long getSnapshotId() {
		return snapshotId;
	}

	/**
	 * @return the global attributes node
	 * @throws IOException
	 */
	public AttributesNode getGlobalAttributesNode() throws IOException {
		return getAttributesNode(PATH_GLOBAL_ATTRIBUTES);
	}

	/**
	 * @return the global excludes node
	 * @throws IOException
	 */
	public IgnoreNode getGlobalIgnoreNode() throws IOException {
		return getIgnoreNode(PATH_GLOBAL_EXCLUDES);
	}

	/**
	 * @return the info attributes node
	 * @throws IOException
	 */
	public AttributesNode getInfoAttributesNode() throws IOException {
		return getAttributesNode(PATH_INFO_ATTRIBUTES);
	}

	/**
	 * @return the info excludes node
	 * @throws IOException
	 */
	public IgnoreNode getInfoIgnoreNode() throws IOException {
		return getIgnoreNode(PATH_INFO_EXCLUDES);
	}

	/**
	 * @param folderPath
	 *            is the folder queried for a {@link AttributesNode}, no
	 *            trailing '/'
	 * @return the work tree attributes node
	 * @throws IOException
	 */
	public AttributesNode getWorkTreeAttributesNode(String folderPath)
			throws IOException {
		if (folderPath == null)
			folderPath = ""; //$NON-NLS-1$
		if (folderPath.endsWith("/")) //$NON-NLS-1$
			folderPath = folderPath.substring(0, folderPath.length() - 1);
		String filePath = folderPath.isEmpty() ? Constants.DOT_GIT_ATTRIBUTES
				: folderPath + "/" + Constants.DOT_GIT_ATTRIBUTES; //$NON-NLS-1$
		return getAttributesNode(filePath);
	}

	/**
	 * @param folderPath
	 *            is the folder queried for a {@link IgnoreNode}, no trailing
	 *            '/'
	 * @return the info excludes node
	 * @throws IOException
	 */
	public IgnoreNode getWorkTreeIgnoreNode(String folderPath)
			throws IOException {
		if (folderPath == null)
			folderPath = ""; //$NON-NLS-1$
		if (folderPath.endsWith("/")) //$NON-NLS-1$
			folderPath = folderPath.substring(0, folderPath.length() - 1);
		String filePath = folderPath.isEmpty() ? Constants.DOT_GIT_IGNORE
				: folderPath + "/" + Constants.DOT_GIT_IGNORE; //$NON-NLS-1$
		return getIgnoreNode(filePath);
	}

	private AttributesNode getAttributesNode(String path)
			throws IOException {
		ensureInitialized();

		return (AttributesNode) nodeCache.get(path);
	}

	private IgnoreNode getIgnoreNode(String path) throws IOException {
		ensureInitialized();

		return (IgnoreNode) nodeCache.get(path);
	}

	private void ensureInitialized() throws IOException {
		if (initialized || lock.writeLock().isHeldByCurrentThread())
			return;

		lock.writeLock().lock();
		try {
			// double check
			if (initialized)
				return;

			if (repo.isBare())
				return;

			nodeCache.clear();
			final FS fs = repo.getFS();

			putFile(DotFileType.ATTRIBUTES, PATH_GLOBAL_ATTRIBUTES,
					locateGlobalAttributes(repo));
			putFile(DotFileType.IGNORE, PATH_GLOBAL_EXCLUDES,
					locateGlobalExcludes(repo));
			putFile(DotFileType.ATTRIBUTES, PATH_INFO_ATTRIBUTES,
					locateInfoAttributes(repo));
			putFile(DotFileType.IGNORE, PATH_INFO_EXCLUDES,
					locateInfoExcludes(repo));

			// visit worktree and continuously interpret ignore files
			final String basePath = repo.getWorkTree().getAbsolutePath()
					+ File.separator;
			Files.walkFileTree(repo.getWorkTree().toPath(),
					new FileVisitor<Path>() {
						@Override
						public FileVisitResult preVisitDirectory(Path dirPath,
								BasicFileAttributes attrs) throws IOException {
							if (dirPath.endsWith(Constants.DOT_GIT))
								return FileVisitResult.SKIP_SUBTREE;
							File dir = dirPath.toFile();
							// check for attributes
							File f = new File(dir,
									Constants.DOT_GIT_ATTRIBUTES);
							if (f.exists()) {
								putFile(DotFileType.ATTRIBUTES, fs.relativize(
										basePath,
										f.getAbsolutePath()), f);
							}
							// check for ignore
							f = new File(dir, Constants.DOT_GIT_IGNORE);
							if (f.exists()) {
								putFile(DotFileType.IGNORE, fs.relativize(
										basePath,
										f.getAbsolutePath()), f);
							}
							// early ignore check
							String path = fs
									.relativize(basePath, dir.getAbsolutePath())
									.replace(File.separatorChar, '/');
							if (ignoreHierarchy.isIgnored(path,
									FileMode.TREE)) {
								return FileVisitResult.SKIP_SUBTREE;
							}
							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult visitFile(Path file,
								BasicFileAttributes attrs) throws IOException {
							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult visitFileFailed(Path file,
								IOException exc) throws IOException {
							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult postVisitDirectory(Path dir,
								IOException exc) throws IOException {
							return FileVisitResult.CONTINUE;
						}
					});
		}	finally{
			initialized = true;
			lock.writeLock().unlock();
		}
	}

	private void handleConfigChanged() {
		lock.writeLock().lock();
		try {
			putFile(DotFileType.ATTRIBUTES, PATH_GLOBAL_ATTRIBUTES,
					locateGlobalAttributes(repo));
			putFile(DotFileType.IGNORE, PATH_GLOBAL_EXCLUDES,
					locateGlobalExcludes(repo));
			putFile(DotFileType.ATTRIBUTES, PATH_INFO_ATTRIBUTES,
					locateInfoAttributes(repo));
			putFile(DotFileType.IGNORE, PATH_INFO_EXCLUDES,
					locateInfoExcludes(repo));
		} catch (IOException e) {
			// TODO where to log?
			e.printStackTrace();
		} finally {
			lock.writeLock().unlock();
		}
	}

	private void handleDotFilesChanged(Collection<File> changedFiles) {
		FS fs=repo.getFS();
		String basePath = repo.getWorkTree().getAbsolutePath()
				+ File.separator;

		lock.writeLock().lock();
		try {
			for (File f : changedFiles) {
				if (f == null)
					continue;
				String path = fs.relativize(basePath, f.getAbsolutePath());
				switch (f.getName()) {
				case Constants.DOT_GIT_ATTRIBUTES:
					putFile(DotFileType.ATTRIBUTES, path, f);
					break;
				case Constants.DOT_GIT_IGNORE:
					putFile(DotFileType.IGNORE, path, f);
					break;
				}
			}
			snapshotId++;
		} catch (IOException e) {
			// TODO where to log?
			e.printStackTrace();
		} finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * must be called within a write lock
	 *
	 * @param type
	 *
	 * @param path
	 *            with \ or / separators
	 * @param f
	 * @throws IOException
	 */
	private void putFile(DotFileType type, String path, File f)
			throws IOException {
		path = path.replace(File.separatorChar, '/');
		if (f == null || !f.exists()) {
			if (Debug.isInfo())
				Debug.println("DotFileTree.remove " + path); //$NON-NLS-1$
			nodeCache.remove(path);
			return;
		}

		if (Debug.isInfo())
			Debug.println("DotFileTree.add " + path + " = " + f); //$NON-NLS-1$ //$NON-NLS-2$

		switch (type) {
		case ATTRIBUTES:
			nodeCache.put(path, createAttributesNode(repo.getFS(), f));
			break;
		case IGNORE:
			nodeCache.put(path, createIgnoreNode(repo.getFS(), f));
			break;
		}
	}

	private static AttributesNode createAttributesNode(FS fs, File f)
			throws IOException {
		if (f == null || !fs.exists(f))
			return null;
		if (Debug.isInfo())
			Debug.println("AttributesHierarchy.loadNode " + f); //$NON-NLS-1$
		AttributesNode node = new AttributesNode();
		try (FileInputStream in = new FileInputStream(f)) {
			node.parse(in);
		}
		if (Debug.isInfo()) {
			for (AttributesRule rule : node.getRules()) {
				Debug.println(" " + rule); //$NON-NLS-1$
			}
		}
		return node;
	}

	private static IgnoreNode createIgnoreNode(FS fs, File f)
			throws IOException {
		if (f == null || !fs.exists(f))
			return null;
		if (Debug.isInfo())
			Debug.println("IgnoreHierarchy.loadNode " + f); //$NON-NLS-1$
		IgnoreNode node = new IgnoreNode();
		try (FileInputStream in = new FileInputStream(f)) {
			node.parse(in);
		}
		if (Debug.isInfo()) {
			for (FastIgnoreRule rule : node.getRules()) {
				Debug.println(" " + rule); //$NON-NLS-1$
			}
		}
		return node;
	}

	private static File locateGlobalAttributes(Repository repo) {
		FS fs = repo.getFS();
		String fp = repo.getConfig().get(CoreConfig.KEY).getAttributesFile();
		if (fp != null) {
			if (fp.startsWith("~/")) { //$NON-NLS-1$
				return fs.resolve(fs.userHome(), fp.substring(2));
			} else {
				return fs.resolve(null, fp);
			}
		}
		return null;
	}

	private static File locateGlobalExcludes(Repository repo) {
		FS fs = repo.getFS();
		String fp = repo.getConfig().get(CoreConfig.KEY).getExcludesFile();
		if (fp != null) {
			if (fp.startsWith("~/")) { //$NON-NLS-1$
				return fs.resolve(fs.userHome(), fp.substring(2));
			} else {
				return fs.resolve(null, fp);
			}
		}
		return null;
	}

	private static File locateInfoAttributes(Repository repo) {
		FS fs = repo.getFS();
		return fs.resolve(repo.getDirectory(), Constants.INFO_ATTRIBUTES);
	}

	private static File locateInfoExcludes(Repository repo) {
		FS fs = repo.getFS();
		return fs.resolve(repo.getDirectory(), Constants.INFO_EXCLUDE);
	}

	private static enum DotFileType {
		ATTRIBUTES, IGNORE
	}
}

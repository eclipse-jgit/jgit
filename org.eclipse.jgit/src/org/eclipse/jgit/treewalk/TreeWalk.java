/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.treewalk;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.attributes.Attribute;
import org.eclipse.jgit.attributes.Attributes;
import org.eclipse.jgit.attributes.AttributesHandler;
import org.eclipse.jgit.attributes.AttributesNodeProvider;
import org.eclipse.jgit.attributes.AttributesProvider;
import org.eclipse.jgit.attributes.FilterCommandRegistry;
import org.eclipse.jgit.dircache.DirCacheBuildIterator;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig.EolStreamType;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.QuotedString;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.io.EolStreamTypeUtil;

/**
 * Walks one or more {@link org.eclipse.jgit.treewalk.AbstractTreeIterator}s in
 * parallel.
 * <p>
 * This class can perform n-way differences across as many trees as necessary.
 * <p>
 * Each tree added must have the same root as existing trees in the walk.
 * <p>
 * A TreeWalk instance can only be used once to generate results. Running a
 * second time requires creating a new TreeWalk instance, or invoking
 * {@link #reset()} and adding new trees before starting again. Resetting an
 * existing instance may be faster for some applications as some internal
 * buffers may be recycled.
 * <p>
 * TreeWalk instances are not thread-safe. Applications must either restrict
 * usage of a TreeWalk instance to a single thread, or implement their own
 * synchronization at a higher level.
 * <p>
 * Multiple simultaneous TreeWalk instances per
 * {@link org.eclipse.jgit.lib.Repository} are permitted, even from concurrent
 * threads.
 */
public class TreeWalk implements AutoCloseable, AttributesProvider {
	private static final AbstractTreeIterator[] NO_TREES = {};

	/**
	 * @since 4.2
	 */
	public static enum OperationType {
		/**
		 * Represents a checkout operation (for example a checkout or reset
		 * operation).
		 */
		CHECKOUT_OP,

		/**
		 * Represents a checkin operation (for example an add operation)
		 */
		CHECKIN_OP
	}

	/**
	 *            Type of operation you want to retrieve the git attributes for.
	 */
	private OperationType operationType = OperationType.CHECKOUT_OP;

	/**
	 * The filter command as defined in gitattributes. The keys are
	 * filterName+"."+filterCommandType. E.g. "lfs.clean"
	 */
	private Map<String, String> filterCommandsByNameDotType = new HashMap<>();

	/**
	 * Set the operation type of this walk
	 *
	 * @param operationType
	 *            a {@link org.eclipse.jgit.treewalk.TreeWalk.OperationType}
	 *            object.
	 * @since 4.2
	 */
	public void setOperationType(OperationType operationType) {
		this.operationType = operationType;
	}

	/**
	 * Open a tree walk and filter to exactly one path.
	 * <p>
	 * The returned tree walk is already positioned on the requested path, so
	 * the caller should not need to invoke {@link #next()} unless they are
	 * looking for a possible directory/file name conflict.
	 *
	 * @param reader
	 *            the reader the walker will obtain tree data from.
	 * @param path
	 *            single path to advance the tree walk instance into.
	 * @param trees
	 *            one or more trees to walk through, all with the same root.
	 * @return a new tree walk configured for exactly this one path; null if no
	 *         path was found in any of the trees.
	 * @throws java.io.IOException
	 *             reading a pack file or loose object failed.
	 * @throws org.eclipse.jgit.errors.CorruptObjectException
	 *             an tree object could not be read as its data stream did not
	 *             appear to be a tree, or could not be inflated.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             an object we expected to be a tree was not a tree.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             a tree object was not found.
	 */
	public static TreeWalk forPath(final ObjectReader reader, final String path,
			final AnyObjectId... trees) throws MissingObjectException,
			IncorrectObjectTypeException, CorruptObjectException, IOException {
		return forPath(null, reader, path, trees);
	}

	/**
	 * Open a tree walk and filter to exactly one path.
	 * <p>
	 * The returned tree walk is already positioned on the requested path, so
	 * the caller should not need to invoke {@link #next()} unless they are
	 * looking for a possible directory/file name conflict.
	 *
	 * @param repo
	 *            repository to read config data and
	 *            {@link org.eclipse.jgit.attributes.AttributesNodeProvider}
	 *            from.
	 * @param reader
	 *            the reader the walker will obtain tree data from.
	 * @param path
	 *            single path to advance the tree walk instance into.
	 * @param trees
	 *            one or more trees to walk through, all with the same root.
	 * @return a new tree walk configured for exactly this one path; null if no
	 *         path was found in any of the trees.
	 * @throws java.io.IOException
	 *             reading a pack file or loose object failed.
	 * @throws org.eclipse.jgit.errors.CorruptObjectException
	 *             an tree object could not be read as its data stream did not
	 *             appear to be a tree, or could not be inflated.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             an object we expected to be a tree was not a tree.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             a tree object was not found.
	 * @since 4.3
	 */
	public static TreeWalk forPath(final @Nullable Repository repo,
			final ObjectReader reader, final String path,
			final AnyObjectId... trees)
					throws MissingObjectException, IncorrectObjectTypeException,
					CorruptObjectException, IOException {
		TreeWalk tw = new TreeWalk(repo, reader);
		PathFilter f = PathFilter.create(path);
		tw.setFilter(f);
		tw.reset(trees);
		tw.setRecursive(false);

		while (tw.next()) {
			if (f.isDone(tw)) {
				return tw;
			} else if (tw.isSubtree()) {
				tw.enterSubtree();
			}
		}
		return null;
	}

	/**
	 * Open a tree walk and filter to exactly one path.
	 * <p>
	 * The returned tree walk is already positioned on the requested path, so
	 * the caller should not need to invoke {@link #next()} unless they are
	 * looking for a possible directory/file name conflict.
	 *
	 * @param db
	 *            repository to read tree object data from.
	 * @param path
	 *            single path to advance the tree walk instance into.
	 * @param trees
	 *            one or more trees to walk through, all with the same root.
	 * @return a new tree walk configured for exactly this one path; null if no
	 *         path was found in any of the trees.
	 * @throws java.io.IOException
	 *             reading a pack file or loose object failed.
	 * @throws org.eclipse.jgit.errors.CorruptObjectException
	 *             an tree object could not be read as its data stream did not
	 *             appear to be a tree, or could not be inflated.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             an object we expected to be a tree was not a tree.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             a tree object was not found.
	 */
	public static TreeWalk forPath(final Repository db, final String path,
			final AnyObjectId... trees) throws MissingObjectException,
			IncorrectObjectTypeException, CorruptObjectException, IOException {
		try (ObjectReader reader = db.newObjectReader()) {
			return forPath(db, reader, path, trees);
		}
	}

	/**
	 * Open a tree walk and filter to exactly one path.
	 * <p>
	 * The returned tree walk is already positioned on the requested path, so
	 * the caller should not need to invoke {@link #next()} unless they are
	 * looking for a possible directory/file name conflict.
	 *
	 * @param db
	 *            repository to read tree object data from.
	 * @param path
	 *            single path to advance the tree walk instance into.
	 * @param tree
	 *            the single tree to walk through.
	 * @return a new tree walk configured for exactly this one path; null if no
	 *         path was found in any of the trees.
	 * @throws java.io.IOException
	 *             reading a pack file or loose object failed.
	 * @throws org.eclipse.jgit.errors.CorruptObjectException
	 *             an tree object could not be read as its data stream did not
	 *             appear to be a tree, or could not be inflated.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             an object we expected to be a tree was not a tree.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             a tree object was not found.
	 */
	public static TreeWalk forPath(final Repository db, final String path,
			final RevTree tree) throws MissingObjectException,
			IncorrectObjectTypeException, CorruptObjectException, IOException {
		return forPath(db, path, new ObjectId[] { tree });
	}

	private final ObjectReader reader;

	private final boolean closeReader;

	private final MutableObjectId idBuffer = new MutableObjectId();

	private TreeFilter filter;

	AbstractTreeIterator[] trees;

	private boolean recursive;

	private boolean postOrderTraversal;

	int depth;

	private boolean advance;

	private boolean postChildren;

	private AttributesNodeProvider attributesNodeProvider;

	AbstractTreeIterator currentHead;

	/** Cached attribute for the current entry */
	private Attributes attrs = null;

	/** Cached attributes handler */
	private AttributesHandler attributesHandler;

	private Config config;

	private Set<String> filterCommands;

	/**
	 * Create a new tree walker for a given repository.
	 *
	 * @param repo
	 *            the repository the walker will obtain data from. An
	 *            ObjectReader will be created by the walker, and will be closed
	 *            when the walker is closed.
	 */
	public TreeWalk(final Repository repo) {
		this(repo, repo.newObjectReader(), true);
	}

	/**
	 * Create a new tree walker for a given repository.
	 *
	 * @param repo
	 *            the repository the walker will obtain data from. An
	 *            ObjectReader will be created by the walker, and will be closed
	 *            when the walker is closed.
	 * @param or
	 *            the reader the walker will obtain tree data from. The reader
	 *            is not closed when the walker is closed.
	 * @since 4.3
	 */
	public TreeWalk(final @Nullable Repository repo, final ObjectReader or) {
		this(repo, or, false);
	}

	/**
	 * Create a new tree walker for a given repository.
	 *
	 * @param or
	 *            the reader the walker will obtain tree data from. The reader
	 *            is not closed when the walker is closed.
	 */
	public TreeWalk(final ObjectReader or) {
		this(null, or, false);
	}

	private TreeWalk(final @Nullable Repository repo, final ObjectReader or,
			final boolean closeReader) {
		if (repo != null) {
			config = repo.getConfig();
			attributesNodeProvider = repo.createAttributesNodeProvider();
			filterCommands = FilterCommandRegistry
					.getRegisteredFilterCommands();
		} else {
			config = null;
			attributesNodeProvider = null;
		}
		reader = or;
		filter = TreeFilter.ALL;
		trees = NO_TREES;
		this.closeReader = closeReader;
	}

	/**
	 * Get the reader this walker is using to load objects.
	 *
	 * @return the reader this walker is using to load objects.
	 */
	public ObjectReader getObjectReader() {
		return reader;
	}

	/**
	 * Get the operation type
	 *
	 * @return the {@link org.eclipse.jgit.treewalk.TreeWalk.OperationType}
	 * @since 4.3
	 */
	public OperationType getOperationType() {
		return operationType;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Release any resources used by this walker's reader.
	 * <p>
	 * A walker that has been released can be used again, but may need to be
	 * released after the subsequent usage.
	 *
	 * @since 4.0
	 */
	@Override
	public void close() {
		if (closeReader) {
			reader.close();
		}
	}

	/**
	 * Get the currently configured filter.
	 *
	 * @return the current filter. Never null as a filter is always needed.
	 */
	public TreeFilter getFilter() {
		return filter;
	}

	/**
	 * Set the tree entry filter for this walker.
	 * <p>
	 * Multiple filters may be combined by constructing an arbitrary tree of
	 * <code>AndTreeFilter</code> or <code>OrTreeFilter</code> instances to
	 * describe the boolean expression required by the application. Custom
	 * filter implementations may also be constructed by applications.
	 * <p>
	 * Note that filters are not thread-safe and may not be shared by concurrent
	 * TreeWalk instances. Every TreeWalk must be supplied its own unique
	 * filter, unless the filter implementation specifically states it is (and
	 * always will be) thread-safe. Callers may use
	 * {@link org.eclipse.jgit.treewalk.filter.TreeFilter#clone()} to create a
	 * unique filter tree for this TreeWalk instance.
	 *
	 * @param newFilter
	 *            the new filter. If null the special
	 *            {@link org.eclipse.jgit.treewalk.filter.TreeFilter#ALL} filter
	 *            will be used instead, as it matches every entry.
	 * @see org.eclipse.jgit.treewalk.filter.AndTreeFilter
	 * @see org.eclipse.jgit.treewalk.filter.OrTreeFilter
	 */
	public void setFilter(final TreeFilter newFilter) {
		filter = newFilter != null ? newFilter : TreeFilter.ALL;
	}

	/**
	 * Is this walker automatically entering into subtrees?
	 * <p>
	 * If the walker is recursive then the caller will not see a subtree node
	 * and instead will only receive file nodes in all relevant subtrees.
	 *
	 * @return true if automatically entering subtrees is enabled.
	 */
	public boolean isRecursive() {
		return recursive;
	}

	/**
	 * Set the walker to enter (or not enter) subtrees automatically.
	 * <p>
	 * If recursive mode is enabled the walker will hide subtree nodes from the
	 * calling application and will produce only file level nodes. If a tree
	 * (directory) is deleted then all of the file level nodes will appear to be
	 * deleted, recursively, through as many levels as necessary to account for
	 * all entries.
	 *
	 * @param b
	 *            true to skip subtree nodes and only obtain files nodes.
	 */
	public void setRecursive(final boolean b) {
		recursive = b;
	}

	/**
	 * Does this walker return a tree entry after it exits the subtree?
	 * <p>
	 * If post order traversal is enabled then the walker will return a subtree
	 * after it has returned the last entry within that subtree. This may cause
	 * a subtree to be seen by the application twice if {@link #isRecursive()}
	 * is false, as the application will see it once, call
	 * {@link #enterSubtree()}, and then see it again as it leaves the subtree.
	 * <p>
	 * If an application does not enable {@link #isRecursive()} and it does not
	 * call {@link #enterSubtree()} then the tree is returned only once as none
	 * of the children were processed.
	 *
	 * @return true if subtrees are returned after entries within the subtree.
	 */
	public boolean isPostOrderTraversal() {
		return postOrderTraversal;
	}

	/**
	 * Set the walker to return trees after their children.
	 *
	 * @param b
	 *            true to get trees after their children.
	 * @see #isPostOrderTraversal()
	 */
	public void setPostOrderTraversal(final boolean b) {
		postOrderTraversal = b;
	}

	/**
	 * Sets the {@link org.eclipse.jgit.attributes.AttributesNodeProvider} for
	 * this {@link org.eclipse.jgit.treewalk.TreeWalk}.
	 * <p>
	 * This is a requirement for a correct computation of the git attributes. If
	 * this {@link org.eclipse.jgit.treewalk.TreeWalk} has been built using
	 * {@link #TreeWalk(Repository)} constructor, the
	 * {@link org.eclipse.jgit.attributes.AttributesNodeProvider} has already
	 * been set. Indeed,the {@link org.eclipse.jgit.lib.Repository} can provide
	 * an {@link org.eclipse.jgit.attributes.AttributesNodeProvider} using
	 * {@link org.eclipse.jgit.lib.Repository#createAttributesNodeProvider()}
	 * method. Otherwise you should provide one.
	 * </p>
	 *
	 * @see Repository#createAttributesNodeProvider()
	 * @param provider
	 *            a {@link org.eclipse.jgit.attributes.AttributesNodeProvider}
	 *            object.
	 * @since 4.2
	 */
	public void setAttributesNodeProvider(AttributesNodeProvider provider) {
		attributesNodeProvider = provider;
	}

	/**
	 * Get the attributes node provider
	 *
	 * @return the {@link org.eclipse.jgit.attributes.AttributesNodeProvider}
	 *         for this {@link org.eclipse.jgit.treewalk.TreeWalk}.
	 * @since 4.3
	 */
	public AttributesNodeProvider getAttributesNodeProvider() {
		return attributesNodeProvider;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Retrieve the git attributes for the current entry.
	 *
	 * <h3>Git attribute computation</h3>
	 *
	 * <ul>
	 * <li>Get the attributes matching the current path entry from the info file
	 * (see {@link AttributesNodeProvider#getInfoAttributesNode()}).</li>
	 * <li>Completes the list of attributes using the .gitattributes files
	 * located on the current path (the further the directory that contains
	 * .gitattributes is from the path in question, the lower its precedence).
	 * For a checkin operation, it will look first on the working tree (if any).
	 * If there is no attributes file, it will fallback on the index. For a
	 * checkout operation, it will first use the index entry and then fallback
	 * on the working tree if none.</li>
	 * <li>In the end, completes the list of matching attributes using the
	 * global attribute file define in the configuration (see
	 * {@link AttributesNodeProvider#getGlobalAttributesNode()})</li>
	 *
	 * </ul>
	 *
	 *
	 * <h3>Iterator constraints</h3>
	 *
	 * <p>
	 * In order to have a correct list of attributes for the current entry, this
	 * {@link TreeWalk} requires to have at least one
	 * {@link AttributesNodeProvider} and a {@link DirCacheIterator} set up. An
	 * {@link AttributesNodeProvider} is used to retrieve the attributes from
	 * the info attributes file and the global attributes file. The
	 * {@link DirCacheIterator} is used to retrieve the .gitattributes files
	 * stored in the index. A {@link WorkingTreeIterator} can also be provided
	 * to access the local version of the .gitattributes files. If none is
	 * provided it will fallback on the {@link DirCacheIterator}.
	 * </p>
	 *
	 * @since 4.2
	 */
	@Override
	public Attributes getAttributes() {
		if (attrs != null)
			return attrs;

		if (attributesNodeProvider == null) {
			// The work tree should have a AttributesNodeProvider to be able to
			// retrieve the info and global attributes node
			throw new IllegalStateException(
					"The tree walk should have one AttributesNodeProvider set in order to compute the git attributes."); //$NON-NLS-1$
		}

		try {
			// Lazy create the attributesHandler on the first access of
			// attributes. This requires the info, global and root
			// attributes nodes
			if (attributesHandler == null) {
				attributesHandler = new AttributesHandler(this);
			}
			attrs = attributesHandler.getAttributes();
			return attrs;
		} catch (IOException e) {
			throw new JGitInternalException("Error while parsing attributes", //$NON-NLS-1$
					e);
		}
	}

	/**
	 * Get the EOL stream type of the current entry using the config and
	 * {@link #getAttributes()}.
	 *
	 * @param opType
	 *            the operationtype (checkin/checkout) which should be used
	 * @return the EOL stream type of the current entry using the config and
	 *         {@link #getAttributes()}. Note that this method may return null
	 *         if the {@link org.eclipse.jgit.treewalk.TreeWalk} is not based on
	 *         a working tree
	 * @since 4.10
	 */
	@Nullable
	public EolStreamType getEolStreamType(OperationType opType) {
		if (attributesNodeProvider == null || config == null)
			return null;
		return EolStreamTypeUtil.detectStreamType(
				opType != null ? opType : operationType,
					config.get(WorkingTreeOptions.KEY), getAttributes());
	}

	/**
	 * Get the EOL stream type of the current entry using the config and
	 * {@link #getAttributes()}.
	 *
	 * @return the EOL stream type of the current entry using the config and
	 *         {@link #getAttributes()}. Note that this method may return null
	 *         if the {@link org.eclipse.jgit.treewalk.TreeWalk} is not based on
	 *         a working tree
	 * @since 4.3
	 * @deprecated use {@link #getEolStreamType(OperationType)} instead.
	 */
	@Deprecated
	public @Nullable EolStreamType getEolStreamType() {
		return (getEolStreamType(operationType));
	}

	/**
	 * Reset this walker so new tree iterators can be added to it.
	 */
	public void reset() {
		attrs = null;
		attributesHandler = null;
		trees = NO_TREES;
		advance = false;
		depth = 0;
	}

	/**
	 * Reset this walker to run over a single existing tree.
	 *
	 * @param id
	 *            the tree we need to parse. The walker will execute over this
	 *            single tree if the reset is successful.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             the given tree object does not exist in this repository.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             the given object id does not denote a tree, but instead names
	 *             some other non-tree type of object. Note that commits are not
	 *             trees, even if they are sometimes called a "tree-ish".
	 * @throws org.eclipse.jgit.errors.CorruptObjectException
	 *             the object claimed to be a tree, but its contents did not
	 *             appear to be a tree. The repository may have data corruption.
	 * @throws java.io.IOException
	 *             a loose object or pack file could not be read.
	 */
	public void reset(final AnyObjectId id) throws MissingObjectException,
			IncorrectObjectTypeException, CorruptObjectException, IOException {
		if (trees.length == 1) {
			AbstractTreeIterator o = trees[0];
			while (o.parent != null)
				o = o.parent;
			if (o instanceof CanonicalTreeParser) {
				o.matches = null;
				o.matchShift = 0;
				((CanonicalTreeParser) o).reset(reader, id);
				trees[0] = o;
			} else {
				trees[0] = parserFor(id);
			}
		} else {
			trees = new AbstractTreeIterator[] { parserFor(id) };
		}

		advance = false;
		depth = 0;
		attrs = null;
	}

	/**
	 * Reset this walker to run over a set of existing trees.
	 *
	 * @param ids
	 *            the trees we need to parse. The walker will execute over this
	 *            many parallel trees if the reset is successful.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             the given tree object does not exist in this repository.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             the given object id does not denote a tree, but instead names
	 *             some other non-tree type of object. Note that commits are not
	 *             trees, even if they are sometimes called a "tree-ish".
	 * @throws org.eclipse.jgit.errors.CorruptObjectException
	 *             the object claimed to be a tree, but its contents did not
	 *             appear to be a tree. The repository may have data corruption.
	 * @throws java.io.IOException
	 *             a loose object or pack file could not be read.
	 */
	public void reset(final AnyObjectId... ids) throws MissingObjectException,
			IncorrectObjectTypeException, CorruptObjectException, IOException {
		final int oldLen = trees.length;
		final int newLen = ids.length;
		final AbstractTreeIterator[] r = newLen == oldLen ? trees
				: new AbstractTreeIterator[newLen];
		for (int i = 0; i < newLen; i++) {
			AbstractTreeIterator o;

			if (i < oldLen) {
				o = trees[i];
				while (o.parent != null)
					o = o.parent;
				if (o instanceof CanonicalTreeParser && o.pathOffset == 0) {
					o.matches = null;
					o.matchShift = 0;
					((CanonicalTreeParser) o).reset(reader, ids[i]);
					r[i] = o;
					continue;
				}
			}

			o = parserFor(ids[i]);
			r[i] = o;
		}

		trees = r;
		advance = false;
		depth = 0;
		attrs = null;
	}

	/**
	 * Add an already existing tree object for walking.
	 * <p>
	 * The position of this tree is returned to the caller, in case the caller
	 * has lost track of the order they added the trees into the walker.
	 * <p>
	 * The tree must have the same root as existing trees in the walk.
	 *
	 * @param id
	 *            identity of the tree object the caller wants walked.
	 * @return position of this tree within the walker.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             the given tree object does not exist in this repository.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             the given object id does not denote a tree, but instead names
	 *             some other non-tree type of object. Note that commits are not
	 *             trees, even if they are sometimes called a "tree-ish".
	 * @throws org.eclipse.jgit.errors.CorruptObjectException
	 *             the object claimed to be a tree, but its contents did not
	 *             appear to be a tree. The repository may have data corruption.
	 * @throws java.io.IOException
	 *             a loose object or pack file could not be read.
	 */
	public int addTree(final AnyObjectId id) throws MissingObjectException,
			IncorrectObjectTypeException, CorruptObjectException, IOException {
		return addTree(parserFor(id));
	}

	/**
	 * Add an already created tree iterator for walking.
	 * <p>
	 * The position of this tree is returned to the caller, in case the caller
	 * has lost track of the order they added the trees into the walker.
	 * <p>
	 * The tree which the iterator operates on must have the same root as
	 * existing trees in the walk.
	 *
	 * @param p
	 *            an iterator to walk over. The iterator should be new, with no
	 *            parent, and should still be positioned before the first entry.
	 *            The tree which the iterator operates on must have the same
	 *            root as other trees in the walk.
	 * @return position of this tree within the walker.
	 */
	public int addTree(AbstractTreeIterator p) {
		int n = trees.length;
		AbstractTreeIterator[] newTrees = new AbstractTreeIterator[n + 1];

		System.arraycopy(trees, 0, newTrees, 0, n);
		newTrees[n] = p;
		p.matches = null;
		p.matchShift = 0;

		trees = newTrees;
		return n;
	}

	/**
	 * Get the number of trees known to this walker.
	 *
	 * @return the total number of trees this walker is iterating over.
	 */
	public int getTreeCount() {
		return trees.length;
	}

	/**
	 * Advance this walker to the next relevant entry.
	 *
	 * @return true if there is an entry available; false if all entries have
	 *         been walked and the walk of this set of tree iterators is over.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             {@link #isRecursive()} was enabled, a subtree was found, but
	 *             the subtree object does not exist in this repository. The
	 *             repository may be missing objects.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             {@link #isRecursive()} was enabled, a subtree was found, and
	 *             the subtree id does not denote a tree, but instead names some
	 *             other non-tree type of object. The repository may have data
	 *             corruption.
	 * @throws org.eclipse.jgit.errors.CorruptObjectException
	 *             the contents of a tree did not appear to be a tree. The
	 *             repository may have data corruption.
	 * @throws java.io.IOException
	 *             a loose object or pack file could not be read.
	 */
	public boolean next() throws MissingObjectException,
			IncorrectObjectTypeException, CorruptObjectException, IOException {
		try {
			if (advance) {
				advance = false;
				postChildren = false;
				popEntriesEqual();
			}

			for (;;) {
				attrs = null;
				final AbstractTreeIterator t = min();
				if (t.eof()) {
					if (depth > 0) {
						exitSubtree();
						if (postOrderTraversal) {
							advance = true;
							postChildren = true;
							return true;
						}
						popEntriesEqual();
						continue;
					}
					return false;
				}

				currentHead = t;
				if (filter.matchFilter(this) == 1) {
					skipEntriesEqual();
					continue;
				}

				if (recursive && FileMode.TREE.equals(t.mode)) {
					enterSubtree();
					continue;
				}

				advance = true;
				return true;
			}
		} catch (StopWalkException stop) {
			stopWalk();
			return false;
		}
	}

	/**
	 * Notify iterators the walk is aborting.
	 * <p>
	 * Primarily to notify {@link DirCacheBuildIterator} the walk is aborting so
	 * that it can copy any remaining entries.
	 *
	 * @throws IOException
	 *             if traversal of remaining entries throws an exception during
	 *             object access. This should never occur as remaining trees
	 *             should already be in memory, however the methods used to
	 *             finish traversal are declared to throw IOException.
	 */
	void stopWalk() throws IOException {
		for (AbstractTreeIterator t : trees) {
			t.stopWalk();
		}
	}

	/**
	 * Obtain the tree iterator for the current entry.
	 * <p>
	 * Entering into (or exiting out of) a subtree causes the current tree
	 * iterator instance to be changed for the nth tree. This allows the tree
	 * iterators to manage only one list of items, with the diving handled by
	 * recursive trees.
	 *
	 * @param nth
	 *            tree to obtain the current iterator of.
	 * @param clazz
	 *            type of the tree iterator expected by the caller.
	 * @return r the current iterator of the requested type; null if the tree
	 *         has no entry to match the current path.
	 */
	@SuppressWarnings("unchecked")
	public <T extends AbstractTreeIterator> T getTree(final int nth,
			final Class<T> clazz) {
		final AbstractTreeIterator t = trees[nth];
		return t.matches == currentHead ? (T) t : null;
	}

	/**
	 * Obtain the raw {@link org.eclipse.jgit.lib.FileMode} bits for the current
	 * entry.
	 * <p>
	 * Every added tree supplies mode bits, even if the tree does not contain
	 * the current entry. In the latter case
	 * {@link org.eclipse.jgit.lib.FileMode#MISSING}'s mode bits (0) are
	 * returned.
	 *
	 * @param nth
	 *            tree to obtain the mode bits from.
	 * @return mode bits for the current entry of the nth tree.
	 * @see FileMode#fromBits(int)
	 */
	public int getRawMode(final int nth) {
		final AbstractTreeIterator t = trees[nth];
		return t.matches == currentHead ? t.mode : 0;
	}

	/**
	 * Obtain the {@link org.eclipse.jgit.lib.FileMode} for the current entry.
	 * <p>
	 * Every added tree supplies a mode, even if the tree does not contain the
	 * current entry. In the latter case
	 * {@link org.eclipse.jgit.lib.FileMode#MISSING} is returned.
	 *
	 * @param nth
	 *            tree to obtain the mode from.
	 * @return mode for the current entry of the nth tree.
	 */
	public FileMode getFileMode(final int nth) {
		return FileMode.fromBits(getRawMode(nth));
	}

	/**
	 * Obtain the {@link org.eclipse.jgit.lib.FileMode} for the current entry on
	 * the currentHead tree
	 *
	 * @return mode for the current entry of the currentHead tree.
	 * @since 4.3
	 */
	public FileMode getFileMode() {
		return FileMode.fromBits(currentHead.mode);
	}

	/**
	 * Obtain the ObjectId for the current entry.
	 * <p>
	 * Using this method to compare ObjectId values between trees of this walker
	 * is very inefficient. Applications should try to use
	 * {@link #idEqual(int, int)} or {@link #getObjectId(MutableObjectId, int)}
	 * whenever possible.
	 * <p>
	 * Every tree supplies an object id, even if the tree does not contain the
	 * current entry. In the latter case
	 * {@link org.eclipse.jgit.lib.ObjectId#zeroId()} is returned.
	 *
	 * @param nth
	 *            tree to obtain the object identifier from.
	 * @return object identifier for the current tree entry.
	 * @see #getObjectId(MutableObjectId, int)
	 * @see #idEqual(int, int)
	 * @see #getObjectId(MutableObjectId, int)
	 * @see #idEqual(int, int)
	 */
	public ObjectId getObjectId(final int nth) {
		final AbstractTreeIterator t = trees[nth];
		return t.matches == currentHead ? t.getEntryObjectId() : ObjectId
				.zeroId();
	}

	/**
	 * Obtain the ObjectId for the current entry.
	 * <p>
	 * Every tree supplies an object id, even if the tree does not contain the
	 * current entry. In the latter case
	 * {@link org.eclipse.jgit.lib.ObjectId#zeroId()} is supplied.
	 * <p>
	 * Applications should try to use {@link #idEqual(int, int)} when possible
	 * as it avoids conversion overheads.
	 *
	 * @param out
	 *            buffer to copy the object id into.
	 * @param nth
	 *            tree to obtain the object identifier from.
	 * @see #idEqual(int, int)
	 */
	public void getObjectId(final MutableObjectId out, final int nth) {
		final AbstractTreeIterator t = trees[nth];
		if (t.matches == currentHead)
			t.getEntryObjectId(out);
		else
			out.clear();
	}

	/**
	 * Compare two tree's current ObjectId values for equality.
	 *
	 * @param nthA
	 *            first tree to compare the object id from.
	 * @param nthB
	 *            second tree to compare the object id from.
	 * @return result of
	 *         <code>getObjectId(nthA).equals(getObjectId(nthB))</code>.
	 * @see #getObjectId(int)
	 */
	public boolean idEqual(final int nthA, final int nthB) {
		final AbstractTreeIterator ch = currentHead;
		final AbstractTreeIterator a = trees[nthA];
		final AbstractTreeIterator b = trees[nthB];
		if (a.matches != ch && b.matches != ch) {
			// If neither tree matches the current path node then neither
			// tree has this entry. In such case the ObjectId is zero(),
			// and zero() is always equal to zero().
			//
			return true;
		}
		if (!a.hasId() || !b.hasId())
			return false;
		if (a.matches == ch && b.matches == ch)
			return a.idEqual(b);
		return false;
	}

	/**
	 * Get the current entry's name within its parent tree.
	 * <p>
	 * This method is not very efficient and is primarily meant for debugging
	 * and final output generation. Applications should try to avoid calling it,
	 * and if invoked do so only once per interesting entry, where the name is
	 * absolutely required for correct function.
	 *
	 * @return name of the current entry within the parent tree (or directory).
	 *         The name never includes a '/'.
	 */
	public String getNameString() {
		final AbstractTreeIterator t = currentHead;
		final int off = t.pathOffset;
		final int end = t.pathLen;
		return RawParseUtils.decode(Constants.CHARSET, t.path, off, end);
	}

	/**
	 * Get the current entry's complete path.
	 * <p>
	 * This method is not very efficient and is primarily meant for debugging
	 * and final output generation. Applications should try to avoid calling it,
	 * and if invoked do so only once per interesting entry, where the name is
	 * absolutely required for correct function.
	 *
	 * @return complete path of the current entry, from the root of the
	 *         repository. If the current entry is in a subtree there will be at
	 *         least one '/' in the returned string.
	 */
	public String getPathString() {
		return pathOf(currentHead);
	}

	/**
	 * Get the current entry's complete path as a UTF-8 byte array.
	 *
	 * @return complete path of the current entry, from the root of the
	 *         repository. If the current entry is in a subtree there will be at
	 *         least one '/' in the returned string.
	 */
	public byte[] getRawPath() {
		final AbstractTreeIterator t = currentHead;
		final int n = t.pathLen;
		final byte[] r = new byte[n];
		System.arraycopy(t.path, 0, r, 0, n);
		return r;
	}

	/**
	 * Get the path length of the current entry.
	 *
	 * @return The path length of the current entry.
	 */
	public int getPathLength() {
		return currentHead.pathLen;
	}

	/**
	 * Test if the supplied path matches the current entry's path.
	 * <p>
	 * This method detects if the supplied path is equal to, a subtree of, or
	 * not similar at all to the current entry. It is faster to use this
	 * method than to use {@link #getPathString()} to first create a String
	 * object, then test <code>startsWith</code> or some other type of string
	 * match function.
	 * <p>
	 * If the current entry is a subtree, then all paths within the subtree
	 * are considered to match it.
	 *
	 * @param p
	 *            path buffer to test. Callers should ensure the path does not
	 *            end with '/' prior to invocation.
	 * @param pLen
	 *            number of bytes from <code>buf</code> to test.
	 * @return -1 if the current path is a parent to p; 0 if p matches the current
	 *         path; 1 if the current path is different and will never match
	 *         again on this tree walk.
	 * @since 4.7
	 */
	public int isPathMatch(final byte[] p, final int pLen) {
		final AbstractTreeIterator t = currentHead;
		final byte[] c = t.path;
		final int cLen = t.pathLen;
		int ci;

		for (ci = 0; ci < cLen && ci < pLen; ci++) {
			final int c_value = (c[ci] & 0xff) - (p[ci] & 0xff);
			if (c_value != 0) {
				// Paths do not and will never match
				return 1;
			}
		}

		if (ci < cLen) {
			// Ran out of pattern but we still had current data.
			// If c[ci] == '/' then pattern matches the subtree.
			// Otherwise it is a partial match == miss
			return c[ci] == '/' ? 0 : 1;
		}

		if (ci < pLen) {
			// Ran out of current, but we still have pattern data.
			// If p[ci] == '/' then this subtree is a parent in the pattern,
			// otherwise it's a miss.
			return p[ci] == '/' && FileMode.TREE.equals(t.mode) ? -1 : 1;
		}

		// Both strings are identical.
		return 0;
	}

	/**
	 * Test if the supplied path matches the current entry's path.
	 * <p>
	 * This method tests that the supplied path is exactly equal to the current
	 * entry or is one of its parent directories. It is faster to use this
	 * method then to use {@link #getPathString()} to first create a String
	 * object, then test <code>startsWith</code> or some other type of string
	 * match function.
	 * <p>
	 * If the current entry is a subtree, then all paths within the subtree
	 * are considered to match it.
	 *
	 * @param p
	 *            path buffer to test. Callers should ensure the path does not
	 *            end with '/' prior to invocation.
	 * @param pLen
	 *            number of bytes from <code>buf</code> to test.
	 * @return &lt; 0 if p is before the current path; 0 if p matches the current
	 *         path; 1 if the current path is past p and p will never match
	 *         again on this tree walk.
	 */
	public int isPathPrefix(final byte[] p, final int pLen) {
		final AbstractTreeIterator t = currentHead;
		final byte[] c = t.path;
		final int cLen = t.pathLen;
		int ci;

		for (ci = 0; ci < cLen && ci < pLen; ci++) {
			final int c_value = (c[ci] & 0xff) - (p[ci] & 0xff);
			if (c_value != 0)
				return c_value;
		}

		if (ci < cLen) {
			// Ran out of pattern but we still had current data.
			// If c[ci] == '/' then pattern matches the subtree.
			// Otherwise we cannot be certain so we return -1.
			//
			return c[ci] == '/' ? 0 : -1;
		}

		if (ci < pLen) {
			// Ran out of current, but we still have pattern data.
			// If p[ci] == '/' then pattern matches this subtree,
			// otherwise we cannot be certain so we return -1.
			//
			return p[ci] == '/' && FileMode.TREE.equals(t.mode) ? 0 : -1;
		}

		// Both strings are identical.
		//
		return 0;
	}

	/**
	 * Test if the supplied path matches (being suffix of) the current entry's
	 * path.
	 * <p>
	 * This method tests that the supplied path is exactly equal to the current
	 * entry, or is relative to one of entry's parent directories. It is faster
	 * to use this method then to use {@link #getPathString()} to first create
	 * a String object, then test <code>endsWith</code> or some other type of
	 * string match function.
	 *
	 * @param p
	 *            path buffer to test.
	 * @param pLen
	 *            number of bytes from <code>buf</code> to test.
	 * @return true if p is suffix of the current path;
	 *         false if otherwise
	 */
	public boolean isPathSuffix(final byte[] p, final int pLen) {
		final AbstractTreeIterator t = currentHead;
		final byte[] c = t.path;
		final int cLen = t.pathLen;

		for (int i = 1; i <= pLen; i++) {
			// Pattern longer than current path
			if (i > cLen)
				return false;
			// Current path doesn't match pattern
			if (c[cLen - i] != p[pLen - i])
				return false;
		}

		// Whole pattern tested -> matches
		return true;
	}

	/**
	 * Get the current subtree depth of this walker.
	 *
	 * @return the current subtree depth of this walker.
	 */
	public int getDepth() {
		return depth;
	}

	/**
	 * Is the current entry a subtree?
	 * <p>
	 * This method is faster then testing the raw mode bits of all trees to see
	 * if any of them are a subtree. If at least one is a subtree then this
	 * method will return true.
	 *
	 * @return true if {@link #enterSubtree()} will work on the current node.
	 */
	public boolean isSubtree() {
		return FileMode.TREE.equals(currentHead.mode);
	}

	/**
	 * Is the current entry a subtree returned after its children?
	 *
	 * @return true if the current node is a tree that has been returned after
	 *         its children were already processed.
	 * @see #isPostOrderTraversal()
	 */
	public boolean isPostChildren() {
		return postChildren && isSubtree();
	}

	/**
	 * Enter into the current subtree.
	 * <p>
	 * If the current entry is a subtree this method arranges for its children
	 * to be returned before the next sibling following the subtree is returned.
	 *
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             a subtree was found, but the subtree object does not exist in
	 *             this repository. The repository may be missing objects.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             a subtree was found, and the subtree id does not denote a
	 *             tree, but instead names some other non-tree type of object.
	 *             The repository may have data corruption.
	 * @throws org.eclipse.jgit.errors.CorruptObjectException
	 *             the contents of a tree did not appear to be a tree. The
	 *             repository may have data corruption.
	 * @throws java.io.IOException
	 *             a loose object or pack file could not be read.
	 */
	public void enterSubtree() throws MissingObjectException,
			IncorrectObjectTypeException, CorruptObjectException, IOException {
		attrs = null;
		final AbstractTreeIterator ch = currentHead;
		final AbstractTreeIterator[] tmp = new AbstractTreeIterator[trees.length];
		for (int i = 0; i < trees.length; i++) {
			final AbstractTreeIterator t = trees[i];
			final AbstractTreeIterator n;
			// If we find a GITLINK when attempting to enter a subtree, then the
			// GITLINK must exist as a TREE in the index, meaning the working tree
			// entry should be treated as a TREE
			if (t.matches == ch && !t.eof() &&
					(FileMode.TREE.equals(t.mode)
							|| (FileMode.GITLINK.equals(t.mode) && t.isWorkTree())))
				n = t.createSubtreeIterator(reader, idBuffer);
			else
				n = t.createEmptyTreeIterator();
			tmp[i] = n;
		}
		depth++;
		advance = false;
		System.arraycopy(tmp, 0, trees, 0, trees.length);
	}

	@SuppressWarnings("unused")
	AbstractTreeIterator min() throws CorruptObjectException {
		int i = 0;
		AbstractTreeIterator minRef = trees[i];
		while (minRef.eof() && ++i < trees.length)
			minRef = trees[i];
		if (minRef.eof())
			return minRef;

		minRef.matches = minRef;
		while (++i < trees.length) {
			final AbstractTreeIterator t = trees[i];
			if (t.eof())
				continue;
			final int cmp = t.pathCompare(minRef);
			if (cmp < 0) {
				t.matches = t;
				minRef = t;
			} else if (cmp == 0) {
				t.matches = minRef;
			}
		}

		return minRef;
	}

	void popEntriesEqual() throws CorruptObjectException {
		final AbstractTreeIterator ch = currentHead;
		for (int i = 0; i < trees.length; i++) {
			final AbstractTreeIterator t = trees[i];
			if (t.matches == ch) {
				t.next(1);
				t.matches = null;
			}
		}
	}

	void skipEntriesEqual() throws CorruptObjectException {
		final AbstractTreeIterator ch = currentHead;
		for (int i = 0; i < trees.length; i++) {
			final AbstractTreeIterator t = trees[i];
			if (t.matches == ch) {
				t.skip();
				t.matches = null;
			}
		}
	}

	void exitSubtree() {
		depth--;
		for (int i = 0; i < trees.length; i++)
			trees[i] = trees[i].parent;

		AbstractTreeIterator minRef = null;
		for (final AbstractTreeIterator t : trees) {
			if (t.matches != t)
				continue;
			if (minRef == null || t.pathCompare(minRef) < 0)
				minRef = t;
		}
		currentHead = minRef;
	}

	private CanonicalTreeParser parserFor(final AnyObjectId id)
			throws IncorrectObjectTypeException, IOException {
		final CanonicalTreeParser p = new CanonicalTreeParser();
		p.reset(reader, id);
		return p;
	}

	static String pathOf(final AbstractTreeIterator t) {
		return RawParseUtils.decode(Constants.CHARSET, t.path, 0, t.pathLen);
	}

	static String pathOf(final byte[] buf, int pos, int end) {
		return RawParseUtils.decode(Constants.CHARSET, buf, pos, end);
	}

	/**
	 * Get the tree of that type.
	 *
	 * @param type
	 *            of the tree to be queried
	 * @return the tree of that type or null if none is present.
	 * @since 4.3
	 * @param <T>
	 *            a tree type.
	 */
	public <T extends AbstractTreeIterator> T getTree(
			Class<T> type) {
		for (int i = 0; i < trees.length; i++) {
			AbstractTreeIterator tree = trees[i];
			if (type.isInstance(tree)) {
				return type.cast(tree);
			}
		}
		return null;
	}

	/**
	 * Inspect config and attributes to return a filtercommand applicable for
	 * the current path, but without expanding %f occurences
	 *
	 * @param filterCommandType
	 *            which type of filterCommand should be executed. E.g. "clean",
	 *            "smudge"
	 * @return a filter command
	 * @throws java.io.IOException
	 * @since 4.2
	 */
	public String getFilterCommand(String filterCommandType)
			throws IOException {
		Attributes attributes = getAttributes();

		Attribute f = attributes.get(Constants.ATTR_FILTER);
		if (f == null) {
			return null;
		}
		String filterValue = f.getValue();
		if (filterValue == null) {
			return null;
		}

		String filterCommand = getFilterCommandDefinition(filterValue,
				filterCommandType);
		if (filterCommand == null) {
			return null;
		}
		return filterCommand.replaceAll("%f", //$NON-NLS-1$
				QuotedString.BOURNE.quote((getPathString())));
	}

	/**
	 * Get the filter command how it is defined in gitconfig. The returned
	 * string may contain "%f" which needs to be replaced by the current path
	 * before executing the filter command. These filter definitions are cached
	 * for better performance.
	 *
	 * @param filterDriverName
	 *            The name of the filter driver as it is referenced in the
	 *            gitattributes file. E.g. "lfs". For each filter driver there
	 *            may be many commands defined in the .gitconfig
	 * @param filterCommandType
	 *            The type of the filter command for a specific filter driver.
	 *            May be "clean" or "smudge".
	 * @return the definition of the command to be executed for this filter
	 *         driver and filter command
	 */
	private String getFilterCommandDefinition(String filterDriverName,
			String filterCommandType) {
		String key = filterDriverName + "." + filterCommandType; //$NON-NLS-1$
		String filterCommand = filterCommandsByNameDotType.get(key);
		if (filterCommand != null)
			return filterCommand;
		filterCommand = config.getString(ConfigConstants.CONFIG_FILTER_SECTION,
				filterDriverName, filterCommandType);
		boolean useBuiltin = config.getBoolean(
				ConfigConstants.CONFIG_FILTER_SECTION,
				filterDriverName, ConfigConstants.CONFIG_KEY_USEJGITBUILTIN, false);
		if (useBuiltin) {
			String builtinFilterCommand = Constants.BUILTIN_FILTER_PREFIX
					+ filterDriverName + '/' + filterCommandType;
			if (filterCommands != null
					&& filterCommands.contains(builtinFilterCommand)) {
				filterCommand = builtinFilterCommand;
			}
		}
		if (filterCommand != null) {
			filterCommandsByNameDotType.put(key, filterCommand);
		}
		return filterCommand;
	}
}

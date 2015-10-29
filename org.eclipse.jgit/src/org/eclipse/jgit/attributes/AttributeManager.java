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
package org.eclipse.jgit.attributes;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.attributes.Attribute.State;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.AbstractDotFileManager;
import org.eclipse.jgit.util.Debug;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.StringUtils;

/**
 * Git attribute manager for a repo
 * <p>
 * This instance does lazy loading and caches the detected .gitattributes
 * <p>
 * http://git-scm.com/docs/gitattributes
 */
public class AttributeManager
 extends AbstractDotFileManager<AttributesNode> {
	private static final String MACRO_PREFIX = "[attr]"; //$NON-NLS-1$

	private Map<String, List<Attribute>> m_macrosSnapshot = null;

	/**
	 * @param root
	 * @param fs
	 */
	public AttributeManager(File root, FS fs) {
		super(root, fs, Constants.DOT_GIT_ATTRIBUTES);
		if (Debug.isInfo())
			Debug.println("AttributeManager.<init> " + root); //$NON-NLS-1$
	}

	/**
	 * Setup locators for global and info .gitattributes
	 *
	 * @param repository
	 */
	public void initFromRepository(final Repository repository) {
		setGlobalFileLocator(new IFileLocator() {
			@Override
			public File locateFile() throws IOException {
				String path = repository.getConfig().get(CoreConfig.KEY)
						.getAttributesFile();
				if (path == null)
					return null;
				FS fs = repository.getFS();
				if (path.startsWith("~/")) { //$NON-NLS-1$
					return fs.resolve(fs.userHome(), path.substring(2));
				}
				return fs.resolve(null, path);
			}
		});
		setInfoFileLocator(new IFileLocator() {
			@Override
			public File locateFile() throws IOException {
				FS fs = repository.getFS();
				return fs.resolve(repository.getDirectory(),
						Constants.INFO_ATTRIBUTES);
			}
		});
	}

	@Override
	protected AttributesNode loadNode(File f) throws IOException {
		if (Debug.isInfo())
			Debug.println("AttributeManager.loadNode " + f); //$NON-NLS-1$
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

	@Override
	protected void clearSnapshots() {
		m_macrosSnapshot = null;
	}

	/**
	 * @param path
	 * @param fileMode
	 * @return attributes with all macros expanded
	 * @throws IOException
	 */
	public AttributeSet getAttributes(String path, FileMode fileMode)
			throws IOException {
		final boolean isDirectory = FileMode.TREE.equals(fileMode);
		AttributeSet result = new AttributeSet();
		// accumulate per-node attributes (no overrides)
		collectSubTreeAttributes(path, isDirectory, result);

		// accumulate root attributes (no overrides)
		AttributesNode node = lookupRootNode();
		if (node != null) {
			node.getAttributes(this, path, isDirectory, result);
		}

		// accumulate global attributes (no overrides)
		node = lookupGlobalNode();
		if (node != null) {
			node.getAttributes(this, path, isDirectory, result);
		}

		// now override with info attributes
		node = lookupInfoNode();
		if (node != null) {
			AttributeSet overrides = new AttributeSet();
			node.getAttributes(this, path, isDirectory, overrides);
			if (!overrides.isEmpty()) {
				for (Attribute a : overrides.getAttributes()) {
					result.putAttribute(a);
				}
			}
		}
		// remove unspecified entries (the ! marker)
		for (Attribute a : result.getAttributes()) {
			if (a.getState() == State.UNSPECIFIED)
				result.removeAttribute(a.getKey());
		}

		if (Debug.isDetail())
			Debug.println("  " + result + " '" + path + "'"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return result;
	}

	private void collectSubTreeAttributes(String path, boolean isDirectory,
			AttributeSet result) throws IOException {
		List<String> pathElements = StringUtils.splitPath(path);
		int folderElementCount = (isDirectory ? pathElements.size()
				: pathElements.size() - 1);

		if (folderElementCount == 0)
			return;

		for (int i = folderElementCount; i > 0; i--) {
			String folderPath = StringUtils.join(pathElements.subList(0, i),
					"/"); //$NON-NLS-1$
			AttributesNode node = lookupWorkTreeNode(folderPath);
				if (node != null) {
				String relativePath = i < pathElements.size() ? StringUtils
						.join(pathElements.subList(i, pathElements.size()), "/") //$NON-NLS-1$
						: ""; //$NON-NLS-1$
					node.getAttributes(this, relativePath, isDirectory, result);
				}
		}
	}


	/**
	 * @param a
	 * @return the macro extension including the attribute iself. The result
	 *         collection is a mutable {@link Set}
	 * @throws IOException
	 */
	public Collection<Attribute> expandMacro(Attribute a) throws IOException {
		Map<String, List<Attribute>> snapshot = getMacrosSnapshot();
		List<Attribute> collector = new ArrayList<>(1);
		expandMacroRec(snapshot, a, collector);
		if (collector.size() <= 1)
			return collector;
		Map<String, Attribute> result = new LinkedHashMap<String, Attribute>(
				collector.size());
		for (Attribute elem : collector)
			result.put(elem.getKey(), elem);
		return result.values();
	}

	private void expandMacroRec(Map<String, List<Attribute>> snapshot,
			Attribute a, List<Attribute> collector) throws IOException {
		// loop detection
		if (collector.contains(a))
			return;

		// also add macro to result set, same does gitbash
		collector.add(a);

		List<Attribute> expansion = snapshot.get(a.getKey());
		if (expansion == null) {
			return;
		}
		switch (a.getState()) {
		case UNSET: {
			for (Attribute e : expansion) {
				switch (e.getState()) {
				case SET:
					expandMacroRec(snapshot,
							new Attribute(e.getKey(), State.UNSET), collector);
					break;
				case UNSET:
					expandMacroRec(snapshot,
							new Attribute(e.getKey(), State.SET), collector);
					break;
				case UNSPECIFIED:
					expandMacroRec(snapshot,
							new Attribute(e.getKey(), State.UNSPECIFIED),
							collector);
					break;
				case CUSTOM:
				default:
					expandMacroRec(snapshot, e, collector);
				}
			}
			break;
		}
		case CUSTOM: {
			for (Attribute e : expansion) {
				switch (e.getState()) {
				case SET:
				case UNSET:
				case UNSPECIFIED:
					expandMacroRec(snapshot, e, collector);
					break;
				case CUSTOM:
				default:
					expandMacroRec(snapshot,
							new Attribute(e.getKey(), a.getValue()), collector);
				}
			}
			break;
		}
		case UNSPECIFIED: {
			for (Attribute e : expansion) {
				expandMacroRec(snapshot,
						new Attribute(e.getKey(), State.UNSPECIFIED),
						collector);
			}
			break;
		}
		case SET:
		default:
			for (Attribute e : expansion) {
				expandMacroRec(snapshot, e, collector);
			}
			break;
		}
	}

	private Map<String, List<Attribute>> getMacrosSnapshot()
			throws IOException {
		Map<String, List<Attribute>> snapshot = m_macrosSnapshot;
		if (snapshot != null) {
			return snapshot;
		}

		getLock().writeLock().lock();
		try {
			// double check
			snapshot = m_macrosSnapshot;
			if (snapshot != null)
				return snapshot;

			Map<String, List<Attribute>> tmp = new LinkedHashMap<>();
			// [attr]binary -diff -merge -text
			AttributesRule predefinedRule = new AttributesRule(
					MACRO_PREFIX + "binary", //$NON-NLS-1$
					"-diff -merge -text"); //$NON-NLS-1$
			tmp.put(predefinedRule.getPattern().substring(6),
					predefinedRule.getAttributes());

			AttributesNode[] nodes = new AttributesNode[] { lookupGlobalNode(),
					lookupRootNode(), lookupInfoNode() };
			for (AttributesNode node : nodes) {
				if (node == null)
					continue;
				for (AttributesRule rule : node.getRules()) {
					if (rule.getPattern().startsWith(MACRO_PREFIX)) {
						tmp.put(rule.getPattern().substring(6),
								rule.getAttributes());
					}
				}
			}
			m_macrosSnapshot = tmp;
			return tmp;
		} finally {
			getLock().writeLock().unlock();
		}
	}

}

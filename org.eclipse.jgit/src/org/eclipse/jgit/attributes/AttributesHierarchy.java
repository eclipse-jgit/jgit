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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.jgit.attributes.Attribute.State;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.util.Debug;
import org.eclipse.jgit.util.DotFileTree;
import org.eclipse.jgit.util.StringUtils;

/**
 * Git attribute manager for a repo
 * <p>
 * This instance does lazy loading and caches the detected .gitattributes
 * <p>
 * http://git-scm.com/docs/gitattributes
 */
public class AttributesHierarchy {
	private static final String MACRO_PREFIX = "[attr]"; //$NON-NLS-1$

	private final DotFileTree dotFileTree;

	private long dotFileTreeSnapshotId;

	private Map<String, List<Attribute>> m_macroCache = null;

	private final ReadWriteLock m_macroCacheLock = new ReentrantReadWriteLock(
			true);

	/**
	 * @param dotFileTree
	 */
	public AttributesHierarchy(DotFileTree dotFileTree) {
		this.dotFileTree = dotFileTree;
		this.dotFileTreeSnapshotId = dotFileTree.getSnapshotId();
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
		AttributesNode node = dotFileTree.getWorkTreeAttributesNode(""); //$NON-NLS-1$
		if (node != null) {
			node.getAttributes(this, path, isDirectory, result);
		}

		// accumulate global attributes (no overrides)
		node = dotFileTree.getGlobalAttributesNode();
		if (node != null) {
			node.getAttributes(this, path, isDirectory, result);
		}

		// now override with info attributes
		node = dotFileTree.getInfoAttributesNode();
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
			AttributesNode node = dotFileTree
					.getWorkTreeAttributesNode(folderPath);
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
		Map<String, List<Attribute>> macroCache = getMacroCache();
		List<Attribute> collector = new ArrayList<>(1);
		expandMacroRec(macroCache, a, collector);
		if (collector.size() <= 1)
			return collector;
		Map<String, Attribute> result = new LinkedHashMap<String, Attribute>(
				collector.size());
		for (Attribute elem : collector)
			result.put(elem.getKey(), elem);
		return result.values();
	}

	private void expandMacroRec(Map<String, List<Attribute>> macroCache,
			Attribute a, List<Attribute> collector) throws IOException {
		// loop detection
		if (collector.contains(a))
			return;

		// also add macro to result set, same does gitbash
		collector.add(a);

		List<Attribute> expansion = macroCache.get(a.getKey());
		if (expansion == null) {
			return;
		}
		switch (a.getState()) {
		case UNSET: {
			for (Attribute e : expansion) {
				switch (e.getState()) {
				case SET:
					expandMacroRec(macroCache,
							new Attribute(e.getKey(), State.UNSET), collector);
					break;
				case UNSET:
					expandMacroRec(macroCache,
							new Attribute(e.getKey(), State.SET), collector);
					break;
				case UNSPECIFIED:
					expandMacroRec(macroCache,
							new Attribute(e.getKey(), State.UNSPECIFIED),
							collector);
					break;
				case CUSTOM:
				default:
					expandMacroRec(macroCache, e, collector);
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
					expandMacroRec(macroCache, e, collector);
					break;
				case CUSTOM:
				default:
					expandMacroRec(macroCache,
							new Attribute(e.getKey(), a.getValue()), collector);
				}
			}
			break;
		}
		case UNSPECIFIED: {
			for (Attribute e : expansion) {
				expandMacroRec(macroCache,
						new Attribute(e.getKey(), State.UNSPECIFIED),
						collector);
			}
			break;
		}
		case SET:
		default:
			for (Attribute e : expansion) {
				expandMacroRec(macroCache, e, collector);
			}
			break;
		}
	}

	private Map<String, List<Attribute>> getMacroCache()
			throws IOException {
		m_macroCacheLock.readLock().lock();
		try{
			if(dotFileTreeSnapshotId!=dotFileTree.getSnapshotId()){
				m_macroCache=null;
				dotFileTreeSnapshotId=dotFileTree.getSnapshotId();
			}
			Map<String, List<Attribute>> cache= m_macroCache;
			if (cache!= null) {
				return cache;
			}
		}
		finally{
			m_macroCacheLock.readLock().unlock();
		}

		m_macroCacheLock.writeLock().lock();
		try {
			// double check
			Map<String, List<Attribute>> cache = m_macroCache;
			if (cache != null) {
				return cache;
			}

			Map<String, List<Attribute>> tmp = new LinkedHashMap<>();
			// [attr]binary -diff -merge -text
			AttributesRule predefinedRule = new AttributesRule(
					MACRO_PREFIX + "binary", //$NON-NLS-1$
					"-diff -merge -text"); //$NON-NLS-1$
			tmp.put(predefinedRule.getPattern().substring(6),
					predefinedRule.getAttributes());

			AttributesNode[] nodes = new AttributesNode[] {
					dotFileTree.getGlobalAttributesNode(),
					dotFileTree.getWorkTreeAttributesNode(null),
					dotFileTree.getInfoAttributesNode()
					};
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
			m_macroCache = tmp;
			return tmp;
		} finally {
			m_macroCacheLock.writeLock().unlock();
		}
	}

}

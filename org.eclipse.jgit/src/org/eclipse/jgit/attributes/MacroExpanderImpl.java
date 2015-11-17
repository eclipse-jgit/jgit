/*
 * Copyright (C) 2015, Ivan Motsch <ivan.motsch@bsiag.com>
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
package org.eclipse.jgit.attributes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.attributes.Attribute.State;

/**
 * Attributes {@link MacroExpander} implementation
 * http://git-scm.com/docs/gitattributes
 *
 * @since 4.2
 */
public class MacroExpanderImpl implements MacroExpander {
	private static final String MACRO_PREFIX = "[attr]"; //$NON-NLS-1$

	private final Map<String, List<Attribute>> expansions = new HashMap<>();

	private AttributesNode globalNode;

	private AttributesNode infoNode;

	private AttributesNode rootNode;

	/**
	 * Create an empty {@link MacroExpander} with only the default rules
	 */
	public MacroExpanderImpl() {
		reload();
	}

	/**
	 * If the global, info or rootNode changed, this method detects that the
	 * reference is different. In such a case the macro hierarchy has to be
	 * recalculated.
	 *
	 * @param actualGlobalNode
	 * @param actualInfoNode
	 * @param actualRootNode
	 */
	public void updateWhenDifferent(@Nullable AttributesNode actualGlobalNode,
			@Nullable AttributesNode actualInfoNode,
			@Nullable AttributesNode actualRootNode) {
		if (globalNode == actualGlobalNode && infoNode == actualInfoNode
				&& rootNode == actualRootNode) {
			return;
		}
		globalNode = actualGlobalNode;
		infoNode = actualInfoNode;
		rootNode = actualRootNode;
		reload();
	}

	private void reload() {
		// [attr]binary -diff -merge -text
		AttributesRule predefinedRule = new AttributesRule(
				MACRO_PREFIX + "binary", //$NON-NLS-1$
				"-diff -merge -text"); //$NON-NLS-1$
		expansions.put(predefinedRule.getPattern().substring(6),
				predefinedRule.getAttributes());

		for (AttributesNode node : new AttributesNode[] { globalNode, rootNode,
				infoNode }) {
			if (node == null)
				continue;
			for (AttributesRule rule : node.getRules()) {
				if (rule.getPattern().startsWith(MACRO_PREFIX)) {
					expansions.put(rule.getPattern().substring(6),
							rule.getAttributes());
				}
			}
	}
	}

	/**
	 * @param attr
	 * @return the macro extension including the attribute iself
	 */
	@Override
	public Collection<Attribute> expandMacro(Attribute attr) {
		List<Attribute> collector = new ArrayList<>(1);
		expandMacroRec(attr, collector);
		if (collector.size() <= 1)
			return collector;
		Map<String, Attribute> result = new LinkedHashMap<String, Attribute>(
				collector.size());
		for (Attribute elem : collector)
			result.put(elem.getKey(), elem);
		return result.values();
	}

	private void expandMacroRec(Attribute a, List<Attribute> collector) {
		// loop detection
		if (collector.contains(a))
			return;

		// also add macro to result set, same does gitbash
		collector.add(a);

		List<Attribute> expansion = expansions.get(a.getKey());
		if (expansion == null) {
			return;
		}
		switch (a.getState()) {
		case UNSET: {
			for (Attribute e : expansion) {
				switch (e.getState()) {
				case SET:
					expandMacroRec(new Attribute(e.getKey(), State.UNSET),
							collector);
					break;
				case UNSET:
					expandMacroRec(new Attribute(e.getKey(), State.SET),
							collector);
					break;
				case UNSPECIFIED:
					expandMacroRec(new Attribute(e.getKey(), State.UNSPECIFIED),
							collector);
					break;
				case CUSTOM:
				default:
					expandMacroRec(e, collector);
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
					expandMacroRec(e, collector);
					break;
				case CUSTOM:
				default:
					expandMacroRec(new Attribute(e.getKey(), a.getValue()),
							collector);
				}
			}
			break;
		}
		case UNSPECIFIED: {
			for (Attribute e : expansion) {
				expandMacroRec(new Attribute(e.getKey(), State.UNSPECIFIED),
						collector);
			}
			break;
		}
		case SET:
		default:
			for (Attribute e : expansion) {
				expandMacroRec(e, collector);
			}
			break;
		}
	}
}

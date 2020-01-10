/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import java.nio.file.attribute.AttributeView;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.HashMap;
import java.util.Map;

public class AttrsStorageImpl implements AttrsStorage {

	final Properties content = new Properties();
	final Map<String, AttributeView> viewsNameIndex = new HashMap<String, AttributeView>();
	final Map<Class<?>, AttributeView> viewsTypeIndex = new HashMap<Class<?>, AttributeView>();

	@Override
	public AttrsStorage getAttrStorage() {
		return this;
	}

	@Override
	public <V extends AttributeView> void addAttrView(final V view) {
		viewsNameIndex.put(view.name(), view);
		if (view instanceof ExtendedAttributeView) {
			final ExtendedAttributeView extendedView = (ExtendedAttributeView) view;
			for (Class<? extends BasicFileAttributeView> type : extendedView.viewTypes()) {
				viewsTypeIndex.put(type, view);
			}
		} else {
			viewsTypeIndex.put(view.getClass(), view);
		}
	}

	@Override
	public <V extends AttributeView> V getAttrView(final Class<V> type) {
		return (V) viewsTypeIndex.get(type);
	}

	@Override
	public <V extends AttributeView> V getAttrView(final String name) {
		return (V) viewsNameIndex.get(name);
	}

	@Override
	public void clear() {
		viewsNameIndex.clear();
		viewsTypeIndex.clear();
		content.clear();
	}
}

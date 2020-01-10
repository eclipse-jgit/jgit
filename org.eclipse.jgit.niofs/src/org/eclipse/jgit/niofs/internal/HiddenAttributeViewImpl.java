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

import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotEmpty;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.niofs.fs.attribute.HiddenAttributeView;
import org.eclipse.jgit.niofs.fs.attribute.HiddenAttributes;

/**
 * This is a view that extends a Basic Attribute View and adds the "isHidden"
 * attribute. That attribute lets you know if the branch you are querying is a
 * hidden branch or not. Hidden branches should not be used, are just a
 * mechanism to merge.
 */
public abstract class HiddenAttributeViewImpl<P extends Path> extends AbstractBasicFileAttributeView<P>
		implements HiddenAttributeView {

	public static final String HIDDEN = "hidden";

	public HiddenAttributeViewImpl(final P path) {
		super(path);
	}

	@Override
	public String name() {
		return HIDDEN;
	}

	@Override
	public Map<String, Object> readAttributes(final String... attributes) throws IOException {
		final HiddenAttributes attrs = readAttributes();

		return new HashMap<String, Object>(super.readAttributes(attributes)) {
			{

				for (final String attribute : attributes) {
					checkNotEmpty("attribute", attribute);

					if (attribute.equals("*") || attribute.equals(HIDDEN)) {
						put(HIDDEN, attrs.isHidden());
					}

					if (attribute.equals("*")) {
						break;
					}
				}
			}
		};
	}
}
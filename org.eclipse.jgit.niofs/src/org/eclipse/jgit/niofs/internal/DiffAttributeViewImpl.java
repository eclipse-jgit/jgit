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

import org.eclipse.jgit.niofs.fs.attribute.DiffAttributeView;
import org.eclipse.jgit.niofs.fs.attribute.DiffAttributes;

public abstract class DiffAttributeViewImpl<P extends Path> extends AbstractBasicFileAttributeView<P>
		implements DiffAttributeView {

	public static final String DIFF = "diff";

	public DiffAttributeViewImpl(final P path) {
		super(path);
	}

	@Override
	public String name() {
		return DIFF;
	}

	@Override
	public Map<String, Object> readAttributes(final String... attributes) throws IOException {
		final DiffAttributes attrs = readAttributes();

		return new HashMap<String, Object>(super.readAttributes(attributes)) {
			{
				for (final String attribute : attributes) {
					checkNotEmpty("attribute", attribute);

					if (attribute.equals("*") || attribute.equals(DIFF)) {
						put(DIFF, attrs.branchDiff());
					}

					if (attribute.equals("*")) {
						break;
					}
				}
			}
		};
	}
}

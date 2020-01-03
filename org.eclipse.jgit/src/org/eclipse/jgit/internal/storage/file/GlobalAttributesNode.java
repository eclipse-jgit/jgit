/*
 * Copyright (C) 2014, Arthur Daussy <arthur.daussy@obeo.fr>
 * Copyright (C) 2015, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.attributes.AttributesNode;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;

/**
 * Attribute node loaded from global system-wide file.
 */
public class GlobalAttributesNode extends AttributesNode {
	final Repository repository;

	/**
	 * Constructor for GlobalAttributesNode.
	 *
	 * @param repository
	 *            the {@link org.eclipse.jgit.lib.Repository}.
	 */
	public GlobalAttributesNode(Repository repository) {
		this.repository = repository;
	}

	/**
	 * Load the attributes node
	 *
	 * @return the attributes node
	 * @throws java.io.IOException
	 */
	public AttributesNode load() throws IOException {
		AttributesNode r = new AttributesNode();

		FS fs = repository.getFS();
		String path = repository.getConfig().get(CoreConfig.KEY)
				.getAttributesFile();
		if (path != null) {
			File attributesFile;
			if (path.startsWith("~/")) { //$NON-NLS-1$
				attributesFile = fs.resolve(fs.userHome(),
						path.substring(2));
			} else {
				attributesFile = fs.resolve(null, path);
			}
			FileRepository.AttributesNodeProviderImpl.loadRulesFromFile(r, attributesFile);
		}
		return r.getRules().isEmpty() ? null : r;
	}
}

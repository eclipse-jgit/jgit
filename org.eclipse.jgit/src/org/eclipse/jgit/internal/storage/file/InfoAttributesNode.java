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
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;

/**
 * Attribute node loaded from the $GIT_DIR/info/attributes file.
 */
public class InfoAttributesNode extends AttributesNode {
	final Repository repository;

	/**
	 * Constructor for InfoAttributesNode.
	 *
	 * @param repository
	 *            the {@link org.eclipse.jgit.lib.Repository}.
	 */
	public InfoAttributesNode(Repository repository) {
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

		File attributes = fs.resolve(repository.getDirectory(),
				Constants.INFO_ATTRIBUTES);
		FileRepository.AttributesNodeProviderImpl.loadRulesFromFile(r, attributes);

		return r.getRules().isEmpty() ? null : r;
	}

}

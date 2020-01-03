/*
 * Copyright (C) 2014, Arthur Daussy <arthur.daussy@obeo.fr> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.attributes;

import java.io.IOException;

import org.eclipse.jgit.lib.CoreConfig;

/**
 * An interface used to retrieve the global and info
 * {@link org.eclipse.jgit.attributes.AttributesNode}s.
 *
 * @since 4.2
 */
public interface AttributesNodeProvider {

	/**
	 * Retrieve the {@link org.eclipse.jgit.attributes.AttributesNode} that
	 * holds the information located in $GIT_DIR/info/attributes file.
	 *
	 * @return the {@link org.eclipse.jgit.attributes.AttributesNode} that holds
	 *         the information located in $GIT_DIR/info/attributes file.
	 * @throws java.io.IOException
	 *             if an error is raised while parsing the attributes file
	 */
	AttributesNode getInfoAttributesNode() throws IOException;

	/**
	 * Retrieve the {@link org.eclipse.jgit.attributes.AttributesNode} that
	 * holds the information located in the global gitattributes file.
	 *
	 * @return the {@link org.eclipse.jgit.attributes.AttributesNode} that holds
	 *         the information located in the global gitattributes file.
	 * @throws java.io.IOException
	 *             java.io.IOException if an error is raised while parsing the
	 *             attributes file
	 * @see CoreConfig#getAttributesFile()
	 */
	AttributesNode getGlobalAttributesNode() throws IOException;

}

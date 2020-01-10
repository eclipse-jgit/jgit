/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import java.io.IOException;
import java.nio.file.attribute.AttributeView;
import java.util.Map;

public interface ExtendedAttributeView extends AttributeView {

	Map<String, Object> readAllAttributes() throws IOException;

	Map<String, Object> readAttributes(final String... attributes) throws IOException;

	void setAttribute(final String attribute, final Object value) throws IOException;

	Class[] viewTypes();

	boolean isSerializable();
}

/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.fs.attribute;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;

public interface VersionAttributeView extends BasicFileAttributeView {

	String name();

	VersionAttributes readAttributes() throws IOException;
}

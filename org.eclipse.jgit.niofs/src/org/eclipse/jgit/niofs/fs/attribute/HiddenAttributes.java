/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.fs.attribute;

import java.nio.file.attribute.BasicFileAttributes;

/**
 * Represents files attributes with the addition of a hidden field. That hidden
 * attribute tell if a branch is hidden or not. I.E.: A Pull Request hidden
 * branch. You should not use those branches unless you have to use them.
 */
public interface HiddenAttributes extends BasicFileAttributes {

	boolean isHidden();
}

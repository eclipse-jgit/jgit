/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.fs.options;

import java.nio.file.CopyOption;

/**
 * This is the CopyOption that allows to merge two branches when executing copy
 * method. You have to apply it as the third parameter of
 * FileSystemProvider.copy() method.
 */
public class MergeCopyOption implements CopyOption {

}

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

import java.nio.file.attribute.AttributeView;

public interface AttrHolder {

	AttrsStorage getAttrStorage();

	<V extends AttributeView> void addAttrView(final V view);

	<V extends AttributeView> V getAttrView(final Class<V> type);

	<V extends AttributeView> V getAttrView(final String name);
}

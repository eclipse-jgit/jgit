/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.commands;

import java.io.IOException;
import java.util.function.Consumer;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;

public class WriteConfiguration {

	private final Repository repo;
	private final Consumer<StoredConfig> consumer;

	public WriteConfiguration(final Repository repo, final Consumer<StoredConfig> consumer) {
		this.repo = repo;
		this.consumer = consumer;
	}

	public void execute() {
		final StoredConfig cfg = repo.getConfig();
		consumer.accept(cfg);
		try {
			cfg.save();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

/*
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.SystemReader;

public class RefDirectoryAfterOpenConfigTest extends RefDirectoryTest {
	@Override
	public void refDirectorySetup() throws Exception {
		StoredConfig userConfig = SystemReader.getInstance().getUserConfig();
		userConfig.setEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_TRUST_PACKED_REFS_STAT,
				CoreConfig.TrustPackedRefsStat.AFTER_OPEN);
		userConfig.save();
		super.refDirectorySetup();
	}
}

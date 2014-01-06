/*
 * Copyright (C) 2012, Tomasz Zarna <Tomasz.Zarna@pl.ibm.com> and
 * other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v1.0 which accompanies this
 * distribution, is reproduced below, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.pgm;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_getAndSetOptions")
class Config extends TextBuiltin {
	@Option(name = "--system", usage = "usage_configSystem")
	private boolean system;

	@Option(name = "--global", usage = "usage_configGlobal")
	private boolean global;

	@Option(name = "--local", usage = "usage_configLocal")
	private boolean local;

	@Option(name = "--list", aliases = { "-l" }, usage = "usage_configList")
	private boolean list;

	@Option(name = "--file", aliases = { "-f" }, metaVar = "metaVar_file", usage = "usage_configFile")
	private File configFile;

	@Override
	protected void run() throws Exception {
		if (list)
			list();
		else
			throw new NotSupportedException(
					"only --list option is currently supported");
	}

	private void list() throws IOException, ConfigInvalidException {
		final FS fs = getRepository().getFS();
		if (configFile != null) {
			list(new FileBasedConfig(configFile, fs));
			return;
		}
		if (system
				|| (isListAll() && StringUtils.isEmptyOrNull(SystemReader
						.getInstance()
						.getenv(Constants.GIT_CONFIG_NOSYSTEM_KEY))))
			list(SystemReader.getInstance().openSystemConfig(null, fs));
		if (global || isListAll())
			list(SystemReader.getInstance().openUserConfig(null, fs));
		if (local || isListAll())
			list(new FileBasedConfig(fs.resolve(getRepository().getDirectory(),
					Constants.CONFIG), fs));
	}

	private boolean isListAll() {
		return !system && !global && !local && configFile == null;
	}

	private void list(StoredConfig config) throws IOException,
			ConfigInvalidException {
		config.load();
		Set<String> sections = config.getSections();
		for (String section : sections) {
			Set<String> names = config.getNames(section);
			for (String name : names) {
				for (String value : config.getStringList(section, null, name))
					outw.println(section + "." + name + "=" + value); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if (names.isEmpty()) {
				for (String subsection : config.getSubsections(section)) {
					names = config.getNames(section, subsection);
					for (String name : names) {
						for (String value : config.getStringList(section,
								subsection, name))
							outw.println(section + "." + subsection + "." //$NON-NLS-1$ //$NON-NLS-2$
									+ name + "=" + value); //$NON-NLS-1$
					}
				}
			}
		}
	}
}
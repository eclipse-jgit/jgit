/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2009, Yann Simon <yann.simon.fr@gmail.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.junit;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

public class MockSystemReader extends SystemReader {
	private final class MockConfig extends FileBasedConfig {
		private MockConfig(File cfgLocation, FS fs) {
			super(cfgLocation, fs);
		}

		@Override
		public void load() throws IOException, ConfigInvalidException {
			// Do nothing
		}

		@Override
		public boolean isOutdated() {
			return false;
		}
	}

	final Map<String, String> values = new HashMap<String, String>();

	FileBasedConfig userGitConfig;

	FileBasedConfig systemGitConfig;

	public MockSystemReader() {
		init(Constants.OS_USER_NAME_KEY);
		init(Constants.GIT_AUTHOR_NAME_KEY);
		init(Constants.GIT_AUTHOR_EMAIL_KEY);
		init(Constants.GIT_COMMITTER_NAME_KEY);
		init(Constants.GIT_COMMITTER_EMAIL_KEY);
		userGitConfig = new MockConfig(null, null);
		systemGitConfig = new MockConfig(null, null);
	}

	private void init(final String n) {
		setProperty(n, n);
	}

	public void clearProperties() {
		values.clear();
	}

	public void setProperty(String key, String value) {
		values.put(key, value);
	}

	@Override
	public String getenv(String variable) {
		return values.get(variable);
	}

	@Override
	public String getProperty(String key) {
		return values.get(key);
	}

	@Override
	public FileBasedConfig openUserConfig(Config parent, FS fs) {
		assert parent == null || parent == systemGitConfig;
		return userGitConfig;
	}

	@Override
	public FileBasedConfig openSystemConfig(Config parent, FS fs) {
		assert parent == null;
		return systemGitConfig;
	}

	@Override
	public String getHostname() {
		return "fake.host.example.com";
	}

	@Override
	public long getCurrentTime() {
		return 1250379778668L; // Sat Aug 15 20:12:58 GMT-03:30 2009
	}

	@Override
	public int getTimezone(long when) {
		return TimeZone.getTimeZone("GMT-03:30").getOffset(when) / (60 * 1000);
	}

}

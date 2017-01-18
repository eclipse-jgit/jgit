/*
 * Copyright (C) 2008,2010, Google Inc.
 * Copyright (C) 2017, Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.transport.http;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;

/**
 * Http section of a git configuration file
 *
 * @since 4.7
 */
public class HttpConfig {
	/**
	 * Config values for http.followRedirects
	 */
	public enum HttpRedirectEnum implements Config.ConfigEnum {

		/**
		 * Value for following redirects only for the initial request to a
		 * remote, but not for subsequent follow-up HTTP requests. This is the
		 * default.
		 */
		INITIAL("initial"), //$NON-NLS-1$
		/**
		 * Value for transparently following any redirect issued by a server it
		 * encounters
		 */
		TRUE("true"), //$NON-NLS-1$
		/**
		 * Value for treating all redirects as errors
		 */
		FALSE("false"); //$NON-NLS-1$

		private final String configValue;

		private HttpRedirectEnum(String configValue) {
			this.configValue = configValue;
		}

		@Override
		public String toConfigValue() {
			return configValue;
		}

		@Override
		public boolean matchConfigValue(String s) {
			return configValue.equals(s);
		}
	}

	private final int postBuffer;

	/**
	 * @return the configured postBuffer
	 */
	public int getPostBuffer() {
		return postBuffer;
	}

	/**
	 * @return {@code true} if SSL verification is switched on
	 */
	public boolean isSslVerify() {
		return sslVerify;
	}

	/**
	 * @return the configured redirect mode
	 */
	public HttpRedirectEnum getRedirect() {
		return redirect;
	}

	private final boolean sslVerify;

	private final HttpRedirectEnum redirect;

	/**
	 * Create a HttpConfig from the given configuration
	 *
	 * @param rc
	 */
	public HttpConfig(final Config rc) {
		postBuffer = rc.getInt(ConfigConstants.CONFIG_HTTP_SECTION,
				ConfigConstants.CONFIG_KEY_POSTBUFFER, 1 * 1024 * 1024);
		sslVerify = rc.getBoolean(ConfigConstants.CONFIG_HTTP_SECTION,
				ConfigConstants.CONFIG_KEY_SSLVERIFY, true);
		redirect = rc.getEnum(HttpRedirectEnum.values(),
				ConfigConstants.CONFIG_HTTP_SECTION, null,
				ConfigConstants.CONFIG_KEY_FOLLOWREDIRECTS,
				HttpRedirectEnum.INITIAL);
	}

	/**
	 * construct an empty HttpConfig
	 */
	public HttpConfig() {
		this(new Config());
	}
}
/*
 * Copyright (C) 2011, Google Inc.
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

package org.eclipse.jgit.transport;

import java.util.List;

import org.eclipse.jgit.storage.pack.PackWriter;

/**
 * {@link UploadPackLogger} that delegates to a list of other loggers.
 * <p>
 * loggers are run in the order passed to the constructor.
 */
public class UploadPackLoggerChain implements UploadPackLogger {
	private final UploadPackLogger[] loggers;
	private final int count;

	/**
	 * Create a new logger chaining the given loggers together.
	 *
	 * @param loggers
	 *            loggers to execute, in order.
	 * @return a new logger chain of the given loggers.
	 */
	public static UploadPackLogger newChain(
			List<? extends UploadPackLogger> loggers) {
		UploadPackLogger[] newLoggers = new UploadPackLogger[loggers.size()];
		int i = 0;
		for (UploadPackLogger logger : loggers)
			if (logger != UploadPackLogger.NULL)
				newLoggers[i++] = logger;
		if (i == 0)
			return UploadPackLogger.NULL;
		else if (i == 1)
			return newLoggers[0];
		else
			return new UploadPackLoggerChain(newLoggers, i);
	}

	public void onPackStatistics(PackWriter.Statistics stats) {
		for (int i = 0; i < count; i++)
			loggers[i].onPackStatistics(stats);
	}

	private UploadPackLoggerChain(UploadPackLogger[] loggers, int count) {
		this.loggers = loggers;
		this.count = count;
	}
}

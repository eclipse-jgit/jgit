/*
 * Copyright (C) 2017, Leif Frenzel <himself@leiffrenzel.de>
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
package org.eclipse.jgit.diff;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.isExecutable;
import static org.eclipse.jgit.diff.DiffEntry.DEV_NULL;
import static org.eclipse.jgit.diff.DiffEntry.ChangeType.ADD;
import static org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE;
import static org.eclipse.jgit.util.FileUtils.createTempDir;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffFormatter.FormatResult;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.ProcessResult;
import org.eclipse.jgit.util.ProcessResult.Status;
import org.eclipse.jgit.util.io.NullOutputStream;

/**
 * Call an external programm instead of our own diff implementation to print
 * diff output.
 *
 * @author himself@leiffrenzel.de
 * @since 4.9
 */
public class ExternalDiffDriver {

	private final Repository repo;

	private final DiffFormatter diffFormatter;

	private final OutputStream out;

	private File tempDir;

	/**
	 * @param repo
	 * @param diffFormatter
	 * @param out
	 */
	public ExternalDiffDriver(Repository repo, DiffFormatter diffFormatter,
			OutputStream out) {
		this.repo = repo;
		this.diffFormatter = diffFormatter;
		this.out = safeOutputStream(out);
	}

	/**
	 * Flush the underlying output stream.
	 *
	 * @throws IOException
	 *             the stream's own flush method threw an exception.
	 */
	public void flush() throws IOException {
		out.flush();
	}

	/**
	 * @param entry
	 * @throws ExternalDiffException
	 */
	public void format(DiffEntry entry) throws ExternalDiffException {
		if (canExecuteDriver()) {
			ByteArrayOutputStream errors = new ByteArrayOutputStream();
			try {
				String[] params = createDiffParams(entry);
				execute(configuredDriver(), params, errors);
			} catch (IOException | InterruptedException ex) {
				throw new ExternalDiffException(errors, ex);
			}
		}
	}

	private void execute(String driver, String[] params,
			ByteArrayOutputStream errors)
			throws IOException, InterruptedException, ExternalDiffException {
		PrintStream err = createErrRedirect(errors);
		ProcessBuilder process = FS.DETECTED.runInShell(driver, params);
		process.directory(getWorkDirectory());
		int exitCode = FS.DETECTED.runProcess(process, out, err, (String) null);
		ProcessResult result = new ProcessResult(exitCode, Status.OK);
		if (result.isExecutedWithError()) {
			throw new ExternalDiffException(errors);
		}
	}

	/*
	 * For a path that is added, removed, or modified, GIT_EXTERNAL_DIFF is
	 * called with 7 parameters:
	 *
	 * path old-file old-hex old-mode new-file new-hex new-mode
	 *
	 * Reference: https://git-scm.com/docs/git#_git_diffs
	 */
	private String[] createDiffParams(DiffEntry entry) throws IOException {
		FormatResult formatResult = diffFormatter.createFormatResult(entry);

		List<String> params = new ArrayList<>();
		params.add(getPath(entry));
		params.add(getOldPath(entry, formatResult.a));
		params.add(entry.getOldId().name());
		params.add(entry.getOldMode().toString());
		params.add(getNewPath(entry));
		params.add(entry.getNewId().name());
		params.add(entry.getNewMode().toString());
		return params.toArray(new String[0]);
	}

	private String getPath(DiffEntry entry) {
		boolean deleted = entry.getChangeType() == DELETE;
		return deleted ? entry.getOldPath() : entry.getNewPath();
	}

	private String getOldPath(DiffEntry entry, RawText content)
			throws IOException {
		boolean added = entry.getChangeType() == ADD;
		return added ? DEV_NULL : asTempFile(entry.getOldPath(), content);
	}

	private String getNewPath(DiffEntry entry) {
		boolean deleted = entry.getChangeType() == DELETE;
		return deleted ? DEV_NULL : entry.getNewPath();
	}

	private String asTempFile(String path, RawText content) throws IOException {
		File tempFile = new File(getTempDir(), path);
		try (FileOutputStream stream = new FileOutputStream(tempFile)) {
			stream.write(content.content);
		}
		return tempFile.getAbsolutePath();
	}

	private File getTempDir() throws IOException {
		if (tempDir == null) {
			tempDir = createTempDir("jgit-diff", null, null); //$NON-NLS-1$
		}
		return tempDir;
	}

	private boolean canExecuteDriver() {
		File driver = new File(configuredDriver());
		return FS.DETECTED.exists(driver) && isExecutable(driver.toPath());
	}

	private String configuredDriver() {
		return repo.getConfig().get(DiffConfig.KEY).getExternal();
	}

	private PrintStream createErrRedirect(ByteArrayOutputStream captor) {
		try {
			return new PrintStream(captor, false, UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			// UTF-8 is guaranteed to be available
			return null;
		}
	}

	private File getWorkDirectory() {
		return repo.isBare() ? repo.getDirectory() : repo.getWorkTree();
	}

	private OutputStream safeOutputStream(OutputStream outputStream) {
		return (outputStream == null) ? NullOutputStream.INSTANCE
				: new BufferedOutputStream(outputStream);
	}

	private class ExternalDiffException extends GitAPIException {

		private static final long serialVersionUID = 1L;

		ExternalDiffException(ByteArrayOutputStream errors, Exception ex) {
			super(new String(errors.toByteArray(), UTF_8), ex);
		}

		ExternalDiffException(ByteArrayOutputStream errors) {
			super(new String(errors.toByteArray(), UTF_8));
		}
	}
}

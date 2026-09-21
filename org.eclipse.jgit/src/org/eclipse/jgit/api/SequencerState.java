/*
 * Copyright (c) 2026 Vector Informatik GmbH
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FileUtils;

/**
 * Reads and writes the {@code $GIT_DIR/sequencer} directory that native Git
 * uses to keep track of an in-progress, possibly multi-commit
 * {@code cherry-pick} or {@code revert} operation.
 * <p>
 * The layout of the directory and the format of the files it contains are
 * defined by native Git (see {@code sequencer.c}) and are reproduced here so
 * that a multi-commit cherry-pick or revert started with JGit can be continued,
 * skipped or aborted with either JGit or the native {@code git} command line
 * tool, and vice versa.
 * </p>
 * <p>
 * The directory contains:
 * </p>
 * <dl>
 * <dt>{@code todo}</dt>
 * <dd>one line per not-yet-applied commit, of the form
 * {@code "pick <abbreviated sha1> <subject>"} (or {@code revert} instead of
 * {@code pick})</dd>
 * <dt>{@code head}</dt>
 * <dd>the id of the commit HEAD pointed to before the sequence was started</dd>
 * <dt>{@code opts}</dt>
 * <dd>a git-config-style file recording some of the options that were in effect
 * for the whole sequence</dd>
 * <dt>{@code abort-safety}</dt>
 * <dd>the id of the commit HEAD is expected to point to; used to detect whether
 * HEAD was changed by someone else while the sequence was stopped</dd>
 * </dl>
 */
final class SequencerState {

	private static final String OPTIONS_SECTION = "options"; //$NON-NLS-1$

	/** The sequencer command associated with a single todo line. */
	enum Action {

		/** {@code pick}, written for {@link CherryPickCommand}. */
		PICK("pick"), //$NON-NLS-1$

		/** {@code revert}, written for {@link RevertCommand}. */
		REVERT("revert"); //$NON-NLS-1$

		private final String command;

		Action(String command) {
			this.command = command;
		}

		String getCommand() {
			return command;
		}
	}

	/** A single, not yet applied entry of the sequencer {@code todo} file. */
	static final class TodoLine {
		final Action action;

		final ObjectId commitId;

		final String shortMessage;

		TodoLine(Action action, ObjectId commitId, String shortMessage) {
			this.action = action;
			this.commitId = commitId;
			this.shortMessage = shortMessage;
		}
	}

	/**
	 * The subset of the native {@code sequencer/opts} options that
	 * {@link CherryPickCommand} and {@link RevertCommand} know how to persist
	 * and restore.
	 */
	static final class Options {

		private static final class Names {

			static final String NO_COMMIT = "no-commit"; //$NON-NLS-1$

			static final String MAINLINE = "mainline"; //$NON-NLS-1$

			static final String STRATEGY = "strategy"; //$NON-NLS-1$

		}

		boolean noCommit;

		Integer mainline;

		String strategy;
	}

	private SequencerState() {
		// no instances
	}

	private static File dir(Repository repo) {
		return new File(repo.getDirectory(), Constants.SEQUENCER_DIR);
	}

	private static File todoFile(Repository repo) {
		return new File(repo.getDirectory(), Constants.SEQUENCER_TODO_FILE);
	}

	private static File optsFile(Repository repo) {
		return new File(repo.getDirectory(), Constants.SEQUENCER_OPTS_FILE);
	}

	private static File headFile(Repository repo) {
		return new File(repo.getDirectory(), Constants.SEQUENCER_HEAD_FILE);
	}

	private static File abortSafetyFile(Repository repo) {
		return new File(repo.getDirectory(),
				Constants.SEQUENCER_ABORT_SAFETY_FILE);
	}

	/**
	 * Tells whether a sequencer-based cherry-pick or revert operation is
	 * currently in progress, i.e. whether there is a {@code todo} file left
	 * over from a previous invocation that was interrupted by a conflict and
	 * has not yet been completed, skipped or aborted.
	 *
	 * @param repo
	 *            the repository to check
	 * @return {@code true} if a sequencer operation is in progress
	 */
	static boolean isInProgress(Repository repo) {
		return todoFile(repo).isFile();
	}

	/**
	 * Starts a new sequencer operation: creates the sequencer directory and
	 * writes the {@code head} and {@code opts} files. The caller is responsible
	 * for calling {@link #writeTodo(Repository, Action, List)} before
	 * processing each commit, and {@link #end(Repository)} once the whole
	 * sequence has been completed successfully.
	 *
	 * @param repo
	 *            the repository
	 * @param originalHead
	 *            the id of the commit HEAD pointed to before the sequence was
	 *            started
	 * @param options
	 *            the options in effect for the whole sequence
	 * @throws IOException
	 *             if the sequencer state cannot be written
	 */
	static void begin(Repository repo, ObjectId originalHead, Options options)
			throws IOException {
		FileUtils.mkdir(dir(repo), true);
		writeFile(headFile(repo), originalHead.name() + '\n');
		writeOpts(repo, options);
		writeAbortSafety(repo, originalHead);
	}

	/**
	 * Rewrites the {@code todo} file so that it contains exactly the given, not
	 * yet applied commits. Called before processing each commit of the sequence
	 * so that, in case of a conflict, the file correctly reflects the commit
	 * that failed together with all the commits still to be applied after it.
	 *
	 * @param repo
	 *            the repository
	 * @param action
	 *            whether the commits are being picked or reverted
	 * @param remaining
	 *            the commits still to be applied, in application order
	 * @throws IOException
	 *             if the sequencer state cannot be written
	 */
	static void writeTodo(Repository repo, Action action,
			List<RevCommit> remaining) throws IOException {
		StringBuilder sb = new StringBuilder();
		for (RevCommit c : remaining) {
			sb.append(action.getCommand()).append(' ').append(
					c.abbreviate(Constants.OBJECT_ID_ABBREV_STRING_LENGTH)
							.name())
					.append(' ').append(c.getShortMessage()).append('\n');
		}
		writeFile(todoFile(repo), sb.toString());
	}

	/**
	 * Updates the {@code abort-safety} file, recording the commit HEAD is
	 * expected to point to. Native Git uses this to detect whether HEAD was
	 * changed by someone else while the sequence was stopped, before allowing
	 * {@code --abort} to reset to the original HEAD.
	 *
	 * @param repo
	 *            the repository
	 * @param head
	 *            the commit HEAD currently points to
	 * @throws IOException
	 *             if the sequencer state cannot be written
	 */
	static void writeAbortSafety(Repository repo, ObjectId head)
			throws IOException {
		writeFile(abortSafetyFile(repo), head.name() + '\n');
	}

	/**
	 * Removes the whole sequencer directory. Called once a sequence has been
	 * completed successfully, or explicitly aborted.
	 *
	 * @param repo
	 *            the repository
	 * @throws IOException
	 *             if the sequencer state cannot be removed
	 */
	static void end(Repository repo) throws IOException {
		FileUtils.delete(dir(repo),
				FileUtils.RECURSIVE | FileUtils.SKIP_MISSING);
	}

	/**
	 * Reads the original HEAD commit id that was recorded when the current
	 * sequence was started.
	 *
	 * @param repo
	 *            the repository
	 * @return the original HEAD, or {@code null} if no sequencer operation is
	 *         in progress
	 * @throws IOException
	 *             if the sequencer state cannot be read
	 */
	static ObjectId readHead(Repository repo) throws IOException {
		String content = readFile(headFile(repo));
		if (content == null) {
			return null;
		}
		return ObjectId.fromString(content.trim());
	}

	/**
	 * Reads the not yet applied commits of the current sequence.
	 *
	 * @param repo
	 *            the repository
	 * @return the list of remaining {@link TodoLine}s, in application order;
	 *         empty if no sequencer operation is in progress
	 * @throws IOException
	 *             if the sequencer state cannot be read
	 */
	static List<TodoLine> readTodo(Repository repo) throws IOException {
		List<TodoLine> result = new ArrayList<>();
		String content = readFile(todoFile(repo));
		if (content == null) {
			return result;
		}
		for (String line : content.split("\n", -1)) { //$NON-NLS-1$
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) { //$NON-NLS-1$
				continue; // comment
			}
			int sp1 = trimmed.indexOf(' ');
			if (sp1 < 0) {
				continue;
			}
			String cmd = trimmed.substring(0, sp1);
			Action action;
			if (Action.PICK.getCommand().equals(cmd)) {
				action = Action.PICK;
			} else if (Action.REVERT.getCommand().equals(cmd)) {
				action = Action.REVERT;
			} else {
				// Skip commands native Git may have written that JGit
				// doesn't (yet) understand, e.g. from an interactive edit.
				continue;
			}
			String rest = trimmed.substring(sp1 + 1);
			int sp2 = rest.indexOf(' ');
			String sha = sp2 < 0 ? rest : rest.substring(0, sp2);
			String message = sp2 < 0 ? "" : rest.substring(sp2 + 1); //$NON-NLS-1$
			ObjectId commitId = repo.resolve(sha);
			if (commitId == null) {
				continue;
			}
			result.add(new TodoLine(action, commitId, message));
		}
		return result;
	}

	/**
	 * Reads the options recorded for the current sequence.
	 *
	 * @param repo
	 *            the repository
	 * @return the {@link Options} in effect; all defaults if no sequencer
	 *         operation is in progress
	 * @throws IOException
	 *             if the sequencer state cannot be read
	 */
	static Options readOpts(Repository repo) throws IOException {
		Options options = new Options();
		String content = readFile(optsFile(repo));
		if (content == null) {
			return options;
		}
		Config cfg = new Config();
		try {
			cfg.fromText(content);
		} catch (ConfigInvalidException e) {
			throw new IOException(MessageFormat.format(
					JGitText.get().couldNotReadSequencerFile,
					optsFile(repo).getPath()), e);
		}
		options.noCommit = cfg.getBoolean(OPTIONS_SECTION,
				Options.Names.NO_COMMIT, false);
		int mainline = cfg.getInt(OPTIONS_SECTION, Options.Names.MAINLINE, -1);
		if (mainline > 0) {
			options.mainline = Integer.valueOf(mainline);
		}
		options.strategy = cfg.getString(OPTIONS_SECTION, null,
				Options.Names.STRATEGY);
		return options;
	}

	private static void writeOpts(Repository repo, Options options)
			throws IOException {
		Config cfg = new Config();
		if (options.noCommit) {
			cfg.setBoolean(OPTIONS_SECTION, null, Options.Names.NO_COMMIT,
					true);
		}
		if (options.mainline != null) {
			cfg.setInt(OPTIONS_SECTION, null, Options.Names.MAINLINE,
					options.mainline.intValue());
		}
		if (options.strategy != null) {
			cfg.setString(OPTIONS_SECTION, null, Options.Names.STRATEGY,
					options.strategy);
		}
		String text = cfg.toText();
		if (text.isEmpty()) {
			FileUtils.delete(optsFile(repo), FileUtils.SKIP_MISSING);
			return;
		}
		writeFile(optsFile(repo), text);
	}

	private static void writeFile(File file, String content)
			throws IOException {
		try {
			Files.write(file.toPath(),
					content.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new IOException(MessageFormat.format(
					JGitText.get().couldNotWriteSequencerFile, file.getPath()),
					e);
		}
	}

	private static String readFile(File file) throws IOException {
		if (!file.isFile()) {
			return null;
		}
		try {
			return new String(Files.readAllBytes(file.toPath()),
					StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IOException(MessageFormat.format(
					JGitText.get().couldNotReadSequencerFile, file.getPath()),
					e);
		}
	}
}

/*
 * Copyright (C) 2018-2021, Andre Bossert <andre.bossert@siemens.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.diffmergetool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Map;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.FS_POSIX;
import org.eclipse.jgit.util.FS_Win32;
import org.eclipse.jgit.util.FS_Win32_Cygwin;

/**
 * Runs a command with help of FS.
 */
public class CommandExecutor {

	private FS fs;

	private boolean checkExitCode;

	private File commandFile;

	private boolean useMsys2;

	/**
	 * @param fs
	 *            the file system
	 * @param checkExitCode
	 *            should the exit code be checked for errors ?
	 */
	public CommandExecutor(FS fs, boolean checkExitCode) {
		this.fs = fs;
		this.checkExitCode = checkExitCode;
	}

	/**
	 * @param command
	 *            the command string
	 * @param workingDir
	 *            the working directory
	 * @param env
	 *            the environment
	 * @return the execution result
	 * @throws ToolException
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public ExecutionResult run(String command, File workingDir,
			Map<String, String> env)
			throws ToolException, IOException, InterruptedException {
		String[] commandArray = createCommandArray(command);
		try {
			ProcessBuilder pb = fs.runInShell(commandArray[0],
					Arrays.copyOfRange(commandArray, 1, commandArray.length));
			pb.directory(workingDir);
			Map<String, String> envp = pb.environment();
			if (env != null) {
				envp.putAll(env);
			}
			ExecutionResult result = fs.execute(pb, null);
			int rc = result.getRc();
			if (rc != 0) {
				boolean execError = isCommandExecutionError(rc);
				if (checkExitCode || execError) {
					throw new ToolException(
							"JGit: tool execution return code: " + rc + "\n" //$NON-NLS-1$ //$NON-NLS-2$
									+ "checkExitCode: " + checkExitCode + "\n" //$NON-NLS-1$ //$NON-NLS-2$
									+ "execError: " + execError + "\n" //$NON-NLS-1$ //$NON-NLS-2$
									+ "stderr: \n" //$NON-NLS-1$
									+ new String(
											result.getStderr().toByteArray()),
							result, execError);
				}
			}
			return result;
		} finally {
			deleteCommandArray();
		}
	}

	private void deleteCommandArray() {
		deleteCommandFile();
	}

	private String[] createCommandArray(String command)
			throws ToolException, IOException {
		String[] commandArray = null;
		checkUseMsys2(command);
		createCommandFile(command);
		if (fs instanceof FS_POSIX) {
			commandArray = new String[1];
			commandArray[0] = commandFile.getCanonicalPath();
		} else if (fs instanceof FS_Win32) {
			if (useMsys2) {
				commandArray = new String[3];
				commandArray[0] = "bash.exe"; //$NON-NLS-1$
				commandArray[1] = "-c"; //$NON-NLS-1$
				commandArray[2] = commandFile.getCanonicalPath().replace("\\", //$NON-NLS-1$
						"/"); //$NON-NLS-1$
			} else {
				commandArray = new String[1];
				commandArray[0] = commandFile.getCanonicalPath();
			}
		} else if (fs instanceof FS_Win32_Cygwin) {
			commandArray = new String[1];
			commandArray[0] = commandFile.getCanonicalPath().replace("\\", "/"); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			throw new ToolException(
					"JGit: file system not supported: " + fs.toString()); //$NON-NLS-1$
		}
		return commandArray;
	}

	private void checkUseMsys2(String command) {
		useMsys2 = false;
		String useMsys2Str = System.getProperty("jgit.usemsys2bash"); //$NON-NLS-1$
		if (useMsys2Str != null && !useMsys2Str.isEmpty()) {
			if (useMsys2Str.equalsIgnoreCase("auto")) { //$NON-NLS-1$
				useMsys2 = command.contains(".sh"); //$NON-NLS-1$
			} else {
				useMsys2 = Boolean.parseBoolean(useMsys2Str);
			}
		}
	}

	private void createCommandFile(String command)
			throws ToolException, IOException {
		String fileExtension = null;
		if (useMsys2 || fs instanceof FS_POSIX
				|| fs instanceof FS_Win32_Cygwin) {
			fileExtension = ".sh"; //$NON-NLS-1$
		} else if (fs instanceof FS_Win32) {
			fileExtension = ".cmd"; //$NON-NLS-1$
			command = "@echo off" + System.lineSeparator() + command //$NON-NLS-1$
					+ System.lineSeparator() + "exit /B %ERRORLEVEL%"; //$NON-NLS-1$
		} else {
			throw new ToolException(
					"JGit: file system not supported: " + fs.toString()); //$NON-NLS-1$
		}
		commandFile = File.createTempFile(".__", //$NON-NLS-1$
				"__jgit_tool" + fileExtension); //$NON-NLS-1$
		try (OutputStream outStream = new FileOutputStream(commandFile)) {
			byte[] strToBytes = command.getBytes();
			outStream.write(strToBytes);
			outStream.close();
		}
		commandFile.setExecutable(true);
	}

	private void deleteCommandFile() {
		if (commandFile != null && commandFile.exists()) {
			commandFile.delete();
		}
	}

	private boolean isCommandExecutionError(int rc) {
		if (useMsys2 || fs instanceof FS_POSIX
				|| fs instanceof FS_Win32_Cygwin) {
			// 126: permission for executing command denied
			// 127: command not found
			if ((rc == 126) || (rc == 127)) {
				return true;
			}
		}
		else if (fs instanceof FS_Win32) {
			// 9009, 0x2331: Program is not recognized as an internal or
			// external command, operable program or batch file. Indicates that
			// command, application name or path has been misspelled when
			// configuring the Action.
			if (rc == 9009) {
				return true;
			}
		}
		return false;
	}

}

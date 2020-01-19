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

/**
 * Pre-defined merge tools.
 *
 * Adds same merge tools as also pre-defined in C-Git
 * see "git-core\mergetools\"
 * see links to command line parameter description for the tools
 *
 * @formatter:off
 *
 * araxis
 * bc
 * bc3
 * codecompare
 * deltawalker
 * diffmerge
 * diffuse
 * ecmerge
 * emerge
 * examdiff
 * guiffy
 * gvimdiff
 * gvimdiff2
 * gvimdiff3
 * kdiff3
 * kompare
 * meld
 * opendiff
 * p4merge
 * tkdiff
 * tortoisemerge
 * vimdiff
 * vimdiff2
 * vimdiff3
 * winmerge
 * xxdiff
 *
 * @formatter:on
 *
 * @since 5.13
 *
 */
@SuppressWarnings("nls")
public enum CommandLineMergeTool {
	/**
	 * @see: https://www.araxis.com/merge/documentation-windows/command-line.en
	 */
	araxis("compare",
			"-wait -merge -3 -a1 \"$BASE\" \"$LOCAL\" \"$REMOTE\" \"$MERGED\"",
			"-wait -2 \"$LOCAL\" \"$REMOTE\" \"$MERGED\"",
			false),
	/**
	 * @see: https://www.scootersoftware.com/v4help/index.html?command_line_reference.html
	 */
	bc("bcomp", "\"$LOCAL\" \"$REMOTE\" \"$BASE\" --mergeoutput=\"$MERGED\"",
			"\"$LOCAL\" \"$REMOTE\" --mergeoutput=\"$MERGED\"",
			false),
	/**
	 * @see: https://www.scootersoftware.com/v4help/index.html?command_line_reference.html
	 */
	bc3("bcompare", bc),
	/**
	 * @see: https://www.devart.com/codecompare/docs/index.html?merging_via_command_line.htm
	 */
	codecompare("CodeMerge",
			"-MF=\"$LOCAL\" -TF=\"$REMOTE\" -BF=\"$BASE\" -RF=\"$MERGED\"",
			"-MF=\"$LOCAL\" -TF=\"$REMOTE\" -RF=\"$MERGED\"",
			false),
	/**
	 * @see: https://www.deltawalker.com/integrate/command-line
	 * @hint: $(pwd) command must be defined
	 */
	deltawalker("DeltaWalker",
			"\"$LOCAL\" \"$REMOTE\" \"$BASE\" -pwd=\"$(pwd)\" -merged=\"$MERGED\"",
			"\"$LOCAL\" \"$REMOTE\" -pwd=\"$(pwd)\" -merged=\"$MERGED\"",
			true),
	/**
	 * @see: https://sourcegear.com/diffmerge/webhelp/sec__clargs__diff.html
	 */
	diffmerge("diffmerge", //$NON-NLS-1$
			"--merge --result=\"$MERGED\" \"$LOCAL\" \"$BASE\" \"$REMOTE\"",
			"--merge --result=\"$MERGED\" \"$LOCAL\" \"$REMOTE\"",
			true),
	/**
	 * @see: http://diffuse.sourceforge.net/manual.html#introduction-usage
	 * @hint: check the ' | cat' for the call
	 */
	diffuse("diffuse", "\"$LOCAL\" \"$MERGED\" \"$REMOTE\" \"$BASE\"",
			"\"$LOCAL\" \"$MERGED\" \"$REMOTE\"", false),
	/**
	 * @see: http://www.elliecomputing.com/en/OnlineDoc/ecmerge_en/44205167.asp
	 */
	ecmerge("ecmerge",
			"--default --mode=merge3 \"$BASE\" \"$LOCAL\" \"$REMOTE\" --to=\"$MERGED\"",
			"--default --mode=merge2 \"$LOCAL\" \"$REMOTE\" --to=\"$MERGED\"",
			false),
	/**
	 * @see: https://www.gnu.org/software/emacs/manual/html_node/emacs/Overview-of-Emerge.html
	 * @hint: $(basename) command must be defined
	 */
	emerge("emacs",
			"-f emerge-files-with-ancestor-command \"$LOCAL\" \"$REMOTE\" \"$BASE\" \"$(basename \"$MERGED\")\"",
			"-f emerge-files-command \"$LOCAL\" \"$REMOTE\" \"$(basename \"$MERGED\")\"",
			true),
	/**
	 * @see: https://www.prestosoft.com/ps.asp?page=htmlhelp/edp/command_line_options
	 */
	examdiff("ExamDiff",
			"-merge \"$LOCAL\" \"$BASE\" \"$REMOTE\" -o:\"$MERGED\" -nh",
			"-merge \"$LOCAL\" \"$REMOTE\" -o:\"$MERGED\" -nh",
			false),
	/**
	 * @see: https://www.guiffy.com/help/GuiffyHelp/GuiffyCmd.html
	 */
	guiffy("guiffy", "-s \"$LOCAL\" \"$REMOTE\" \"$BASE\" \"$MERGED\"",
			"-m \"$LOCAL\" \"$REMOTE\" \"$MERGED\"", true),
	/**
	 * @see: http://vimdoc.sourceforge.net/htmldoc/diff.html
	 */
	gvimdiff("gvim",
			"-f -d -c '4wincmd w | wincmd J' \"$LOCAL\" \"$BASE\" \"$REMOTE\" \"$MERGED\"",
			"-f -d -c 'wincmd l' \"$LOCAL\" \"$MERGED\" \"$REMOTE\"",
			true),
	/**
	 * @see: http://vimdoc.sourceforge.net/htmldoc/diff.html
	 */
	gvimdiff2("gvim", "-f -d -c 'wincmd l' \"$LOCAL\" \"$MERGED\" \"$REMOTE\"",
			"-f -d -c 'wincmd l' \"$LOCAL\" \"$MERGED\" \"$REMOTE\"", true),
	/**
	 * @see: http://vimdoc.sourceforge.net/htmldoc/diff.html
	 */
	gvimdiff3("gvim",
			"-f -d -c 'hid | hid | hid' \"$LOCAL\" \"$REMOTE\" \"$BASE\" \"$MERGED\"",
			"-f -d -c 'hid | hid' \"$LOCAL\" \"$REMOTE\" \"$MERGED\"", true),
	/**
	 * @see: http://kdiff3.sourceforge.net/doc/documentation.html
	 */
	kdiff3("kdiff3",
			"--auto --L1 \"$MERGED (Base)\" --L2 \"$MERGED (Local)\" --L3 \"$MERGED (Remote)\" -o \"$MERGED\" \"$BASE\" \"$LOCAL\" \"$REMOTE\"",
			"--auto --L1 \"$MERGED (Local)\" --L2 \"$MERGED (Remote)\" -o \"$MERGED\" \"$LOCAL\" \"$REMOTE\"",
			true),
	/**
	 * @see: https://docs.kde.org/trunk5/en/kdesdk/kompare/commandline-options.html
	 * @hint: cannot merge
	 */
	// kompare("kompare", "\"$LOCAL\" \"$REMOTE\"", true),
	/**
	 * @see: http://meldmerge.org/help/file-mode.html
	 * @hint: use meld with output option only (new versions)
	 */
	meld("meld", "--output=\"$MERGED\" \"$LOCAL\" \"$BASE\" \"$REMOTE\"",
			"\"$LOCAL\" \"$MERGED\" \"$REMOTE\"",
			false),
	/**
	 * @see: http://www.manpagez.com/man/1/opendiff/
	 * @hint: check the ' | cat' for the call
	 */
	opendiff("opendiff",
			"\"$LOCAL\" \"$REMOTE\" -ancestor \"$BASE\" -merge \"$MERGED\"",
			"\"$LOCAL\" \"$REMOTE\" -merge \"$MERGED\"",
			false),
	/**
	 * @see: https://www.perforce.com/manuals/v15.1/cmdref/p4_merge.html
	 * @hint: check how to fix "no base present" / create_virtual_base problem
	 */
	p4merge("p4merge", "\"$BASE\" \"$REMOTE\" \"$LOCAL\" \"$MERGED\"",
			"\"$REMOTE\" \"$LOCAL\" \"$MERGED\"", false),
	/**
	 * @see: http://linux.math.tifr.res.in/manuals/man/tkdiff.html
	 */
	tkdiff("tkdiff", "-a \"$BASE\" -o \"$MERGED\" \"$LOCAL\" \"$REMOTE\"",
			"-o \"$MERGED\" \"$LOCAL\" \"$REMOTE\"",
			true),
	/**
	 * @see: https://tortoisegit.org/docs/tortoisegitmerge/tme-automation.html#tme-automation-basics
	 * @hint: merge without base is not supported
	 * @hint: cannot diff
	 */
	tortoisegitmerge("tortoisegitmerge",
			"-base \"$BASE\" -mine \"$LOCAL\" -theirs \"$REMOTE\" -merged \"$MERGED\"",
			null, false),
	/**
	 * @see: https://tortoisegit.org/docs/tortoisegitmerge/tme-automation.html#tme-automation-basics
	 * @hint: merge without base is not supported
	 * @hint: cannot diff
	 */
	tortoisemerge("tortoisemerge",
			"-base:\"$BASE\" -mine:\"$LOCAL\" -theirs:\"$REMOTE\" -merged:\"$MERGED\"",
			null, false),
	/**
	 * @see: http://vimdoc.sourceforge.net/htmldoc/diff.html
	 */
	vimdiff("vim", gvimdiff),
	/**
	 * @see: http://vimdoc.sourceforge.net/htmldoc/diff.html
	 */
	vimdiff2("vim", gvimdiff2),
	/**
	 * @see: http://vimdoc.sourceforge.net/htmldoc/diff.html
	 */
	vimdiff3("vim", gvimdiff3),
	/**
	 * @see: http://manual.winmerge.org/Command_line.html
	 * @hint: check how 'mergetool_find_win32_cmd "WinMergeU.exe" "WinMerge"'
	 *        works
	 */
	winmerge("WinMergeU",
			"-u -e -dl Local -dr Remote \"$LOCAL\" \"$REMOTE\" \"$MERGED\"",
			"-u -e -dl Local -dr Remote \"$LOCAL\" \"$REMOTE\" \"$MERGED\"",
			false),
	/**
	 * @see: http://furius.ca/xxdiff/doc/xxdiff-doc.html
	 */
	xxdiff("xxdiff",
			"-X --show-merged-pane -R 'Accel.SaveAsMerged: \"Ctrl+S\"' -R 'Accel.Search: \"Ctrl+F\"' -R 'Accel.SearchForward: \"Ctrl+G\"' --merged-file \"$MERGED\" \"$LOCAL\" \"$BASE\" \"$REMOTE\"",
			"-X -R 'Accel.SaveAsMerged: \"Ctrl+S\"' -R 'Accel.Search: \"Ctrl+F\"' -R 'Accel.SearchForward: \"Ctrl+G\"' --merged-file \"$MERGED\" \"$LOCAL\" \"$REMOTE\"",
			false);

	CommandLineMergeTool(String path, String parametersWithBase,
			String parametersWithoutBase,
			boolean exitCodeTrustable) {
		this.path = path;
		this.parametersWithBase = parametersWithBase;
		this.parametersWithoutBase = parametersWithoutBase;
		this.exitCodeTrustable = exitCodeTrustable;
    }

	CommandLineMergeTool(CommandLineMergeTool from) {
		this(from.getPath(), from.getParameters(true),
				from.getParameters(false), from.isExitCodeTrustable());
	}

	CommandLineMergeTool(String path, CommandLineMergeTool from) {
		this(path, from.getParameters(true), from.getParameters(false),
				from.isExitCodeTrustable());
	}

	private final String path;

	private final String parametersWithBase;

	private final String parametersWithoutBase;

	private final boolean exitCodeTrustable;

	/**
	 * @return path
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @param withBase
	 *            return parameters with base present?
	 * @return parameters with or without base present
	 */
	public String getParameters(boolean withBase) {
		if (withBase) {
			return parametersWithBase;
		}
		return parametersWithoutBase;
	}

	/**
	 * @return parameters
	 */
	public boolean isExitCodeTrustable() {
		return exitCodeTrustable;
	}

	/**
	 * @return true if command with base present is valid, false otherwise
	 */
	public boolean canMergeWithoutBasePresent() {
		return parametersWithoutBase != null;
	}

}

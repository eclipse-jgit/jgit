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
 * Pre-defined command line diff tools.
 *
 * Adds same diff tools as also pre-defined in C-Git
 * see "git-core\mergetools\"
 * see links to command line parameter description for the tools

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
 */
@SuppressWarnings("nls")
public enum CommandLineDiffTool {
	/**
	 * @see: https://www.araxis.com/merge/documentation-windows/command-line.en
	 */
	araxis("compare", "-wait -2 \"$LOCAL\" \"$REMOTE\""),
	/**
	 * @see: https://www.scootersoftware.com/v4help/index.html?command_line_reference.html
	 */
	bc("bcomp", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * @see: https://www.scootersoftware.com/v4help/index.html?command_line_reference.html
	 */
	bc3("bcompare", bc),
	/**
	 * @see: https://www.devart.com/codecompare/docs/index.html?comparing_via_command_line.htm
	 */
	codecompare("CodeCompare", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * @see: https://www.deltawalker.com/integrate/command-line
	 */
	deltawalker("DeltaWalker", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * @see: https://sourcegear.com/diffmerge/webhelp/sec__clargs__diff.html
	 */
	diffmerge("diffmerge", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * @see: http://diffuse.sourceforge.net/manual.html#introduction-usage
	 */
	diffuse("diffuse", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * @see: http://www.elliecomputing.com/en/OnlineDoc/ecmerge_en/44205167.asp
	 */
	ecmerge("ecmerge", "--default --mode=diff2 \"$LOCAL\" \"$REMOTE\""),
	/**
	 * @see: https://www.gnu.org/software/emacs/manual/html_node/emacs/Overview-of-Emerge.html
	 */
	emerge("emacs", "-f emerge-files-command \"$LOCAL\" \"$REMOTE\""),
	/**
	 * @see: https://www.prestosoft.com/ps.asp?page=htmlhelp/edp/command_line_options
	 */
	examdiff("ExamDiff", "\"$LOCAL\" \"$REMOTE\" -nh"),
	/**
	 * @see: https://www.guiffy.com/help/GuiffyHelp/GuiffyCmd.html
	 */
	guiffy("guiffy", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * @see: http://vimdoc.sourceforge.net/htmldoc/diff.html
	 */
	gvimdiff("gviewdiff", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * @see: http://vimdoc.sourceforge.net/htmldoc/diff.html
	 */
	gvimdiff2(gvimdiff),
	/**
	 * @see: http://vimdoc.sourceforge.net/htmldoc/diff.html
	 */
	gvimdiff3(gvimdiff),
	/**
	 * @see: http://kdiff3.sourceforge.net/doc/documentation.html
	 */
	kdiff3("kdiff3",
			"--L1 \"$MERGED (A)\" --L2 \"$MERGED (B)\" \"$LOCAL\" \"$REMOTE\""),
	/**
	 * @see: https://docs.kde.org/trunk5/en/kdesdk/kompare/commandline-options.html
	 */
	kompare("kompare", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * @see: http://meldmerge.org/help/file-mode.html
	 */
	meld("meld", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * @see: http://www.manpagez.com/man/1/opendiff/
	 * @hint: check the ' | cat' for the call
	 */
	opendiff("opendiff", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * @see: https://www.perforce.com/manuals/v15.1/cmdref/p4_merge.html
	 */
	p4merge("p4merge", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * @see: http://linux.math.tifr.res.in/manuals/man/tkdiff.html
	 */
	tkdiff("tkdiff", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * @see: https://tortoisegit.org/docs/tortoisegitmerge/tme-automation.html#tme-automation-basics
	 * @hint: cannot diff
	 */
	// tortoisemerge / tortoisegitmerge("tortoisegitmerge", "\"$LOCAL\"
	// \"$REMOTE\""),
	/**
	 * @see: http://vimdoc.sourceforge.net/htmldoc/diff.html
	 */
	vimdiff("viewdiff", gvimdiff),
	/**
	 * @see: http://vimdoc.sourceforge.net/htmldoc/diff.html
	 */
	vimdiff2(vimdiff),
	/**
	 * @see: http://vimdoc.sourceforge.net/htmldoc/diff.html
	 */
	vimdiff3(vimdiff),
	/**
	 * @see: http://manual.winmerge.org/Command_line.html
	 * @hint: check how 'mergetool_find_win32_cmd "WinMergeU.exe" "WinMerge"'
	 *        works
	 */
	winmerge("WinMergeU", "-u -e \"$LOCAL\" \"$REMOTE\""),
	/**
	 * @see: http://furius.ca/xxdiff/doc/xxdiff-doc.html
	 */
	xxdiff("xxdiff",
			"-R 'Accel.Search: \"Ctrl+F\"' -R 'Accel.SearchForward: \"Ctrl+G\"' \"$LOCAL\" \"$REMOTE\"");

	CommandLineDiffTool(String path, String parameters) {
		this.path = path;
		this.parameters = parameters;
	}

	CommandLineDiffTool(CommandLineDiffTool from) {
		this.path = from.getPath();
		this.parameters = from.getParameters();
	}

	CommandLineDiffTool(String path, CommandLineDiffTool from) {
		this.path = path;
		this.parameters = from.getParameters();
	}

	private final String path;

	private final String parameters;

	/**
	 * @return path
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @return parameters as one string
	 */
	public String getParameters() {
		return parameters;
	}

}

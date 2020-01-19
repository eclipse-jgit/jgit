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
 * <p>
 * see "git-core\mergetools\"
 * </p>
 * <p>
 * see links to command line parameter description for the tools
 * </p>
 *
 * <pre>
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
 * vimdiff
 * vimdiff2
 * vimdiff3
 * winmerge
 * xxdiff
 * </pre>
 *
 */
@SuppressWarnings("nls")
public enum CommandLineDiffTool {
	/**
	 * See: <a href=
	 * "url">https://www.araxis.com/merge/documentation-windows/command-line.en</a>
	 */
	araxis("compare", "-wait -2 \"$LOCAL\" \"$REMOTE\""),
	/**
	 * See: <a href=
	 * "url">https://www.scootersoftware.com/v4help/index.html?command_line_reference.html</a>
	 */
	bc("bcomp", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * See: <a href=
	 * "url">https://www.scootersoftware.com/v4help/index.html?command_line_reference.html</a>
	 */
	bc3("bcompare", bc),
	/**
	 * See: <a href=
	 * "url">https://www.devart.com/codecompare/docs/index.html?comparing_via_command_line.htm</a>
	 */
	codecompare("CodeCompare", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * See: <a href="url">https://www.deltawalker.com/integrate/command-line</a>
	 */
	deltawalker("DeltaWalker", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * See: <a href=
	 * "url">https://sourcegear.com/diffmerge/webhelp/sec__clargs__diff.html</a>
	 */
	diffmerge("diffmerge", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * See: <a href=
	 * "url">http://diffuse.sourceforge.net/manual.html#introduction-usage</a>
	 */
	diffuse("diffuse", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * See: <a href=
	 * "url">http://www.elliecomputing.com/en/OnlineDoc/ecmerge_en/44205167.asp</a>
	 */
	ecmerge("ecmerge", "--default --mode=diff2 \"$LOCAL\" \"$REMOTE\""),
	/**
	 * See: <a href=
	 * "url">https://www.gnu.org/software/emacs/manual/html_node/emacs/Overview-of-Emerge.html</a>
	 */
	emerge("emacs", "-f emerge-files-command \"$LOCAL\" \"$REMOTE\""),
	/**
	 * See: <a href=
	 * "url">https://www.prestosoft.com/ps.asp?page=htmlhelp/edp/command_line_options</a>
	 */
	examdiff("ExamDiff", "\"$LOCAL\" \"$REMOTE\" -nh"),
	/**
	 * See:
	 * <a href= "url">https://www.guiffy.com/help/GuiffyHelp/GuiffyCmd.html</a>
	 */
	guiffy("guiffy", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * See: <a href="url">http://vimdoc.sourceforge.net/htmldoc/diff.html</a>
	 */
	gvimdiff("gviewdiff", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * See: <a href="url">http://vimdoc.sourceforge.net/htmldoc/diff.html</a>
	 */
	gvimdiff2(gvimdiff),
	/**
	 * See: <a href="url">http://vimdoc.sourceforge.net/htmldoc/diff.html</a>
	 */
	gvimdiff3(gvimdiff),
	/**
	 * See:
	 * <a href= "url">http://kdiff3.sourceforge.net/doc/documentation.html</a>
	 */
	kdiff3("kdiff3",
			"--L1 \"$MERGED (A)\" --L2 \"$MERGED (B)\" \"$LOCAL\" \"$REMOTE\""),
	/**
	 * See: <a href=
	 * "url">https://docs.kde.org/trunk5/en/kdesdk/kompare/commandline-options.html</a>
	 */
	kompare("kompare", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * See: <a href="url">http://meldmerge.org/help/file-mode.html</a>
	 */
	meld("meld", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * See: <a href="url">http://www.manpagez.com/man/1/opendiff/</a>
	 * <p>
	 * Hint: check the ' | cat' for the call
	 * </p>
	 */
	opendiff("opendiff", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * See: <a href=
	 * "url">https://www.perforce.com/manuals/v15.1/cmdref/p4_merge.html</a>
	 */
	p4merge("p4merge", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * See:
	 * <a href= "url">http://linux.math.tifr.res.in/manuals/man/tkdiff.html</a>
	 */
	tkdiff("tkdiff", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 * See: <a href="url">http://vimdoc.sourceforge.net/htmldoc/diff.html</a>
	 */
	vimdiff("viewdiff", gvimdiff),
	/**
	 * See: <a href="url">http://vimdoc.sourceforge.net/htmldoc/diff.html</a>
	 */
	vimdiff2(vimdiff),
	/**
	 * See: <a href="url">http://vimdoc.sourceforge.net/htmldoc/diff.html</a>
	 */
	vimdiff3(vimdiff),
	/**
	 * See: <a href="url">http://manual.winmerge.org/Command_line.html</a>
	 * <p>
	 * Hint: check how 'mergetool_find_win32_cmd "WinMergeU.exe" "WinMerge"'
	 * works
	 * </p>
	 */
	winmerge("WinMergeU", "-u -e \"$LOCAL\" \"$REMOTE\""),
	/**
	 * See: <a href="url">http://furius.ca/xxdiff/doc/xxdiff-doc.html</a>
	 */
	xxdiff("xxdiff",
			"-R 'Accel.Search: \"Ctrl+F\"' -R 'Accel.SearchForward: \"Ctrl+G\"' \"$LOCAL\" \"$REMOTE\"");

	CommandLineDiffTool(String path, String parameters) {
		this.path = path;
		this.parameters = parameters;
	}

	CommandLineDiffTool(CommandLineDiffTool from) {
		this(from.getPath(), from.getParameters());
	}

	CommandLineDiffTool(String path, CommandLineDiffTool from) {
		this(path, from.getParameters());
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

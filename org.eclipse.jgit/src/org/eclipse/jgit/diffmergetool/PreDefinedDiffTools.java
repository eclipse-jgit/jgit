/*
 * Copyright (C) 2018-2019, Andre Bossert <andre.bossert@siemens.com>
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

package org.eclipse.jgit.diffmergetool;

/**
 * Pre-defined diff tools.
 *
 * Adds diff tools as defined in C-Git
 * see "git-core\mergetools\"
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
 * @since 5.3
 */
public enum PreDefinedDiffTools {
	/**
	 *
	 */
	araxis("compare", "-wait -2 \"$LOCAL\" \"$REMOTE\""), //$NON-NLS-1$ //$NON-NLS-2$
	/**
	 *
	 */
	bc("bcomp", "\"$LOCAL\" \"$REMOTE\""), //$NON-NLS-1$ //$NON-NLS-2$
	/**
	 *
	 */
	bc3("bcompare", "\"$LOCAL\" \"$REMOTE\""), //$NON-NLS-1$ //$NON-NLS-2$
	/**
	 *
	 */
	codecompare("CodeCompare", "\"$LOCAL\" \"$REMOTE\""), //$NON-NLS-1$ //$NON-NLS-2$
	/**
	 *
	 */
	deltawalker("DeltaWalker", "\"$LOCAL\" \"$REMOTE\""), //$NON-NLS-1$ //$NON-NLS-2$
	/**
	 *
	 */
	diffmerge("diffmerge", "\"$LOCAL\" \"$REMOTE\""), //$NON-NLS-1$ //$NON-NLS-2$
	/**
	 *
	 */
	diffuse("diffuse", "\"$LOCAL\" \"$REMOTE\""), //$NON-NLS-1$ //$NON-NLS-2$
	/**
	 *
	 */
	ecmerge("ecmerge", "--default --mode=diff2 \"$LOCAL\" \"$REMOTE\""), //$NON-NLS-1$ //$NON-NLS-2$
	/**
	 *
	 */
	emerge("emacs", "-f emerge-files-command \"$LOCAL\" \"$REMOTE\""), //$NON-NLS-1$ //$NON-NLS-2$
	/**
	 * TODO: check how 'mergetool_find_win32_cmd "ExamDiff.com" "ExamDiff Pro"'
	 * works
	 */
	examdiff("ExamDiff.com", "\"$LOCAL\" \"$REMOTE\" -nh"), //$NON-NLS-1$ //$NON-NLS-2$
	/**
	 *
	 */
	guiffy("guiffy", "\"$LOCAL\" \"$REMOTE\""), //$NON-NLS-1$ //$NON-NLS-2$
	/**
	 *
	 */
	gvimdiff("gvim", //$NON-NLS-1$
			"-R -f -d -c 'wincmd l' -c 'cd $GIT_PREFIX' \"$LOCAL\" \"$REMOTE\""), //$NON-NLS-1$
	/**
	 *
	 */
	gvimdiff2(gvimdiff),
	/**
	 *
	 */
	gvimdiff3(gvimdiff),
	/**
	 *
	 */
	kdiff3("kdiff3", //$NON-NLS-1$
			"--L1 \"$MERGED (A)\" --L2 \"$MERGED (B)\" \"$LOCAL\" \"$REMOTE\""), //$NON-NLS-1$
	/**
	 *
	 */
	kompare("kompare", "\"$LOCAL\" \"$REMOTE\""), //$NON-NLS-1$ //$NON-NLS-2$
	/**
	 *
	 */
	meld("meld", "\"$LOCAL\" \"$REMOTE\""), //$NON-NLS-1$ //$NON-NLS-2$
	/**
	 *
	 */
	opendiff("opendiff", "\"$LOCAL\" \"$REMOTE\""), //$NON-NLS-1$ //$NON-NLS-2$
	/**
	 *
	 * TODO: check how empty files are accepted (/dev/null)
	 */
	p4merge("p4merge", "\"$LOCAL\" \"$REMOTE\""), //$NON-NLS-1$ //$NON-NLS-2$
	/**
	 *
	 */
	tkdiff("tkdiff", "\"$LOCAL\" \"$REMOTE\""), //$NON-NLS-1$ //$NON-NLS-2$
	/**
	 * TODO: check if diff supported
	 */
	// tortoisemerge("tortoisegitmerge", "\"$LOCAL\" \"$REMOTE\""),
	/**
	 *
	 */
	vimdiff("vim", //$NON-NLS-1$
			"-R -f -d -c 'wincmd l' -c 'cd $GIT_PREFIX' \"$LOCAL\" \"$REMOTE\""), //$NON-NLS-1$
	/**
	 *
	 */
	vimdiff2(vimdiff),
	/**
	 *
	 */
	vimdiff3(vimdiff),
	/**
	 * TODO: check how 'mergetool_find_win32_cmd "WinMergeU.exe" "WinMerge"'
	 * works
	 */
	winmerge("WinMergeU", "-u -e \"$LOCAL\" \"$REMOTE\""), //$NON-NLS-1$ //$NON-NLS-2$
	/**
	 *
	 */
	xxdiff("xxdiff", //$NON-NLS-1$
			"-R 'Accel.Search: \"Ctrl+F\"' -R 'Accel.SearchForward: \"Ctrl+G\"' \"$LOCAL\" \"$REMOTE\""); //$NON-NLS-1$

	PreDefinedDiffTools(String path, String parameters) {
		this.path = path;
		this.parameters = parameters;
    }

	PreDefinedDiffTools(PreDefinedDiffTools from) {
		this.path = from.getPath();
		this.parameters = from.getParameters();
	}

	private String path;

	private String parameters;

	/**
	 * @return path
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @return parameters
	 */
	public String getParameters() {
		return parameters;
	}

}

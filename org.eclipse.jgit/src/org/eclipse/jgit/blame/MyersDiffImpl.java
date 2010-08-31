/*
 * Copyright (C) 2010, Benjamin Muskalla <bmuskalla@eclipsesource.com>
 * Copyright (C) 2008, Manuel Woelker <manuel.woelker@gmail.com>
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
package org.eclipse.jgit.blame;

import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.MyersDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.util.IntList;

/**
 *
 */
public class MyersDiffImpl implements IDiff {

	private static class EditDifference implements IDifference {

		private Edit edit;

		/**
		 * @param edit
		 */
		public EditDifference(Edit edit) {
			this.edit = edit;
		}

		public int getStartA() {
			return edit.getBeginA();
		}

		public int getLengthA() {
			return edit.getEndA() - edit.getBeginA();
		}

		public int getStartB() {
			return edit.getBeginB();
		}

		public int getLengthB() {
			return edit.getEndB() - edit.getBeginB();
		}

	}

	public IDifference[] diff(byte[] parentBytes, IntList parentLines,
			byte[] targetBytes, IntList targetLines) {
		RawText parentSeq = RawText.FACTORY.create(parentBytes);
		RawText targetSeq = RawText.FACTORY.create(targetBytes);
		MyersDiff myersDiff = new MyersDiff(parentSeq, targetSeq);
		EditList edits = myersDiff.getEdits();
		IDifference[] diffs = new IDifference[edits.size()];
		int d = 0;
		for (Edit edit : edits) {
			IDifference newDiff = new EditDifference(edit);
			diffs[d] = newDiff;
			d++;
		}
		return diffs;
	}

}

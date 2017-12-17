/*
 * Copyright (C) 2010, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.pgm;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.eclipse.jgit.awtui.CommitGraphPane;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.revplot.PlotWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;

@Command(usage = "usage_Glog")
class Glog extends RevWalkTextBuiltin {
	final JFrame frame;

	final CommitGraphPane graphPane;

	Glog() {
		frame = new JFrame();
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(final WindowEvent e) {
				frame.dispose();
			}
		});

		graphPane = new CommitGraphPane();

		final JScrollPane graphScroll = new JScrollPane(graphPane);

		final JPanel buttons = new JPanel(new FlowLayout());
		final JButton repaint = new JButton();
		repaint.setText(CLIText.get().repaint);
		repaint.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				graphPane.repaint();
			}
		});
		buttons.add(repaint);

		final JPanel world = new JPanel(new BorderLayout());
		world.add(buttons, BorderLayout.SOUTH);
		world.add(graphScroll, BorderLayout.CENTER);

		frame.getContentPane().add(world);
	}

	/** {@inheritDoc} */
	@Override
	protected int walkLoop() throws Exception {
		graphPane.getCommitList().source(walk);
		graphPane.getCommitList().fillTo(Integer.MAX_VALUE);

		frame.setTitle("[" + repoName() + "]"); //$NON-NLS-1$ //$NON-NLS-2$
		frame.pack();
		frame.setVisible(true);
		return graphPane.getCommitList().size();
	}

	/** {@inheritDoc} */
	@Override
	protected void show(final RevCommit c) throws Exception {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	protected RevWalk createWalk() {
		if (objects)
			throw die(CLIText.get().cannotUseObjectsWithGlog);
		final PlotWalk w = new PlotWalk(db);
		w.sort(RevSort.BOUNDARY, true);
		return w;
	}

	private String repoName() {
		final File gitDir = db.getDirectory();
		if (gitDir == null)
			return db.toString();
		String n = gitDir.getName();
		if (Constants.DOT_GIT.equals(n))
			n = gitDir.getParentFile().getName();
		return n;
	}
}

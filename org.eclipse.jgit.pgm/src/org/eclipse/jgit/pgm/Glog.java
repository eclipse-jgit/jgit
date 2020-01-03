/*
 * Copyright (C) 2010, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
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
			public void windowClosing(WindowEvent e) {
				frame.dispose();
			}
		});

		graphPane = new CommitGraphPane();

		final JScrollPane graphScroll = new JScrollPane(graphPane);

		final JPanel buttons = new JPanel(new FlowLayout());
		final JButton repaint = new JButton();
		repaint.setText(CLIText.get().repaint);
		repaint.addActionListener((ActionEvent e) -> {
			graphPane.repaint();
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
	protected void show(RevCommit c) throws Exception {
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

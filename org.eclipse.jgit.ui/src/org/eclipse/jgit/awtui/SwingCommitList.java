/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.awtui;

import java.awt.Color;
import java.util.LinkedList;

import org.eclipse.jgit.revplot.PlotCommitList;
import org.eclipse.jgit.revplot.PlotLane;

class SwingCommitList extends PlotCommitList<SwingCommitList.SwingLane> {
	final LinkedList<Color> colors;

	SwingCommitList() {
		colors = new LinkedList<>();
		repackColors();
	}

	private void repackColors() {
		colors.add(Color.green);
		colors.add(Color.blue);
		colors.add(Color.red);
		colors.add(Color.magenta);
		colors.add(Color.darkGray);
		colors.add(Color.yellow.darker());
		colors.add(Color.orange);
	}

	/** {@inheritDoc} */
	@Override
	protected SwingLane createLane() {
		final SwingLane lane = new SwingLane();
		if (colors.isEmpty())
			repackColors();
		lane.color = colors.removeFirst();
		return lane;
	}

	/** {@inheritDoc} */
	@Override
	protected void recycleLane(SwingLane lane) {
		colors.add(lane.color);
	}

	static class SwingLane extends PlotLane {
		private static final long serialVersionUID = 1L;
		Color color;
	}
}

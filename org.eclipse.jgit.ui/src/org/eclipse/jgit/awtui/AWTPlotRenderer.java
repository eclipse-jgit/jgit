/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
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

package org.eclipse.jgit.awtui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.io.Serializable;

import org.eclipse.jgit.awtui.CommitGraphPane.GraphCellRender;
import org.eclipse.jgit.awtui.SwingCommitList.SwingLane;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revplot.AbstractPlotRenderer;
import org.eclipse.jgit.revplot.PlotCommit;

final class AWTPlotRenderer extends AbstractPlotRenderer<SwingLane, Color>
		implements Serializable {
	private static final long serialVersionUID = 1L;

	final GraphCellRender cell;

	transient Graphics2D g;

	AWTPlotRenderer(final GraphCellRender c) {
		cell = c;
	}

	void paint(final Graphics in, final PlotCommit<SwingLane> commit) {
		g = (Graphics2D) in.create();
		try {
			final int h = cell.getHeight();
			g.setColor(cell.getBackground());
			g.fillRect(0, 0, cell.getWidth(), h);
			if (commit != null)
				paintCommit(commit, h);
		} finally {
			g.dispose();
			g = null;
		}
	}

	/** {@inheritDoc} */
	@Override
	protected void drawLine(final Color color, int x1, int y1, int x2,
			int y2, int width) {
		if (y1 == y2) {
			x1 -= width / 2;
			x2 -= width / 2;
		} else if (x1 == x2) {
			y1 -= width / 2;
			y2 -= width / 2;
		}

		g.setColor(color);
		g.setStroke(CommitGraphPane.stroke(width));
		g.drawLine(x1, y1, x2, y2);
	}

	/** {@inheritDoc} */
	@Override
	protected void drawCommitDot(final int x, final int y, final int w,
			final int h) {
		g.setColor(Color.blue);
		g.setStroke(CommitGraphPane.strokeCache[1]);
		g.fillOval(x, y, w, h);
		g.setColor(Color.black);
		g.drawOval(x, y, w, h);
	}

	/** {@inheritDoc} */
	@Override
	protected void drawBoundaryDot(final int x, final int y, final int w,
			final int h) {
		g.setColor(cell.getBackground());
		g.setStroke(CommitGraphPane.strokeCache[1]);
		g.fillOval(x, y, w, h);
		g.setColor(Color.black);
		g.drawOval(x, y, w, h);
	}

	/** {@inheritDoc} */
	@Override
	protected void drawText(final String msg, final int x, final int y) {
		final int texth = g.getFontMetrics().getHeight();
		final int y0 = (y - texth) / 2 + (cell.getHeight() - texth) / 2;
		g.setColor(cell.getForeground());
		g.drawString(msg, x, y0 + texth - g.getFontMetrics().getDescent());
	}

	/** {@inheritDoc} */
	@Override
	protected Color laneColor(final SwingLane myLane) {
		return myLane != null ? myLane.color : Color.black;
	}

	void paintTriangleDown(final int cx, final int y, final int h) {
		final int tipX = cx;
		final int tipY = y + h;
		final int baseX1 = cx - 10 / 2;
		final int baseX2 = tipX + 10 / 2;
		final int baseY = y;
		final Polygon triangle = new Polygon();
		triangle.addPoint(tipX, tipY);
		triangle.addPoint(baseX1, baseY);
		triangle.addPoint(baseX2, baseY);
		g.fillPolygon(triangle);
		g.drawPolygon(triangle);
	}

	/** {@inheritDoc} */
	@Override
	protected int drawLabel(int x, int y, Ref ref) {
		String txt;
		String name = ref.getName();
		if (name.startsWith(Constants.R_HEADS)) {
			g.setBackground(Color.GREEN);
			txt = name.substring(Constants.R_HEADS.length());
		} else if (name.startsWith(Constants.R_REMOTES)){
			g.setBackground(Color.LIGHT_GRAY);
			txt = name.substring(Constants.R_REMOTES.length());
		} else if (name.startsWith(Constants.R_TAGS)){
			g.setBackground(Color.YELLOW);
			txt = name.substring(Constants.R_TAGS.length());
		} else {
			// Whatever this would be
			g.setBackground(Color.WHITE);
			if (name.startsWith(Constants.R_REFS))
				txt = name.substring(Constants.R_REFS.length());
			else
				txt = name; // HEAD and such
		}
		if (ref.getPeeledObjectId() != null) {
			float[] colorComponents = g.getBackground().getRGBColorComponents(null);
			colorComponents[0] *= 0.9f;
			colorComponents[1] *= 0.9f;
			colorComponents[2] *= 0.9f;
			g.setBackground(new Color(colorComponents[0],colorComponents[1],colorComponents[2]));
		}
		if (txt.length() > 12)
			txt = txt.substring(0,11) + "\u2026"; // ellipsis "…" (in UTF-8) //$NON-NLS-1$

		final int texth = g.getFontMetrics().getHeight();
		int textw = g.getFontMetrics().stringWidth(txt);
		g.setColor(g.getBackground());
		int arcHeight = texth/4;
		int y0 = y - texth/2 + (cell.getHeight() - texth)/2;
		g.fillRoundRect(x , y0, textw + arcHeight*2, texth -1, arcHeight, arcHeight);
		g.setColor(g.getColor().darker());
		g.drawRoundRect(x, y0, textw + arcHeight*2, texth -1 , arcHeight, arcHeight);
		g.setColor(Color.BLACK);
		g.drawString(txt, x + arcHeight, y0 + texth - g.getFontMetrics().getDescent());

		return arcHeight * 3 + textw;
	}
}
